# Brief outages (seen in a single poll)

## Objective
Label outages that e-distribución published in only one poll as "brief" across the site, instead of hiding them or presenting them like any other outage.

## Problem / why
328 of 3,020 outages in 2026 (11%, 11,663 of 88,180 affected supplies) were seen in exactly one poll and then disappeared. The evidence (median 6 min from start to first sighting; typical remote reconfiguration after a fault, e.g. 30/09 Alcosa: 12 records at 15:01, only the 737-supply one remained) says they are most likely real short outages, not data errors. Hiding them would under-report micro-outages; showing them unlabelled mixes them with long outages. The Telegram bot already skips them (two-poll confirmation) and stays unchanged.

## Definition
brief = resolvedAt != null AND fetchedAt == firstSeenAt (seen in one poll only, already gone). Observed duration is then < one polling interval after the first sighting (~5 min, up to ~10 min from start). An active outage seen once is NOT brief (it may still be ongoing).

## Scope
- Backend: expose `brief` in the outage API response and as a CSV column; document it in the open-data/methodology docs.
- Frontend: show a "Corte breve" label where outages are listed; state how many of a period's outages are brief where totals are shown, if it fits naturally; explain the rule on /datos (methodology).
- No change to counts, ranking or map inclusion (brief outages still count). No DB migration.

## Constraints
- Branch feat/brief-outages. No commit until the user reviews locally.
- Route: delegated direct (one writer; backend + frontend, 2+ non-trivial files).
- TDD: off (no project TDD config); ordinary checks.
- Checks: backend `./mvnw test` (both timezones via surefire), frontend `npx ng test --watch=false`, `npx ng build`.
- UI copy in Spanish (site language), code/comments English.

## Tasks
- [x] B1 Backend `brief` flag in response + CSV + tests.
- [x] B2 Frontend label + methodology text + tests.
- [x] B3 Verify all checks; local review by the user.

## Progress / evidence
- B1 (uncommitted): `EnelOutage.isBrief()` (derived, no migration); `brief` appended as last field of `EnelOutageResponse`, last column of the public CSV (`OpenDataOutageRow`, both variants) and of the admin export (`OutageExportDto`); README (field note + CSV column table). Tests: new `EnelOutageResponseTest`, extended `OutageExportDtoTest`, `OpenDataControllerTest`. Backend `./mvnw -q test`: passes with the 3 Docker/Testcontainers classes excluded (`OutageIdentityFlywayMigrationTest`, `EnelOutageRepositoryPostgresTest`, `TimeZoneIndependencePostgresTest`) since Docker is unavailable locally; without the exclusion those 3 error out with "Could not find a valid Docker environment" (231 run, 0 failures).
- B2 (uncommitted): `brief?: boolean` on `EnelOutage` (api-outage.service.ts); outage-card shows a "Corte breve" pill (visible text + title + sr-only), "(N breves)" under the outage count tile and per-day "· N breves"; methodology "Corte breve" entry plus `brief` dictionary row. Frontend `ng test`: 144/144 passed; `ng build`: OK.
- B3: user reviewed locally on 4300 (prod data via temporary local proxy deriving `brief`; Sept 2026: 1481 outages, 167 brief) and approved. Commits: 608b8b2 (backend), 0b7575a (frontend). Postgres/Testcontainers classes not run locally (Docker Desktop not running); CI covers them.
