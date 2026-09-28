# Operations runbooks

Infrastructure hardening for Sevilla Sin Luz: VPS, nginx, PostgreSQL, CI
supply chain and monitoring. The real nginx config, VPS SSH access and the
GitHub rename are **not** things this repository can apply for you — every
file here is a drop-in snippet or reference to diff against your real setup,
with copy-pasteable commands, a verification step, and a rollback note.

## Checklist (recommended order)

- [ ] **T1 — Branch rename** ([`branch-rename.md`](branch-rename.md)): merge
  feature branches into `master`, rename to `main` on GitHub, update local
  clones. Do this **first** — dependabot.yml and deploy.yml already target
  `main`, so PRs/deploys against the old default branch may behave
  unexpectedly until the rename lands.
- [ ] **T2 — Dependabot**: `.github/dependabot.yml` (Maven `/backend`, npm
  `/frontend` — Dependabot's npm ecosystem also reads `pnpm-lock.yaml` —
  GitHub Actions `/`), weekly, Europe/Madrid, grouped minor+patch updates,
  `target-branch: main`. Nothing to install; GitHub picks it up once merged
  to the (renamed) default branch.
- [ ] **T3 — nginx** ([`nginx.md`](nginx.md)): rate limiting, security headers,
  CSP (built from an actual inspection of the frontend, not guessed), API
  headers. Files in `infra/nginx/`.
- [ ] **T4 — VPS hardening** ([`vps-hardening.md`](vps-hardening.md)): ufw,
  SSH hardening, scoped sudo for CI, fail2ban, unattended-upgrades. Contains
  explicit lock-out warnings — read before running.
- [ ] **T5 — PostgreSQL** ([`postgres.md`](postgres.md)): least-privilege
  role, automated daily backups + retention, restore drill. Files in
  `infra/postgres/`.
- [ ] **T6 — Monitoring** ([`monitoring.md`](monitoring.md)): external uptime
  checks, SSL expiry alerts, `certbot renew --dry-run`.

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
