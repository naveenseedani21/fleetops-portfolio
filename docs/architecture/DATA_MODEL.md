# DynamoDB access patterns (defined before implementation)
One table with string `pk`, `sk`. Tenant data lives under `ORG#<tenant>` and every repository operation receives an authorized tenant. JSON payload plus numeric `revision` supports domain evolution without an ORM. All ordinary reads use GetItem or Query, never Scan.

| Access | Partition / sort key | Semantics |
|---|---|---|
| Device detail | ORG#id / DEVICE#id | Strong read |
| Device list | ORG#id / prefix DEVICE# | Strong query, 1–100 evaluated items |
| Serial uniqueness | ORG#id / SERIAL#canonical-serial | Transactional conditional create |
| Policies | ORG#id / prefix POLICY# | Current policies, paginated |
| Policy history | ORG#id / PV#policy#padded-version | Immutable revisions |
| Compliance history | ORG#id / EVAL#device#time#UUID | Timestamp + ID descending compatibility Query |
| Audit | ORG#id / AUDIT#time#UUID | Timestamp + ID descending compatibility Query |
| Report receipt | ORG#id / REPORT#device#report-id | Strong read, conditional create in report transaction |

Pagination returns an opaque base64 sort-key cursor; only the key is client-controlled, never the partition. Cursor must match requested prefix. A page can be empty when no records exist. No GSI is required for these initial patterns. Search/status/OS/model indexes and tenant summary counters are future work; client-visible inventory is a page, not a fleet-wide count.

Register transaction: device + serial reservation + audit, all absent conditions. Update transaction: expected device revision + audit. Retired serials remain reserved. Policy update transaction: current revision + immutable version + audit. Evaluation transaction: compare device revision, persist new summary and evidence + audit. Telemetry uses monotonically increasing sequence and nondecreasing report time for unseen reports. Each newly accepted report creates a receipt in the same transaction as device/evidence/audit writes. Receipt lookup precedes sequence checks after authorization/device/lifecycle validation: a matching canonical digest returns the current device unchanged; altered content returns 409. An unseen stale report is rejected. Concurrent submissions can lose the optimistic transaction and return 409; a later exact retry reads the committed receipt. No partial receipt/evidence/device effects are committed.

Expected initial scale: hundreds of devices/tenant. Transaction/item limits bound policy evaluation. Initial evaluator caps enabled policies per operation and rejects excess instead of silently omitting policies. The overview performs tenant-device Queries, at most 11 pages of 100, and rejects results over 1,000 devices. It aggregates non-retired device posture and 24-hour freshness; pass/fail percentage excludes UNKNOWN and NOT_APPLICABLE. Separate pages are not a transactionally frozen snapshot. DynamoDB on-demand still charges reads/writes and storage; bound page size and simulator pacing.

## Current retention behavior (audit record, 2026-11-13)

No TTL is configured. Device records, policy versions, compliance evaluations and audit records currently remain until an explicitly authorized deletion outside the application. There is no audit-delete API. Raw report bodies are not retained. New report receipts retain organization/device/report IDs, sequence, canonical payload hash and server acceptance time; resulting evidence and the latest device snapshot remain. Receipts have no TTL and protect accepted IDs for their stored lifetime, including across restarts. This follows existing no-automatic-deletion behavior; no finite expiration/cleanup was introduced. Old reports cannot be reconstructed and are not backfilled. The legacy latest-report fallback remains.

Simulator state remains in its local JSON file, including pending requests for recovery. Local Compose volumes survive an ordinary stop. Random test tables in an externally running DynamoDB Local emulator remain after tests; Testcontainers-owned ephemeral databases stop with the suite. No retention period, automatic expiration, or bulk cleanup is approved by this document. Any future expiration or deletion policy requires a separate retention/recovery decision; do not automatically expire receipts or audit evidence.

## Approved lifecycle correction (A3)

Reuse existing `Device.status` for lifecycle and `Device.compliance` for posture. Compliance evaluation no longer writes NONCOMPLIANT into lifecycle or reactivates INACTIVE devices. Legitimate check-in and administrator retirement transitions remain separate. No bulk migration, key change, history rewrite or parallel status model was introduced.

`DeviceLifecycle` provides read compatibility. A legacy NONCOMPLIANT record with sequence=0 and no check-in/report ID/hash is reliably a never-reported registration and is exposed as REGISTERED. Ambiguous records retain their stored legacy value, are labeled Needs review in the UI and counted by `lifecycleReviewRequired`; no blanket ACTIVE inference is made. Read paths never persist conversions. A later authorized write can persist the reliable resolved lifecycle, while ambiguous metadata/evaluation writes preserve the ambiguous value until a real check-in or retirement establishes state. Retired records stay retired. Dashboard active count is now based on lifecycle, separate from compliance and 24-hour freshness.

The A3 correction itself did not change keys or retention. The subsequent reviewer-readiness request authorizes the focused receipt and history-ordering fixes documented below.

## Compatible report and history remediation — 2026-11-13

The receipt is additive; existing device IDs, report formats, state files and history records remain unchanged. The owner requested persistent report-ID checks and corrected history ordering after A1–A3. No incompatible schema/API migration or bulk rewrite was needed.

New history keys use exactly nine fractional digits. Reads sort `Audit.timestamp` or `Evidence.evaluatedAt` as Instants descending and break ties by event ID descending, across legacy and new keys. Opaque cursors encode actual stored keys and remain prefix-scoped. Each page queries all pages under that authorized prefix and uses O(page-size) candidate memory; read cost is O(history length). This deliberately preserves old records at a scalability cost. A new indexed layout/migration is out of scope. API pages are not a frozen snapshot during concurrent inserts.
