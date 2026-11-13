# ADR 0001: Modular monolith with explicit DynamoDB access
Accepted for the initial slice.

Java/Spring Boot and Angular meet the learning objectives and offer mature validation/security/UI tooling; React or Node would reuse more prior experience but not develop the requested skills. AWS SDK v2 exposes transactional and conditional semantics directly, avoiding an unmaintained Spring Data abstraction.

One deployable backend is easier for one developer to operate than microservices. Functional packages separate identity, devices, policies, compliance, audit and persistence. The Store adapter centralizes AWS-specific access, while the pure Engine has no I/O.

DynamoDB single-table prefixes support the initial finite access patterns. This makes arbitrary filtering and aggregate reports explicit future design work. A relational database would make ad hoc reports simpler, but would not meet the project's DynamoDB objective.

Keycloak provides a reproducible standards-based local identity provider. No custom password handling is implemented. Production identity may use Cognito or hosted OIDC; trusted membership claims must be mapped explicitly.

Synchronous telemetry keeps device/evidence/audit atomic and observable. An SQS worker is justified only if traffic or latency requires it; eventual consistency and retry semantics would then require more design.
