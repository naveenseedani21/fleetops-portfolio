# Threat model and permission matrix

Assets: tenant inventory, employee assignment labels, compliance evidence, identity claims, service credentials and audit events. Boundaries: browser→OIDC, browser/simulator→API, API→DynamoDB. Use synthetic employee labels in demos.

| Operation | ADMIN | VIEWER | INGESTOR | Platform role alone |
|---|---|---|---|---|
| Read tenant inventory/policies/audit | yes | yes | no | no |
| Register synthetic devices | yes | no | no | no |
| Update metadata / retire | yes | no | no | no |
| Change policies / evaluate | yes | no | no | no |
| Submit reports to existing devices | yes | no | scoped | no |
| Organization/member administration | not implemented | no | no | not implemented |

Controls tested: unauthenticated denial; other-tenant denial; viewer mutation denial; ingestion-only read denial; platform-role non-escalation; actual JWT signature/issuer/audience/expiry validation; optimistic state protection and duplicate registration transactions.

Spoofed tenant: never accept memberships from request bodies or headers; only verified JWT claims. Provider administrators control membership. Use five-minute access tokens; revocation takes effect no later than token expiration unless additional revocation enforcement is added. Local realm has no self-registration. Do not allow end users to edit membership attributes in a production identity provider.

Tampering/replay: serial reservation and inventory/audit writes are transactional. Expected revisions reject lost updates. Sequence and timestamp checks reject stale reports. Last-report digest detects payload changes. Concurrent exact retries may receive 409 and should retry/read current state. Tenant device state is never fetched before tenant authorization.

Missing telemetry: nullable security fields yield UNKNOWN. Inactivity becomes noncompliant only when evaluated against an age policy; no scheduled evaluator is implemented. Historical evaluation documents reference the policy version actually read; evaluations do not guarantee a globally atomic snapshot of concurrent policy edits.

Browser: PKCE, sessionStorage, same-origin API requests, Angular escaping, credential interceptor restricted to relative API paths. XSS can steal sessionStorage tokens; a production BFF with HttpOnly cookies is a possible improvement. Nginx adds CSP/frame protections; adapt OIDC connect-src for hosting. CSRF protection is disabled because the API is stateless bearer-token-only and does not authenticate cookies. HTTPS is mandatory outside loopback.

Audit immutability is application-level: there is no update/delete audit endpoint. A privileged AWS principal can still alter the table. Use separate IAM permissions, backups and an archival sink for stronger retention guarantees. Successful mutations are atomically audited; persistent authorization-denial audit and external alerting are pending.

Deployment gaps: local credentials are intentionally public; never deploy the dev realm. No rate limiter, production membership provisioning, complete dependency scan for Java, or penetration test has been completed. npm audit currently reports moderate development-tool vulnerabilities in glob/minimatch dependency chains; no high/critical frontend advisory was reported during the recorded run. These remain tracked, not suppressed. This build is not certified production-ready.

## STRIDE register — audit addition, 2026-11-13

Likelihood/impact below are qualitative design assessments, not measured incident rates. Controls describe the current build; missing controls remain residual risk.

| Category / threat | Component | Likelihood / impact | Existing mitigation | Verification | Residual risk / next action |
|---|---|---|---|---|---|
| Spoofing: forged/expired/not-yet-valid token | API identity | Medium / high | Signature, issuer, audience, time validation | TokenValidationTest uses actual signed tokens | Explicit token-type/scopes contract and production issuer configuration still needed |
| Spoofing: stolen browser token | Angular/OIDC | Medium / high | PKCE, sessionStorage, short local token lifetime, CSP in nginx | Auth/interceptor tests; historical real login | XSS can read session tokens; production HTTPS/CSP not deployed |
| Elevation: cross-tenant request | Services/Store | Medium / high | Verified membership before tenant reads/writes; reconstructed partition | FleetIntegrationTest device/policy/audit boundaries | No exhaustive endpoint matrix; provider membership administrators remain trusted |
| Elevation: overprivileged machine token | Simulator/API | Medium / high | INGESTOR denied registration, reads, metadata, policy edits and evaluation; optional signed device scopes | Ingestor denial regression tests | Provider-managed scope configuration and short-lived credential rotation remain operational responsibilities |
| Tampering: lost updates/partial writes | DynamoDB | Medium / high | Expected revisions and transactions | Duplicate/optimistic-lock tests; conflicting-transaction no-partial-audit test | Production IAM/throughput behavior not validated; concurrent policy snapshot is not globally atomic |
| Tampering: stale/replayed telemetry | Ingestion | Medium / high | Sequence/time/hash checks and latest replay no-op | MIN_OS recovery/stale-report test; report replay test | Old report IDs can be reused after intervening reports; durable ledger missing |
| Repudiation: audit modification or absent denial evidence | Audit/storage | Medium / high | Success audits atomic; no application update/delete audit routes | Persistence/audit counts in integration tests | AWS principals may alter records; immutable archive and persistent denial events absent |
| Information disclosure: errors, logs, credentials | API/operations | Medium / high | Safe known error messages, status-only health, no token logging in custom code | Probe details hidden; JWT tests; code inspection | Full log redaction/secret scan not completed; local realm contains public fixture credentials |
| Denial of service: unbounded reads or submissions | API/DynamoDB | Medium / high | Page bounds, 20-policy evaluation cap, 1,000-device overview cap, simulator pacing | Page/summary tests; code inspection | No server rate limiter, payload-budget review or measured load test |
| Cloud misconfiguration | IAM/network/OIDC | Unknown / high | Foundation task role/table scope, deletion protection, private repo, no deployment | Terraform validation and static review only | Complete network/TLS/hosting/IAM runtime validation absent; owner approval required |

A passing unit/integration suite does not establish production security. Legacy lifecycle ambiguity, limited report ledger and CI/Docker limitations are tracked in SPEC_AUDIT.md, not silently accepted.
