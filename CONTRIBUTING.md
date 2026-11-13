# Contributing
Use Java 21, Node 22 and Python 3.11+. Keep changes in coherent vertical slices. Run `mvn -f backend/pom.xml spotless:apply` and frontend `npm run format` before committing; run the validation commands in README. Testcontainers requires Docker, or supply a dedicated `TEST_DYNAMODB_ENDPOINT`.

Use feature branches and pull requests for subsequent changes. Commit messages should describe behavior, e.g. `feat(inventory): add indexed status filtering`. Keep TASKS.md and validation evidence accurate. Do not commit secrets, simulator state, dependency directories or build output. New tenant operations must include denial tests. Cloud apply/destroy/deploy requires explicit owner approval.
