# Validation record

Validated locally on November 12, 2026. This is an execution record, not a claim that every master-specification requirement is complete.

| Check | Result |
| --- | --- |
| Java 21 Maven verify and Spotless | 16 tests passed, including DynamoDB Local integration, signed JWT validation, tenant boundaries, and dashboard aggregation |
| Angular unit tests | 11 passed across 4 files; compound policy preservation, validation, failed-save recovery, viewer controls, history response ordering, and device evidence pagination/retry |
| Angular ESLint and production build | Passed |
| Python simulator unit tests | 4 passed |
| Terraform foundation | fmt, init without backend, and validate passed; no infrastructure applied |
| GitHub workflow syntax | actionlint passed |
| Manual browser | Real OIDC sign-in, organization switching, registration inventory, policy creation, evaluated failure evidence, live overview, compound policy save (two rules/two targets), and preserved version history checked |

## Unresolved execution limits

GitHub Actions reports startup_failure before creating jobs, including on manual dispatch. No CI success is claimed. Local Playwright Chromium cannot launch under this environment (SIGABRT/EPERM); the complete end-to-end suite remains unverified. Docker is unavailable locally, so Compose image/startup validation is outstanding. npm reports moderate tooling advisories; complete dependency/container scanning is outstanding.

The overview uses real tenant-scoped API data. It caps fleet aggregation at 1,000 devices and excludes retired devices from freshness and compliance distributions. Full accessibility validation, larger-fleet reporting, and hosted-environment validation remain outstanding. See TASKS.md for remaining scope.

## 2026-11-13 controlled audit batch

Fresh baseline:16 backend tests,11 frontend tests, frontend lint/format/build,4 simulator tests pass. After compatible health and regression changes:24 backend tests pass (zero failures/errors/skips), with successful jar packaging and isolated live HTTP probe checks on18081. Terraform foundation formatting/validation and actionlint pass. See SPEC_AUDIT.md E1–E7 for commands, corrected intermediate failures, remote CI startup failure, temporary-process cleanup restriction and exact limitations. No new full Playwright, Docker or cloud deployment result is claimed.
