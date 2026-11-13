# API contract
All endpoints except `/actuator/health` require `Authorization: Bearer <access-token>`. JWT audience is `fleetops-api`. Do not use ID tokens as access tokens. The signed `organizations` claim maps tenant IDs to `ADMIN`, `VIEWER`, or `INGESTOR`.

| Route | Method | Permission / result |
|---|---|---|
| `/api/v1/organizations` | GET | Signed membership map |
| `/api/v1/organizations/{org}/devices` | GET / POST | Reader list / admin or ingestor registration (201) |
| `.../devices/{id}` | GET / PATCH | Reader detail / admin metadata with expected revision |
| `.../devices/{id}/retire` | POST | Admin, `{revision}` |
| `.../devices/{id}/reports` | POST | Admin or ingestor, complete telemetry snapshot |
| `.../devices/{id}/evaluations` | GET / POST | Reader history / admin evaluation |
| `.../policies` | GET / POST | Reader list / admin create, version 0 |
| `.../policies/{id}` | PUT | Admin replace current policy with expected version |
| `.../policies/{id}/history` | GET | Reader immutable versions |
| `.../audit-events` | GET | Reader, newest first |

List responses: `{items: [...], nextCursor: string|null}`. `limit` defaults to 25 and is constrained to 1–100. Pass `cursor` unchanged on the next call. Partition comes exclusively from the authorized route; cursors cannot change tenant. Pagination is not a consistent snapshot across separate requests.

Register example:
```json
{"name":"Synthetic Mac 001","type":"MAC","model":"Synthetic MacBook","os":"macOS","osVersion":"15.10","serial":"SYN-MAC-001","employee":"Synthetic Employee"}
```
Report example:
```json
{"reportId":"report-001","sequence":1,"reportedAt":"2026-11-12T12:00:00Z","osVersion":"15.10","encryption":true,"screenLock":true,"firewall":true}
```
Omitted security fields become unknown; a report is a snapshot, not a patch. Sequence must increase and report time cannot precede the current check-in or exceed server time by more than five minutes. An exact last-report replay returns the existing device without another audit or revision; changed payload under the same ID conflicts. Old duplicates outside the last-report window conflict.

Errors: 400 invalid payload, 401 invalid/missing token, 403 tenant/role denied, 404 authorized resource absent, 409 duplicate/stale revision, 503 persistence unavailable. Domain errors use RFC 9457 Problem Details. Security filter 401 responses currently use Spring Security's standard bearer challenge rather than the same JSON body. `X-Request-ID` correlates application audit events with requests.

Policy rules: MIN_OS (numeric dotted version), ENCRYPTION/SCREEN_LOCK/FIREWALL (true/false), MAX_AGE_HOURS (1–99999), ALLOWED_TYPE (comma-separated MAC/IPHONE/IPAD). Firewall is not applicable to iPhone/iPad. Any rule failure makes a policy fail, then UNKNOWN takes precedence, then COMPLIANT, otherwise NOT_APPLICABLE. The same precedence combines policy outcomes, regardless of severity. Disabled and untargeted policies are not applicable. Empty applicable set is NOT_APPLICABLE, never compliant. Maximum 20 policies per organization can be evaluated; excess fails explicitly. Policy edits require explicit device re-evaluation.
