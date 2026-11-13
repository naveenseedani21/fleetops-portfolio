# FleetOps reviewer walkthrough

A five-minute walkthrough of the implemented device/compliance boundary. Use only the local synthetic fleet. Hosting is deferred; this project does not enroll or control physical Apple devices.

## Prepare an isolated stack

Requirements: Docker Engine with Compose, Node22, Python3 and the repository checkout. Existing native services may remain running. From the repository root:

```sh
python3 scripts/prepare-reviewer.py --project fleetops-reviewer-review
```

Run the two exact Compose commands printed by the generator. It derives the setup from the documented root Compose file, preserving the service images, application builds and realm roles. It creates a new fixture directory and refuses to overwrite one. Separate project names give separate database/identity volumes. Ports are fixed for this reviewer stack: frontend14200, backend18080, Keycloak18180, DynamoDB18000. Only one reviewer stack can use these ports at a time; stop a prior reviewer stack normally before starting another, without deleting its volumes.

If Docker reports `Input/output error` reading a bind-mounted file from a synced checkout on macOS, prepare the fixtures on local storage:

```sh
python3 scripts/prepare-reviewer.py --project fleetops-reviewer-review --output /private/tmp/fleetops-reviewer-review
```

Use the printed configuration path consistently. It copies only the public development realm, init script, frontend configuration and nginx configuration; source builds still use the checkout. The database and identity named volumes are unchanged if the project name is retained. Never use `down --volumes` on the existing application or reviewer data.

Wait for these checks before signing in:

```sh
curl -fsS http://localhost:18080/actuator/health/readiness
curl -fsS http://localhost:18180/realms/fleetops/.well-known/openid-configuration > /dev/null
curl -fsS http://localhost:14200/config.json
```

## Five-minute narrative

1. **Identity and provisioning (one minute).** Open `http://localhost:14200`, sign in as `admin` / `local-admin-only`, and register a synthetic Mac. Point out lifecycle REGISTERED and compliance UNKNOWN. Registration requires an administrator; report credentials cannot provision devices.
2. **Policy and failure (one minute).** Create a Mac baseline requiring disk encryption and minimum OS17.0. Submit a machine report with OS16.0 and encryption disabled. Open the device: lifecycle ACTIVE, compliance NONCOMPLIANT, immutable evidence containing the policy version and failing rules. ACTIVE describes the device lifecycle, not its security posture.
3. **Recovery and integrity (one minute).** Submit the next report with OS17.0 and encryption enabled. Compliance becomes COMPLIANT while lifecycle stays ACTIVE. Expand the new evidence and show the older failure still present. Retry the first accepted report: current state remains the recovered state, with no additional evidence. Reusing that ID with changed content conflicts.
4. **Authorization (one minute).** Show the automated denial assertions: machine registration/policy writes are forbidden, unknown devices are not auto-created, and the machine cannot report to an existing device in `lab`. Sign in as `viewer` / `local-viewer-only`: inventory/policies remain readable and mutation controls are absent; API write attempts are also denied.
5. **Engineering evidence (one minute).** Open SPEC_AUDIT's latest checkpoint, the restart integration test and actual GitHub run. Explain the retained standard-library simulator, atomic receipt/evidence/audit transaction, and history compatibility cost. Describe only the execution results recorded in VALIDATION; do not call a startup-blocked CI run successful.

The executable browser test performs this sequence with real OIDC login, service credentials and database persistence. It also retires its synthetic device at the end and verifies later reports/evaluations cannot reactivate it. For a live narrated walkthrough, stop before that final retirement if you want to leave the device active.

## Execute the browser acceptance suite

```sh
cd frontend
npm ci
npx playwright install chromium
FLEETOPS_E2E_WEB=http://localhost:14200 \
FLEETOPS_E2E_API=http://localhost:18080 \
FLEETOPS_E2E_ISSUER=http://localhost:18180/realms/fleetops \
npm run e2e
```

Omit the environment overrides to test the existing native4200/8080/8180 stack after its verified backend restart. Tests use the two public local users and the public local simulator client secret. They never mock the backend, JWT verification or database. They create uniquely named devices/policies and retain those records; repeat runs consume the documented20-policy limit, so use a fresh isolated project for a new demonstration rather than deleting existing data or relaxing the cap.

Screenshots are written to Playwright's ignored `frontend/test-results/` directory: `compliance-failure.png`, `compliance-recovery.png` and `viewer-policies.png`. Copy only genuinely passing-run images to `docs/screenshots/` with their environment/date recorded. Traces can include session tokens; do not commit or publicly share them.

For the Python provisioning/reporting commands and existing state compatibility, see [SIMULATOR.md](SIMULATOR.md). For exact ordering, receipt retention, 401 behavior and pre-upgrade limitations, see [API.md](api/API.md).
