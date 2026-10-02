# Safe deploy (audit 2026-10-02, items 5 and 6 code part)

## Objective
A deploy that breaks the backend must not leave the site down with a green workflow, and a half-copied frontend must never be served.

## Scope (no change to the VPS layout; runs as the existing VPS_USER)
- Backend: upload the new JAR next to the running one, keep the previous JAR, swap, restart, poll http://127.0.0.1:8081/api/health; if it does not come up, restore the previous JAR, restart, fail the job. Then check https://api.sevillasinluz.es/api/health from the runner.
- Frontend: run unit tests in CI; upload to a staging dir; copy hashed assets first and index.html/boot.js last; check the site answers 200.
- Workflow: `concurrency` (no overlapping deploys), frontend `needs: backend`, `pnpm install --frozen-lockfile`.
- Backend prod profile: `server.address: 127.0.0.1` (nginx proxies to 127.0.0.1:8081).
- Docs: docs/operations README (deploy section, stale checklist).

## Out of scope (owner decisions on the VPS)
Dedicated deploy user / no root key, fail2ban nginx-limit-req backend check.

## Tasks
- [x] S1 Workflow + application.yaml + docs (route: delegated direct).
- [ ] S2 Review, push, observe the first deploy end to end.

## Progress / evidence
- S1 (delegated direct, uncommitted): deploy.yml, application.yaml (server.address 127.0.0.1), docs/operations/README.md. Checks: yaml.safe_load ok; action-validator exit 0; ng test 155 passed; backend mvn test (excluding Postgres/Flyway ITs) exit 0.
