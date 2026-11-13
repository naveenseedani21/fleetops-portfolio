# Validation record

This is an execution record, not a claim that every master-specification requirement is complete. Checkpoints below preserve their dates; later results supersede earlier blockers.

## Reviewer verification — November 13, 2026

| Check | Verified result |
| --- | --- |
| GitHub CI | [Run43393690562](https://github.com/naveenseedani21/fleetops/actions/runs/43393690562), code7670c5b: all five jobs executed and passed; [current CI](https://github.com/naveenseedani21/fleetops/actions/workflows/ci.yml) tracks subsequent revisions |
| Backend | 34 tests; scoped retained receipts, two-JVM restart, exact history pagination, error contracts, A2/A3; Spotless and packaging pass |
| Frontend | 17 unit/component tests, format/lint/build and npm high-severity audit pass in GitHub |
| Simulator | 37 pytest cases and original4 unittest cases, Ruff; standard-library implementation retained |
| Final native browser | Code0c62ed8, real4200/8080/8180:2 tests passed in7.9s |
| Final isolated Docker browser | Code0c62ed8, real14200/18080/18180:2 tests passed in8.4s; separate persistent volumes |
| Production styling | Fixed critical-CSS loader incompatibility with unchanged CSP; rebuilt only isolated frontend, verified direct stylesheet and browser layout assertions |
| Evidence | Six genuine final PNGs under screenshots; [console results](evidence/reviewer-browser-20261113.txt); screenshots visually inspected |
| Hosting | Deferred; no cloud resources provisioned or paid settings changed |

Both browser runs use real administrator/viewer OIDC sessions, limited machine credentials and DynamoDB. They exercise provisioning→policy failure→recovery, independent lifecycle, retained history/replay, conflicting/stale reports, unchanged state after denials, existing cross-tenant targets and retirement. The final run is `work/browser-20261113T083527Z`; test sources matched the checkout. Native backend uses the verified reviewer JAR on8080 (PID67914 at inspection);18081 is free and original native Keycloak/DynamoDB remain running.

Historical CI startup issue resolved; private account details omitted from this public copy.

## Historical baseline — November 12, 2026

| Check | Result |
| --- | --- |
| Java 21 Maven verify and Spotless | 16 tests passed, including DynamoDB Local integration, signed JWT validation, tenant boundaries, and dashboard aggregation |
| Angular unit tests | 11 passed across 4 files; compound policy preservation, validation, failed-save recovery, viewer controls, history response ordering, and device evidence pagination/retry |
| Angular ESLint and production build | Passed |
| Python simulator unit tests | 4 passed |
| Terraform foundation | fmt, init without backend, and validate passed; no infrastructure applied |
| GitHub workflow syntax | actionlint passed |
| Manual browser | Real OIDC sign-in, organization switching, registration inventory, policy creation, evaluated failure evidence, live overview, compound policy save (two rules/two targets), and preserved version history checked |

## Historical execution limits — November 12

GitHub Actions reports startup_failure before creating jobs, including on manual dispatch. No CI success is claimed. Local Playwright Chromium cannot launch under this environment (SIGABRT/EPERM); the complete end-to-end suite remains unverified. Docker is unavailable locally, so Compose image/startup validation is outstanding. npm reports moderate tooling advisories; complete dependency/container scanning is outstanding.

The overview uses real tenant-scoped API data. It caps fleet aggregation at 1,000 devices and excludes retired devices from freshness and compliance distributions. Full accessibility validation, larger-fleet reporting, and hosted-environment validation remain outstanding. See TASKS.md for remaining scope.

## 2026-11-13 controlled audit batch

Fresh baseline:16 backend tests,11 frontend tests, frontend lint/format/build,4 simulator tests pass. After compatible health and regression changes:24 backend tests pass (zero failures/errors/skips), with successful jar packaging and isolated live HTTP probe checks on18081. Terraform foundation formatting/validation and actionlint pass. See SPEC_AUDIT.md E1–E7 for commands, corrected intermediate failures, remote CI startup failure, temporary-process cleanup restriction and exact limitations. No new full Playwright, Docker or cloud deployment result is claimed.

## Approved A1–A3 remediation — 2026-11-13

- Backend:29 tests, zero failures/errors/skips; Java21 Spotless and Maven packaging passed against loopback DynamoDB Local. Repository sources match the verified build copy byte-for-byte.
- Real simulator workflow: administrator-only CLI provisioning, machine-only reporting, signed JWT HTTP validation and DynamoDB persistence passed. Duplicate/altered/stale reports leave persisted device/evaluation/audit state unchanged; failure→recovery preserves active lifecycle.
- Simulator:37 pytest tests (including original4), standalone4 unittest tests and Ruff pass. Compatibility tests passed before changing behavior. Pytest/Ruff integrated into CI without replacing unittest.
- Frontend:17 tests/6 files, formatting, lint and production build pass. Live inventory/detail visually checked, including legacy Needs review guidance and separate compliance badge; screenshot lifecycle-review.jpg. Backend8080 still requires host restart for new server behavior.
- actionlint and git diff checks pass. Last checked GitHub run43378198611 still startup_failure; no successful remote CI claimed. Docker/complete Playwright/cloud deployment remain unverified.
- No data reset, bulk migration, provider replacement or cloud action. See SPEC_AUDIT.md E8–E12 for scope, intermediate failures/fixes and unresolved limitations.

## Reviewer-readiness checkpoint — 2026-11-13

- Report integrity/order/contracts:34 backend tests pass, including two separate JVMs with retained receipts, A/B/A replay, changed payload conflicts, tenant/device scoping, fixed out-of-order timestamp fixtures and105-row pagination. Spotless/package pass.37 pytest + original4 unittest and Ruff pass again.
- Updated native build activated by owner: PID65658 opens the checksummed reviewer jar; both health probes UP; old18081 stopped; existing identity/database processes retained. Real administrator UI registration → machine report → visible NONCOMPLIANT → corrected report → visible COMPLIANT passed, with ACTIVE lifecycle throughout reports. Machine registration/policy/cross-tenant denials and unknown-device rejection verified against8080. Genuine failure/recovery screenshots saved.
- Docker: Engine29.9.3/Compose5.7.0; both images built. Isolated project uses new volumes and14200/18080/18180/18000. Initial synced bind-mount read failed with Input/output error; local fixture copies resolved init. All services started, readiness UP and identity discovery succeeded; subsequent local timeouts and full browser acceptance remain separately tracked.
- Local automated Chromium: missing executable corrected by installing the lockfile-required browser. Direct launch then denied by macOS Mach-port permissions before test bodies. Host-only acceptance command supplied; no sandbox bypass attempted and no local automated pass claimed yet.
- GitHub startup blocker was the actual billing/spending annotation, now cleared enough for [run43389765935](https://github.com/naveenseedani21/fleetops/actions/runs/43389765935) to execute all jobs. Backend/frontend/simulator/Compose passed. Browser viewer test passed; admin test failed an outdated exact heading locator. Compatible locator correction and the expanded acceptance suite were pushed in f19f5d5. Full passing CI still requires the next result.
- Product styling remains unchanged pending the owner's choice among frontend concepts. No hosting, cloud resources, data migration/reset or provider replacement. See SPEC_AUDIT E13–E20 for detailed provenance and limitations.
