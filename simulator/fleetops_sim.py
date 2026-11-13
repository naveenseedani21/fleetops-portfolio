"""Synthetic Apple inventory; never executes commands on real devices."""
import argparse
from datetime import datetime, timedelta, timezone
import json
import math
import os
from pathlib import Path
import random
import time
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, urlparse, quote
from urllib.request import Request, HTTPRedirectHandler, build_opener
import uuid


def fleet(count: int, seed: int):
    rng = random.Random(seed)
    for index in range(count):
        kind = rng.choice(["MAC", "IPHONE", "IPAD"])
        os_name, model, version = {"MAC": ("macOS", "MacBook Pro", "15.10"), "IPHONE": ("iOS", "iPhone", "18.3"), "IPAD": ("iPadOS", "iPad", "18.3")}[kind]
        yield {"name": f"{model} {index + 1:03}", "type": kind, "model": model, "os": os_name, "osVersion": version, "serial": f"SYN-{seed}-{index:05}", "employee": f"Employee {index + 1:03}"}


def telemetry(device, sequence, scenario):
    return {"reportId": str(uuid.uuid4()), "sequence": sequence,
            "reportedAt": (datetime.now(timezone.utc) - timedelta(days=7 if scenario == "inactive" else 0)).isoformat(),
            "osVersion": "12.0" if scenario == "outdated" else device["osVersion"],
            "encryption": scenario != "noncompliant", "screenLock": True, "firewall": device["type"] == "MAC"}


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        # Neither access tokens nor client secrets may follow a redirect.
        return None


_opener = build_opener(NoRedirect())


def urlopen(request, timeout):
    return _opener.open(request, timeout=timeout)


class Client:
    def __init__(self, api, issuer, org, secret, delay, admin_token=None):
        self.api, self.issuer, self.org = api.rstrip('/'), issuer.rstrip('/'), org
        self.secret, self.delay, self.token, self.expires = secret, delay, None, 0
        self.admin_token = admin_token

    def _send(self, request, purpose):
        for attempt in range(3):
            time.sleep(self.delay)
            try:
                with urlopen(request, timeout=15) as response:
                    return json.load(response)
            except HTTPError as error:
                code = error.code
                error.close()
                if code not in (429, 502, 503, 504) or attempt == 2:
                    raise RuntimeError(f'{purpose} returned HTTP {code}; state preserved') from None
            except (URLError, TimeoutError, ConnectionError):
                if attempt == 2:
                    raise RuntimeError(f'{purpose} request failed; state preserved') from None
            except (ValueError, UnicodeError):
                raise RuntimeError(f'{purpose} returned invalid JSON; state preserved') from None
            time.sleep(2 ** attempt + random.uniform(0, 0.25))

    def request(self, method, path, body):
        if self.admin_token is not None:
            token = self.admin_token
        else:
            if time.monotonic() >= self.expires:
                data = urlencode({"grant_type": "client_credentials", "client_id": "fleetops-simulator", "client_secret": self.secret}).encode()
                auth = self._send(Request(self.issuer + '/protocol/openid-connect/token', data=data), 'Identity service')
                try:
                    token, lifetime = auth['access_token'], float(auth['expires_in'])
                    if not isinstance(token, str) or not token or not math.isfinite(lifetime) or lifetime <= 0:
                        raise ValueError('Invalid token fields')
                except (KeyError, TypeError, ValueError):
                    raise RuntimeError('Identity service returned an invalid token response') from None
                self.token = token
                self.expires = time.monotonic() + max(0, lifetime - 30)
            token = self.token
        request = Request(self.api + '/api/v1/organizations/' + quote(self.org, safe='') + '/' + path,
                          data=json.dumps(body).encode(), method=method,
                          headers={'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token})
        return self._send(request, 'API')


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
    if not 1 <= args.count <= 1000 or (not math.isfinite(args.delay) or args.delay < 0.2):
        parser.error('count must be 1–1000 and delay must be at least 0.2 seconds')
    for url in (args.api, args.issuer):
        parsed = urlparse(url)
        if parsed.username or parsed.password or parsed.query or parsed.fragment:
            parser.error('Target URLs must not contain credentials, queries or fragments')
        if parsed.scheme != 'https' and not (parsed.scheme == 'http' and parsed.hostname in ('localhost', '127.0.0.1')):
            parser.error('HTTPS required except for loopback development URLs')
    secret = os.environ.get('FLEETOPS_CLIENT_SECRET')
    admin_token = os.environ.get('FLEETOPS_ADMIN_TOKEN') if args.command == 'register' else None
    if args.command == 'register' and not admin_token:
        parser.error('Registration requires FLEETOPS_ADMIN_TOKEN: a short-lived administrator access token')
    if args.command == 'check-in' and not secret:
        parser.error('Set FLEETOPS_CLIENT_SECRET for report-only machine authentication')
    client = Client(args.api, args.issuer, args.org, secret, args.delay, admin_token=admin_token)
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
