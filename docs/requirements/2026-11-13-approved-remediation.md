# User decisions — 2026-11-13

Source: user replies to approval questions A1, A2 and A3 in the active FleetOps audit chat. These decisions supersede the earlier pending status; they do not authorize unrelated replacements.

## A1 — approved deviation plus targeted simulator quality work

User: “Accept the current standard-library simulator as an approved deviation, subject to verifying its functionality. Do not perform a full migration solely to match the original technology list.”

Authorized: add pytest tests and Ruff in GitHub Actions while retaining useful tests; preserve commands/arguments/configuration/state compatibility and add regression tests before behavior changes; verify deterministic data, timeouts, bounded transient retries and safe authentication/validation errors; automate duplicate/out-of-order scenarios against the backend; document simplicity/limits. Propose a concrete benefit and compatibility impact before replacing libraries. No HTTPX/Pydantic/Typer migration approved.

## A2 — approved administrator provisioning and report-only machines

User: “Approve separate administrator provisioning and report-only machine access.”

Authorized: tenant-authorized admins register; viewers/machines cannot register; machines report only to existing devices in their tenant and configured device scope; reject unknown devices; enforce in backend. Provide separate admin-authorized fleet setup then restricted machine reporting. No embedded admin passwords, privilege elevation or bypass. Integration tests must verify positive admin/report flow and denials, with no unauthorized data changes. Preserve IDs, payloads and state files; no reset/recreation. Auth provider and unrelated components are out of scope.

## A3 — approved compatible lifecycle correction

User: “Approve the compatible correction. Separate device lifecycle from compliance for future writes, preserve existing records, and do not perform a bulk migration.”

Authorized: reuse existing suitable fields; lifecycle REGISTERED/ACTIVE/INACTIVE/RETIRED distinct from COMPLIANT/NONCOMPLIANT/UNKNOWN/NOT_APPLICABLE. Evaluations must not change lifecycle or reactivate inactive/retired devices. Preserve legitimate check-in/retirement transitions. Legacy NONCOMPLIANT lifecycle records must remain readable; infer only from reliable evidence and flag ambiguity without rewriting storage. Review API, details/table, filters and summary; active noncompliant counts in both categories. Test failure/recovery lifecycle stability, retirement, legacy reads and dashboard counts; run backend/frontend checks. Preserve IDs/history/simulator format; ask before incompatible API/schema changes.
