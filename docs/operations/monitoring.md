# Monitoring

## External uptime checks

Use a free-tier external monitor (not hosted on the same VPS, so a VPS outage
is actually detected) — e.g. [UptimeRobot](https://uptimerobot.com/) or
[Better Stack](https://betterstack.com/uptime):

| Check | URL | Expected | Interval |
|---|---|---|---|
| Site | `https://sevillasinluz.es` | HTTP 200 | 5 min |
| API liveness | `https://api.sevillasinluz.es/api/outages/live` | HTTP 200 | 5 min |
| Data freshness | `https://api.sevillasinluz.es/api/health` | HTTP 200 | 5 min |

### Data freshness (`/api/health`)

`/api/outages/live` answers 200 even when the backend stopped refreshing Endesa data
(2026-09-29 incident: 200 with an empty list). Point an UptimeRobot **HTTP(s)** monitor at
`https://api.sevillasinluz.es/api/health`; it only needs to check the HTTP status:

- `200`: `UP` (last successful fetch <= `health.max-fetch-age`, default 20m) or `STARTING`
  (just restarted, within `health.startup-grace`, default 10m).
- `503`: `STALE`, no successful fetch for too long. Treat 503 as an alert.

The body is `{"status": "...", "lastSuccessfulFetch": "<ISO-8601 or null>", "ageSeconds": <n or null>}`
and nothing else. To investigate, look for `Scheduler: failed to fetch` in the backend logs.

Configure alert notifications (email/SMS/Slack — whatever the free tier
offers) so an outage reaches you without you having to check manually.

## SSL certificate expiry

Most uptime monitors above also offer SSL expiry alerts (typically 15-30 days
before expiry) for the same URLs — enable it for both `sevillasinluz.es` and
`api.sevillasinluz.es`.

As a local backup check, verify certbot's auto-renewal actually works instead
of just trusting the timer exists:

```bash
sudo certbot renew --dry-run
```

**Verify:** the command exits 0 and reports
`Congratulations, all simulated renewals succeeded`.

```bash
systemctl list-timers | grep certbot   # confirm the renewal timer is active
```

**Rollback:** none needed — `--dry-run` makes no real changes.

## Checklist

- [ ] External monitor configured for `https://sevillasinluz.es`
- [ ] External monitor configured for `https://api.sevillasinluz.es/api/outages/live`
- [ ] External monitor configured for `https://api.sevillasinluz.es/api/health` (503 = alert)
- [ ] SSL expiry alerts enabled for both hostnames
- [ ] `certbot renew --dry-run` run and passing
