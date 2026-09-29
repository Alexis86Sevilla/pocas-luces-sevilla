# Timezone audit: detect and (only if needed) correct shifted datetimes

Companion to the 2026-09-29 timezone incident (see `backend/README.md`, "Timezone
contract", and `odd/tasks/timezone-independence.md`). Everything in **Step 1** is
read-only. **Step 2** is a correction script that must be run by hand, inside a
transaction, only if Step 1 finds rows; it is deliberately *not* a Flyway migration.

## What could be shifted, and why

Every `enel_outages` datetime column is Europe/Madrid wall-clock (`timestamp` without
time zone). Two write paths existed:

| Path | Conversion (before the fix) | Under a UTC JVM |
|------|-----------------------------|-----------------|
| Native `INSERT ... ON CONFLICT` (`EnelOutageRepositoryImpl`, since 2026-07-11 17:42) | `Timestamp.valueOf` on write, `Timestamp.toLocalDateTime` on read: both use the JVM zone | wall-clock round-trips unchanged: **stored correctly** |
| JPA/Hibernate with `hibernate.jdbc.time_zone=Europe/Madrid` (added 2026-07-11 13:44) | write: JVM zone -> Madrid (+offset); read: Madrid -> JVM zone (-offset) | JPA reads of native rows were **shown** -2h (API symptom), JPA writes were **stored** +2h |

Only JPA *writes* can have left wrong values in the database. The candidates:

1. **Scheduler `repository.save()` between commits `0e7d694` (2026-07-11 13:44, adds
   `hibernate.jdbc.time_zone`) and `b5c6363` (2026-07-11 17:42, switches to the native
   upsert).** If production ran a build from that window with a UTC JVM, every row
   fetched during it has *all* datetime columns stored +2h (CEST). Whether such a build
   was ever deployed is not recorded; the query below answers it from the data.
2. **`DistrictBackfillRunner.save()` (prod, after V2).** Assessed as **harmless**: the
   entity is loaded through JPA (values read as `stored - 2h`), only `district_name`
   changes, and Hibernate's UPDATE rebinds the datetime fields through the same
   `+2h` conversion, so `stored - 2h + 2h = stored`. Net shift: zero. The
   `nativeRowSavedAgainThroughJpaKeepsItsWallClock` test covers this path.
3. **V5 `resolveStaleActiveOutages`** (JPQL bulk `SET resolved_at = fetched_at`):
   column-to-column, nothing shifted. Its `WHERE fetched_at < :now` compared against a
   `+2h` `:now`, which is why every just-upserted outage was resolved in the same run;
   that is a wrong `active`/`resolved_at` *state*, not a shifted datetime, and the first
   scheduler run after the fix re-opens every outage Endesa still publishes (the upsert
   clears `resolved_at`). Outages that ended in between keep `resolved_at = fetched_at`
   of the last poll that saw them, which is the intended semantics.
4. Rows from 2026-07-10 (JPA save, but **no** `hibernate.jdbc.time_zone` yet): both ends
   used the JVM zone, so wall-clock round-tripped. Not shifted.
5. H2 dev paths: not production.

## Step 1: read-only diagnostic (PostgreSQL 16, run in prod)

The `raw_response` column holds Endesa's own JSON for each row, including the original
`"interruption_date": "dd/MM/yyyy HH:mm"` string. Since 2026-07-11 17:42 it is the
per-feature fragment `{"attributes": {...}}`; before that it was the whole page
`{"features": [{"attributes": {...}}, ...]}`. The query handles both and matches page
features by `objectid1` or by coordinates.

```sql
-- READ-ONLY. Compares the stored interruption_date with the wall-clock string Endesa
-- published in raw_response. Any non-zero "shift" is a row that was written through a
-- zone conversion.
WITH evidence AS (
    SELECT o.id, o.object_id, o.latitude, o.longitude, o.service_type, o.active,
           o.interruption_date, o.reposition_date, o.first_seen_at, o.fetched_at, o.resolved_at,
           CASE
               WHEN p.j ? 'attributes' THEN p.j -> 'attributes'
               WHEN p.j ? 'features' THEN (
                   SELECT f -> 'attributes'
                   FROM jsonb_array_elements(p.j -> 'features') f
                   WHERE (f -> 'attributes' ->> 'objectid1') = o.object_id
                      OR ((f -> 'attributes' ->> 'latitude')::double precision = o.latitude
                          AND (f -> 'attributes' ->> 'longitude')::double precision = o.longitude)
                   LIMIT 1)
           END AS attrs
    FROM enel_outages o
    CROSS JOIN LATERAL (SELECT o.raw_response::jsonb AS j) p
    WHERE o.raw_response IS NOT NULL
      AND pg_input_is_valid(o.raw_response, 'jsonb')
),
parsed AS (
    SELECT e.*,
           e.attrs ->> 'interruption_date' AS endesa_text,
           CASE
               -- "dd/MM/yyyy HH:mm" or "dd/MM/yyyy HH:mm:ss" (Endesa's format), parsed as
               -- plain wall-clock: no time zone is involved anywhere in this expression.
               WHEN (e.attrs ->> 'interruption_date') ~ '^\d{2}/\d{2}/\d{4} \d{2}:\d{2}(:\d{2})?$'
                   THEN (substr(e.attrs ->> 'interruption_date', 7, 4) || '-'
                      || substr(e.attrs ->> 'interruption_date', 4, 2) || '-'
                      || substr(e.attrs ->> 'interruption_date', 1, 2) || ' '
                      || substr(e.attrs ->> 'interruption_date', 12))::timestamp
               -- ISO-like fallbacks accepted by the scheduler's parser.
               WHEN (e.attrs ->> 'interruption_date') ~ '^\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}'
                   THEN (e.attrs ->> 'interruption_date')::timestamp
           END AS endesa_wall_clock
    FROM evidence e
)
SELECT interruption_date - endesa_wall_clock AS shift,
       count(*)                               AS rows,
       min(first_seen_at)                     AS first_seen_min,
       max(first_seen_at)                     AS first_seen_max,
       count(*) FILTER (WHERE active)         AS still_active
FROM parsed
WHERE endesa_wall_clock IS NOT NULL
GROUP BY 1
ORDER BY 1;
```

Expected healthy result: a single group with `shift = 00:00:00`. Any other group
(typically `02:00:00`, or `01:00:00` for CET dates) lists shifted rows; the
`first_seen_*` bounds should then fall inside 2026-07-11 afternoon (the JPA window
above) if the hypothesis in this document is right.

Coverage check, so you know how many rows the evidence cannot vouch for:

```sql
-- READ-ONLY. Rows without usable Endesa evidence (no raw_response, invalid JSON, or an
-- interruption_date string in an unexpected format).
SELECT count(*) FILTER (WHERE raw_response IS NULL OR raw_response = '')            AS no_raw_response,
       count(*) FILTER (WHERE raw_response IS NOT NULL AND raw_response <> ''
                          AND NOT pg_input_is_valid(raw_response, 'jsonb'))          AS invalid_json,
       count(*)                                                                      AS total
FROM enel_outages;
```

Row-level detail and twin detection (a shifted row whose corrected key already exists
because the native upsert later stored the same outage correctly):

```sql
-- READ-ONLY. One line per shifted row, with the id of a correctly stored twin if any.
WITH evidence AS ( /* same CTE as above */ ), parsed AS ( /* same CTE as above */ )
SELECT p.id, p.object_id, p.active, p.interruption_date AS stored, p.endesa_wall_clock AS endesa,
       p.interruption_date - p.endesa_wall_clock AS shift,
       p.first_seen_at, p.fetched_at, p.resolved_at,
       t.id AS twin_id
FROM parsed p
LEFT JOIN enel_outages t
       ON t.latitude = p.latitude AND t.longitude = p.longitude
      AND t.service_type = p.service_type
      AND t.interruption_date = p.endesa_wall_clock
      AND t.id <> p.id
WHERE p.endesa_wall_clock IS NOT NULL
  AND p.interruption_date <> p.endesa_wall_clock
ORDER BY p.first_seen_at, p.id;
```

## Step 2: correction (only if Step 1 found shifted rows; never run blindly)

Decision: **no Flyway `V6`**. A migration runs unattended at the next deploy against
data nobody has looked at, cannot show a dry run, and would be a permanent no-op in
history if Step 1 finds nothing (the most likely outcome given the analysis above). A
correction that depends on per-row evidence and may need to merge duplicates belongs
in a reviewed, transactional, one-off script. If Step 1 reports zero shifted rows,
there is nothing to do and this section is moot.

Run as the application role or `postgres`, inside one transaction, and read the
counts before committing:

```sql
BEGIN;

-- 1. Snapshot the shifted rows (same detection as Step 1). Session-scoped temp table.
CREATE TEMP TABLE shifted_rows ON COMMIT DROP AS
WITH evidence AS ( /* same CTE as Step 1 */ ), parsed AS ( /* same CTE as Step 1 */ )
SELECT p.id, p.latitude, p.longitude, p.service_type,
       p.interruption_date - p.endesa_wall_clock AS shift,
       p.endesa_wall_clock,
       p.first_seen_at - (p.interruption_date - p.endesa_wall_clock) AS corrected_first_seen_at,
       t.id AS twin_id
FROM parsed p
LEFT JOIN enel_outages t
       ON t.latitude = p.latitude AND t.longitude = p.longitude
      AND t.service_type = p.service_type
      AND t.interruption_date = p.endesa_wall_clock
      AND t.id <> p.id
WHERE p.endesa_wall_clock IS NOT NULL
  AND p.interruption_date <> p.endesa_wall_clock;

SELECT count(*) AS shifted, count(twin_id) AS with_twin FROM shifted_rows;   -- review

-- 2. Rows with a correctly stored twin: the twin (written later by the native upsert)
--    is the authoritative row. Keep it, give it the earlier first_seen_at, drop the
--    shifted duplicate. Idempotent: a second run finds no shifted rows.
UPDATE enel_outages t
SET first_seen_at = LEAST(t.first_seen_at, s.corrected_first_seen_at)
FROM shifted_rows s
WHERE s.twin_id = t.id;

DELETE FROM enel_outages o
USING shifted_rows s
WHERE o.id = s.id AND s.twin_id IS NOT NULL;

-- 3. Rows without a twin: move every datetime column back by the detected shift.
--    All of them were bound through the same conversion in the same INSERT, so the
--    same delta applies to all of them (resolved_at was NULL at the time and, if V5
--    backfilled it from fetched_at, it carries the same shift).
UPDATE enel_outages o
SET interruption_date = o.interruption_date - s.shift,
    reposition_date   = o.reposition_date   - s.shift,
    first_seen_at     = o.first_seen_at     - s.shift,
    fetched_at        = o.fetched_at        - s.shift,
    created_at        = o.created_at        - s.shift,
    updated_at        = o.updated_at        - s.shift,
    resolved_at       = o.resolved_at       - s.shift
FROM shifted_rows s
WHERE o.id = s.id AND s.twin_id IS NULL;

-- 4. Re-run the Step 1 summary here: it must now show a single shift = 00:00:00 group.
--    Then COMMIT, or ROLLBACK if anything looks off.
COMMIT;
```

Take a backup first (`infra/postgres/backup-postgres.sh`) and keep the Step 1 output
with the backup so the change is auditable.
