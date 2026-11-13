# FleetOps

A device inventory and security compliance platform. Spring Boot and Angular simulate Apple fleet workflows using persistent DynamoDB records, OIDC identities, versioned policies, and transactional audit evidence. **This is not Apple MDM and cannot control real devices.**

## Current build

A reviewer-focused engineering demonstration of inventory, authorization and compliance workflows. Inventory registration/detail/update/retirement, tenant-scoped reads, cursor pagination, six compliance rule types, immutable policy versions, authenticated telemetry, replay/stale-write protection and audit history are implemented. Angular provides an Operations workspace with a compact navigation rail, inventory and device inspector, detail, policy, audit and a bounded live overview. The Python simulator generates synthetic fleets.

Not complete: organization/member administration, platform administration, indexed search/filtering, materialized large-fleet reporting and historical trends, automatic policy-change re-evaluation, persistent authorization-denial audit, full Terraform application hosting, and comprehensive accessibility review. Policy UI supports multiple requirements and target types, plus paginated immutable version history. No cloud deployment has occurred.

Start with the [five-minute reviewer walkthrough](docs/DEMO.md), [validation record](docs/VALIDATION.md), and [specification audit](docs/SPEC_AUDIT.md). A1–A3 decisions remain preserved; hosting is deferred.

![Operations inventory with real device selection and independent lifecycle and compliance](docs/screenshots/operations-inventory.jpg)

## Stack

Java 21; Spring Boot 3.5.16; AWS Java SDK v2 2.59.0; Spring Security resource server; Springdoc 2.8.17; Angular 21; Angular Material 21; TypeScript 5.9; Node 22 LTS; Python 3.11+; DynamoDB; Keycloak 26.8.2; Maven; JUnit/Mockito/Testcontainers; Vitest; Playwright.

Frontend exact resolved versions are recorded in `frontend/package-lock.json`. Use `npm ci`. No global CLI installation is required.

```mermaid
flowchart LR
  Browser[Angular SPA] -->|JWT / REST| API[Spring Boot]
  Simulator[Python simulator] -->|Service JWT| API
  OIDC[Keycloak / OIDC] --> Browser
  OIDC --> Simulator
  API -->|Queries and transactions| DB[DynamoDB]
  API --> Logs[Structured logs / Actuator]
```

## Local setup

Prerequisites: Docker Engine/Desktop with Compose. Local credentials below are intentionally public demo credentials, valid only for the loopback-bound development realm.

```sh
git clone https://github.com/naveenseedani21/fleetops.git
cd fleetops
docker compose up --build
```

Open http://localhost:4200 after Keycloak and the backend start. Sign in with `admin` / `local-admin-only` or `viewer` / `local-viewer-only`. Admin belongs to `demo` and `lab`; viewer only to `demo`. The simulator service account has only `INGESTOR` in `demo`.

Both Docker images have built locally and on GitHub, and the isolated local Compose stack has started. See [validation evidence](docs/VALIDATION.md) for the separate browser acceptance and CI status. For an existing local installation, use the [isolated reviewer setup](docs/DEMO.md) with separate ports and volumes. DynamoDB and Keycloak use named persistent volumes. Do not expose this Compose stack publicly.

For native development, run DynamoDB Local and Keycloak using the same realm in `infrastructure/keycloak/`, initialize the table with `scripts/init-db.sh` (AWS CLI required), then:

```sh
# Terminal 1: Java 21 and Maven
export AWS_ACCESS_KEY_ID=local AWS_SECRET_ACCESS_KEY=local AWS_REGION=us-east-1
export DYNAMODB_ENDPOINT=http://localhost:8000
mvn -f backend/pom.xml spring-boot:run
# Terminal 2: Node 22
cd frontend
npm ci
npm start
```

The Keycloak public issuer must be `http://localhost:8180/realms/fleetops`. Import the supplied realm with `start-dev --import-realm --http-port=8180 --hostname=http://localhost:8180`. Serve the UI on `localhost`, matching the registered redirect URI. `/actuator/health` includes a database-table check.

## Synthetic fleet workflow

Sign in as an organization administrator and obtain a short-lived access token from that session. See the [simulator setup guide](docs/SIMULATOR.md) for the exact token source, permissions and recovery behavior. Registration and reporting use separate credentials:

```sh
printf 'Administrator access token: '
read -rs FLEETOPS_ADMIN_TOKEN
printf '\n'
export FLEETOPS_ADMIN_TOKEN
python3 simulator/fleetops_sim.py register --count 100 --seed 42
unset FLEETOPS_ADMIN_TOKEN
export FLEETOPS_CLIENT_SECRET=local-simulator-secret-not-for-production
python3 simulator/fleetops_sim.py check-in --count 100
python3 simulator/fleetops_sim.py check-in --count 10 --scenario noncompliant
```

Create policies through the UI before checking in to observe compliance changes. New devices are `UNKNOWN`; evaluation without applicable policies is `NOT_APPLICABLE`. An inactivity scenario backdates reports and is accepted only for a device without a newer check-in; use a fresh fleet/state file. Reports cannot rewind existing device state.

## Validation

Backend:34 passing tests, including two-process restart idempotency and mixed-precision history pagination. Frontend:20 tests. Simulator:37 pytest cases plus the retained4 unittest cases. [GitHub run43393690562](https://github.com/naveenseedani21/fleetops/actions/runs/43393690562) passed all five jobs, including the real Docker browser workflow. Native and isolated Docker browser suites also passed from the host. See the [validation record](docs/VALIDATION.md) for dated results, the production stylesheet correction, screenshot provenance and remaining limitations; [current CI](https://github.com/naveenseedani21/fleetops/actions/workflows/ci.yml) shows subsequent commits.

```sh
# Java 21, Python 3.11+ and Docker (unless TEST_DYNAMODB_ENDPOINT is supplied)
AWS_ACCESS_KEY_ID=local AWS_SECRET_ACCESS_KEY=local mvn -f backend/pom.xml spotless:check verify
cd frontend
npm run format:check
npm run lint
npm test
npm run build
npm run e2e # running local backend, Keycloak and frontend required
cd ..
python3 -m pip install -e 'simulator[dev]' # use a virtual environment
ruff check simulator
python3 -m pytest simulator -q
python3 -m unittest discover -s simulator -v
docker compose config --quiet
```

## API and security

API prefix: `/api/v1/organizations/{organization}/`. JWT claims assign tenant membership and role. Every service authorizes before persistence access. Platform roles grant no implicit tenant access. Signed JWTs validate issuer, audience, expiry and signature. Browser login uses authorization code + PKCE; machine reporting uses client credentials without administrator access. Fleet provisioning requires a separate administrator access token; machine registration is denied. Lifecycle and compliance are independent, with conservative handling of legacy lifecycle values.

OpenAPI JSON is available at `/v3/api-docs` with an access token. See [API guide](docs/api/API.md), [threat model](docs/security/THREAT_MODEL.md), [data model](docs/architecture/DATA_MODEL.md), and [operations](docs/OPERATIONS.md).

## Hosting

Hosting is planned only after the owner approves the build. The target is ECS Fargate for the API and S3/CloudFront for Angular, with DynamoDB and a production OIDC provider. The initial Terraform foundation is not a complete hosted application. No deployment workflow is enabled and no resources have been provisioned. See [deployment readiness](docs/DEPLOYMENT.md).
