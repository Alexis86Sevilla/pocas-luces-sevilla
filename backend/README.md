# Backend

Spring Boot 3 API for **Sevilla Sin Luz**. It fetches real-time outage data from Endesa, stores it in PostgreSQL and exposes REST endpoints for the Angular frontend.

## Requirements

- Java 21
- Maven (wrapper included)
- PostgreSQL 16 (production) or H2 (development)

## Profiles

### Development (default)

Uses an in-memory H2 database and runs on port `8080`.

```bash
./mvnw spring-boot:run
```

### Production

Connects to PostgreSQL and runs on port `8081`.

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
```

Or run the packaged JAR:

```bash
java -jar -Dspring.profiles.active=prod target/backend-0.0.1-SNAPSHOT.jar
```

## Configuration

Database and runtime settings are in `src/main/resources/application.yaml`:

- `spring.profiles.active=dev` by default
- `prod` profile points to PostgreSQL with the `sevillasinluz` database

## Main endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/outages/yearly?year=2026` | All outages for a year |
| GET | `/api/outages/monthly?year=2026&month=7` | Outages for a specific month |
| GET | `/api/outages/live` | Currently active outages |
| GET | `/api/outages/chart?year=2026` | Aggregated chart data |
| POST | `/api/outages/fetch` | Manually trigger a fetch from Endesa |

## Scheduling

`OutageDataScheduler` fetches data from Endesa every 30 minutes and upserts records by `objectId`.

## Tests

```bash
./mvnw test
```

## Packaging

```bash
./mvnw clean package -DskipTests
```

The JAR is produced at `target/backend-0.0.1-SNAPSHOT.jar`.

## Data source

Outage data comes from Endesa's public ArcGIS feature service for the Spanish distribution network.
