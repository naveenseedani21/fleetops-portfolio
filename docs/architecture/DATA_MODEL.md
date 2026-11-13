# DynamoDB access patterns (defined before implementation)
One table with string `pk`, `sk`. Tenant data lives under `ORG#<tenant>` and every repository operation receives an authorized tenant. JSON payload plus numeric `revision` supports domain evolution without an ORM. All ordinary reads use GetItem or Query, never Scan.

| Access | Partition / sort key | Semantics |
|---|---|---|
| Device detail | ORG#id / DEVICE#id | Strong read |
| Device list | ORG#id / prefix DEVICE# | Strong query, 1–100 evaluated items |
| Serial uniqueness | ORG#id / SERIAL#canonical-serial | Transactional conditional create |
| Policies | ORG#id / prefix POLICY# | Current policies, paginated |
| Policy history | ORG#id / PV#policy#padded-version | Immutable revisions |
| Compliance history | ORG#id / EVAL#device#time#UUID | Newest-first query |
| Audit | ORG#id / AUDIT#time#UUID | Newest-first query |

Pagination returns an opaque base64 sort-key cursor; only the key is client-controlled, never the partition. Cursor must match requested prefix. A page can be empty when no records exist. No GSI is required for these initial patterns. Search/status/OS/model indexes and tenant summary counters are future work; client-visible inventory is a page, not a fleet-wide count.

Register transaction: device + serial reservation + audit, all absent conditions. Update transaction: expected device revision + audit. Retired serials remain reserved. Policy update transaction: current revision + immutable version + audit. Evaluation transaction: compare device revision, persist new summary and evidence + audit. Telemetry uses monotonically increasing sequence; exact most-recent replay is accepted only when report ID and canonical payload digest match. Older reports are rejected, never applied. This is bounded last-report deduplication, not an indefinite idempotency ledger.

Expected initial scale: hundreds of devices/tenant. Transaction/item limits bound policy evaluation. Initial evaluator caps enabled policies per operation and rejects excess instead of silently omitting policies. Normal dashboard aggregation is not implemented. DynamoDB on-demand still charges reads/writes and storage; bound page size and simulator pacing.
