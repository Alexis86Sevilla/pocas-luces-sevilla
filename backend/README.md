# Backend

Spring Boot 3 API for **Sevilla Sin Luz**. It fetches real-time outage data from Endesa, stores it in PostgreSQL and exposes REST endpoints for the Angular frontend.

## Requirements

- Java 21
- Maven (wrapper included)
- PostgreSQL 16 (production) or H2 (development)

## Profiles

### Production (default)

Connects to PostgreSQL and runs on port `8081`.

```bash
./mvnw spring-boot:run
```

Or run the packaged JAR:

```bash
java -jar target/backend-0.0.1-SNAPSHOT.jar
```

### Development

Uses an in-memory H2 database (with the H2 console enabled) and runs on port `8080`. Must be requested explicitly.

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Or with an environment variable:

```bash
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

## Configuration

Database and runtime settings are in `src/main/resources/application.yaml`:

- `spring.profiles.active=prod` by default
- `prod` profile points to PostgreSQL with the `sevillasinluz` database and applies Flyway migrations from `src/main/resources/db/migration`
- `dev` profile (opt-in) seeds sample data from `src/main/resources/data.sql` and enables the H2 console at `/h2-console`

Environment variables:

| Variable | Profile | Purpose |
|----------|---------|---------|
| `DB_PASSWORD` | prod | PostgreSQL password |
| `ADMIN_API_KEY` | all | Key required in the `X-API-Key` header for protected endpoints |
| `SPRING_PROFILES_ACTIVE` | all | Set to `dev` to opt into the H2/dev profile |
| `TELEGRAM_BOT_TOKEN` | all | Bot token for the public [Telegram alerts](#telegram-alerts). Secret, never logged. Feature off when missing |
| `TELEGRAM_CHAT_ID` | all | Channel to post to, e.g. `@SevillaSinLuz`. Feature off when missing |

Tests run against the `dev` profile (H2) explicitly via `@ActiveProfiles("dev")`, regardless of the default.

`spring.jpa.properties.hibernate.type.java_time_use_direct_jdbc=true` (default document, all profiles) is part of the [timezone contract](#timezone-contract): do not remove it and do not add `hibernate.jdbc.time_zone`.

## Main endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/outages/yearly?year=2026` | All outages for a year |
| GET | `/api/outages/monthly?year=2026&month=7` | Outages for a specific month |
| GET | `/api/outages/live` | Currently active outages |
| GET | `/api/outages/chart?year=2026` | Aggregated chart data by month and district |
| GET | `/api/outages/enel?year=&month=&neighborhood=&page=&size=` | Paged, filterable outage list |
| GET | `/api/health` | Public health check for uptime monitors: `200` (`UP`/`STARTING`) or `503` (`STALE`) based on the last successful Endesa fetch |
| GET | `/api/testimonials` | Video testimonials |
| GET | `/api/neighborhoods` | Seeded neighborhoods |
| POST | `/api/outages/fetch` | Manually trigger a fetch from Endesa (requires `X-API-Key`) |
| GET | `/api/open-data/outages.csv?year=&month=&format=` | Public open-data CSV (CC BY 4.0), see [Open data CSV](#open-data-csv); `month` requires `year` |
| GET | `/api/outages/export/csv?year=&month=` | Admin CSV export with internal fields (requires `X-API-Key`) |

If `ADMIN_API_KEY` is not configured, protected endpoints always return `403`.

`POST /api/outages/fetch` is additionally rate-limited: it returns `429 Too Many Requests` if the previous manual fetch happened less than `admin.fetch.cooldown` ago (default `5m`).

`GET /api/health` returns `{"status", "lastSuccessfulFetch", "ageSeconds"}`: `UP` (200) while the last successful Endesa fetch is at most `health.max-fetch-age` old (default `20m`), `STARTING` (200) when none has succeeded yet within `health.startup-grace` of startup (default `10m`), otherwise `STALE` (503). The timestamp is in memory and recorded after the scheduler's transaction commits; a failed fetch is never recorded (a successful fetch with zero outages is).

## Scheduling

`OutageDataScheduler` fetches data from Endesa every 5 minutes (fixed delay) and upserts records by the location key `(latitude, longitude, interruption_date, service_type)`.

On each run, every outage returned by Endesa is upserted as active with `resolved_at` cleared (an outage found again is re-opened). After that, any outage still marked active whose `fetched_at` predates this run is marked resolved: `active = false` and `resolved_at` set to its `fetched_at`, i.e. the last poll in which Endesa still published it. This is a conservative lower bound of the real end: it never inflates durations, even if our own polling had gaps — see [Data source](#data-source) below. A fetch that returns zero outages resolves every currently active one and is logged as a warning (an outage-free Sevilla is plausible, so it is applied, not skipped, but it is worth flagging). A failed fetch changes nothing: no upsert and no resolution happen for that run. The live endpoint returns active outages fetched within the last 6 hours.

After the resolve step, and in the same transaction, the run increments `missing_polls` for every announced outage it did not see (see [Telegram alerts](#telegram-alerts)); once the transaction commits, `OutageAnnouncer` decides what to post.

Each outage is assigned a district using the official district polygons in `src/main/resources/geojson/distritos-sevilla.json`. Outages created before the district column existed are backfilled at startup by `DistrictBackfillRunner` (non-dev profiles).

## Telegram alerts

`OutageAnnouncer` posts to a public Telegram channel after each successful poll, through `TelegramClient` (Bot API `sendMessage`, plain text, no `parse_mode`, link previews off, own `RestTemplate` with 5 s / 10 s timeouts). It is **off unless both** `TELEGRAM_BOT_TOKEN` and `TELEGRAM_CHAT_ID` are set; at startup the log says `Telegram alerts enabled for chat <id>` or `Telegram alerts disabled (...)`. The token is never logged: every error text that could carry the request URL is redacted (`bot***`) first. VPS setup, verification, disabling and token rotation: [`docs/operations/telegram.md`](../docs/operations/telegram.md).

The bot must never publish something false, so every decision is made from persisted state (`announce_eligible`, `announced_at`, `restoration_announced_at`, `missing_polls`, added by V6) and confirmed by two polls:

- **New outage**: eligible, not yet announced, active (published in the current poll) and `fetched_at > first_seen_at`, i.e. also published in at least one earlier successful poll (the upsert advances `fetched_at` every poll and never changes `first_seen_at`, so two upserts within one run do not count). The start must be in the past and at most 12 h old: a stale backlog that Endesa republishes is never announced. Failed fetches change nothing, so they neither count nor reset.
- **Restored**: announced by the bot, not yet announced as restored, inactive, and `missing_polls >= 2`. The scheduler increments the counter in the same transaction as its resolve step for announced-but-not-restored inactive rows; the upsert resets it to 0 when the outage reappears. A single empty or partial feed response resolves the outages, but they are back (counter 0) on the next poll, so no message is sent. The observed duration in the message is `resolved_at - interruption_date`, the same lower bound the site uses.
- **Go-live**: V6 backfills `announce_eligible = FALSE` for every existing row, so outages already known when the feature is enabled produce neither a "new" nor a "restored" message. The upsert never touches `announce_eligible` or the `*_announced_at` marks.

Messages are grouped per poll and type (`🔴 Nuevos cortes de luz en Sevilla` / `🟢 Luz restablecida`, one line per outage, footer `Datos de e-distribución · https://sevillasinluz.es`), split under Telegram's length limit and capped at 3 messages per type per poll; beyond that the rest is summarized as `… y N más en https://sevillasinluz.es/mapa` and still counts as announced. Lines only contain persisted values (district, neighborhood marked `aprox.`, Endesa's start and restoration estimate, supply points, `Avería`/`Trabajos programados` with the same fallback as the website).

Failure handling: the announcement runs after the scheduler's transaction commits (Spring `afterCommit`, like `FetchHealthTracker`), the candidate query and each mark run in their own `REQUIRES_NEW` transactions, and Telegram is never called inside a database transaction. An outage is marked `announced_at` / `restoration_announced_at` **only after Telegram answers `ok:true`**; on any failure nothing is marked, a `WARN` (token redacted) is logged and the same candidates are retried on the next poll. HTTP 429 stops the rest of that poll. Errors never propagate to the scheduler, so a Telegram outage cannot affect data collection or the health endpoint.

## Tests

```bash
./mvnw test
```

The suite runs **twice**: the main surefire execution under a `UTC` JVM (what production
and CI run in) and a second execution (`test-jvm-europe-madrid`) under `Europe/Madrid`.
Reports land in `target/surefire-reports` and `target/surefire-reports-europe-madrid`.
The main execution's zone can be changed for an extra run, e.g.
`./mvnw test -Dsurefire.jvm.timezone=America/New_York`. See
[Timezone contract](#timezone-contract) for why.

`EnelOutageRepositoryPostgresTest`, `TimeZoneIndependencePostgresTest` and
`OutageIdentityFlywayMigrationTest` use Testcontainers and need a running Docker daemon.

## Packaging

```bash
./mvnw clean package -DskipTests
```

The JAR is produced at `target/backend-0.0.1-SNAPSHOT.jar`.

## Data source

Outage data comes from e-distribución/Endesa's public ArcGIS outage feature service for
the Spanish distribution network (`ESP_Prod_power_cut_View`). It is a read-only, public
endpoint: this application only polls it and never writes back. The scheduler polls it
every 5 minutes for outages in Sevilla municipality.

What is verified vs. approximate:

- **Interruption time (`interruptionDate`)** is Endesa's own reported start time, taken
  verbatim.
- **Reposition time (`repositionDate`)** is Endesa's own *estimate* of when supply will
  be restored, reported while the outage is ongoing. It can change between polls and is
  not the real end of the outage.
- **Resolved time (`resolvedAt`)** is the last poll in which the outage was still
  published (see Scheduling above), i.e. an *observed* end, not an estimate. Durations
  derived from it are a minimum: the real end happened up to one polling interval
  (~5 minutes) later, or more if our own polling had a gap, never earlier. It is NULL
  while an outage is active. Historical rows were backfilled the same way by V5.
- **Brief (`brief`)** is derived, not stored: `true` when the outage is resolved and
  `fetchedAt` equals `firstSeenAt`, i.e. it was published in a single poll only. The poll
  interval is ~5 minutes, so it usually lasted only a few minutes. This is not a guarantee:
  if a poll is delayed (restart, Endesa not answering), a longer outage can also be seen once.
  Most likely a real short outage (for example restored by remote network
  reconfiguration), not a data error, so it is kept and counted. An active outage seen once
  is never brief, since it may still be ongoing. Exposed in the API response and as the
  last CSV column.
- **Neighborhood (`neighborhoodName`)** is our own approximation, inferred from the
  outage's coordinates against a neighborhood boundary dataset. It is not provided by
  Endesa and can be wrong near boundaries or when the feed omits coordinates.
- **District (`districtName`)** uses official district polygons and is more reliable
  than the neighborhood inference, but is still derived from the same coordinates.
- **Cause (`cause`)** is taken verbatim from Endesa's own `des_cause_es` feed field
  (e.g. "Avería" or "Trabajos programados") — it is not inferred or guessed.

Before this fix, outages were identified by `(neighborhoodName, interruptionDate,
serviceType)`. Because neighborhood is itself derived from coordinates, two distinct
outages in the same neighborhood starting at the same minute were silently merged into
a single row, overwriting the earlier one's `affectedClients`. Outages merged under
that identity key **before this fix cannot be recovered** — the overwritten data was
never stored. Outage identity is now `(latitude, longitude, interruptionDate,
serviceType)`.

### Migrations

- `V3__fix_outage_identity_key.sql`: replaces the old natural-key unique constraint
  with the new location-based one described above; backfills any legacy NULL
  coordinates to `0.0` (required for the new `NOT NULL` columns) and defensively drops
  any pre-existing row that would violate the new key (expected to affect zero rows in
  practice).
- `V4__add_cause.sql`: adds the nullable `cause` column populated from Endesa's
  `des_cause_es` field.
- `V5__add_resolved_at.sql`: adds the nullable `resolved_at` column described above.
  Backfills it for rows that were already inactive before this migration, using
  `fetched_at` (their last-seen time) as the best available proxy for when they were
  resolved.
- `V6__add_telegram_announcement_state.sql`: adds `announce_eligible` (NOT NULL,
  default TRUE), `announced_at`, `restoration_announced_at` (nullable) and
  `missing_polls` (NOT NULL, default 0) for the [Telegram alerts](#telegram-alerts), and
  backfills `announce_eligible = FALSE` for every existing row so nothing known before
  go-live is ever announced.

## Timezone contract

Every datetime this API stores or returns (`interruptionDate`, `repositionDate`,
`firstSeenAt`, `fetchedAt`, `createdAt`, `updatedAt`, `resolvedAt`, `announcedAt`,
`restorationAnnouncedAt`) is **Europe/Madrid
wall-clock time with no offset**, exactly as Endesa publishes it (`"29/09/2026 10:59"`
in the feed is stored and returned as `2026-09-29T10:59:00`). Columns are `timestamp`
(without time zone), entities use `LocalDateTime`, and the JSON has no zone suffix.

The JVM default time zone must be **irrelevant**. Production runs without `TZ` (UTC),
CI runs on UTC runners, developers run in Europe/Madrid, and the answer has to be the
same everywhere. The rules that keep it that way:

- **"Now" comes from the `Clock` bean** (`ClockConfig`, `Europe/Madrid`):
  `LocalDateTime.now(clock)`. Never `LocalDateTime.now()` for anything that is stored
  or compared with stored data.
- **Hibernate binds `java.time` values directly** through JDBC 4.2
  (`hibernate.type.java_time_use_direct_jdbc=true` in `application.yaml`). Without it,
  Hibernate 6 converts `LocalDateTime` through `java.sql.Timestamp`, whose
  `valueOf`/`toLocalDateTime` use the JVM default zone.
- **The native JDBC path does the same** (`EnelOutageRepositoryImpl`): parameters are
  passed as `LocalDateTime` (`setObject`) and read with
  `rs.getObject(column, LocalDateTime.class)`. Never `Timestamp.valueOf(...)` or
  `rs.getTimestamp(...).toLocalDateTime()`.
- **Never set `hibernate.jdbc.time_zone`.** It makes Hibernate convert between the JVM
  zone and that zone, which is exactly the mismatch below.
- Tests prove it: `AbstractTimeZoneIndependenceTest` (H2 and PostgreSQL subclasses)
  runs every scenario under five JVM default zones set with `TimeZone.setDefault`,
  checks what the database actually holds as text, and covers wall-clock times that do
  not exist in the JVM zone (DST gaps). On top of that the whole suite runs under UTC
  and under Europe/Madrid (see [Tests](#tests)).

### Incident 2026-09-29

`hibernate.jdbc.time_zone=Europe/Madrid` was configured while the production JVM ran in
UTC. Native upserts wrote wall-clock verbatim (`Timestamp.valueOf` round-trips in a
single zone), but Hibernate shifted every JPA read by -2h (`/monthly` showed `08:59`
for Endesa's `10:59`) and every JPA-bound parameter by +2h. The V5 resolve step,
`UPDATE ... WHERE active AND fetched_at < :now`, therefore compared natively written
`fetched_at` values against a `:now` two hours in the future and resolved every outage
in the very run that had just upserted it, so `/live` returned nothing while Endesa
published active outages. Forcing `-Duser.timezone=Europe/Madrid` in surefire had hidden
the same failure in CI the day before. The fix removed the mismatched conversions
instead of aligning zones, so no deployment setting is load-bearing anymore.

Setting `Environment=TZ=Europe/Madrid` in the systemd unit is harmless and makes log
timestamps local, but the application no longer depends on it. Whether any stored rows
were shifted by an earlier JPA write path, and how to check and correct that, is
covered in [`docs/operations/timezone-audit.md`](../docs/operations/timezone-audit.md).

## Open data CSV

`GET /api/open-data/outages.csv` is a public, unauthenticated download of the outage
history, licensed [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) (the response
carries a `Link: <...>; rel="license"` header). The license covers this project's
compilation and processing; the source data belongs to e-distribución (Grupo Endesa).
Suggested citation: "Sevilla Sin Luz (sevillasinluz.es), a partir de datos públicos de
e-distribución (Grupo Endesa)".

- Optional `year` and `month` filters by interruption start (`month` requires `year`; invalid values return `400`).
- Optional `format`: `csv` (default, the standard file) or `excel`; any other value returns `400`. The `excel` variant is for Spanish-locale Excel (double-click to open): `;` delimiter, decimal comma (`37,40825877`), datetimes as `yyyy-MM-dd HH:mm:ss` (space instead of `T`), same columns, BOM, CRLF and formula neutralization; fields containing `;` are quoted. Its filename ends in `-excel.csv`.
- Filename: `sevillasinluz-cortes-<all|YYYY|YYYY-MM>[-excel].csv`. UTF-8 with BOM, CRLF line endings, `Cache-Control: public, max-age=300`.
- Ordered by `interruption_start` ascending. Rows are read in pages of 500 and streamed, so memory stays bounded.
- Datetimes are `yyyy-MM-ddTHH:mm:ss` in Europe/Madrid local time, without offset. Empty cell = no data.
- Text values starting with `=`, `+`, `-`, `@`, tab or CR are prefixed with `'` to neutralize spreadsheet formula injection.
- Raw feed payloads, hashes and source URLs are never exported.
- This path deliberately does not start with `/api/outages/export`, which nginx rate-limits as an admin endpoint.

| Column | Meaning |
|--------|---------|
| `interruption_start` | Outage start as published by the distributor. |
| `estimated_restoration` | The distributor's own *estimate* of restoration; not the real end. May be empty. |
| `observed_end` | Last poll in which the distributor still published the outage (`resolved_at`). The real end may be up to ~5 minutes later. Empty while active. |
| `observed_duration_min` | Whole minutes from `interruption_start` to `observed_end`; a minimum. Empty unless `observed_end` is after the start. |
| `affected_supply_points` | Supply points (homes or premises) affected, not people. |
| `category` | `Avería` or `Programado`, derived from `cause`; falls back to `service_type` (`LV` = scheduled) when `cause` is empty. Same rule as the frontend. |
| `cause` | Distributor's cause (`Avería` / `Trabajos programados`). May be empty for outages recorded before 2026-09-28. |
| `service_type` | Distributor's service type code, verbatim. |
| `district` | District from the outage coordinates and official district polygons. |
| `neighborhood_approx` | Nearest reference neighborhood; our approximation, unreliable near boundaries. |
| `latitude`, `longitude` | Coordinates as published (WGS84 decimal degrees). |
| `first_seen` | First poll in which we saw the outage. |
| `last_seen` | Last poll in which we saw it published. |
| `active` | `true` if still published at the last poll. |
| `brief` | `true` if the outage was published in a single poll only and was already gone at the next one (resolved, with `first_seen` equal to `last_seen`). The poll interval is ~5 minutes, so it usually lasted only a few minutes; not guaranteed, since a delayed poll (restart, Endesa not answering) can also leave a longer outage seen once. Most likely a real short outage (for example power restored by remote network reconfiguration), not a data error. Always `false` while active. Brief outages are kept and still count in every total. |
