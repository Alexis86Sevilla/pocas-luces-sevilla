# Data credibility (audit 2026-10-02, items 2, 3, 9)

## Objective
Remove the ways the site can currently publish inflated or false outage data, found by the 2026-10-02 audit.

## Problems
1. Re-keyed duplicates. Endesa sometimes republishes the same physical outage with a corrected interruption start. Our identity key (latitude, longitude, interruption_date, service_type) then inserts a second row and resolves the first in the same poll, usually as a fake "brief" outage. Live CSV: 88/3100 rows match the strict signature (B first seen in the poll where A was last seen, same coords + service_type, B.start <= A.last_seen; 45 with identical affected clients). Example: Polígono Sur, 9 clients, A start 23:00 seen once 23:05:09 (brief), B start 22:50 first seen 23:10:09.
2. Empty or reshaped feed. A response without `features`, a renamed field, a municipality label change, `exceededTransferLimit` ignored, or many unparseable rows all count as a successful poll: every active outage is resolved and /api/health stays UP.
3. API errors. Unknown /api paths and missing request params answer 500 and log ERROR with a stack trace.

## Constraints
- Never publish something false: when unsure, prefer not to resolve/merge, and document the rule in /datos methodology.
- Historical data: correct only rows matching the strict signature, via a reviewed, idempotent Flyway migration with a documented rule; never delete information silently (keep an audit trail of merged rows).
- Branch fix/data-credibility. Backend tests run in UTC and Europe/Madrid; Postgres/Testcontainers classes need Docker (CI). Route: delegated direct (Fable writer). TDD off.

## Tasks
- [x] D1 Ingest: treat a start-time correction of the same outage as an update of the existing row, not a new row (+ regression test with the Polígono Sur case).
- [x] D2 Historical cleanup migration for strict-signature duplicates, with audit trail (+ Postgres test: compiles, CI pending).
- [x] D3 Feed guards: missing features / exceededTransferLimit / high skip ratio fail the run; mass resolution needs two consecutive polls; health exposes feature count and consecutive empty polls.
- [x] D4 404/400 handlers without stack traces.
- [x] D5 Methodology text on /datos + backend README.
- [ ] D6 Verify, review, deploy.

## Design decisions
- **D1 matching rule** (`OutageDataScheduler.applyStartCorrections`): a correction is applied only when, at one (latitude, longitude, service_type), exactly one active row is absent from the poll and exactly one incoming feature has a key no stored row owns, and the incoming start is not after the vanished row's `fetched_at`. The existing row is moved to the corrected key (`correctInterruptionDate`: keeps id, `first_seen_at`, announcement state; sets `original_interruption_date` once and `start_corrected_at`), then the normal upsert updates it. Ambiguous groups and taken keys fall back to the literal behaviour. Telegram: the row never goes inactive (no false "restored"); if announced, it is announced once with the corrected start.
- **D2 rule** (`V8__merge_start_corrected_duplicates.sql`): same point/type; A resolved; A.fetched_at < B.first_seen_at <= A.fetched_at + 10 min; B.interruption_date <= A.fetched_at; one-to-one. B survives (earliest first_seen_at/created_at, original start from A, start_corrected_at = B.first_seen_at, announcement marks = earliest non-null, missing_polls = greater, resolution state B's own); A copied to `enel_outage_merged` (+ merged_into_id, merged_at, merge_reason) then deleted. Chains merged head first, one link per pass; idempotent. Expected merge count on prod: at most the audit's 88; lower if any pair is ambiguous (several rows vanishing/appearing at one point) or more than 10 minutes apart. The migration logs the actual total as a `NOTICE`.
- **D3 thresholds**: `features == null` fails the page; `exceededTransferLimit` keeps paging and fails the run if still set on the last page (or beyond 50 pages); skipped features > 20% fail the run (before any write); mass resolution = would resolve all active rows, or > 50% when >= 4 were active, applied only on two consecutive successful polls (in-memory flag, reset by failed/rejected polls). Health adds `lastFeatureCount` and `consecutiveEmptyPolls`; rejected runs never call `recordSuccess`.
- **D4**: `GlobalExceptionHandler` maps NoResourceFound/NoHandlerFound -> 404, ServletRequestBinding (missing param) / type mismatch / validation -> 400, method not supported -> 405, all with the existing JSON body and DEBUG/WARN logs without stack traces. `ExportController` validates params before writing the CSV header.

## Impact on published numbers
- Outage count: V8 removes the historical start-corrected duplicates (<= 88 rows, about 2.8% of 3100; false "brief" outages mostly), and D1 stops new ones. Durations and `resolved_at` are unchanged for surviving rows; merged rows start at the corrected (earlier) time, so their duration grows by the correction (10 minutes in the Polígono Sur case).
- Mass-resolution guard: `active` flips one poll (5 min) later when a single or a majority of outages vanish at once; durations unaffected (resolved_at = last sighting). Telegram "restored" follows one poll later in that case.

## Progress / evidence (route: delegated direct, Fable writer)
- `cd backend && ./mvnw -q test-compile`: exit 0.
- `cd backend && ./mvnw -q test -Dtest='!OutageIdentityFlywayMigrationTest,!EnelOutageRepositoryPostgresTest,!TimeZoneIndependencePostgresTest,!WeeklySummaryRepositoryPostgresTest,!StartCorrectionMergeMigrationTest' -Dsurefire.failIfNoSpecifiedTests=false` (fresh reports): UTC execution 296 tests, 0 failures, 0 errors; Europe/Madrid execution 296 tests, 0 failures, 0 errors; exit 0.
- `cd frontend && npx ng test --watch=false`: first run 152/155 passed, 3 failed = the known-flaky ChartComponent specs (backend suite running in parallel); rerun: 155 passed (155), 32 files, exit 0.
- `cd frontend && npx ng build`: exit 0, bundle generation complete.
- Postgres-only proofs (`StartCorrectionMergeMigrationTest`, `OutageIdentityFlywayMigrationTest` 6 migrations / version 8, `EnelOutageRepositoryPostgresTest.correctInterruptionDateThenOnConflictUpsert...`, `TimeZoneIndependencePostgresTest` with the two-poll resolve): compile, CI pending (no Docker locally).
- Not touched: `.atl/`, `difusion.md`, `infra/`, VPS; `docs/operations/telegram.md` unchanged (no message semantics changed).

## Next step
D6: review the diff, run CI (Docker tests), deploy; after the V8 run check the `NOTICE` with the merged total against the audit's 88 and spot-check `enel_outage_merged`.
