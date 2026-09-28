-- Fix outage identity.
--
-- The previous unique key (neighborhood_name, interruption_date, service_type) let a
-- distinct outage in the same barrio with the same start minute silently overwrite an
-- unrelated one (affected_clients replaced) because neighborhood_name is our own
-- inference from coordinates, not an identity Endesa provides. Endesa's objectId was
-- abandoned as an identity source in July 2026 (commit 3de153d) because it is not
-- stable across feed layer republishes. The new key uses the raw coordinates plus
-- interruption_date and service_type, which together identify a physical outage.

-- 1. Backfill NULL coordinates to 0.0 so the columns can become NOT NULL.
--    NULL only happens when Endesa's feed omits latitude/longitude; the application
--    already treats a missing coordinate as 0.0 for neighborhood/district lookup, so
--    this keeps stored data consistent with that behavior instead of leaving the new
--    key unable to compare these rows at all.
UPDATE enel_outages SET latitude = 0.0 WHERE latitude IS NULL;
UPDATE enel_outages SET longitude = 0.0 WHERE longitude IS NULL;

ALTER TABLE enel_outages ALTER COLUMN latitude SET NOT NULL;
ALTER TABLE enel_outages ALTER COLUMN longitude SET NOT NULL;

-- 2. Defensive pre-check / dedup: delete any existing rows that would violate the new
--    key, keeping the most recently fetched row per key. In practice this should find
--    zero rows, because neighborhood_name is itself derived from (latitude, longitude),
--    so two rows sharing the new key would already have shared the old key too and
--    would already have been merged by the old upsert.
DO $$
DECLARE
    removed_count integer;
BEGIN
    WITH ranked AS (
        SELECT id,
               ROW_NUMBER() OVER (
                   PARTITION BY latitude, longitude, interruption_date, service_type
                   ORDER BY fetched_at DESC, id DESC
               ) AS rn
        FROM enel_outages
    ),
    deleted AS (
        DELETE FROM enel_outages
        WHERE id IN (SELECT id FROM ranked WHERE rn > 1)
        RETURNING id
    )
    SELECT count(*) INTO removed_count FROM deleted;

    RAISE NOTICE 'V3 migration: removed % duplicate row(s) that violated the new (latitude, longitude, interruption_date, service_type) key', removed_count;
END $$;

-- 3. Replace the old natural-key constraint with the new location-based key.
ALTER TABLE enel_outages DROP CONSTRAINT IF EXISTS uk_enel_outage_natural_key;
ALTER TABLE enel_outages ADD CONSTRAINT uk_enel_outage_location_key
    UNIQUE (latitude, longitude, interruption_date, service_type);
