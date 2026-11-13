# Architecture decision index

Preserve [ADR0001: stack and boundaries](../decisions/0001-stack-and-boundaries.md). It records an implementation decision, not proof that the user approved every deviation.

| Topic required by §19.2 | Current evidence | Follow-up |
|---|---|---|
| Spring Boot | ADR0001 and backend/pom.xml | Separate detailed ADR with alternatives/consequences |
| Angular | ADR0001 and frontend manifests | Separate detailed ADR |
| DynamoDB modeling | ADR0001 and DATA_MODEL.md | Expand index/ledger and growth tradeoffs before migration |
| Modular monolith | ADR0001 and ARCHITECTURE.md | Expand module boundary rationale |
| Authentication | ADR0001 and THREAT_MODEL.md | Membership ownership / production provider decision pending |
| Report consistency | ADR0001 and DeviceService transactions | Preserve atomicity; document bounded deduplication limitation |
| Cloud deployment | DEPLOYMENT.md and Terraform foundation | Full hosting design awaits owner review |

Do not relabel prospective plans as accepted ADRs. The audit's A1–A3 are pending user decisions; further report ledger and wire-status decisions must preserve existing data/contracts.
