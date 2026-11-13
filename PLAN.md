# FleetOps implementation plan
Build a Java 21 / Spring Boot modular monolith and standalone Angular client. DynamoDB is the source of truth; Keycloak supplies local OIDC and production uses a configurable issuer. No actual MDM integrations or cloud provisioning.

1. Foundation: pin dependencies, document access patterns, connect DynamoDB, implement health and local identity configuration.
2. Inventory vertical slice: verified JWT tenant membership, device registration, detail, cursor list, metadata update, retirement, atomic audit, Angular UI, unit and integration checks.
3. Policy slice: immutable policy revisions, deterministic rules, persisted evaluations and summary, authenticated telemetry with sequence protection.
4. Simulator and local demo: reproducible synthetic data, paced requests, repeatable workflows.
5. Quality and delivery: CI, Docker, security model, API/runbook documentation, evaluate deployment readiness.
6. Remaining portfolio scope: organization administration, indexed filters, full reporting, production infrastructure, browser workflows, accessibility and security review.

Dependencies: identity and persistence precede writes; device concurrency precedes ingestion; versioned policy engine precedes reporting. Implement slices and validate before broadening. Mark unverified work clearly. AWS apply/destroy/deploy requires separate explicit approval.
