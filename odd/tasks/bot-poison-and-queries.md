# Telegram poison message + query hygiene (audit 2026-10-02, items 14 and 13)

## Objective
One undeliverable Telegram message must not block every later alert; database access should use indexes and avoid needless work, without changing any published number.

## Scope
- Bot (14): a deterministic non-429 Telegram failure for one message (e.g. HTTP 400) is retried a bounded number of times, then that message is skipped and its outages marked so they are not retried forever; other messages keep flowing. Transient failures (network, 5xx, 429) keep the current retry-next-poll behaviour. Same idea for the weekly summary.
- Queries (13): YEAR()/MONTH() predicates -> half-open date ranges (index-friendly); `raw_response` not loaded by list/CSV endpoints; open-data paging without a COUNT per page (Slice/keyset); Endesa HTTP calls outside the DB transaction (fetch, then write in a transaction; keep afterCommit hooks and all data-credibility guards intact); `spring.jpa.open-in-view: false`.

## Constraints
- No change in API/CSV output (same rows, same order, same fields): prove it with tests comparing results before/after on the same fixtures.
- Branch fix/bot-poison-and-queries. Route: delegated direct (one writer, Fable). TDD off. Backend tests in UTC + Madrid; Postgres tests need Docker (CI).

## Tasks
- [x] Q1 Telegram poison-message handling (+ weekly summary) with tests.
- [x] Q2 Query hygiene with equivalence tests. (Postgres-only proofs: compile, CI pending.)
- [ ] Q3 Review, deploy, verify prod numbers unchanged.

## Decisions
- Q1 give-up semantics: given-up outages are kept in the existing bounded in-memory "never resend" set, **not** marked `announced_at`/`restoration_announced_at`. That mark means "Telegram confirmed this message"; faking it would later make the bot announce the restoration of an outage the channel never saw. Cost: after a restart the message is retried 3 more polls (3 HTTP calls) and given up again.
- Q1 counting: strikes are counted per (kind, outage id), not per message identity, so a poison outage is caught even when the per-poll grouping around it changes; a message with N outages gives each of them one strike. Chat-wide failures (403/404, 400 about the chat or the bot's rights, unknown 400 descriptions) stop the poll with ERROR and count nothing (conservative direction: misclassification only retries longer).
- Q2 `raw_response`: closed Spring Data interface projection `OutageView` on derived queries (selects exactly 17 columns, verified with show-sql). The entity must NOT implement the view (Spring Data then treats the return type as non-projecting and loads full rows; caught by a test); `OutageView.of(entity)` adapter + DTO overloads instead. Native `/live` and scheduler selects list columns without `raw_response`.
- Q2 transaction split: `fetchAndSaveOutages()` fetches and parses, then `TransactionTemplate.executeWithoutResult(apply(...))`; hooks (health tracker, deferral afterCompletion, announcers afterCommit) register inside the template's transaction. Public no-transaction constructor for tests (`TransactionOperations.withoutTransaction()`).
- Q2 paging: open-data and admin CSV use `Slice` (no COUNT per page); `/enel` keeps `Page` because `totalElements` is public output.
- Q2 `findByNeighborhoodNameIgnoreCase` left case-insensitive: changing it would alter output for lowercased input; documented in the controller (an expression index would need a migration; table is small).
- No new Flyway migration; `OutageIdentityFlywayMigrationTest` untouched.

## Progress / evidence
- Route: delegated direct, one writer (Fable). Changed lines ~1320 (926 added / 152 deleted in tracked files + 548 in new files); above the ~400 heuristic because of the 366-line equivalence suite and the per-path Q1 tests, kept together so the "no output change" proof ships with the change.
- `cd backend && ./mvnw -q test-compile`: exit 0.
- `cd backend && ./mvnw -q test -Dtest='!OutageIdentityFlywayMigrationTest,!EnelOutageRepositoryPostgresTest,!TimeZoneIndependencePostgresTest,!WeeklySummaryRepositoryPostgresTest,!StartCorrectionMergeMigrationTest,!QueryEquivalencePostgresTest' -Dsurefire.failIfNoSpecifiedTests=false`: exit 0; fresh reports (target/surefire-reports* deleted first): UTC run 29 classes / 341 tests / 0 failures / 0 errors / 0 skipped; Europe/Madrid run 29 / 341 / 0 / 0 / 0.
- New/extended tests: TelegramClientTest 15 (classification by error_code/description), OutageAnnouncerTest 24 (skip-and-continue, give-up after 3 without marking, per-outage counting across regrouping, chat unavailable stops poll with ERROR and never counts, transient unchanged), WeeklySummaryAnnouncerTest 19 (week skipped after 3 rejections, chat unavailable keeps retrying), QueryEquivalenceH2Test 28 (7 scenarios x 4 JVM zones: year/month lists, aggregation, paged totals, slice vs page sequences, projection vs entity for every DTO, open-data CSV bytes and admin CSV end to end through the controllers, fixture on 00:00:00 of the 1st, 23:59:59 of the last day, Dec->Jan, Feb 1st, DST gap and overlap), EnelOutageRepositoryTest +1 (native reads leave raw_response in DB, projection is not the entity).
- Postgres (Docker, CI pending): QueryEquivalencePostgresTest (same 28 cases), EnelOutageRepositoryPostgresTest +1 (native reads without raw_response, every other column verbatim). Compile only locally.
- Known environmental: no Docker locally (Postgres tests excluded). Observed once in run 1 (not in run 2): H2 `IndexOutOfBoundsException` logged by the scheduler error handler during a SpringBootTest context startup (shared `jdbc:h2:mem:pocasluces` across cached contexts, `@Scheduled` first run at startup); not a test failure, pre-existing pattern, JPQL untouched.
- Docs: `docs/operations/telegram.md` (failure kinds, operator actions, weekly summary), `backend/README.md` (Telegram failure handling, scheduler transaction split, "Read paths").
- Not committed; `.atl/`, frontend, infra, `difusion.md` untouched. Engram mirror: `odd/bot-poison-and-queries/decisions` (this document's mirror under `odd/bot-poison-and-queries/tasks` pending the parent's write).

## Next step
Q3: parent review of the diff, work-unit commit(s) on the feature branch, CI run for the Postgres classes, deploy, then compare prod `/api/outages/yearly`, `/monthly`, `/chart`, `/enel` and both CSVs before/after (same rows and totals expected).
