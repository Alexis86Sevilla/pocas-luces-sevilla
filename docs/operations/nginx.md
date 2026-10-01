# nginx hardening

Files in `infra/nginx/` (drop-in snippets + a reference config, **not** a copy
of your real nginx config, which is not in this repository):

| File | Where it goes | Context |
|---|---|---|
| `rate-limit.conf` | `/etc/nginx/conf.d/rate-limit.conf` | `http {}` |
| `security-headers.conf` | `/etc/nginx/snippets/security-headers.conf`, `include`d from the `sevillasinluz.es`/`www` server block | `server {}` |
| `security-headers-api.conf` | `/etc/nginx/snippets/security-headers-api.conf`, `include`d from the `api.sevillasinluz.es` server block | `server {}` |
| `sevillasinluz.conf.example` | reference only — diff against your real config | — |

## Install

```bash
sudo cp infra/nginx/rate-limit.conf /etc/nginx/conf.d/rate-limit.conf
sudo mkdir -p /etc/nginx/snippets
sudo cp infra/nginx/security-headers.conf /etc/nginx/snippets/security-headers.conf
sudo cp infra/nginx/security-headers-api.conf /etc/nginx/snippets/security-headers-api.conf
```

Then, in your real server blocks:

- Site block (`sevillasinluz.es`/`www`): add `include snippets/security-headers.conf;`
  - **Also inside every `location` that has its own `add_header`** (for example `Cache-Control`). nginx does not inherit server-level `add_header` into a location that defines any `add_header`, so without this the security headers silently disappear. Verify with `curl -sI https://sevillasinluz.es/ | grep -i content-security` and on a hashed asset (`curl -sI https://sevillasinluz.es/main-XXXX.js`).
- API block (`api.sevillasinluz.es`): add `include snippets/security-headers-api.conf;`
- API `location` blocks that proxy to the backend: add `limit_req zone=api_general burst=20 nodelay;` (general) or `limit_req zone=api_admin burst=2 nodelay;` (for `/api/outages/fetch` and `/api/outages/export*`) — see `sevillasinluz.conf.example` for full context.

```bash
sudo nginx -t && sudo systemctl reload nginx
```

**Verify:**

```bash
curl -sI https://sevillasinluz.es/ | grep -iE 'strict-transport|x-frame|content-security-policy'
curl -sI https://api.sevillasinluz.es/api/outages/live | grep -iE 'strict-transport|content-security-policy'
for i in $(seq 1 30); do curl -s -o /dev/null -w "%{http_code}\n" https://api.sevillasinluz.es/api/outages/live; done | sort | uniq -c
```

**Rollback:** remove the `include` lines and the copied files, `nginx -t && systemctl reload nginx`.

## CSP: how it was built

Derived by inspecting `frontend/src` on 2026-09-28 (index.html, all feature
components, `environment.prod.ts`, `angular.json`), not guessed:

| Directive | Value | Why |
|---|---|---|
| `default-src` | `'self'` | baseline |
| `connect-src` | `'self' https://api.sevillasinluz.es` | `environment.prod.ts` → `apiBaseUrl: 'https://api.sevillasinluz.es/api'` |
| `frame-src` | `https://www.youtube.com https://www.youtube-nocookie.com https://www.instagram.com` | `testimonials/video-card` renders a YouTube `<iframe>` for `platform: 'youtube'`. Instagram testimonials currently render a plain outbound `<a>` link (no iframe embed exists today) — Instagram is allow-listed anyway so a future iframe embed doesn't silently break |
| `img-src` | `'self' data: https://tile.openstreetmap.org` | local assets (`favicon.svg`, `og-sevilla-sin-luz.jpg`, inline SVG icons) plus the OpenStreetMap raster tiles the `/mapa` page loads. No other external image host |
| `style-src` | `'self' 'unsafe-inline'` | **required**, see below |
| `script-src` | `'self'` | see below — kept strict |
| `object-src` | `'none'` | no plugins used |
| `base-uri` | `'self'` | |
| `frame-ancestors` | `'none'` | site is never meant to be framed |
| `form-action` | `'self'` | |
| `upgrade-insecure-requests` | | |

