# Validation record

Validated locally on November 12, 2026. This is an execution record, not a claim that every master-specification requirement is complete.

| Check | Result |
| --- | --- |
| Java 21 Maven verify and Spotless | 16 tests passed, including DynamoDB Local integration, signed JWT validation, tenant boundaries, and dashboard aggregation |
| Angular unit tests | 3 passed across 2 files |
| Angular ESLint and production build | Passed |
| Python simulator unit tests | 4 passed |
| Terraform foundation | fmt, init without backend, and validate passed; no infrastructure applied |
| GitHub workflow syntax | actionlint passed |
| Manual browser | Real OIDC sign-in, organization switching, registration inventory, policy creation, evaluated failure evidence, and live overview checked |

## Unresolved execution limits

GitHub Actions reports startup_failure before creating jobs, including on manual dispatch. No CI success is claimed. Local Playwright Chromium cannot launch under this environment (SIGABRT/EPERM); the complete end-to-end suite remains unverified. Docker is unavailable locally, so Compose image/startup validation is outstanding. npm reports moderate tooling advisories; complete dependency/container scanning is outstanding.

The overview uses real tenant-scoped API data. It caps fleet aggregation at 1,000 devices and excludes retired devices from freshness and compliance distributions. Full accessibility validation, larger-fleet reporting, and hosted-environment validation remain outstanding. See TASKS.md for remaining scope.
