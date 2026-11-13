# Current endpoint permissions

This describes implemented behavior, not acceptance of deviations. All tenant roles come from a verified JWT `organizations` map. Unknown roles and platform roles alone grant no tenant access. The proposed report-only machine role is pending **A2** in SPEC_AUDIT.md.

Paths below use `/api/v1/organizations/{org}` unless stated otherwise.

| Method / path | ADMIN | VIEWER | INGESTOR | Enforcement |
|---|---|---|---|---|
| GET `/api/v1/organizations` | own claim map | own claim map | own claim map | authenticated; TenantAccess.memberships |
| GET `/devices`, `/devices/{id}` | yes | yes | no | DeviceService → require(false) |
| POST `/devices` | yes | no | yes, pending A2 | DeviceService → ingest |
| PATCH `/devices/{id}` | yes | no | no | require(true), revision/live checks |
| POST `/devices/{id}/retire` | yes | no | no | require(true), revision/live checks |
| POST `/devices/{id}/reports` | yes | no | yes | ingest, live/order/hash/time checks |
| GET `/devices/{id}/evaluations` | yes | yes | no | require(false), device existence |
| POST `/devices/{id}/evaluations` | yes | no | no | require(true), live check |
| GET `/policies`, `/policies/{id}/history` | yes | yes | no | PolicyService → require(false) |
| POST `/policies`, PUT `/policies/{id}` | yes | no | no | require(true), validation/version checks |
| GET `/audit-events` | yes | yes | no | ReadController → require(false) |
| GET `/dashboard/summary` | yes | yes | no | DashboardService → require(false) |

The role must belong to the **requested organization**. Every Store operation rebuilds the tenant partition; a cursor cannot select a different tenant. Persisted organization/member administration and report export are not implemented.

Global endpoints: `/actuator/health` and descendants are public status-only probes. `/actuator/info` and `/v3/api-docs` require authentication. Other Actuator endpoints are not exposed. There is no platform-administrator bypass. These rules do not grant AWS table access; IAM is a separate boundary.

Tests: FleetIntegrationTest covers device tenant isolation, platform non-escalation, viewer retirement denial, policy create/update and audit/read boundaries, and machine metadata/evaluation denial. TokenValidationTest validates real signatures and claim constraints. Not every endpoint/role combination is exhaustively tested. Persistent denial audit remains missing.
