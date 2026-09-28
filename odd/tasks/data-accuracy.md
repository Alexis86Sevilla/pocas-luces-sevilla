# Data accuracy

## Objective
Make every public figure traceable to Endesa's data without distortion introduced by our own processing (activism credibility).

## Problem
Audit on 2026-09-28 found: distinct outages merged by our natural key (affected clients overwritten), CSV export times shifted +2h, "Avería/Programado" inferred from a service-type heuristic while the feed provides the cause explicitly, and spoofed request headers that are not needed.

## Constraints
- Worktree `../pocas-luces-sevilla-worktrees/data-accuracy`, branch `fix/data-accuracy` from `main`.
- NO commits until the user reviews (user preference).
- Production DB is PostgreSQL with Flyway (`baseline-on-migrate`, `ddl-auto: validate`); migrations must be safe on existing data. Dev/H2 uses `ddl-auto: update` with Flyway disabled.
- Backend date semantics: API datetimes are Europe/Madrid wall-clock (verified against production).
- TDD: not configured. Checks: `./mvnw test` (in `backend/`, Docker needed for Testcontainers).
- Route: delegated direct.

## Tasks
- [x] D1 Outage identity: unique key `(latitude, longitude, interruption_date, service_type)` instead of `(neighborhood_name, interruption_date, service_type)`; Flyway V3 migration with pre-check; upsert (Postgres + H2 path) updated; tests proving two outages at different points with the same start no longer merge.
- [x] D2 Cause from source: persist Endesa's `des_cause_es` (new nullable column via V4 migration), expose it in `EnelOutageResponse` and the CSV export; frontend not touched (parent will wire it later).
- [x] D3 CSV export: remove the wrong UTC→Madrid conversion; export Madrid wall-clock as stored; test.
- [x] D4 Remove spoofed `Referer`/`User-Agent`; send an honest User-Agent identifying the project.
- [x] D5 Docs: README data-source section states what is verified, what is approximate (barrio), and that past merged outages cannot be recovered; documents V3/V4 migrations.

## Acceptance criteria
- `./mvnw test` passes including Postgres Testcontainers tests for the migration and new key. MET: 113/113 tests pass, including a real Flyway-on-Postgres migration test.

## Progress / evidence

Route: delegated direct (single writer agent), no commits made (uncommitted in worktree per constraint).

### D1 — Outage identity
- `entity/EnelOutage.java`: `latitude`/`longitude` now `@Column(nullable = false)`; unique constraint renamed `uk_enel_outage_location_key` on `(latitude, longitude, interruption_date, service_type)`; `equals`/`hashCode` updated to the same key.
- `repository/EnelOutageRepositoryImpl.java`: Postgres `ON CONFLICT (latitude, longitude, interruption_date, service_type)`; H2 path renamed `findByLocationKey`; both keep `neighborhood_name`/`district_name`/`cause` updated on conflict.
- `repository/EnelOutageRepository.java`: `findByNeighborhoodNameAndInterruptionDateAndServiceType` → `findByLatitudeAndLongitudeAndInterruptionDateAndServiceType`.
- `service/OutageDataScheduler.java`: now persists the already-defaulted `lat`/`lon` locals (previously it defaulted them only for neighborhood/district lookup but persisted the raw, possibly-null `attr.getLatitude()/getLongitude()`), so the column is never null going forward.
- Migration `V3__fix_outage_identity_key.sql`: backfills NULL lat/long to `0.0`, sets `NOT NULL`, defensively dedups any row that would violate the new key (keeps most-recently-fetched, `RAISE NOTICE` count — provably a no-op on real data since neighborhood is a pure function of coordinates), drops old constraint, adds new one.
- **NULL coordinate decision**: chose "backfill to 0.0 + NOT NULL" over "nullable + COALESCE unique index" because the app already treats missing coordinates as the `0.0/0.0` "unknown location" sentinel (see `DistrictBackfillRunner`); making the column NOT NULL keeps the ON CONFLICT upsert a plain equality match instead of relying on Postgres 15+ `UNIQUE NULLS NOT DISTINCT` or a COALESCE-based partial index.
- Tests: `EnelOutageRepositoryTest` (H2) and `EnelOutageRepositoryPostgresTest` (Testcontainers) both got a new `shouldTreatDifferentCoordinatesAsDistinctOutagesEvenWithSameNeighborhoodAndStart` test (2 rows, both `affectedClients` preserved) and the H2 test also got `shouldCollapseSameCoordinatesStartAndTypeIntoOneUpdatedRow` (idempotency). The existing concurrency test (renamed `shouldPreventDuplicateLocationKeyOnConcurrentUpsert`) still passes.
- New `test/.../migration/OutageIdentityFlywayMigrationTest.java`: boots a bare Testcontainers Postgres (no Spring context), creates the schema exactly as it stood after V1+V2 via raw SQL, inserts pre-existing rows (including one with NULL coordinates and two rows that collide only under the new key), then runs real Flyway (`baselineVersion=2`, `baselineOnMigrate=true`, mirroring the `prod` profile) and asserts: exactly V3+V4 executed (not skipped), the dedup kept the most-recently-fetched row, NULL coordinates became `0.0`, old constraint is gone/new one enforced (duplicate insert throws `SQLException`), and the `cause` column round-trips. This is the proof that `EnelOutageRepositoryPostgresTest` (which runs Hibernate `ddl-auto=update` under the `dev` profile, Flyway disabled) does not provide.

