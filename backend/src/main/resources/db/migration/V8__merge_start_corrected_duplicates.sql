-- Start-time corrections (audit 2026-10-02).
--
-- Endesa sometimes republishes the same physical outage with a corrected start time. The
-- identity key (latitude, longitude, interruption_date, service_type) then produced a second
-- row (B) while the first one (A) was resolved in the very same poll, usually as a false
-- "brief" outage, inflating the outage count. The scheduler now recognises the correction at
-- ingest (OutageDataScheduler, "start-time corrections") and keeps one row. This migration
-- applies the same rule to the rows already stored, and adds the columns the scheduler uses
-- to record a correction.
--
-- original_interruption_date  Start Endesa published first, kept only when a later poll
--                             corrected it (NULL for rows never corrected). Set once; later
--                             corrections keep the first value.
-- start_corrected_at          Poll (Europe/Madrid wall-clock) of the latest correction.
ALTER TABLE enel_outages ADD COLUMN original_interruption_date TIMESTAMP;
ALTER TABLE enel_outages ADD COLUMN start_corrected_at TIMESTAMP;

-- Audit trail: every row merged away is copied here verbatim (same columns, same id) plus
-- the id of the row it was merged into, when, and why. Nothing is deleted silently. Column
-- list taken from enel_outages at this point (no constraints or defaults are copied, so the
-- original ids are kept as they were).
CREATE TABLE enel_outage_merged (LIKE enel_outages);
ALTER TABLE enel_outage_merged
    ADD COLUMN merged_into_id BIGINT NOT NULL,
    ADD COLUMN merged_at TIMESTAMP NOT NULL,
    ADD COLUMN merge_reason VARCHAR(100) NOT NULL,
    ADD PRIMARY KEY (id);
CREATE INDEX idx_enel_outage_merged_into ON enel_outage_merged (merged_into_id);

-- Merge rule (the "strict signature" of the audit). A pair (A, B) is merged when ALL hold:
--   * same latitude, longitude and service_type (same point, same kind of supply);
--   * A is resolved (active = false, resolved_at set): it vanished from the feed;
--   * B appeared in the poll right after A's last sighting: A.fetched_at < B.first_seen_at
--     and B.first_seen_at <= A.fetched_at + 10 minutes (polls run every 5 minutes; the
--     10-minute window tolerates one slow poll and excludes genuine re-faults after a gap);
--   * B's start is not after A's last sighting (B.interruption_date <= A.fetched_at): B
--     describes an outage that was already going on while A was published, i.e. the same one
--     with a corrected start, not a new fault that began later;
--   * the match is one-to-one: A is the only such A for B, and B the only such B for A.
--     Anything ambiguous (several rows vanishing or appearing together at one point) is left
--     untouched; when unsure, the data is not changed.
-- Chains (A -> B -> C, the start corrected twice) are merged head first, one link per pass,
-- until no pair is left; the audit table then links A to B and B to C. Running the rule again
-- finds nothing (merged rows are gone and survivors only gain an earlier first_seen_at), so
-- the step is idempotent.
--
-- The survivor is B (the row with the corrected data). It takes from A:
--   * first_seen_at / created_at: the earliest, so B is no longer "first seen" in the poll
--     it was republished in (and is never a brief outage by construction);
--   * original_interruption_date: A's start (or A's own original if A was itself a survivor);
--   * announcement state: announce_eligible = both eligible; announced_at and
--     restoration_announced_at = the earliest non-null of the two (if either message was
--     sent, the merged outage counts as announced, so nothing is sent again); missing_polls =
--     the greater. Resolution state (active, resolved_at, fetched_at) stays B's own.
DO $$
DECLARE
    merged_in_pass integer;
    merged_total integer := 0;
    pass integer := 0;
BEGIN
    LOOP
        pass := pass + 1;

        CREATE TEMP TABLE merge_pairs ON COMMIT DROP AS
        WITH candidates AS (
            SELECT a.id AS a_id, b.id AS b_id
            FROM enel_outages a
            JOIN enel_outages b
              ON b.latitude = a.latitude
             AND b.longitude = a.longitude
             AND b.service_type = a.service_type
             AND b.id <> a.id
             AND b.first_seen_at > a.fetched_at
             AND b.first_seen_at <= a.fetched_at + INTERVAL '10 minutes'
             AND b.interruption_date <= a.fetched_at
            WHERE a.active = FALSE
              AND a.resolved_at IS NOT NULL
        ),
        one_to_one AS (
            SELECT a_id, b_id
            FROM candidates c
            WHERE (SELECT count(*) FROM candidates x WHERE x.a_id = c.a_id) = 1
              AND (SELECT count(*) FROM candidates x WHERE x.b_id = c.b_id) = 1
        )
        -- Head first: a B that is itself the A of another pair waits for the next pass.
        SELECT a_id, b_id
        FROM one_to_one p
        WHERE NOT EXISTS (SELECT 1 FROM one_to_one q WHERE q.b_id = p.a_id);

        SELECT count(*) INTO merged_in_pass FROM merge_pairs;
        EXIT WHEN merged_in_pass = 0;

        INSERT INTO enel_outage_merged
        SELECT a.*, p.b_id, now() AT TIME ZONE 'Europe/Madrid',
               'start_corrected_duplicate_v8'
        FROM enel_outages a
        JOIN merge_pairs p ON p.a_id = a.id;

        UPDATE enel_outages b
        SET first_seen_at = LEAST(a.first_seen_at, b.first_seen_at),
            created_at = LEAST(a.created_at, b.created_at),
            original_interruption_date = COALESCE(a.original_interruption_date, a.interruption_date),
            start_corrected_at = COALESCE(b.start_corrected_at, b.first_seen_at),
            announce_eligible = a.announce_eligible AND b.announce_eligible,
            announced_at = LEAST(a.announced_at, b.announced_at),
            restoration_announced_at = LEAST(a.restoration_announced_at, b.restoration_announced_at),
            missing_polls = GREATEST(a.missing_polls, b.missing_polls),
            updated_at = GREATEST(a.updated_at, b.updated_at)
        FROM merge_pairs p
        JOIN enel_outages a ON a.id = p.a_id
        WHERE b.id = p.b_id;

        DELETE FROM enel_outages a
        USING merge_pairs p
        WHERE a.id = p.a_id;

        merged_total := merged_total + merged_in_pass;
        RAISE NOTICE 'V8 migration: pass % merged % start-corrected duplicate row(s)', pass, merged_in_pass;

        DROP TABLE merge_pairs;
    END LOOP;

    DROP TABLE IF EXISTS merge_pairs;
    RAISE NOTICE 'V8 migration: merged % start-corrected duplicate row(s) in total (copied to enel_outage_merged)', merged_total;
END $$;
