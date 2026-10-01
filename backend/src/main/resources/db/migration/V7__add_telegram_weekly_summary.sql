-- One row per week whose Telegram summary was confirmed sent (Telegram answered ok:true),
-- so the weekly summary is posted at most once per week, also across restarts.
--
-- week_start  The Monday (Europe/Madrid calendar date) of the summarized week.
-- sent_at     Europe/Madrid wall-clock time at which the send was confirmed.
--
-- Rows are inserted only after a confirmed send, with insert-if-absent semantics
-- (ON CONFLICT DO NOTHING), so a retry can never produce a second row nor an error.
CREATE TABLE telegram_weekly_summary (
    week_start DATE PRIMARY KEY,
    sent_at TIMESTAMP NOT NULL
);