### D2 — Cause from source
- `dto/EnelApiResponse.Attributes` already mapped `des_cause_es` → `cause` (pre-existing, unused). Now wired through: `OutageDataScheduler` sets `.cause(attr.getCause())`; entity got a nullable `cause VARCHAR(255)` column (`V4__add_cause.sql`); both upsert paths persist/update it; `EnelOutageResponse` and `OutageExportDto` (CSV, new trailing `cause` column) expose it.
- Frontend intentionally untouched per instructions.

### D3 — CSV export time bug
- `dto/OutageExportDto.java`: removed `toMadridWallClock`'s UTC→Madrid `atZoneSameInstant` conversion (was adding +2h to values that are already Madrid wall-clock, since `ClockConfig` fixes the app `Clock` to `Europe/Madrid` and Endesa's own dates are Madrid local time). Replaced with `formatWallClock`, which just formats the stored `LocalDateTime` as-is; comment corrected.
- New test `fromShouldNotShiftStoredWallClockTimes` asserts a stored `2026-09-28T15:38:00` exports unchanged (matches the verified Endesa raw "28/09/2026 15:38" → API example in the constraints).

### D4 — Honest headers
- `service/EnelApiService.java`: removed the spoofed `Referer: https://www.e-distribucion.com/`; `User-Agent` changed from generic `Mozilla/5.0` to `SevillaSinLuz/1.0 (+https://sevillasinluz.es)`.
- `EnelApiServiceTest.shouldSetExpectedHeaders` updated to assert the new User-Agent and the absence of a `Referer` header.

### D5 — Docs
- `backend/README.md` "Data source" section rewritten: names the exact ArcGIS service, read-only/polling cadence, what's verified (times, cause from `des_cause_es`) vs. approximate (neighborhood from coordinates; district from official polygons, more reliable), and states outages merged under the old identity key before this fix cannot be recovered. Added a "Migrations" note for V3/V4.

### Verification
- `cd backend && ./mvnw -o test`: **113/113 tests pass, 0 failures, 0 errors** (includes `EnelOutageRepositoryPostgresTest` and the new `OutageIdentityFlywayMigrationTest`, both against real Testcontainers Postgres; Docker was available locally).
- One pre-existing test (`DistrictBackfillRunnerTest.shouldSaveUnknownDistrictForNullCoordinates`) tested a "legacy row with NULL coordinates" scenario that the NOT NULL constraint now makes unreachable; removed with a comment pointing to the equivalent `0.0/0.0` sentinel test that still covers the same "unknown location" behavior.
- `git status --short` in the worktree: only the files listed above changed/added; nothing committed; no changes outside the worktree.

### Pre-deploy check for production (read-only, run before applying V3)
```sql
-- Count rows that would collide under the new key (expected: 0, per the reasoning
-- above — neighborhood is a pure function of coordinates, so the old key was already
-- at least as strict as the new one for real application data).
SELECT latitude, longitude, interruption_date, service_type, COUNT(*) AS collisions
FROM enel_outages
GROUP BY latitude, longitude, interruption_date, service_type
HAVING COUNT(*) > 1;

-- Count rows with NULL coordinates that V3 will backfill to 0.0.
SELECT COUNT(*) AS null_coordinate_rows
FROM enel_outages
WHERE latitude IS NULL OR longitude IS NULL;
```

### Risks for the production migration
- `ALTER TABLE ... SET NOT NULL` on `latitude`/`longitude` requires a full table scan/lock in Postgres to verify the constraint (no `NOT VALID` fast path available for `SET NOT NULL` before PG 12's check-constraint trick, and this migration doesn't use that trick) — acceptable for this table's expected size, but worth running the pre-deploy check above first and doing the deploy during low-traffic hours.
- The `DO $$ ... $$` dedup block silently deletes rows if the pre-check above returns non-zero counts; the `RAISE NOTICE` only appears in the Postgres/Flyway log, so check that log after migrating in case anything was actually deleted (expected: "removed 0 duplicate row(s)").
- `DistrictBackfillRunner` (`@Profile("!dev")`) still runs on every prod startup and calls `repository.save()` per row; unaffected by this change but worth knowing it will still process the full backlog if `district_name` is null for any row.
