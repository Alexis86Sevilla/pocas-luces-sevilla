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

Tests run against the `dev` profile (H2) explicitly via `@ActiveProfiles("dev")`, regardless of the default.

## Main endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/outages/yearly?year=2026` | All outages for a year |
| GET | `/api/outages/monthly?year=2026&month=7` | Outages for a specific month |
| GET | `/api/outages/live` | Currently active outages |
| GET | `/api/outages/chart?year=2026` | Aggregated chart data by month and district |
| GET | `/api/outages/enel?year=&month=&neighborhood=&page=&size=` | Paged, filterable outage list |
| GET | `/api/testimonials` | Video testimonials |
| GET | `/api/neighborhoods` | Seeded neighborhoods |
| POST | `/api/outages/fetch` | Manually trigger a fetch from Endesa (requires `X-API-Key`) |
| GET | `/api/outages/export/csv?year=&month=` | CSV export (requires `X-API-Key`) |

If `ADMIN_API_KEY` is not configured, protected endpoints always return `403`.

`POST /api/outages/fetch` is additionally rate-limited: it returns `429 Too Many Requests` if the previous manual fetch happened less than `admin.fetch.cooldown` ago (default `5m`).

## Scheduling

`OutageDataScheduler` fetches data from Endesa every 5 minutes (fixed delay) and upserts records by the location key `(latitude, longitude, interruption_date, service_type)`.

On each run, every outage returned by Endesa is upserted as active with `resolved_at` cleared (an outage found again is re-opened). After that, any outage still marked active whose `fetched_at` predates this run is marked resolved: `active = false` and `resolved_at` set to its `fetched_at`, i.e. the last poll in which Endesa still published it. This is a conservative lower bound of the real end: it never inflates durations, even if our own polling had gaps — see [Data source](#data-source) below. A fetch that returns zero outages resolves every currently active one and is logged as a warning (an outage-free Sevilla is plausible, so it is applied, not skipped, but it is worth flagging). A failed fetch changes nothing: no upsert and no resolution happen for that run. The live endpoint returns active outages fetched within the last 6 hours.

Each outage is assigned a district using the official district polygons in `src/main/resources/geojson/distritos-sevilla.json`. Outages created before the district column existed are backfilled at startup by `DistrictBackfillRunner` (non-dev profiles).

## Tests

```bash
./mvnw test
```

`EnelOutageRepositoryPostgresTest` uses Testcontainers and needs a running Docker daemon.

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
