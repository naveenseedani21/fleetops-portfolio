# FleetOps progress — 2026-11-13

## Current checkpoint
Controlled audit batch1 against the supplied master specification. First-pass mapping covers sections0–28 in SPEC_AUDIT.md; deep verification and all gaps are not complete. Baseline was209c9f3 with a clean tree. Working native app remains on4200/8080 with Keycloak8180 and DynamoDB Local8000. No cloud deployment.

## Completed in this batch
- Preserved user specifications under docs/requirements and documented approval provenance.
- Re-ran baseline:16 backend,11 frontend,4 Python tests; frontend format/lint/build passed.
- Added separate liveness/readiness groups and24-test backend suite covering actual DynamoDB failure/recovery behavior and authorization boundaries.
- Verified health over HTTP using the built jar on18081. This separate instance has not replaced the running8080 build.
- Added instructions, test runbook, endpoint matrix, audit, API/ADR indexes, structured threats, retention notes and five interview questions.
- Terraform foundation fmt/validate and actionlint passed. Application manifests/lockfiles/framework/provider/model were preserved.

## Decisions and blockers
- A1: stdlib simulator versus specified HTTPX/Pydantic/Typer/Pytest/Ruff; awaiting explicit choice.
- A2: INGESTOR can register devices; report-only redesign requires provisioning workflow; awaiting choice.
- A3: withCompliance stores NONCOMPLIANT in lifecycle; compatible correction/legacy handling awaits choice. Do not migrate records silently.
- Older report IDs are not durably remembered; missing report ledger/history needs retention/compatibility design. Rule status vocabulary differs from spec.
- CI run43374792166 failed at startup. Docker unavailable; complete Playwright and Docker builds unverified. Do not invent an account/billing diagnosis.
- Process cleanup: automatic review rejected terminal interrupt and sandbox denied SIGTERM for the temporary verification server on18081. Stop it from a host terminal/Activity Monitor after identifying that process; do not stop the main8080 app or the database.
- Hosted infrastructure, production identity and deployment remain deferred until owner build/cost approval. No apply/destroy.

## Exact next work
1. Read any A1–A3 response; record the decision verbatim with provenance before dependent implementation. Prior GitHub commit/push authorization persists.
2. Independent §12.3: add MockMvc contract tests for malformed `limit`/cursor, authentication failures and consistent safe Problem Details. Reproduce first; make only narrow compatible error handling changes. Do not change valid API payloads/routes.
3. Extend Playwright to the full minimum-OS failure→audit→healthy recovery journey and viewer policy denial. Run only against isolated local fixtures; seek a Docker-capable environment for full browser/container verification.
4. Investigate CI startup with accessible GitHub run/check-suite diagnostics. Preserve workflow architecture; add missing coverage/Terraform/scanning checks incrementally after diagnosis.

## Validation procedure/state
Backend source was copied to /private/tmp/fleetops-audit/backend to avoid synced-folder I/O delays. Java21 and cached Maven dependencies are under /private/tmp/fleetops-runtime. Tests explicitly used loopback TEST_DYNAMODB_ENDPOINT and dummy AWS credentials; random test tables were left in the local emulator. Do not reset the application table. Formatted changed test sources were copied back. Raw logs are in the task workspace's work/audit; canonical evidence is SPEC_AUDIT.md E1–E7. The final backend run passed24 tests and jar packaging; frontend was unchanged after its11-test/lint/build baseline. No dependency or lockfile changes.

## Approval update received during this session
A1: retain stdlib simulator as approved deviation, add pytest/Ruff/CI and preserve/test compatibility. A2: admin-only provisioning and scoped report-only machines approved. A3: independent lifecycle/compliance plus conservative legacy read handling approved; no bulk migration or incompatible wire/schema change. See requirements/2026-11-13-approved-remediation.md. **Current next task supersedes the earlier pending list:** implement A2/A3 together with backend/frontend regressions, then A1 simulator compatibility tests and admin setup/retry/scenario improvements. Current `status`/`compliance` fields are suitable and must not be renamed incompatibly.

## Current checkpoint after approved A1–A3 work (supersedes pending items above)

A1–A3 implementation is complete and locally verified:29 backend tests including real Python CLI→signed JWT HTTP→DynamoDB workflow;37 pytest tests including original4 (standalone unittest still4);17 frontend tests; Ruff/frontend lint/format/build/Maven/actionlint pass. Read SPEC_AUDIT.md E8–E12 and SIMULATOR.md for exact evidence and limitations. No bulk migration or data reset. Registration now needs FLEETOPS_ADMIN_TOKEN; machine credentials report only. Existing status/compliance wire fields and simulator state remain compatible. Legacy ambiguity is visible; future evaluations preserve lifecycle.

The frontend on4200 has the new lifecycle presentation, visually inspected and captured in screenshots/lifecycle-review.jpg. Main API8080 still runs the earlier build and must be restarted from the host with the newly built jar/source to activate backend changes. The separate old verification process18081 also remains because automatic approval review rejected interrupt and the sandbox denied SIGTERM. Do not work around process-control restrictions or imply live server hardening is active yet.

Next unchecked task: §12.3 malformed limit/cursor and auth Problem Details contract tests; reproduce first and preserve valid APIs. Then minimum-OS browser recovery/audit journey and CI startup diagnosis. Report ledger/history key redesign needs a concrete preservation/retention proposal before implementation. Hosting waits for build/cost approval. Source of truth remains outputs/fleetops/.git; regular focused commits/pushes are authorized.

Implementation checkpoint: c5618fc (`fix: enforce report-only machines and independent device lifecycle`). Editable simulator installation with pinned dev extras and the installed `fleetops-sim --help` entry point also passed. The following documentation checkpoint records results and screenshot evidence separately.
