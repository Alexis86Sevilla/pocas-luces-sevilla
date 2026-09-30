# Telegram alerts

The backend announces new outages and restorations in the public channel
`https://t.me/SevillaSinLuz` through the bot `@SevillaSinLuzBot` (already an
administrator of the channel with permission to post). The feature is part of
the ordinary backend: nothing else to run or monitor. It is **off** unless both
environment variables below are set, and the token is a secret that lives only
in the VPS systemd environment. How the messages are decided (two-poll
confirmation both ways, no duplicates across restarts) is described in
[`backend/README.md`, "Telegram alerts"](../../backend/README.md#telegram-alerts).

| Variable | Value | Secret |
|---|---|---|
| `TELEGRAM_BOT_TOKEN` | The token @BotFather gave for `@SevillaSinLuzBot` | **Yes** — never commit it, paste it in a chat, or put it in a log |
| `TELEGRAM_CHAT_ID` | `@SevillaSinLuz` (the channel's public handle; a numeric `-100…` id also works) | No |

## Enable on the VPS

Put the variables in a systemd drop-in of the `sevillasinluz` service, readable by
root only. Do not edit the main unit file (deploys and package updates never
touch a drop-in).

```bash
sudo mkdir -p /etc/systemd/system/sevillasinluz.service.d
sudo install -m 600 /dev/null /etc/systemd/system/sevillasinluz.service.d/telegram.conf
sudo editor /etc/systemd/system/sevillasinluz.service.d/telegram.conf
```

Content (replace the placeholder with the real token; keep the quotes):

```ini
[Service]
Environment="TELEGRAM_BOT_TOKEN=<paste the token from @BotFather here>"
Environment="TELEGRAM_CHAT_ID=@SevillaSinLuz"
```

Apply:

```bash
sudo systemctl daemon-reload
sudo systemctl restart sevillasinluz
```

**Verify:**

```bash
journalctl -u sevillasinluz -n 200 | grep -i "telegram alerts"
```

Expected line right after startup: `Telegram alerts enabled for chat @SevillaSinLuz`
(with the feature off it says `Telegram alerts disabled (set TELEGRAM_BOT_TOKEN and
TELEGRAM_CHAT_ID to enable)`). The token itself is never printed. Then wait for the
next outage: the first message arrives two polls (about 10 minutes) after Endesa
starts publishing it, and the scheduler log shows `Telegram: announced N new
outage(s)`. Delivery problems appear as `WARN ... Telegram: ...` lines with the
HTTP status or Telegram's description, and are retried on the next poll.

Nothing that existed before the restart is ever announced: the V6 migration
marks every pre-existing outage as not eligible, so enabling the feature during
an ongoing outage produces neither a "new" nor a "restored" message for it.

**Rollback / disable:**

```bash
sudo rm /etc/systemd/system/sevillasinluz.service.d/telegram.conf
sudo systemctl daemon-reload
sudo systemctl restart sevillasinluz
```

The log then reports `Telegram alerts disabled` and no HTTP call to Telegram is
made. Announcement state stays in the database, so re-enabling later does not
repeat messages that were already sent.

## Rotate the token

Rotate immediately if the token may have leaked (pasted somewhere, visible in a
screenshot, found in a log). Only the drop-in changes; the code does not.

1. In Telegram, open **@BotFather** → `/mybots` → `@SevillaSinLuzBot` → **API Token**
   → **Revoke current token**. The old token stops working at once, so the backend
   logs `WARN ... Telegram: ... HTTP 401 (Unauthorized)` until the next steps.
2. Edit `/etc/systemd/system/sevillasinluz.service.d/telegram.conf` with the new
   token (`sudo editor ...`, permissions stay `600`).
3. `sudo systemctl daemon-reload && sudo systemctl restart sevillasinluz`.
4. Verify as above. Messages that could not be sent while the token was invalid
   are sent on the next poll after the restart (they were never marked as
   announced).

## Notes

- Telegram's Bot API requires the token in the request URL. The client redacts it
  from every logged error, but do not enable `DEBUG` for
  `org.springframework.web.client` in production, since Spring logs request URLs
  at that level.
- If Telegram confirms a message but saving the "announced" mark fails (for example a
  transient database error), the backend retries the mark immediately (3 attempts) and,
  if it still fails, keeps those outages in memory: they are not sent again and the mark
  is retried once at the start of every poll (`WARN ... held in memory` in the log). While
  any mark is still pending, that poll sends nothing at all (`WARN ... sending nothing in
  this poll`), so the in-memory list never holds more than one poll's messages. The only
  remaining way to get a duplicate is a JVM restart between a confirmed send and a
  successful mark, which can repeat that one message once.
- Rate limiting (`HTTP 429`) skips the rest of that poll; nothing is lost, the
  same candidates are sent on the next one.
- `TELEGRAM_CHAT_ID` is public information (it is the channel handle). Only the
  token is a secret.
