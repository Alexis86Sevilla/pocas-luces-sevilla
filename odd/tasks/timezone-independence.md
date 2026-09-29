# Timezone independence (fix/timezone-independence)

## Objective
Store and return every outage datetime as Europe/Madrid wall-clock (the API contract, verified against Endesa's feed) regardless of the JVM default time zone, on every read/write path (native JDBC and JPA/JPQL), in PostgreSQL and H2, and prove it with tests that run under non-Madrid zones.

## Problem (production, 2026-09-29)
- Endesa publishes `interruption_date "29/09/2026 10:59"` (Madrid wall-clock). `GET /api/outages/monthly` returned `2026-09-29T08:59:00` (-2h). `GET /api/outages/live` returned 0 while Endesa published 2 active outages; every recent row had `resolved_at == fetched_at` from the same run.
- Production JVM runs in UTC (systemd unit without `TZ`).

## Root cause (confirmed by reproduction)
Two datetime conversions with different zones on the same column:
- `application.yaml` set `hibernate.jdbc.time_zone: Europe/Madrid`. Hibernate 6.6 (`TimestampJdbcType`) converts `LocalDateTime` -> `java.sql.Timestamp` via the **JVM zone** (`Timestamp.from(value.atZone(ZoneId.systemDefault()))`) and then hands it to the driver with a **Madrid** `Calendar`. Under a UTC JVM: JPA writes/bound parameters +2h, JPA reads -2h.
- The native path (`EnelOutageRepositoryImpl`) used `Timestamp.valueOf` / `rs.getTimestamp().toLocalDateTime()`: JVM zone on both ends, so wall-clock round-tripped and rows were stored correctly.
- The V5 resolve step `UPDATE ... WHERE active AND fetched_at < :now` bound `:now` through Hibernate (+2h) and compared it against natively written `fetched_at` (verbatim): every just-upserted row matched and was resolved in the same run -> `/live` empty. JPQL reads (`/monthly`, `/yearly`, `/enel`) showed -2h.
- `pom.xml` forced `-Duser.timezone=Europe/Madrid` for surefire (commit b77901f, 2026-09-28) because `EnelOutageRepositoryPostgresTest` failed on UTC CI runners: that failure was this bug; the flag masked it.
- Reproduced RED before the fix with the new tests under a UTC JVM against real PostgreSQL: `resolveStaleActiveOutages(now)` returned 1 instead of 0; native-written `10:59` read back through JPA as `08:59`.

## Chosen fix and why
Remove the conversions instead of aligning zones:
1. `hibernate.type.java_time_use_direct_jdbc: true` (default document, all profiles) and **remove** `hibernate.jdbc.time_zone`. Verified in `hibernate-core-6.6.53.Final`: `LocalDateTimeJavaType.getRecommendedJdbcType` picks `LocalDateTimeJdbcType` (SQL type 3009) when the flag is on, whose binder/extractor are `SetObjectBinder`/`GetObjectExtractor` (JDBC 4.2 `setObject`/`getObject(LocalDateTime.class)`), no `Timestamp`, no zone. DDL type code stays 93 (TIMESTAMP), so `ddl-auto: validate` in prod is unaffected.
2. Native path binds `LocalDateTime` directly (Spring `StatementCreatorUtils` -> `setObject`; pgjdbc binds it as `timestamp` text) and reads with `rs.getObject(col, LocalDateTime.class)`.
3. `LocalDateTime.now(clock)` with the Europe/Madrid `Clock` is unchanged: "now" semantics stay Madrid.
Rejected alternatives: only removing `jdbc.time_zone` (both ends would use the JVM zone; correct in UTC/Madrid but a wall-clock inside a DST gap of a third zone would still be moved by `Timestamp.valueOf`); setting the JVM default zone at startup (does not fix tests/CI, and leaves code that silently depends on the zone).

## Scope / constraints
- Branch `fix/timezone-independence`, no commits (user tests first). `.atl/` and `frontend/` untouched.
- TDD: not configured for the project; this change was nevertheless done RED -> GREEN with the new regression tests. Runner: `cd backend && ./mvnw test`.
- Route: single bounded writer (delegated direct).

## Tasks
- [x] T1 Root-cause fix: `application.yaml` (direct java.time JDBC, no `jdbc.time_zone`), `EnelOutageRepositoryImpl` (setObject/getObject LocalDateTime, class-level contract comment).
- [x] T2 Tests proving zone independence: `pom.xml` surefire runs the suite twice (`default-test` under `${surefire.jvm.timezone}`, default `UTC`; `test-jvm-europe-madrid` under Europe/Madrid, reports in `target/surefire-reports-europe-madrid`); the Madrid crutch is gone. New `AbstractTimeZoneIndependenceTest` + `TimeZoneIndependenceH2Test` + `TimeZoneIndependencePostgresTest` (Testcontainers): 7 scenarios x zones {UTC, Europe/Madrid, America/New_York, Asia/Kolkata, Pacific/Auckland} + DST-gap cases (NY 2026-03-08 02:30, Madrid 2026-03-29 02:30) = 32 cases per database per execution. Scenarios: same-run resolve must not resolve the just-upserted outage; resolve only earlier runs; native write read back verbatim by SQL text, native read and JPA reads; JPA write likewise; native row re-saved through JPA (DistrictBackfillRunner path) keeps wall-clock; real `OutageDataScheduler` run (mocked feed `"29/09/2026 10:59"`, Madrid Clock) keeps the outage active and resolves it on the next empty poll with `resolvedAt = fetchedAt`; wall-clock missing from the JVM zone stored verbatim.
- [x] T3 Data assessment + read-only SQL: `docs/operations/timezone-audit.md`. Conclusion: the only JPA write path that could have stored shifted values is the scheduler's `repository.save()` between commits 0e7d694 (2026-07-11 13:44, adds `jdbc.time_zone`) and b5c6363 (2026-07-11 17:42, native upsert), *if* a build from that window ran in prod. `DistrictBackfillRunner.save()` is a net zero (read -2h, rebound +2h). V5 resolve shifts nothing (column-to-column); its wrong `active/resolved_at` state self-heals on the first run after the fix. No V6: correction, if Step 1 finds rows, is a reviewed transactional one-off script (documented, not run).
- [x] T4 Docs: `backend/README.md` (Tests section, Configuration note, new "Timezone contract" section with the incident), `docs/operations/README.md` link, this file.

## Evidence
- Baseline before changes: `./mvnw test` 119 tests, 0 failures (single execution, forced Madrid zone).
- RED (new tests, fix not yet applied, main execution UTC): H2 32 run / 27 failures, PostgreSQL 32 run / 24 failures; e.g. PostgreSQL UTC `resolveStepMustNotResolveOutagesUpsertedInTheSameRun`: expected 0 but was 1; `nativeWriteIsReadBackVerbatimByEveryReadPath`: expected 2026-09-29T10:59 but was 2026-09-29T08:59. Only the Europe/Madrid case passed on PostgreSQL.
- GREEN (fix applied): `TimeZoneIndependence*Test` 32/32 per database in both executions.
- Full suite `./mvnw test`: default execution (UTC) 183 run / 0 failures / 0 errors; Europe/Madrid execution 183 / 0 / 0; BUILD SUCCESS. Surefire XML `user.timezone` property: `UTC` in all 17 main reports, `Europe/Madrid` in all 17 second-execution reports.
- Full suite `./mvnw test -Dsurefire.jvm.timezone=America/New_York`: see "Verification" below.

## Verification
- `cd backend && ./mvnw test`: 183 + 183 tests, 0 failures, 0 errors, BUILD SUCCESS (Docker available).
- `cd backend && ./mvnw test -Dsurefire.jvm.timezone=America/New_York`: main execution 183 run / 0 failures / 0 errors with `user.timezone=America/New_York` in all 17 reports; Europe/Madrid execution 183 / 0 / 0; BUILD SUCCESS.
- `git status --short`: M backend/README.md, backend/pom.xml, backend/src/main/java/.../EnelOutageRepositoryImpl.java, backend/src/main/resources/application.yaml, docs/operations/README.md; ?? backend/src/test/java/.../AbstractTimeZoneIndependenceTest.java, TimeZoneIndependenceH2Test.java, TimeZoneIndependencePostgresTest.java, docs/operations/timezone-audit.md, odd/tasks/timezone-independence.md. The two `.atl/` entries were already modified before this work and were not touched. Nothing committed.

## Deploy notes
- The `Environment=TZ=Europe/Madrid` systemd mitigation is valid for the *old* build (with a Madrid JVM both conversions agree) and can go in first. After this fix deploys it is optional: keep it if you want local-time log lines; nothing in the application depends on it any more, and the test suite guards against that dependency coming back.
- First scheduler run after deploy re-opens every outage Endesa still publishes (upsert clears `resolved_at`); `/live` recovers within 5 minutes. No migration runs (no V6).
- Then run Step 1 of `docs/operations/timezone-audit.md` in prod. Expected: one group with `shift = 00:00:00`. Only if other groups appear, review and run Step 2 by hand inside a transaction, after a backup.
- Follow-up (out of scope): `Testimonial.createdAt = LocalDateTime.now()` uses the JVM zone for a default that is only used by seeded dev data; consider the Clock if testimonials are ever created at runtime.
