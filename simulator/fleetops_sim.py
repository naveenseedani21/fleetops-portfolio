"""Synthetic Apple inventory; never executes commands on real devices."""
import argparse
from datetime import datetime, timedelta, timezone
import json
import os
from pathlib import Path
import random
import time
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, urlparse, quote
from urllib.request import Request, urlopen
import uuid


def fleet(count: int, seed: int):
    rng = random.Random(seed)
    for index in range(count):
        kind = rng.choice(["MAC", "IPHONE", "IPAD"])
        os_name, model, version = {"MAC": ("macOS", "Synthetic MacBook Pro", "15.10"), "IPHONE": ("iOS", "Synthetic iPhone", "18.3"), "IPAD": ("iPadOS", "Synthetic iPad", "18.3")}[kind]
        yield {"name": f"Demo {kind.title()} {index + 1:03}", "type": kind, "model": model, "os": os_name, "osVersion": version, "serial": f"SYN-{seed}-{index:05}", "employee": f"Synthetic Employee {index + 1:03}"}


def telemetry(device, sequence, scenario):
    return {"reportId": str(uuid.uuid4()), "sequence": sequence,
            "reportedAt": (datetime.now(timezone.utc) - timedelta(days=7 if scenario == "inactive" else 0)).isoformat(),
            "osVersion": "12.0" if scenario == "outdated" else device["osVersion"],
            "encryption": scenario != "noncompliant", "screenLock": True, "firewall": device["type"] == "MAC"}


class Client:
    def __init__(self, api, issuer, org, secret, delay):
        self.api, self.issuer, self.org = api.rstrip('/'), issuer.rstrip('/'), org
        self.secret, self.delay, self.token, self.expires = secret, delay, None, 0

    def request(self, method, path, body):
        if time.monotonic() >= self.expires:
            data = urlencode({"grant_type": "client_credentials", "client_id": "fleetops-simulator", "client_secret": self.secret}).encode()
            with urlopen(Request(self.issuer + '/protocol/openid-connect/token', data=data), timeout=15) as response:
                auth = json.load(response)
            self.token = auth['access_token']
            self.expires = time.monotonic() + auth['expires_in'] - 30
        request = Request(self.api + '/api/v1/organizations/' + quote(self.org, safe='') + '/' + path, data=json.dumps(body).encode(), method=method, headers={'Content-Type': 'application/json', 'Authorization': 'Bearer ' + self.token})
        for attempt in range(3):
            time.sleep(self.delay)
            try:
                with urlopen(request, timeout=15) as response:
                    return json.load(response)
            except HTTPError as error:
                if error.code not in (429, 502, 503, 504) or attempt == 2:
                    raise RuntimeError(f'API returned HTTP {error.code}; state preserved for inspection') from None
            except URLError:
                if attempt == 2:
                    raise RuntimeError('Network request failed; state preserved') from None
            time.sleep(2 ** attempt)


def save(path, state):
    temporary = path.with_suffix('.tmp')
    temporary.write_text(json.dumps(state, indent=2) + '\n')
    temporary.replace(path)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['register', 'check-in'])
    parser.add_argument('--count', type=int, default=20)
    parser.add_argument('--seed', type=int, default=42)
    parser.add_argument('--scenario', choices=['healthy', 'noncompliant', 'outdated', 'inactive'], default='healthy')
    parser.add_argument('--api', default='http://localhost:8080')
    parser.add_argument('--issuer', default='http://localhost:8180/realms/fleetops')
    parser.add_argument('--org', default='demo')
    parser.add_argument('--state', type=Path, default=Path('.fleetops-state.json'))
    parser.add_argument('--delay', type=float, default=0.25)
    args = parser.parse_args()
    if not 1 <= args.count <= 1000 or args.delay < 0.2:
        parser.error('count must be 1–1000 and delay must be at least 0.2 seconds')
    for url in (args.api, args.issuer):
        parsed = urlparse(url)
        if parsed.scheme != 'https' and not (parsed.scheme == 'http' and parsed.hostname in ('localhost', '127.0.0.1')):
            parser.error('HTTPS required except for loopback development URLs')
    secret = os.environ.get('FLEETOPS_CLIENT_SECRET')
    if not secret:
        parser.error('Set FLEETOPS_CLIENT_SECRET; never use an administrator credential')
    client = Client(args.api, args.issuer, args.org, secret, args.delay)
    state = json.loads(args.state.read_text()) if args.state.exists() else {'api': args.api, 'org': args.org, 'devices': []}
    if state['api'] != args.api or state['org'] != args.org:
        parser.error('State belongs to another API or organization; choose a different state file')
    try:
        if args.command == 'register':
            known = {d['serial'] for d in state['devices']}
            for device in fleet(args.count, args.seed):
                if device['serial'] in known:
                    continue
                registered = client.request('POST', 'devices', device)
                state['devices'].append(registered)
                save(args.state, state)
                print('Registered', registered['name'])
        else:
            for device in state['devices'][:args.count]:
                # Save a pending report before sending; an interrupted run replays the same payload.
                report = device.get('pending') or telemetry(device, device.get('sequence', 0) + 1, args.scenario)
                device['pending'] = report
                save(args.state, state)
                updated = client.request('POST', 'devices/' + device['id'] + '/reports', report)
                device.clear()
                device.update(updated)
                save(args.state, state)
                print('Checked in', device['name'], device['compliance'])
    except (RuntimeError, HTTPError, URLError) as error:
        parser.exit(1, str(error) + '\n')


if __name__ == '__main__':
    main()
