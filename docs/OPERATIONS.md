# Operations

Start: `docker compose up --build`. Stop: `docker compose down` (preserves volumes). Logs: `docker compose logs -f backend keycloak`. Health: `curl -f http://localhost:8080/actuator/health`. A DOWN database health check means the table cannot be described; inspect endpoint, region, IAM and table initialization.

Local native path: start DynamoDB Local on 8000, Keycloak on 8180 with supplied realm, initialize `fleetops`, start Spring on 8080 and Angular on 4200. Set local AWS placeholders; never use production credentials for the emulator.

401: inspect issuer, audience, clock and JWK connectivity, without copying tokens into logs. Sign in again after expiry. 403: inspect provider-managed organization membership. 409: refresh current revision; do not blindly overwrite newer records. 503: inspect DynamoDB connectivity/throttling and structured server logs. Report timestamp rejection means state would rewind or clock is too far ahead. Preserve the simulator state file: pending reports are replayed with the same ID, sequence and payload.

Correlation: response `X-Request-ID` matches audit requestId. Actuator HTTP metrics are registered internally, with custom report and compliance-duration meters. Only health/info are exposed over HTTP; remote metrics export and CloudWatch dashboards are pending.

Reset demo only after accepting loss of local data: stop the stack, run `docker compose down --volumes`, then restart and delete the corresponding simulator state file. This also removes local Keycloak data. No cloud cleanup command is run by the project.

Deployment/rollback: no automatic deployment exists. Hosting requires production identity, DNS/TLS, approved cost plan and full Docker/E2E validation. Retain prior immutable image digests for ECS rollback; do not use `latest`. Review Terraform plans before apply or destroy, back up data before deletion. The initial Terraform foundation defaults to table deletion protection.
