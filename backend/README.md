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

Outage data comes from Endesa's public ArcGIS feature service for the Spanish distribution network.
