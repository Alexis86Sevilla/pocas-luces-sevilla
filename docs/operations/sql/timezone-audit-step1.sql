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
