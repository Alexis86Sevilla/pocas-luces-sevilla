# Real outage duration and methodology

## Objective
Publish only defensible figures: real (observed) outage durations instead of Endesa's estimated restoration, precise wording for affected supply points, and a public methodology section.

## Problem
- "Minutos de media" is computed from `reposition_date`, which is Endesa's *estimate* (`outage-card.component.ts:45-46`). We never record when an outage actually disappears from the feed (`setAllInactive()` stores no timestamp).
- "Afectados" shows `affected_clients` (supply points: homes/premises), which reads as people.
- Figures before 2026-09-28 may be undercounted (outages merged by the old identity key) and are not labelled as such.

## Key facts
- While active, `fetched_at` is refreshed on every 5-minute poll, so for an inactive row `fetched_at` = last time Endesa published it. Real end ∈ (`fetched_at`, first poll where it was missing] → ±5 min.
- API datetimes are Europe/Madrid wall-clock.

## Constraints
- Branch `feat/real-duration` from `main`. NO commits until the user tests.
- Prod PostgreSQL + Flyway (next migration V5); dev H2 `ddl-auto: update`.
- UI copy Spanish; code/docs English. TDD not configured; checks `./mvnw test`, `npx ng test --watch=false`, `npx ng build`.
- Route: delegated direct (2+ non-trivial files).

## Tasks
- [x] R1 Backend: `resolved_at` column (V5); set when an active outage is missing from a successful fetch; cleared if it reappears; backfill historical inactive rows with `fetched_at` (last seen). Expose `resolvedAt` in API and CSV.
- [x] R2 Frontend: average duration uses real duration (`resolvedAt − interruptionDate`) of resolved outages only; ongoing outages keep "estimated restoration"; labels make the difference explicit.
- [x] R3 Wording: "afectados" → "suministros afectados" (or "clientes") everywhere, including hero/context copy if it quantifies people from this field.
- [x] R4 Methodology section in the web (and README link): source, polling, what is exact/approximate/estimated, coverage, known gaps, reliable-from date.

