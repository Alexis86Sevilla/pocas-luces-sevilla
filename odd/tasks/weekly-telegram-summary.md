# Weekly Telegram summary

## Objective
Every Monday morning the public Telegram channel gets one automatic summary of the previous week's outages, so the channel promotes the site by itself (the author's time for promotion is minimal).

## Content (Spanish, same honest tone as the alerts)
- Week range (Mon–Sun, Europe/Madrid), e.g. "Semana del 21 al 27 de septiembre".
- Total outages that started that week and total affected supplies; how many of them were brief (seen in a single poll) — brief ones still count, stated explicitly.
- Top 3 districts by number of outages (with count). Ties ordered alphabetically.
- Comparison with the previous week's total only if both weeks are fully covered by our data (no fabricated trend).
- Link to https://sevillasinluz.es and the source line "Datos de e-distribución".
- A week with zero outages still gets a (short) summary.

## Rules
- Week = interruption_date in [Monday 00:00, next Monday 00:00) Madrid wall-clock (backend datetimes are Madrid wall-clock LocalDateTime).
- Sent at most once per week, persisted (new Flyway migration V7, e.g. table telegram_weekly_summary(week_start DATE PK, sent_at TIMESTAMP)); survives restarts; no duplicate on retry.
- Sent after each successful poll once now >= Monday 09:00 Madrid, only for the week that just ended, and only until Monday 23:59 (if the app is down all Monday, skip that week rather than posting late).
- Uses the existing TelegramClient; disabled when Telegram alerts are disabled. Failure → retried on the next poll the same day. Token never logged.
- Must not delay or block outage alerts.

## Constraints
- Branch feat/weekly-telegram-summary. Commit only after user review.
- Route: delegated direct (one writer; backend only, 2+ non-trivial files). TDD off; ordinary checks.
- Checks: backend tests (Testcontainers classes need Docker → CI).

## Tasks
- [x] W1 Migration + persistence of sent weeks. (V7 + WeeklySummaryRepository; Postgres IT compiles, CI pending)
- [x] W2 Weekly stats query + message formatter + tests (incl. zero-outage week, ties, comparison rule). (stats SQL test compiles, CI pending)
- [x] W3 Scheduling hook after successful poll + tests (window, idempotency, disabled, failure retry).
- [x] W4 Docs (docs/operations/telegram.md, backend README).
- [x] W5 User review of a sample message (approved 2026-10-01; "(ninguno breve)" dropped when zero); deploy.

## Progress / evidence
- Route: delegated direct (one writer; 2+ non-trivial files). TDD off, ordinary checks.
- Implemented: `WeeklySummaryAnnouncer` (hook after alerts in `OutageDataScheduler`), `WeeklySummaryFormatter`, `WeeklySummaryRepository` (JDBC stats + sent weeks), `V7__add_telegram_weekly_summary.sql`; tests: `WeeklySummaryFormatterTest` (10), `WeeklySummaryAnnouncerTest` (12), `WeeklySummaryRepositoryPostgresTest` (Testcontainers, compiles, CI pending); scheduler tests updated for the new constructor arg.
- Decisions: top list excludes "Zona no identificada" (totals include it); comparison only if MIN(first_seen_at) <= previous week start; a week is summarized only if MIN(first_seen_at) <= that week's Monday 00:00 (else skipped, never partial totals or a false zero week); if mark fails after a send the week is held in memory.
- `./mvnw -q test-compile`: OK. `./mvnw -q test` (excluding Testcontainers classes): exit 0, no failures.
- Review follow-ups (uncommitted, on top of the 3 reviewed commits):
  1. Pending-mark gate: entries are abandoned after 3 failed retry runs (alerts resume, ids kept in a bounded never-resend set of 1000); `OutageAnnouncerTest` covers deterministic failure, other-kind exclusion and health flag; docs updated.
  2. Partial-week coverage: week skipped unless earliest `first_seen_at` <= its Monday 00:00; tests, README and telegram.md updated.
  3. 429: `OutageAnnouncer.telegramHealthy()` passed by the scheduler as a `BooleanSupplier`; weekly summary skipped for that poll; tested in `WeeklySummaryAnnouncerTest` and the scheduler test.
  4. afterCommit path: two tests (runs only after commit; exception contained), synchronization cleared in `finally`.
  5. `WeeklySummaryRepositoryPostgresTest` helper takes a `boolean resolved` instead of an ignored timestamp.
  6. Javadoc and this document use the real week "Semana del 21 al 27 de septiembre".
  7. `cert-expiry.yml`: `timeout 15` on `openssl s_client`, `timeout-minutes: 5` on the job.
