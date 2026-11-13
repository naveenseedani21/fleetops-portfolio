# AI engineering notes
AI assistance was used to design the access patterns, implement the backend and Angular client, write tests and review local execution failures. This document records actual observations only.

- An Angular template called a boolean getter as a function. Direct Angular compiler diagnostics identified the mismatch; templates were corrected to property access. This illustrates why lint alone cannot substitute for template compilation.
- The initial npm 10 dependency-resolution run crashed inside Arborist. npm 11 completed dependency resolution without disabling peer checks; a lockfile was retained.
- Native builds aborted inside the LMDB addon, and the local disk approached capacity. macOS marked the downloaded Node runtime `dataless` in Documents. Task-created archive/cache cleanup and a temporary runtime outside the synced directory were used to investigate. Validation results are recorded separately rather than treating an attempted command as success.
- Security tests initially used Spring Security's MockMvc JWT helper for authorization. Real RSA-signed JWT tests were then added to cover signature, issuer, audience and expiry, which the helper bypasses.
- Tests exercise actual DynamoDB transactions through DynamoDB Local. Docker/Testcontainers and production AWS behavior are separate verification claims.

No productivity, coverage, latency or production adoption metrics are claimed.

- Manual browser testing exposed a tenant selector displaying the first sorted option while the API had selected another membership. Switching to the forms value accessor and deterministic membership ordering corrected the mismatch; a regression test verifies selection when option order differs. Tenant authorization remained enforced.
- The overview denominator is explicitly pass/fail-only; tests ensure UNKNOWN and NOT_APPLICABLE do not inflate compliance.
