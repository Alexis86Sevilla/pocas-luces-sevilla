-- Track when an outage was last observed to be missing from a successful Endesa
-- fetch (the scheduler's "resolve" step), instead of only overwriting `active`
-- with no record of when the change happened.
--
-- resolved_at is NULL while an outage is active. It is set to the timestamp of
-- the fetch run in which the outage stopped being reported, and cleared back
-- to NULL if the same physical outage (same location key) reappears later.
-- Because polling runs every ~5 minutes, the real end of the outage lies
-- somewhere within the interval (previous fetched_at, resolved_at] -- i.e.
-- resolved_at is accurate to within one polling interval, not exact.
ALTER TABLE enel_outages ADD COLUMN resolved_at TIMESTAMP;

-- Backfill rows that were already inactive before this migration. The exact
-- polling run that marked each of them inactive was never recorded, so the
-- best available proxy for "last seen" is fetched_at itself: the last
-- successful poll in which Endesa still published the outage. As with new
-- rows going forward, the true end time lies within one polling interval
-- (~5 minutes) after this backfilled value.
UPDATE enel_outages SET resolved_at = fetched_at WHERE active = false;

CREATE INDEX idx_enel_outage_resolved_at ON enel_outages (resolved_at);
