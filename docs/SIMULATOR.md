# Simulator responsibilities and operation

The simulator generates synthetic Apple inventory and reports through authenticated HTTP. It does not access DynamoDB directly, manage physical devices, administer identity, change policies, or grant itself permissions. Its standard-library `argparse`/`urllib` implementation is an explicitly approved A1 deviation: retain a small, inspectable client while demonstrating reliability. Pytest and Ruff are development dependencies; HTTPX, Pydantic and Typer are not required without a concrete benefit and compatibility review.

## Separate provisioning and reporting

An organization administrator provisions the fleet. The machine identity then reports only for existing devices in its organization and any configured device scope. The backend enforces both boundaries. A viewer or machine cannot register; an unknown device report returns 404 without creating a device. There is no silent elevation or administrator password in the simulator.

1. Sign in to the local application as an administrator. Obtain the short-lived **access token** from that authenticated session using browser developer tools (the `access_token` session-storage entry for `http://localhost:4200`). Never use the ID token, save the token in source/state files, or paste it into a committed script or command argument.
2. In a Bash or Zsh terminal, enter the token into a hidden prompt and provision:

```sh
printf 'Administrator access token: '
read -rs FLEETOPS_ADMIN_TOKEN
printf '\n'
export FLEETOPS_ADMIN_TOKEN
python3 simulator/fleetops_sim.py register --count 100 --seed 42
unset FLEETOPS_ADMIN_TOKEN
```

3. Configure policies through the administrator UI. Report using the separate machine credential:

```sh
# Intentionally public local-realm fixture; never use for a hosted realm.
export FLEETOPS_CLIENT_SECRET=local-simulator-secret-not-for-production
python3 simulator/fleetops_sim.py check-in --count 100
python3 simulator/fleetops_sim.py check-in --count 10 --scenario noncompliant
```

The provisioning command uses only the supplied administrator token, even if a machine secret is set. `check-in` uses only client credentials, even if an administrator token remains in the environment. Expired administrator tokens fail without fallback; obtain a fresh session token and rerun. Successful registrations already saved in the state file are skipped by serial. Preserve that file and existing records.

For organizations other than `demo`, use `--org` and a matching separate `--state` file and authorized credentials. A token's organization claim must authorize that organization. Optional signed `device_scopes` is described in [permissions](security/PERMISSIONS.md); the client cannot broaden it.

## Compatibility and failure behavior

Existing commands (`register`, `check-in`), flags, defaults, state filename, device payloads and unknown root-level state fields remain compatible. The intentional A2 change is that `register` now requires `FLEETOPS_ADMIN_TOKEN`; the machine secret no longer authorizes registration. Existing state files need no conversion. Counts remain 1–1,000, with delay at least 0.2 seconds; non-finite delays are rejected. HTTPS is required except for loopback development; credential-bearing URLs, queries, fragments and redirects are rejected.

Seeded inventory is deterministic, including type/model/version/serial/employee labels. Report IDs and observation times are intentionally fresh. A report is saved as `pending` before transmission; retries and interrupted-run recovery reuse its exact ID, sequence and payload. A successful response replaces that device entry while retaining the root state format. An error leaves pending state available for inspection/retry.

Each request has a 15-second timeout. HTTP 429/502/503/504 and network connection/timeout failures receive at most three attempts with pacing and exponential backoff plus bounded jitter. Validation/authentication/authorization/conflict failures do not retry. Tokens are cached and renewed before expiry. Redirects never forward access tokens or client secrets. Errors identify the service and status without reflecting response bodies, transport diagnostics or credentials.

Limits: one process should own a state file; there is no concurrent writer lock. Registration after an ambiguous network failure may encounter a serial conflict; stop and reconcile the existing record/state, never delete or recreate it automatically. Reports are complete snapshots. The `inactive` scenario backdates a report seven days and requires a device with no newer check-in. Inactivity policies affect compliance; no inactivity scheduler changes lifecycle. The backend only remembers the latest report ID/hash, so it does not offer indefinite report-ID deduplication. Maximum fleet/report counts and policy caps remain unchanged.

## Automated evidence

Run `python -m pip install -e 'simulator[dev]'` in a virtual environment, `ruff check simulator`, `python -m pytest simulator -q`, and the retained `python -m unittest discover -s simulator -v` from the repository root. Compatibility tests were added and passed against the pre-change simulator before client behavior changed. The pytest suite covers deterministic generation, CLI/state compatibility, credential separation, exact pending replay, timeout/retry bounds, token renewal, permanent errors and redirect rejection.

`SimulatorWorkflowTest` in the backend suite starts a real Spring HTTP server, an ephemeral signed-JWT identity fixture and a dedicated local DynamoDB test table. It launches `simulator/scenarios/verify_reports.py`, which runs administrator registration followed by machine-only CLI reporting, checks failing→passing compliance, and submits exact/altered duplicates and stale sequence/timestamp reports. It reads persisted device/evaluation/audit records to verify rejected requests do not mutate them. This tests the actual Python/HTTP/backend/database path; it is not a production identity-provider or full browser test. The scenario requires its test-created tenant and credentials and is not a command to run against the application fleet.
