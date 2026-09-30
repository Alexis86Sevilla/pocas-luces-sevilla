# Telegram outage alerts

## Objective
A zero-maintenance public Telegram channel that announces new outages and restorations in Seville, as a citizen service (people subscribe from the website), never publishing false or duplicated information.

## Decisions (user-approved 2026-09-30)
- Implemented inside the existing backend (no extra service to run or monitor). Disabled unless both `TELEGRAM_BOT_TOKEN` and `TELEGRAM_CHAT_ID` are set; secrets only in the VPS systemd environment.
- Bluesky dropped for now.
- New-outage message only after the outage is seen in two consecutive successful polls (filters partial feed responses). Restoration message only after it has been missing for two consecutive polls, and only for outages the bot announced.
- Announcement state persisted in the database (no duplicates after restarts); existing rows at go-live are never announced.
- One grouped message per poll and type; plain text (no parse mode); failures never affect the scheduler and are retried on the next poll.
- Website: "Recibe avisos en Telegram" link to the public channel.
- Channel confirmed by the user: `https://t.me/SevillaSinLuz`, chat id `@SevillaSinLuz`, bot `@SevillaSinLuzBot` already admin with post permission.

## Constraints
- Branch `feat/telegram-bot` from `main`. No commits until the user reviews.
- Next Flyway migration: V6. Timestamps are Europe/Madrid wall-clock; the suite runs under UTC and Europe/Madrid.
- Checks: `./mvnw test` (both surefire executions), `npx ng test --watch=false`, `npx ng build`.

## Design notes (implemented)
- **State (V6)**: `announce_eligible BOOLEAN NOT NULL DEFAULT TRUE` (backfilled FALSE for every existing row), `announced_at`, `restoration_announced_at` (nullable TIMESTAMP), `missing_polls INTEGER NOT NULL DEFAULT 0`. The upsert never touches eligibility or the `*_announced_at` marks; on conflict it only resets `missing_polls` to 0.
- **New**: eligible, not announced, `active`, `fetched_at > first_seen_at` (seen in the current poll and in at least one earlier successful poll; two upserts within one run share the same `now`, so they do not count), start in `[now-12h, now]`. Failed fetches change nothing.
- **Restored**: announced, not restoration-announced, inactive, `missing_polls >= 2`. The scheduler increments the counter in the same transaction as the resolve step (only for announced-not-restored inactive rows, a tiny set); reappearance resets it via the upsert. A single empty/partial poll therefore never produces a message.
- **Delivery**: `OutageAnnouncer.announceAfterCommit()` registered after `FetchHealthTracker.recordSuccess()`; runs in Spring `afterCommit`; candidate query and each mark in `REQUIRES_NEW` transactions; Telegram never called inside a DB transaction; marks only after `ok:true`; 429 skips the rest of the poll; all errors caught, logged with the token redacted (`bot***`), retried next poll.
- **Route**: single delegated writer (this document's tasks B1-T1); exploration read ~20 files before writing.

## Tasks
- [x] B1 V6 migration + entity fields for announcement state, backfilled so existing rows are never announced.
- [x] B2 Announcement selection logic (two-poll confirmation both ways) after each successful fetch.
- [x] B3 Telegram client (Bot API sendMessage), message formatting, grouping and splitting, failure handling.
- [x] B4 Configuration, README and operations doc (how to set the token and chat id on the VPS).
- [x] F1 Frontend "Recibe avisos en Telegram" link (live section and guide), channel URL in one config place.
- [x] T1 Tests.

## Progress / evidence (2026-09-30)
Files:
- Backend main: `config/TelegramProperties.java`, `config/TelegramAlertsConfig.java`, `service/TelegramClient.java`, `service/TelegramMessageFormatter.java`, `service/OutageAnnouncer.java`, `service/OutageDataScheduler.java` (counter + hook), `entity/EnelOutage.java`, `repository/EnelOutageRepository.java` (+`Impl`), `resources/db/migration/V6__add_telegram_announcement_state.sql`, `application.yaml` (`telegram.bot-token`, `telegram.chat-id`).
- Backend tests: `OutageAnnouncementSelectionTest` (H2 scenario: new after 2 polls / not after 1 / failed poll not a sighting / stale backlog / future start / legacy rows never / single missing poll + reappearance → no restoration / restoration after 2 missing polls / empty poll with 3 active then recovery → nothing / flapping after restoration → once), `OutageAnnouncerTest` (disabled → no calls; marks only after ok; failure/429 → no mark, WARN without token; afterCommit), `TelegramClientTest` (payload, no parse_mode, ok:false, 5xx, 403, 429 retry_after, IO error redacted, disabled → no HTTP), `TelegramMessageFormatterTest` (lines, category, thousands, duration, headers, splitting, cap + summary), `OutageIdentityFlywayMigrationTest` (V6 backfill + defaults), `BackendApplicationTests` (disabled without env vars), `OutageDataSchedulerTest` (order: resolve → counter → announcer; nothing on failure).
- Frontend: `core/config/social.ts` (`TELEGRAM_CHANNEL_URL`), `shared/ui/telegram-link/*` (+spec), live section and guide step 2 (+specs).
- Docs: `backend/README.md` ("Telegram alerts", env table, V6, timezone list), `docs/operations/telegram.md`, `docs/operations/README.md` link.

Checks:
- `cd backend && ./mvnw test` (Docker Desktop started locally for the Testcontainers classes): default-test (UTC) `Tests run: 262, Failures: 0, Errors: 0, Skipped: 0`; test-jvm-europe-madrid `Tests run: 262, Failures: 0, Errors: 0, Skipped: 0`; BUILD SUCCESS.
- `cd frontend && npx ng test --watch=false`: 30 files, 136 tests passed.
- `cd frontend && npx ng build`: success.
- `git grep -n -i "bot[0-9]\{6,\}:" -- . ':!*.lock'`: no matches (also none in untracked files).
- Not run: a real send to Telegram (no token locally, by design).

Next step: user reviews the diff, then work-unit commits on `feat/telegram-bot`; on the VPS, follow `docs/operations/telegram.md` (drop-in with the two variables, `daemon-reload`, restart, check the `Telegram alerts enabled for chat @SevillaSinLuz` log line).

### Parent verification (2026-09-30)
- Backend re-run: 262/262 (UTC) and 262/262 (Europe/Madrid). Token-like string scan: none.
- Frontend tests failed to start on this 32-core Windows machine (every vitest fork "Timeout waiting for worker to respond"; `main` failed the same way, so not caused by this change). Fix: `frontend/vitest.config.ts` with `maxWorkers: 4`, wired as `runnerConfig` in `angular.json` → 136/136 in two consecutive runs (~26 s).
