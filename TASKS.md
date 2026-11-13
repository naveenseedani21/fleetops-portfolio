# FleetOps implementation status

## Foundation and architecture
- [x] Inspect empty workspace, preserve work, select Java 21/Angular/SDK stack.
- [x] Define architecture and DynamoDB access patterns before persistence.
- [x] Start native backend, frontend, Keycloak and DynamoDB Local.
- [x] Configure database-aware health, structured logs and request correlation.
- [x] Verify isolated Docker Compose startup and image builds; existing native data preserved (SPEC_AUDIT E19/E21).

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
- [x] Older device evidence pagination with retry and stale-response protection.
- [ ] Device-specific audit filtering.
- [ ] Persistent authorization-denial audit and alerting.
- [ ] Comprehensive keyboard/screen-reader/mobile review.

## Delivery and quality
- [x] Create private GitHub repository and push focused commits.
- [x] Java, frontend and simulator automated tests; see VALIDATION.md for exact results.
- [x] Add Playwright tests and a Docker-based CI browser job.
- [x] Validate workflow syntax with actionlint.
- [x] Diagnose GitHub startup block from the account billing annotation; actual jobs now execute (SPEC_AUDIT E13/E20).
- [x] Verify all five intended jobs execute and pass: run43393690562; track subsequent revisions in CI.
- [x] Execute real native and isolated Docker Playwright workflows from the host: two tests pass on each (E23).
- [x] Verify the Docker production stylesheet correction, loaded screenshots and layout assertions on both stacks (E25).
- [ ] Full Java/container dependency vulnerability scanning and resolution of npm moderate tooling advisories.
- [x] Terraform foundation validates; no resources provisioned.
- [x] Security model, API guide, operations, architecture decisions and interview walkthrough.
- [ ] Complete ECS/network/CloudFront infrastructure, OIDC deployment role, alarms and approved deployment plan.
- [ ] Owner review of build and hosting approval before any paid deployment.

The initial application slices are implemented; the full eight-phase master specification is not complete. Do not describe unchecked items as delivered.

## Controlled specification audit — 2026-11-13
- [x] Preserve source specifications and map substantive sections in docs/SPEC_AUDIT.md.
- [x] Re-run local baseline before application changes.
- [x] Separate process liveness from dependency-aware readiness; verify outage/recovery and live HTTP.
- [x] Expand minimum-OS recovery, tenant/machine denials, retirement, transaction rollback and signed nbf regression coverage (24 backend tests pass).
- [x] Add AGENTS, PROGRESS, TESTING and endpoint permissions; preserve existing API/ADR documents with indexes.
- [x] Add structured threat register, retention notes and25 grounded interview Q&A.
- [x] Record explicit A1/A2/A3 approvals and preserve their boundaries.
- [x] Correct lifecycle/compliance mixing; conservative legacy reads/UI review flags, independent dashboard counts and regression tests. Existing ambiguous stored values are preserved.
- [x] Persist scoped report receipts atomically; verify A/B/A and changed-ID conflicts across a backend restart; document retention and pre-upgrade limits.
- [x] Correct mixed-precision audit/evidence ordering and verify fixed-dataset pagination across 105 rows; add malformed query/authentication error contracts.
- [x] Complete minimum-OS recovery/audit Playwright scenario against real native and isolated Docker services (E23).
- [x] Owner stopped old verification process18081 with guarded host restart; confirmed port free.

## Approved remediation checkpoint
- [x] A1 stdlib approved deviation: compatibility tests before behavior edits, pytest/Ruff plus original unittest in CI, timeout/retry/auth/redirect tests and documented limits.
- [x] A2 admin-only provisioning, report-only scoped machines, unknown-device rejection and persisted denial tests.
- [x] Real Python CLI provisioning→machine reporting→compliance failure/recovery over authenticated HTTP and local DynamoDB; duplicate/stale requests preserve device/evidence/audit.
- [x] Verify29 backend,37 Python (including original4),17 frontend tests; formatting/lint/build/actionlint passed. See SPEC_AUDIT E8–E12.
- [x] Visually inspect legacy lifecycle inventory/detail rendering; screenshot saved.
- [x] Owner restarted main API8080; PID65658 loads verified reviewer jar, health UP, Keycloak/DynamoDB original processes retained.

## Reviewer package
- [x] Real native and isolated Docker acceptance, including MIN_OS failure/recovery, tenant/viewer/machine denials and retirement invariance.
- [x] Preserve receipt/history fixes and A1–A3; actual native restart replay verified.
- [x] Five-minute demo script, verified setup/limitations and genuine final screenshots.
- [ ] Owner selects a frontend direction before implementation.
- [ ] Hosting remains deferred until the owner approves the build.