No `font-src` entry was added: no Google Fonts/Adobe Fonts `<link>` exists in
`index.html`, only the system font stack in `styles.css` via Tailwind.

### `style-src 'unsafe-inline'` — required, documented trade-off

`hero.component.html` uses static `style="..."` attributes, and Angular's
runtime inserts per-component `<style>` tags into `<head>` for view-encapsulated
component styles via DOM APIs — CSP `style-src` governs both regardless of how
they reach the DOM. Angular does support a nonce-based CSP alternative
(`ngCspNonce` + `APP_ID`), but that requires a fresh, unpredictable nonce
generated **per HTTP response** by a server-side template — this is a static
SPA served by nginx from `/var/www/sevillasinluz` with no per-request
templating layer, so a nonce cannot be generated here without adding one
(e.g. moving to SSR, which is out of scope). `'unsafe-inline'` for styles is
the accepted trade-off: it cannot execute JavaScript, only apply CSS, which is
a materially smaller attack surface than script injection.

### `script-src 'self'` — kept strict, one build change made

Angular's default production build (`@angular/build:application`) inlines an
async-CSS-loading pattern when critical-CSS inlining is enabled:
`<link rel="stylesheet" media="print" onload="this.media='all'">`. The
`onload="..."` attribute is an inline script handler and is governed by
`script-src`, not `style-src` — it would have forced `'unsafe-inline'` (or a
fragile `'unsafe-hashes'` hash pin) onto `script-src`, defeating most of CSP's
XSS protection.

**Fix applied:** `frontend/angular.json` now sets
`configurations.production.optimization.styles.inlineCritical: false`,
disabling critical-CSS inlining. Verified by rebuilding
(`pnpm build`) and confirming `dist/frontend/browser/index.html` contains no
`onload=`, no inline `<style>` block, and exactly one external
`<script src="main-*.js" type="module">`. `npx ng test --watch=false` still
passes (5/5). The trade-off is losing the (minor) first-paint benefit of
inlined critical CSS; the stylesheet is 32 KB and loads via a normal
render-blocking `<link rel="stylesheet">` instead.

### Rollout: Report-Only first

Both `security-headers.conf` and `security-headers-api.conf` ship the
enforcing `Content-Security-Policy` header by default. A commented
`Content-Security-Policy-Report-Only` alternative with the identical policy is
included in `security-headers.conf` — swap the active line, deploy, and watch
the browser console (or wire a `report-uri`/`report-to` endpoint if you want
aggregated reports) for a few days of real traffic before switching back to
the enforcing header.

## API headers: no CORS here

`security-headers-api.conf` intentionally does **not** set any
`Access-Control-*` header. CORS is handled in Spring
(`backend/src/main/java/com/pocasluces/backend/config/CorsConfig.java`).
Setting CORS headers in both nginx and Spring produces duplicate/conflicting
headers that browsers reject outright — don't add them in nginx.

## Optional backend change (documented only, not applied)

`server.forward-headers-strategy: native` in the `prod` profile
(`backend/src/main/resources/application.yaml`) would make Spring Boot trust
and use the `X-Forwarded-*` headers nginx sends (already configured in
`sevillasinluz.conf.example`'s `proxy_set_header` lines) for building
correct absolute URLs/client IPs/scheme in the app. **Not applied** here per
the task constraint against editing `application.yaml`.

Binding `server.address: 127.0.0.1` would stop the backend from listening on
all interfaces on port 8081 (defense in depth alongside the ufw rule in
`docs/operations/vps-hardening.md`). **Risk:** if nginx and the backend are
ever split across hosts, or the backend is accessed via a Docker bridge/
different loopback, this silently breaks connectivity — only apply this if
nginx and the JAR always run on the exact same host (which is the case today:
one Ubuntu VPS with a systemd unit and reverse-proxying nginx).