## Acceptance criteria
- No figure labelled as a duration uses the estimated restoration time unless it says "estimada". ✅ (outage-card "min de media (real)" now uses `resolvedAt` only; monthly-history rows show "Duración: X min" only for resolved outages and "Reposición estimada: HH:mm" for ongoing ones; live section's "Hasta (est.)" was already correctly labelled and is untouched).
- Tests cover resolution transitions and the backfill migration on Postgres. ✅ (`OutageDataSchedulerTest`, `EnelOutageRepositoryTest`, `EnelOutageRepositoryPostgresTest`, `OutageIdentityFlywayMigrationTest`).

## Progress / evidence

Status: **done**. Branch `feat/real-duration`, no commits made (per constraint — user tests first).

### R1 — Backend
- `backend/src/main/resources/db/migration/V5__add_resolved_at.sql`: adds nullable `resolved_at TIMESTAMP`, backfills it to `fetched_at` for rows where `active = false`, adds `idx_enel_outage_resolved_at`.
- `EnelOutage.java`: new `resolvedAt` field + index in `@Table`.
- `EnelOutageRepository.java`: new `resolveStaleActiveOutages(now)` — `UPDATE ... SET active=false, resolved_at=:now WHERE active=true AND fetched_at < :now` (JPQL bulk update, works on H2 and Postgres). Removed `setAllInactive()` (no longer called anywhere after the scheduler rewrite; also removed from `EnelOutageRepositoryCustom`/`Impl` and its test). Left `setActiveByObjectIds` untouched — it was already unused in main code before this change, out of scope.
- `EnelOutageRepositoryImpl.java`: `resolved_at` added to the Postgres `INSERT ... ON CONFLICT DO UPDATE` (both insert and `EXCLUDED.resolved_at` on conflict), the H2 merge path, and `findCurrentlyActive`'s row mapper.
- `OutageDataScheduler.fetchAndSaveOutages()`: now upserts every returned outage as `active=true` with `resolvedAt` left at its builder default (`null`) — this both opens new outages and **re-opens** reappearing ones — then calls `resolveStaleActiveOutages(now)` once after the loop. Failed fetch still returns early (no upsert, no resolution). **Zero-feature guard decision: apply, not skip** — an empty Sevilla is plausible and must not be masked; the resolution still runs, but it's logged as `WARN` when it actually resolves ≥1 previously-active outage, so an operator notices a suspicious "cortes desaparecidos de golpe" event without the site silently keeping stale outages as active.
- `EnelOutageResponse` and `OutageExportDto` (+ CSV header/`toCsvRow`) both expose `resolvedAt` (CSV: trailing column, verbatim wall-clock like the other date columns).
- `backend/README.md`: rewrote the "Scheduling" paragraph (also fixed a stale claim that upsert identity was still `(neighborhood_name, interruption_date, service_type)` — it's been location-based since V3) and the "Data source" verified/approximate/estimated breakdown to document `resolvedAt`'s ±5min accuracy; added the V5 bullet under Migrations.
- Root `README.md`: added a line pointing at `/#metodologia` and `backend/README.md#data-source`.

### R2 — Frontend
- `EnelOutage` model (`api-outage.service.ts`): `resolvedAt?: string | null`.
- New `core/utils/outage-duration.ts` (+ spec): `realDurationMinutes(o)` (null unless resolved and `resolvedAt > interruptionDate`) and `estimatedDurationMinutes(o)` (from `repositionDate`, unrelated to `resolvedAt`).
- `outage-card.component.ts`: `avgDuration` now averages only `realDurationMinutes` of resolved outages in the list; returns `null` (rendered as "—") when none are resolved. Added `durationDisplay(outage)` for the per-outage history rows: `"Duración: X min"` when resolved, `"Reposición estimada: HH:mm"` when ongoing.
- `outage-card.component.html`: amber tile now shows `avgDuration() ?? '—'` with label **"min de media (real)"**; per-outage history row shows the new duration line.
- Live section's `"Hasta (est.)"` (from `repositionDate`) was already correctly labelled as an estimate — left untouched, per instructions.

### R3 — Wording ("afectados" → "suministros")
Grepped `frontend/src` for `afectad|afect\.`. Changed:
- `outage-card.component.html` stat tile: `pluralize(..., 'afectado', 'afectados')` → `'suministro afectado'` / `'suministros afectados'`.
- `outage-card.component.html` daily-group header and per-outage badge: `"N afect."` → `"N suministro(s)"` (full word, not abbreviated — an abbreviated "sumin." was explicitly rejected as ugly).
- `live-section.component.html`: `"N afect."` → `"N suministro(s)"`.
- **Not changed** (by design): `context-section.component.html` press quote ("...afectados por un corte de luz") — it's a direct newspaper citation, not our own copy; `pluralize.spec.ts`'s use of `'afectado'` as a generic example word for the pluralize utility itself (not user-facing copy).

### R4 — Methodology
- New standalone component `frontend/src/app/features/methodology/methodology-section.component.{ts,html,spec.ts}`, mounted in `home.component.html` between the monthly section and the donation section, `<section id="metodologia">`. Content: source (e-distribución, 5-min polling), three `<details>` blocks (Exacto / Aproximado / Estimación de Endesa) as a definition list, a limitations callout (sub-5-min outages, server gaps, e-distribución-only coverage, pre-28/09/2026 undercount + how real duration is computed for that period), and links to the GitHub repo and the existing `info@sevillasinluz.es` contact.
- Linked from: the footer (new "Metodología" line next to the email) and one "¿Cómo se calcula?" link inside the monthly-section intro paragraph next to "minutos de media" (chose one clear placement rather than duplicating the link into every district card, which would have been noisy).
- All links are plain `<a href="#metodologia">` anchors — no inline scripts, compatible with the `script-src 'self'` CSP.

### Migration risk (V5, prod Postgres)
- `ALTER TABLE ... ADD COLUMN resolved_at TIMESTAMP` (nullable, no default): metadata-only on Postgres 11+, no table rewrite.
- The backfill `UPDATE enel_outages SET resolved_at = fetched_at WHERE active = false` and the subsequent `CREATE INDEX` do scan/write proportionally to table size; given this project's scale (outages since July 2026 in one municipality) this should be sub-second, but it's worth a quick row-count sanity check right before deploying:
  ```sql
  -- Read-only pre-check: run before applying V5 in prod.
  SELECT
    count(*) FILTER (WHERE active)     AS active_rows,
    count(*) FILTER (WHERE NOT active) AS inactive_rows,
    count(*)                           AS total_rows
  FROM enel_outages;
  ```
  If `inactive_rows` turns out to be unexpectedly large (hundreds of thousands+), consider backfilling in batches instead of a single `UPDATE`; otherwise this is safe to apply as-is.
- `OutageIdentityFlywayMigrationTest` was extended (not just V3/V4) to prove V5 applies cleanly on top of pre-existing V2-shape data with a mix of active/inactive rows, and that the backfill only touches inactive rows.

### Verification
- `cd backend && ./mvnw test`: **119 tests, 0 failures, 0 errors — BUILD SUCCESS** (Docker available; includes `EnelOutageRepositoryPostgresTest` and `OutageIdentityFlywayMigrationTest`, both Testcontainers).
- `cd frontend && npx ng test --watch=false`: **72 tests, 0 failures — all 18 test files passed**.
- `cd frontend && npx ng build`: **success**, initial bundle 405.82 kB raw / 105.57 kB transfer.
- `git status --short`: only the files listed above (plus this task file and the V5 migration/new methodology/outage-duration files as untracked); `.atl/` untouched by this work (its two modified entries predate this session).

### What to check in the browser
1. Monthly section: a district's amber tile should show a number (or "—" if nothing in that month is resolved yet) with the label "min de media (real)"; expand a day's history and confirm resolved outages show "Duración: X min" while ongoing ones show "Reposición estimada: HH:mm".
2. Live section and monthly cards: counts should read "N suministros"/"N suministro" instead of "N afect.".
3. Scroll to (or click the footer "Metodología" link / the monthly section's "¿Cómo se calcula?" link to) `#metodologia` and confirm the three `<details>` blocks expand/collapse and the GitHub/email links work.
4. After the backend has run at least two fetch cycles against a real or seeded dataset, confirm `/api/outages/monthly` and the CSV export include a `resolvedAt` value for outages that disappeared between polls, and `null`/blank for currently active ones.

### Parent correction (after writer)
- `resolveStaleActiveOutages` now sets `resolved_at = fetched_at` (last poll where the outage was still published) instead of the run time. Reason: the backfill already used last-seen, and run time would inflate durations after gaps in our own polling (e.g. server down 3 h ⇒ +3 h). Durations are now consistently a minimum, never exaggerated. Tests updated; methodology copy and backend README reworded. Re-verified: backend 119/119, frontend 72/72.
- Prod pre-check (user, 2026-09-28): 2 active, 2932 inactive, 2934 total — V5 backfill is trivial.
