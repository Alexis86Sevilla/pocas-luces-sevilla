# Monitoring

## External uptime checks

Use a free-tier external monitor (not hosted on the same VPS, so a VPS outage
is actually detected) — e.g. [UptimeRobot](https://uptimerobot.com/) or
[Better Stack](https://betterstack.com/uptime):

| Check | URL | Expected | Interval |
|---|---|---|---|
| Site | `https://sevillasinluz.es` | HTTP 200 | 5 min |
| API liveness | `https://api.sevillasinluz.es/api/outages/live` | HTTP 200 | 5 min |

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
- [ ] SSL expiry alerts enabled for both hostnames
- [ ] `certbot renew --dry-run` run and passing
