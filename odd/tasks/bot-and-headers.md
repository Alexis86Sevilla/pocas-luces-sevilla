# Bot robustness + security headers

## Objective
Close the two non-blocking advisories from the Telegram bot review, and move the site's security headers from probation to enforcement.

## Scope
- Bot: (a) avoid a duplicate public message when Telegram accepted a message but persisting announced_at / restoration_announced_at failed; (b) prove with a Postgres test that the outage upsert preserves announcement state (announce_eligible, announced_at, restoration_announced_at, missing_polls).
- Headers (infra/nginx/*.conf + docs/operations/nginx.md): CSP from Report-Only to enforced on the site after checking every page for violations; HSTS max-age from 300 to 31536000 (1 year) on site and API. No `preload` (irreversible, user decision).
- VPS changes are applied by the user; the repo holds the source of truth and the steps.

## Constraints
- Branch feat/bot-and-headers. No commit until the user reviews.
- TDD off; ordinary checks. Backend `./mvnw test` (Postgres/Testcontainers classes need Docker; CI runs them). Frontend untouched.

## Tasks
- [x] T1 Bot: no duplicate on mark failure (route: delegated direct, writer).
- [x] T2 Bot: Postgres upsert preservation test (route: same writer). Compiles; CI pending (no Docker locally).
- [x] T3 CSP violation check on every route (route: inline, headless browser).
- [x] T4 Enforce CSP + HSTS 1y in infra/nginx + docs (route: inline, mechanical).
- [x] T5 User applies on VPS; verify headers in prod.

## Progress / evidence
- T1 (delegated direct writer): OutageAnnouncer retries the mark 3x, then holds sent-but-unmarked ids in memory (excluded from candidates, retried each poll, stops the poll). 4 new unit tests in OutageAnnouncerTest. Documented the restart limitation in the class javadoc and docs/operations/telegram.md. `./mvnw -q test-compile`: ok; `./mvnw -q test` excluding the 3 Testcontainers classes: 232 run, 0 failures, 0 errors (OutageAnnouncerTest 14).
- T2 (same writer): 2 tests added to EnelOutageRepositoryPostgresTest (announcement columns preserved on re-upsert incl. announce_eligible=false; missing_polls reset to 0 on re-sighting, as the upsert intends). Compiles; NOT run locally (no Docker), CI pending.
- T3: headless Chrome crawl of /, /contexto, /datos, /guia, /mapa (scrolled, video embeds opened, 3 iframes loaded) collecting `securitypolicyviolation` events: 0 violations. Detector validated by injecting an inline script and an external image (both reported).
- T4: infra/nginx already had the enforced CSP and HSTS 1y since it was written; only the hand-made VPS snippets (/etc/nginx/snippets/sevillasinluz-headers.conf, sevillasinluz-api-headers.conf) lagged behind (Report-Only, max-age=300). No repo change needed.
- T5: user applied sed on the VPS (backups in /root/*.bak), nginx -t ok, reloaded. Live check 2026-09-30: site, www and API send `Strict-Transport-Security: max-age=31536000` and the site sends enforced `Content-Security-Policy`. Re-crawl with enforcement: 0 violations. `nginx -T | grep -c "max-age=300|Report-Only"` still returned 1 (outside the active sevillasinluz headers; under investigation).
