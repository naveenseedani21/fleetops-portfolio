# API design index

The maintained contract is [API guide](../api/API.md) with [generated OpenAPI snapshot](../api/openapi.json). This index preserves those existing paths.

Base path `/api/v1/organizations/{org}` makes tenant context explicit. Services authorize verified JWT membership before Store access. Policy updates use PUT with the expected version, device metadata uses PATCH with the expected revision, and immutable history uses `/history` or `/evaluations`. Section12.1 of the master specification permits coherent endpoint adjustments; these names do not require a parallel API.

Responses use bounded cursor pages. Domain, DTO and SDK failures use Problem Details through `shared/Errors`; auth filter failures and generic errors require additional contract coverage. `X-Request-ID` is generated per request and copied into audit records. No report-history, organization-detail or member-administration API exists yet. See [permission matrix](../security/PERMISSIONS.md) and [audit](../SPEC_AUDIT.md) for gaps; the OpenAPI snapshot is not proof of full acceptance.
