# Operations runbooks

Infrastructure hardening for Sevilla Sin Luz: VPS, nginx, PostgreSQL, CI
supply chain and monitoring. The real nginx config, VPS SSH access and the
GitHub rename are **not** things this repository can apply for you — every
file here is a drop-in snippet or reference to diff against your real setup,
with copy-pasteable commands, a verification step, and a rollback note.

## Checklist (recommended order)

- [x] **T1 — Branch rename** ([`branch-rename.md`](branch-rename.md)): done;
  `main` is the default branch and dependabot.yml / deploy.yml target it.
- [ ] **T2 — Dependabot**: `.github/dependabot.yml` (Maven `/backend`, npm
  `/frontend` — Dependabot's npm ecosystem also reads `pnpm-lock.yaml` —
  GitHub Actions `/`), weekly, Europe/Madrid, grouped minor+patch updates,
  `target-branch: main`. Nothing to install; GitHub picks it up once merged
  to the (renamed) default branch.
- [x] **T3 — nginx** ([`nginx.md`](nginx.md)): rate limiting, security headers,
  CSP (built from an actual inspection of the frontend, not guessed), API
  headers. Files in `infra/nginx/`. Applied 2026-09-29; CSP now enforced and HSTS
  max-age 1 year. gzip and cache rules via
  `infra/nginx/sevillasinluz-performance.conf` applied 2026-10-02.
- [x] **T4 — VPS hardening** ([`vps-hardening.md`](vps-hardening.md)): ufw,
  SSH hardening, scoped sudo for CI, fail2ban, unattended-upgrades. Contains
  explicit lock-out warnings — read before running. ufw and SSH (keys only)
  applied 2026-09-29; fail2ban (`/etc/fail2ban/jail.d/sevillasinluz.local`) and unattended-upgrades active the same day.
- **VPS access** ([`vps-access.md`](vps-access.md)): current SSH policy,
  emergency IONOS console, adding or removing a computer.
- **Timezone audit** ([`timezone-audit.md`](timezone-audit.md)): read-only SQL to
  detect datetime rows shifted by the 2026-09-29 timezone bug, and the manual
  correction script to run only if it finds any.
- [ ] **T5 — PostgreSQL** ([`postgres.md`](postgres.md)): least-privilege
  role, automated daily backups + retention, restore drill. Files in
  `infra/postgres/`. Backups not applied (disk space); manual off-box
  `pg_dump` taken 2026-10-02 before V8.
- [x] **T6 — Monitoring** ([`monitoring.md`](monitoring.md)): UptimeRobot on
  `/api/health` plus the cert-expiry workflow.
- [x] **Telegram alerts** ([`telegram.md`](telegram.md)): set `TELEGRAM_BOT_TOKEN`
  and `TELEGRAM_CHAT_ID` in a `600` systemd drop-in, verify the startup log line,
  disable, rotate the token via @BotFather. Off until both variables exist.

## Deploy

`.github/workflows/deploy.yml` runs on every push to `main`; deploys never
overlap (`concurrency: deploy-production`).

1. **Backend**: build and test, upload the JAR as `backend-new.jar`, keep the
   running one as `backend-previous.jar`, swap, restart `sevillasinluz` and poll
   `http://127.0.0.1:8081/api/health` for up to 180 s. Then the runner checks
   `https://api.sevillasinluz.es/api/health`.
2. **Frontend** (only if the backend job succeeded): unit tests, build, upload
   to `/var/www/sevillasinluz-staging/`, copy hashed assets first and
   `boot.js` / `index.html` last, remove staging, check the live `main-*.js`
   returns 200. Old hashed chunks are kept on purpose.

**Rollback**: if the backend is not healthy in time, the workflow restores
`backend-previous.jar`, restarts, prints the last 80 journal lines and fails.
A Flyway migration already applied by the failed version is **not** rolled back.

Manual rollback on the VPS:

```
cp /opt/sevillasinluz/backend-previous.jar /opt/sevillasinluz/backend-0.0.1-SNAPSHOT.jar && systemctl restart sevillasinluz
```

## Files added by this change

```
.github/dependabot.yml
infra/nginx/rate-limit.conf
infra/nginx/security-headers.conf
infra/nginx/security-headers-api.conf
infra/nginx/sevillasinluz.conf.example
infra/postgres/app-role.sql
infra/postgres/backup-postgres.sh
infra/postgres/sevillasinluz-backup.service
infra/postgres/sevillasinluz-backup.timer
infra/fail2ban/jail.local
docs/operations/README.md          (this file)
docs/operations/branch-rename.md
docs/operations/nginx.md
docs/operations/vps-hardening.md
docs/operations/postgres.md
docs/operations/monitoring.md
```

## Key decisions at a glance

- **CSP `script-src 'self'` kept strict**: Angular's default critical-CSS
  inlining injected an inline `onload="..."` handler; disabled via
  `frontend/angular.json`'s `optimization.styles.inlineCritical: false`
  instead of weakening CSP. Details in `nginx.md`.
- **CSP `style-src` needs `'unsafe-inline'`**: static `style="..."` attributes
  and Angular's runtime-injected per-component `<style>` tags require it; a
  nonce-based approach isn't practical for a static SPA with no per-request
  templating. Details in `nginx.md`.
- **PostgreSQL role**: kept the app as owner of its own database (Flyway needs
  DDL at startup) rather than a two-role split, to avoid requiring a new
  secret on the VPS this agent cannot provision remotely. The stricter split
  is documented and ready to apply later. Details in `postgres.md`.
- **No remote/VPS/GitHub write operations were performed.** Every step above
  is something the user runs by hand, with verification and rollback.
