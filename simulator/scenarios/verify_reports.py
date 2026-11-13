"""Isolated HTTP acceptance scenario, launched by SimulatorWorkflowTest.

Requires that test's random tenant, temporary state, signed tokens and local API.
Never targets the application fleet or a cloud database.
"""
import json
import os
from pathlib import Path
import subprocess
import sys
from datetime import datetime, timedelta, timezone
from urllib.request import Request

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from fleetops_sim import Client, telemetry, urlopen  # noqa: E402


def main():
    api, issuer, org, state_path = sys.argv[1:]
    assert api.startswith('http://127.0.0.1:')
    assert org.startswith('sim-test-')
    admin_token = os.environ['FLEETOPS_ADMIN_TOKEN']
    admin = Client(api, issuer, org, None, 0.2, admin_token=admin_token)
    machine = Client(api, issuer, org, os.environ['FLEETOPS_CLIENT_SECRET'], 0.2)
    base = api + '/api/v1/organizations/' + org

    def read(path):
        with urlopen(Request(base + path, headers={'Authorization': 'Bearer ' + admin_token}), timeout=15) as result:
            return json.load(result)

    def denied(client, path, body, status):
        try:
            client.request('POST', path, body)
        except RuntimeError as error:
            assert f'HTTP {status}' in str(error), str(error)
        else:
            raise AssertionError('Unauthorized or stale request unexpectedly succeeded')

    policy = {'version': 0, 'name': 'Encryption baseline', 'targetTypes': ['MAC', 'IPHONE', 'IPAD'],
              'rules': [{'type': 'ENCRYPTION', 'value': 'true'}], 'severity': 'HIGH', 'enabled': True}
    admin.request('POST', 'policies', policy)
    cli = [sys.executable, str(Path(__file__).resolve().parents[1] / 'fleetops_sim.py')]
    args = ['--count', '1', '--seed', '1', '--api', api, '--issuer', issuer, '--org', org,
            '--state', state_path, '--delay', '0.2']
    provision_env = dict(os.environ)
    provision_env.pop('FLEETOPS_CLIENT_SECRET', None)
    subprocess.run([*cli, 'register', *args], env=provision_env, check=True, timeout=30)
    report_env = dict(os.environ)
    report_env.pop('FLEETOPS_ADMIN_TOKEN', None)
    subprocess.run([*cli, 'check-in', *args], env=report_env, check=True, timeout=30)
    device = json.loads(Path(state_path).read_text())['devices'][0]
    assert device['status'] == 'ACTIVE' and device['compliance'] == 'COMPLIANT'
    path = '/devices/' + device['id']
    report_path = path.lstrip('/') + '/reports'
    failed_report = telemetry(device, 2, 'noncompliant')
    failed = machine.request('POST', report_path, failed_report)
    assert failed['status'] == 'ACTIVE' and failed['compliance'] == 'NONCOMPLIANT'
    summary = read('/dashboard/summary')
    assert summary['active'] == 1 and summary['noncompliant'] == 1
    before = (read(path), read(path + '/evaluations'), read('/audit-events'))
    assert machine.request('POST', report_path, failed_report) == failed
    denied(machine, report_path, {**failed_report, 'encryption': True}, 409)
    assert before == (read(path), read(path + '/evaluations'), read('/audit-events'))
    recovered_report = telemetry(device, 3, 'healthy')
    recovered = machine.request('POST', report_path, recovered_report)
    assert recovered['status'] == 'ACTIVE' and recovered['compliance'] == 'COMPLIANT'
    before = (read(path), read(path + '/evaluations'), read('/audit-events'))
    denied(machine, report_path, failed_report, 409)
    stale_time = {**recovered_report, 'reportId': 'stale-time', 'sequence': 4,
                  'reportedAt': (datetime.now(timezone.utc) - timedelta(days=1)).isoformat()}
    denied(machine, report_path, stale_time, 400)
    denied(machine, 'devices', {key: device[key] for key in ('name', 'type', 'model', 'os', 'serial')}, 403)
    denied(machine, 'policies', policy, 403)
    denied(machine, 'devices/unknown/reports', recovered_report, 404)
    assert before == (read(path), read(path + '/evaluations'), read('/audit-events'))
    assert len(before[1]['items']) == 3
    assert before[0]['sequence'] == 3
    print('Verified administrator provisioning, machine reporting, compliance recovery, duplicate/stale rejection and unchanged persisted evidence.')


if __name__ == '__main__':
    main()
