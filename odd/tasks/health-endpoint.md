# Health endpoint

## Objective
Let external uptime monitoring detect a backend that is alive but no longer refreshing Endesa data (the 2026-09-29 incident returned 200 with an empty live list).

## Constraints
- Branch `feat/health-endpoint` from `main`. No commits until the user reviews.
- Public, unauthenticated, cheap; exposes only status and last successful fetch time.
- TDD not configured; checks `./mvnw test` (suite runs in UTC and Europe/Madrid).
- Route: delegated direct (2+ files).

## Tasks
- [x] H1 Scheduler records the time of the last successful Endesa fetch (in memory, via the Madrid `Clock`).
- [x] H2 `GET /api/health`: 200 when the last successful fetch is younger than `health.max-fetch-age` (default 20m); 503 when older, or when none happened after a startup grace period (default 10m).
- [x] H3 Tests and README (endpoint table + monitoring doc).

## Progress / evidence
- H1: `service/FetchHealthTracker` (AtomicReference<Instant> + startedAt, Clock bean). `OutageDataScheduler` calls `recordSuccess()` as the last step of a successful run; it registers a `TransactionSynchronization.afterCommit` when a transaction is active (immediate otherwise), so rollback/commit failure is never recorded. Fetch failure returns early: not recorded. Zero-feature success is recorded.
- H2: `controller/HealthController` GET /api/health, `health.max-fetch-age`/`health.startup-grace` in application.yaml (top-level). Body has only status/lastSuccessfulFetch/ageSeconds; `Cache-Control: no-store`. No auth/CORS/exception-handler changes needed (public, /api/** CORS already covers it).
- H3: FetchHealthTrackerTest, HealthControllerTest (UP/STARTING/STALE with mutable Clock + full-context reachability), 2 scheduler tests; backend/README.md row + paragraph, docs/operations/monitoring.md section/checklist.
- Verification: `cd backend && ./mvnw test`: UTC run 194 tests, 0 failures/errors; Europe/Madrid run 194 tests, 0 failures/errors.
- Route: delegated direct (single writer). Not committed.
- Next: user review, then commit.
