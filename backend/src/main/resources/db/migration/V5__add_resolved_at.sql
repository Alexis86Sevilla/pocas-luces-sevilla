-- Track when an outage ended, instead of only overwriting `active` with no
-- record of when the change happened.
--
-- resolved_at is NULL while an outage is active. When an outage stops being
-- reported, the scheduler sets resolved_at = fetched_at, i.e. the last poll in
-- which Endesa still published it, and clears it back to NULL if the same
-- physical outage (same location key) reappears later. This is a lower bound:
-- the real end happened up to one polling interval (~5 minutes) later, or more
-- if our own polling had a gap, never earlier. Durations are never inflated.
ALTER TABLE enel_outages ADD COLUMN resolved_at TIMESTAMP;

-- Backfill rows that were already inactive before this migration. The exact
-- polling run that marked each of them inactive was never recorded, so the
-- best available proxy for "last seen" is fetched_at itself: the last
-- successful poll in which Endesa still published the outage. As with new
-- rows going forward, the true end time lies within one polling interval
-- (~5 minutes) after this backfilled value.
UPDATE enel_outages SET resolved_at = fetched_at WHERE active = false;

CREATE INDEX idx_enel_outage_resolved_at ON enel_outages (resolved_at);
