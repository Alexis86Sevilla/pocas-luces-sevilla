# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

"Sevilla Sin Luz": a public, civic dashboard of power outages in Seville, built from Endesa/e-distribución's public ArcGIS feed. Site: https://sevillasinluz.es, API: https://api.sevillasinluz.es. Monorepo with `backend/` (Spring Boot) and `frontend/` (Angular). Code, docs and comments are in English; user-facing UI text and Telegram messages are in Spanish.

The repo and its GitHub Actions logs are public: never print secrets, tokens or startup logs in workflows.

## Commands

### Backend (`backend/`, Java 21, Spring Boot 3.5, Maven wrapper)

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev   # H2 in-memory, port 8080, seeds data.sql, /h2-console
./mvnw spring-boot:run                                  # default profile is prod: PostgreSQL, port 8081, needs DB_PASSWORD
./mvnw clean package -DskipTests                        # target/backend-0.0.1-SNAPSHOT.jar
./mvnw test                                             # full suite
./mvnw test -Dtest=ClassName#method                     # single test
```

- Surefire runs the whole suite **twice**: once with the JVM in `${surefire.jvm.timezone}` (UTC by default) and once in `Europe/Madrid` (execution `test-jvm-europe-madrid`, `backend/pom.xml`). A single `-Dtest` also runs in both. Override the first zone with `-Dsurefire.jvm.timezone=America/New_York`.
- Most tests use `@ActiveProfiles("dev")` (H2). The `*PostgresTest` / Flyway migration tests use Testcontainers and need Docker.
- Env vars: `DB_PASSWORD` (prod), `ADMIN_API_KEY` (`X-API-Key` for admin endpoints; unset means 403), `TELEGRAM_BOT_TOKEN` + `TELEGRAM_CHAT_ID` (Telegram stays off unless both are set). Local dev with the `dev` profile needs none, but polls the live Endesa feed over the internet.

### Frontend (`frontend/`, Node 22, pnpm 10, Angular 21, Tailwind 4, Vitest)

```bash
pnpm install
pnpm start                          # ng serve on :4200; proxy.conf.json sends /api to localhost:8080 (backend in dev profile)
pnpm build                          # dist/frontend/browser/
pnpm exec ng test --watch=false     # what CI runs
pnpm exec ng test --watch=false --include='src/app/core/utils/madrid-date.spec.ts'   # single spec
```

There is no lint script; Prettier config lives in `package.json` (printWidth 100, single quotes). Specs sit next to sources as `*.spec.ts`.

## Architecture

### Backend (`com.pocasluces.backend`, layered: controller / service / repository / entity / dto / config)

- **Ingestion**: `OutageDataScheduler` polls the Endesa feed every 5 minutes (fixed delay). HTTP and parsing happen outside any transaction; all writes are then applied in one `TransactionTemplate`. Rows are upserted on `(lat, lon, interruption_date, service_type)`. Outages that disappear from the feed are marked resolved with `resolved_at` = last sighting. Start-time corrections are merged into the existing row, and feed guards reject suspicious polls rather than resolving everything.
- **Geo enrichment**: `DistrictLocator` / `NeighborhoodLocator` use GeoJSON under `src/main/resources/geojson/`. `DistrictBackfillRunner` runs at startup outside `dev`.
- **Health**: `FetchHealthTracker` backs `GET /api/health` (200 `UP`/`STARTING`, 503 `STALE`). The deploy pipeline and external uptime monitoring depend on it.
- **Telegram**: `OutageAnnouncer` and `WeeklySummaryAnnouncer` (Mondays) post via `TelegramClient` after commit. A new outage is announced only after it is seen in two consecutive polls, and is marked announced only after Telegram returns `ok:true`.
- **API**: `/api/outages/*` (yearly, monthly, live, chart, fetch), `/api/open-data/outages.csv` (public CC BY 4.0 export), admin CSV export behind `X-API-Key` (`ApiKeyAuth`). `FetchCooldownGuard` returns 429 for a manual fetch within 5 minutes.
- **Persistence**: PostgreSQL in prod with Flyway migrations in `src/main/resources/db/migration`. The `dev` profile uses H2 + `data.sql`. Add a new `V<n>__*.sql` migration rather than editing existing ones; deploy rollbacks do not undo migrations.
- **Timezone contract** (see the "Timezone contract" section of `backend/README.md`): timestamps are stored and handled in UTC; `Europe/Madrid` is used only for month/year boundaries. `hibernate.type.java_time_use_direct_jdbc=true` in `application.yaml` must stay, and `hibernate.jdbc.time_zone` must never be added. The dual-timezone test runs exist to enforce this.

### Frontend (`frontend/src/app`)

Static SPA (no SSR) served by nginx. `core/` holds config, models, SEO (per-route title and canonical), services (`api-outage.service`) and utils; `layout/` the shell (nav, footer); `shared/ui/` reusable components; `pages/` the routed pages (`/`, `/contexto`, `/datos`, `/guia`, `/mapa`). The map page lazy-loads Leaflet; charts use Chart.js. Dates are rendered in Europe/Madrid via `core/utils/madrid-date.ts`. The API base URL comes from `src/environments/environment*.ts` (relative `/api` in dev).

## Deploy & infra

- `.github/workflows/deploy.yml` deploys on every push to `main`. Backend: `./mvnw clean package` (runs tests), scp the JAR to the VPS, swap it, restart the `sevillasinluz` systemd unit, poll `/api/health` for up to 180 s and roll back to the previous JAR on failure. Frontend (after backend): tests, build, upload to a staging dir, publish hashed assets first and `index.html` last; old bundles are kept 14 days.
- `.github/workflows/cert-expiry.yml` checks TLS certificates daily.
- `infra/` holds reference configs for the Ubuntu VPS (nginx, Postgres backup timer and role, fail2ban), not docker compose. Operational runbooks are in `docs/operations/`.
- `odd/tasks/` holds feature task documents from previous work. `difusion.md` is a git-excluded personal file; ignore it.

## Conventions

- Conventional Commits with a scope (`fix(frontend): ...`, `chore(ci): ...`). Work happens on branches merged into `main`; GitHub Actions are pinned by SHA and updated by Dependabot.
