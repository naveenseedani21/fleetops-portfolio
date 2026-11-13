# Testing FleetOps safely

Inspect environment variables/targets before tests. Do not print secrets. Use local dummy AWS credentials, not a configured cloud profile. Backend unit/integration fixtures must not mutate the application's `fleetops` table or a remote environment. The separately authorized browser acceptance suite deliberately creates synthetic records through the running local application; prefer its isolated reviewer stack.

## Backend

With Java21/Maven, Python3.11+ and Docker:

```sh
AWS_ACCESS_KEY_ID=local AWS_SECRET_ACCESS_KEY=local AWS_REGION=us-east-1 \
  mvn -B -f backend/pom.xml spotless:check verify
```

FleetIntegrationTest, SimulatorWorkflowTest and ReportRestartTest each start an in-memory DynamoDB Local Testcontainer by default. If Docker is unavailable, start DynamoDB Local separately, then explicitly point at loopback:

```sh
TEST_DYNAMODB_ENDPOINT=http://localhost:8000 \
AWS_ACCESS_KEY_ID=local AWS_SECRET_ACCESS_KEY=local AWS_REGION=us-east-1 \
  mvn -B -f backend/pom.xml spotless:check verify
```

The override must point to a disposable local emulator. The integration suite creates random `fleetops-test-UUID`, `fleetops-sim-test-UUID` and restart-test tables and randomized tenants. With an external emulator it leaves that test table behind; it does not delete existing tables. No cleanup or remote reset is implicit. TokenValidationTest uses an ephemeral loopback JWKS server, and HealthProbeTest substitutes the DynamoDB client beneath the real health indicator to simulate outages without stopping a database.

Tests cover actual DynamoDB condition/transaction/query behavior, but local emulation does not prove production IAM, AWS throttling, Docker image startup or deployed network behavior. Spring Security's MockMvc JWT helper tests authorization, while TokenValidationTest separately exercises real RSA signatures/claims. No coverage percentage is measured until JaCoCo is added.

## Frontend and simulator

```sh
cd frontend
npm ci
npm run format:check
npm run lint
npm test
npm run build
cd ..
python3 -m venv .venv
. .venv/bin/activate
python -m pip install -e 'simulator[dev]'
ruff check simulator
python -m pytest simulator -q
python -m unittest discover -s simulator -v
```

Use Node22 and the committed lockfile. Frontend component mocks test form, retry and response-ordering behavior; they do not prove server authorization. Simulator unit tests use in-memory responses and a short-lived loopback redirect fixture; they do not call an external identity provider or API. The separate backend SimulatorWorkflowTest runs the real Python CLI over HTTP with signed JWTs and DynamoDB Local, exercising provisioning, restricted reporting, compliance recovery and unchanged state/evidence after duplicate/stale submissions. It requires the sibling simulator directory and python3 executable. CI installs Python explicitly for this test.

## Browser and infrastructure

`cd frontend && npm run e2e` expects local services on ports4200/8080/8180 and deliberately creates synthetic records. Read e2e/fleet.spec.ts before running. It exercises administrator UI provisioning, real machine reports, minimum-OS/encryption failure and recovery, retained evidence, replay/conflict/stale handling, viewer API/UI restrictions, machine provisioning/policy denials, an existing cross-tenant device and retirement invariance. It compares persisted state/evidence/audit around rejected requests. See DEMO.md for isolated ports and host-run commands; these scenarios require actual execution before claiming a pass. `npx playwright test --list` discovers tests without launching a browser and is not an execution result.

`docker compose config --quiet` validates configuration; `docker compose up --build` is a separate mutable local startup check. Do not use `down --volumes` without explicit approval to erase local data. CI uses isolated hosted-runner volumes and cleans those fixtures only.

Terraform: `terraform -chdir=infrastructure/terraform init -backend=false`, `fmt -check`, then `validate`. Initialization downloads providers; validate does not create resources. Do not run apply/destroy or cloud load tests without owner approval.

See SPEC_AUDIT.md for current baseline/remediation results and VALIDATION.md for earlier evidence. CI startup, Docker builds, full Playwright and cloud deployment are separate claims.

ReportRestartTest starts and stops only its own two child JVMs, using the same isolated table and real signed JWT HTTP requests. It never controls the developer server on8080. History regression fixtures test mixed timestamp precision, equal-time ID tie-breaking and multiple database/API pages; they do not claim frozen-snapshot pagination during concurrent writes.
