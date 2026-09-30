-- Persisted state for the public Telegram alerts, so that no message is ever sent
-- twice (also across restarts) and so that outages already known when the bot
-- went live are never announced, neither as "new" nor as "restored".
--
-- announce_eligible        FALSE for every row that exists when this migration runs
--                          (backfilled below); TRUE by default for rows inserted later.
--                          Never changed afterwards.
-- announced_at             Poll (Europe/Madrid wall-clock) in which the "new outage"
--                          message was confirmed sent (Telegram answered ok:true).
--                          NULL until then.
-- restoration_announced_at Same for the "power restored" message.
-- missing_polls            Number of consecutive successful polls in which an already
--                          announced outage has NOT been published by Endesa. Incremented
--                          by the scheduler after its resolve step, reset to 0 by the
--                          upsert when the outage reappears. The restoration message is
--                          sent only once it reaches 2, so a single partial or empty feed
--                          response never produces a false "restored" message.
ALTER TABLE enel_outages ADD COLUMN announce_eligible BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE enel_outages ADD COLUMN announced_at TIMESTAMP;
ALTER TABLE enel_outages ADD COLUMN restoration_announced_at TIMESTAMP;
ALTER TABLE enel_outages ADD COLUMN missing_polls INTEGER NOT NULL DEFAULT 0;

-- Go-live backfill: nothing that already exists is ever announced.
UPDATE enel_outages SET announce_eligible = FALSE;
