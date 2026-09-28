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

`OutageDataScheduler` fetches data from Endesa every 5 minutes (fixed delay) and upserts records by the natural key `(neighborhood_name, interruption_date, service_type)`.

On each run all stored outages are marked inactive and the ones returned by Endesa are reactivated, so an outage no longer reported is treated as resolved. The live endpoint returns active outages fetched within the last 6 hours.

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

- **Interruption/reposition times** are Endesa's own estimates, as reported by the
  distributor — not independently verified, and reposition times in particular are
  estimates that can change between polls.
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
