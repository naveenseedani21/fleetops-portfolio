# Current endpoint permissions

This describes implemented behavior after the approved A2 authorization correction. All tenant roles come from a verified JWT `organizations` map. Unknown roles and platform roles alone grant no tenant access.

Paths below use `/api/v1/organizations/{org}` unless stated otherwise.

| Method / path | ADMIN | VIEWER | INGESTOR | Enforcement |
|---|---|---|---|---|
| GET `/api/v1/organizations` | own claim map | own claim map | own claim map | authenticated; TenantAccess.memberships |
| GET `/devices`, `/devices/{id}` | yes | yes | no | DeviceService → require(false) |
| POST `/devices` | yes | no | no | DeviceService → require(true) |
| PATCH `/devices/{id}` | yes | no | no | require(true), revision/live checks |
| POST `/devices/{id}/retire` | yes | no | no | require(true), revision/live checks |
| POST `/devices/{id}/reports` | yes | no | scoped, existing devices only | ingest(org, id), live/order/hash/time checks |
| GET `/devices/{id}/evaluations` | yes | yes | no | require(false), device existence |
| POST `/devices/{id}/evaluations` | yes | no | no | require(true), live check |
| GET `/policies`, `/policies/{id}/history` | yes | yes | no | PolicyService → require(false) |
| POST `/policies`, PUT `/policies/{id}` | yes | no | no | require(true), validation/version checks |
| GET `/audit-events` | yes | yes | no | ReadController → require(false) |
| GET `/dashboard/summary` | yes | yes | no | DashboardService → require(false) |

The role must belong to the **requested organization**. Every Store operation rebuilds the tenant partition; a cursor cannot select a different tenant. Persisted organization/member administration and report export are not implemented.

Global endpoints: `/actuator/health` and descendants are public status-only probes. `/actuator/info` and `/v3/api-docs` require authentication. Other Actuator endpoints are not exposed. There is no platform-administrator bypass. These rules do not grant AWS table access; IAM is a separate boundary.

Tests: FleetIntegrationTest covers device tenant isolation, platform non-escalation, viewer retirement denial, policy create/update and audit/read boundaries, and machine metadata/evaluation denial. TokenValidationTest validates real signatures and claim constraints. Not every endpoint/role combination is exhaustively tested. Persistent denial audit remains missing.

## Optional machine device scope

The verified `organizations` claim remains the tenant authorization source. A machine token with `{"organizations":{"demo":"INGESTOR"}}` can report to existing devices in `demo`. An optional signed claim `{"device_scopes":{"demo":["device-id-1","device-id-2"]}}` narrows that access to the listed IDs. When the claim is present, a missing tenant, empty list, malformed map/list or non-string ID fails closed. Unknown devices are never registered implicitly. Administrators retain their existing organization permissions.

Identity-provider administrators must configure this claim through a trusted mapper; it must not be editable by machine clients or end users. The supplied local realm currently omits the claim and permits tenant-wide reporting for `demo`; no provider replacement or silent realm migration was performed. Tests exercise absent, permitted, wrong-device, empty and malformed scopes.

The demonstrated workflow is administrator registration → machine client-credentials report → persisted compliance update. SimulatorWorkflowTest executes the actual CLI and signed-token HTTP path against an isolated local table. FleetIntegrationTest additionally verifies viewer/machine/cross-organization denials and unchanged device/audit records. See [simulator setup](../SIMULATOR.md).
