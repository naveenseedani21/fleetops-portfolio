# FleetOps implementation status

## Foundation and architecture
- [x] Inspect empty workspace, preserve work, select Java 21/Angular/SDK stack.
- [x] Define architecture and DynamoDB access patterns before persistence.
- [x] Start native backend, frontend, Keycloak and DynamoDB Local.
- [x] Configure database-aware health, structured logs and request correlation.
- [ ] Verify complete Docker Compose startup and image builds (Docker unavailable locally).

## Inventory and tenant security
- [x] Device registration, details, metadata/assignment edits and retirement.
- [x] Strong tenant-scoped reads and server-side cursor pagination.
- [x] Serial uniqueness and optimistic revisions using DynamoDB transactions.
- [x] OIDC/PKCE login, short-lived JWT verification and refresh handling.
- [x] Admin/viewer/ingestor service authorization, cross-tenant and real signed-token tests.
- [x] Correct tenant selector binding and add a regression test.
- [ ] Indexed inventory search/status/OS/model/compliance filters.
- [ ] Persisted organizations and membership administration; platform administration.
- [ ] Scheduled inactivity lifecycle updates.

## Policy engine and telemetry
- [x] Six validated rule types, numeric OS comparison and explicit missing-data precedence.
- [x] Immutable policy versions, current-policy updates and historical evidence.
- [x] Atomic device/evaluation/audit persistence.
- [x] Exact-last-report replay and stale/out-of-order protection.
- [x] Paced authenticated Python simulator with deterministic inventory and pending-report recovery.
- [x] UI policy creation/editing across multiple rules and device types.
- [x] Compound policy form editor, validated values and paginated version history UI.
- [ ] Automatic policy-change fleet re-evaluation and inactivity scheduling.
- [ ] Robust registration recovery after an ambiguous network timeout.

## Reporting and experience
- [x] Inventory, detail, policies and audit screens connected to the backend.
- [x] Bounded fleet overview with true counts and explained compliance denominator.
- [x] Manual browser sign-in, policy creation, inventory and failed-rule evidence check.
- [x] Screenshot of actual persisted compliance evidence.
- [ ] Historical compliance trends and severity alert dashboard.
- [ ] Device-specific audit filtering and older evidence pagination UI.
- [ ] Persistent authorization-denial audit and alerting.
- [ ] Comprehensive keyboard/screen-reader/mobile review.

## Delivery and quality
- [x] Create private GitHub repository and push focused commits.
- [x] Java, frontend and simulator automated tests; see VALIDATION.md for exact results.
- [x] Add Playwright tests and a Docker-based CI browser job.
- [x] Validate workflow syntax with actionlint.
- [ ] Resolve GitHub Actions startup_failure; no CI job has executed successfully yet.
- [ ] Execute complete Playwright suite (local browser launch blocked by sandbox).
- [ ] Full Java/container dependency vulnerability scanning and resolution of npm moderate tooling advisories.
- [x] Terraform foundation validates; no resources provisioned.
- [x] Security model, API guide, operations, architecture decisions and interview walkthrough.
- [ ] Complete ECS/network/CloudFront infrastructure, OIDC deployment role, alarms and approved deployment plan.
- [ ] Owner review of build and hosting approval before any paid deployment.

The initial application slices are implemented; the full eight-phase master specification is not complete. Do not describe unchecked items as delivered.
