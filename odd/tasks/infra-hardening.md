# Infra hardening and main branch rename

## Objective
Rename the default branch to `main` and harden the production infrastructure (VPS, nginx, PostgreSQL, CI supply chain) with versioned configuration and runbooks.

## Constraints
- Branch: `chore/infra-hardening` (based on `fix/security-hardening`).
- NO commits until the user reviews/tests (user preference from the previous feature).
- No remote operations by the agent: GitHub rename, push, and every VPS change are executed by the user from the documented commands.
- Existing nginx config is not in the repo: deliver drop-in snippets and a reference server config, not a blind replacement.
- TDD: not configured. Checks: `nginx -t` on the VPS (user), `shellcheck` if available, YAML validity, `./mvnw test` unaffected.
- Route: delegated direct (writer trigger: 2+ non-trivial files).

## Tasks
- [x] T1 Branch rename: `deploy.yml` triggers on `main`; README deploy text; runbook with GitHub + local rename steps.
- [x] T2 Dependabot: `.github/dependabot.yml` for Maven (`/backend`), npm/pnpm (`/frontend`), GitHub Actions (`/`), weekly, grouped.
- [x] T3 nginx: rate limiting for `/api/` (stricter for admin endpoints), security headers (HSTS, CSP tuned to YouTube/Instagram embeds and the API origin, X-Frame-Options, X-Content-Type-Options, Referrer-Policy, Permissions-Policy), proxy only to 127.0.0.1:8081, hide server tokens.
- [x] T4 VPS hardening runbook: ufw (22/80/443 only, 8081 and 5432 closed), SSH (keys only, no root, no passwords), fail2ban (sshd + nginx-limit-req), unattended-upgrades.
- [x] T5 PostgreSQL: least-privilege application role SQL; automated daily `pg_dump` backup script with rotation + systemd timer; restore instructions.
- [x] T6 Uptime monitoring guidance (external HTTP check on site and API).

## Acceptance criteria
- Every VPS step is copy-pasteable, idempotent where possible, and has a verification command and a rollback note. Met — see `docs/operations/*.md`.
- CSP does not break the site: YouTube/Instagram iframes, API calls to `https://api.sevillasinluz.es`, inline styles used by Angular. Met — CSP built from actual inspection of `frontend/src`; see `docs/operations/nginx.md`.

## Progress / evidence

- T1: `.github/workflows/deploy.yml` trigger changed to `[main]`; `README.md` deploy text updated; `docs/operations/branch-rename.md` written with the exact merge → GitHub rename → local rename → push order, and the `origin/feature/district-grouping` note.
- T2: `.github/dependabot.yml` created (maven `/backend`, npm `/frontend`, github-actions `/`; weekly Monday 06:00 Europe/Madrid; grouped minor+patch per ecosystem; `target-branch: main`). No `pyyaml`/`js-yaml` available locally to run an automated parse — validated by careful manual review (structure matches GitHub's documented schema, no tabs, consistent 2-space indentation).
- T3: `infra/nginx/{rate-limit.conf,security-headers.conf,security-headers-api.conf,sevillasinluz.conf.example}` + `docs/operations/nginx.md`. CSP built from grepping `frontend/src` for `iframe`/`youtube`/`instagram`/external URLs and reading `environment.prod.ts`/`angular.json`. Found Angular's critical-CSS inlining injects an inline `onload="..."` handler; fixed by setting `frontend/angular.json` → `configurations.production.optimization.styles.inlineCritical: false`. Verified: `pnpm build` then `grep` on `dist/frontend/browser/index.html` shows no `onload=`/inline `<style>`, only one external `<script type="module">`; `npx ng test --watch=false` → 2 files, 5/5 tests passed.
- T4: `docs/operations/vps-hardening.md` (ufw, SSH drop-in, scoped sudoers for the CI deploy user, fail2ban, unattended-upgrades, nginx install) + `infra/fail2ban/jail.local`.
- T5: `infra/postgres/{app-role.sql,backup-postgres.sh,sevillasinluz-backup.service,sevillasinluz-backup.timer}` + `docs/operations/postgres.md`. Decision: kept the app as owner of its own DB (Flyway needs DDL at startup) rather than a two-role split, since the split needs a new secret provisioned on the VPS that this agent cannot create remotely; the split is documented and commented out in `app-role.sql` for later. `bash -n infra/postgres/backup-postgres.sh` passed; `shellcheck` not installed locally.
- T6: `docs/operations/monitoring.md` (UptimeRobot/Better Stack checks on site + `/api/outages/live`, SSL expiry alerts, `certbot renew --dry-run`).
- Index: `docs/operations/README.md` added and linked from root `README.md`'s new "Operations" section.
- Parent review fix: site `location` blocks in `sevillasinluz.conf.example` set their own `add_header Cache-Control`, which drops inherited server-level headers in nginx; each location now re-includes `security-headers.conf`, and `nginx.md` documents the pitfall with a curl check.
- Trade-off: `inlineCritical: false` makes the global CSS render-blocking (small LCP cost) to keep `script-src 'self'` strict.
- No commits made, no git/GitHub/VPS remote operations performed, per constraints.

## Next step
User review: read `docs/operations/README.md`, then work through T1–T6 in order on the actual VPS/GitHub, each with its own verification and rollback.

## Production rollout log
- 2026-09-29 nginx (T3) applied by the user on the VPS, guided step by step. The VPS nginx also serves another site (urban-oasis), so zone names are unique (`sevillasinluz_api`, `sevillasinluz_admin`) and `limit_req_status` lives in our locations. Files: `conf.d/sevillasinluz-limits.conf`, `snippets/sevillasinluz-{headers,api-headers,proxy}.conf`, rewritten `sites-available/sevillasinluz` (hash-verified before applying; backup `/root/nginx-backup-20260929`).
- Verified externally: all three sites 200; site headers HSTS (max-age=300), nosniff, X-Frame-Options DENY, Referrer-Policy, Permissions-Policy, CSP Report-Only; API headers HSTS + CSP `default-src 'none'`; `server_tokens off`; CORS intact; 60 parallel requests -> 26x200 / 34x429; admin endpoints 429 on the 4th request.
- Pending: enforce CSP after checking browser console for violations; raise HSTS to 1 year after a few days; T4 (ufw, SSH without root/passwords, fail2ban, unattended-upgrades), T5 backups, T6 monitoring.
- 2026-09-29 ufw enabled (deny incoming; allow 22, 80, 443); verified externally 8080/8081/5432 closed, all sites 200. SSH hardened via `/etc/ssh/sshd_config.d/00-hardening.conf` (named 00 because the image ships `50-cloud-init.conf` enabling passwords): PermitRootLogin prohibit-password, PasswordAuthentication no, MaxAuthTries 3. User previously logged in by password; created ed25519 key on their PC first and verified IONOS KVM console as emergency access. Tests: key login OK, password login -> Permission denied (publickey). Runbook `docs/operations/vps-access.md`.
- 2026-09-29 unattended-upgrades already enabled by Ubuntu (20auto-upgrades: Update-Package-Lists 1, Unattended-Upgrade 1). fail2ban installed with `/etc/fail2ban/jail.d/sevillasinluz.local` (sshd: 3 in 10m -> 1h; nginx-limit-req: 20 in 10m -> 10m, http/https only, covers both sites). No IP whitelist (user IP is dynamic IPv6; key auth does not produce failures). Unban: `fail2ban-client unban --all` via IONOS console. Pending: T5 backups, T6 monitoring, CSP enforce, HSTS 1y, optional reboot for deferred service restarts.
