# Threat model and permission matrix

Assets: tenant inventory, employee assignment labels, compliance evidence, identity claims, service credentials and audit events. Boundaries: browser→OIDC, browser/simulator→API, API→DynamoDB. Use synthetic employee labels in demos.

| Operation | ADMIN | VIEWER | INGESTOR | Platform role alone |
|---|---|---|---|---|
| Read tenant inventory/policies/audit | yes | yes | no | no |
| Register synthetic devices | yes | no | yes | no |
| Update metadata / retire | yes | no | no | no |
| Change policies / evaluate | yes | no | no | no |
| Submit reports | yes | no | yes | no |
| Organization/member administration | not implemented | no | no | not implemented |

Controls tested: unauthenticated denial; other-tenant denial; viewer mutation denial; ingestion-only read denial; platform-role non-escalation; actual JWT signature/issuer/audience/expiry validation; optimistic state protection and duplicate registration transactions.

Spoofed tenant: never accept memberships from request bodies or headers; only verified JWT claims. Provider administrators control membership. Use five-minute access tokens; revocation takes effect no later than token expiration unless additional revocation enforcement is added. Local realm has no self-registration. Do not allow end users to edit membership attributes in a production identity provider.

Tampering/replay: serial reservation and inventory/audit writes are transactional. Expected revisions reject lost updates. Sequence and timestamp checks reject stale reports. Last-report digest detects payload changes. Concurrent exact retries may receive 409 and should retry/read current state. Tenant device state is never fetched before tenant authorization.

Missing telemetry: nullable security fields yield UNKNOWN. Inactivity becomes noncompliant only when evaluated against an age policy; no scheduled evaluator is implemented. Historical evaluation documents reference the policy version actually read; evaluations do not guarantee a globally atomic snapshot of concurrent policy edits.

Browser: PKCE, sessionStorage, same-origin API requests, Angular escaping, credential interceptor restricted to relative API paths. XSS can steal sessionStorage tokens; a production BFF with HttpOnly cookies is a possible improvement. Nginx adds CSP/frame protections; adapt OIDC connect-src for hosting. CSRF protection is disabled because the API is stateless bearer-token-only and does not authenticate cookies. HTTPS is mandatory outside loopback.

Audit immutability is application-level: there is no update/delete audit endpoint. A privileged AWS principal can still alter the table. Use separate IAM permissions, backups and an archival sink for stronger retention guarantees. Successful mutations are atomically audited; persistent authorization-denial audit and external alerting are pending.

Deployment gaps: local credentials are intentionally public; never deploy the dev realm. No rate limiter, production membership provisioning, complete dependency scan for Java, or penetration test has been completed. npm audit currently reports moderate development-tool vulnerabilities in glob/minimatch dependency chains; no high/critical frontend advisory was reported during the recorded run. These remain tracked, not suppressed. This build is not certified production-ready.
