# Multi-page structure (option A)

## Objective
Stop the single page from growing: keep every data view on the home page ("todo de un vistazo") and move long text content to their own lazy-loaded pages.

## Decisions (user, 2026-09-29)
- Home `/`: hero, live, monthly detail (cards, history, filter, downloads), annual chart. All data at a glance.
- `/contexto`: context, demands, press, videos/testimonials.
- `/datos`: methodology, downloads, data dictionary.
- Future `/mapa` and `/guia` are added only when built (no empty placeholder pages).
- Support section at the end of every page; floating back-to-top and heart stay.
- Nothing is removed; old links keep working (`#en-directo`, `?anio=&mes=`, `#metodologia` → `/datos`, `#apoyar` on any page).

## Constraints
- Branch `feat/multi-page` from `main`. No commits until the user tests.
- UI copy Spanish; code English. Checks: `npx ng test --watch=false`, `npx ng build` (initial bundle must not grow; target smaller via lazy routes).
- Strict CSP (script-src 'self'); nginx already falls back to index.html for any route.
- Route: delegated direct.

## Tasks
- [x] P1 Routing with lazy-loaded `contexto` and `datos` pages, anchor scrolling and scroll restoration.
- [x] P2 Sticky accessible top navigation (desktop links, mobile menu), active link state.
- [x] P3 Per-page title, meta description and canonical URL.
- [x] P4 Legacy links: `#metodologia` → `/datos`, in-page links updated to router links; `?anio=&mes=` and `#en-directo` unchanged.
- [x] P5 Support section and floating buttons on every page.

## Progress / evidence
- Route: delegated direct (one writer). Not committed (user tests first).
- Layout: nav, support section, footer and floating buttons live once in app.html; pages only render their own content.
- Fragments: withInMemoryScrolling anchorScrolling; targets are static (support/footer in app shell, methodology renders synchronously), so the router's post-navigation scroll finds them; `html { scroll-padding-top: 4rem }` clears the sticky nav. "Apoyar" re-click on same fragment scrolls manually.
- Legacy: Home redirects `/#metodologia` to `/datos#metodologia` (also on hash change while on home).
- SEO: PageMetaTitleStrategy (route title + data.description, canonical, og:url).
- `npx ng test --watch=false`: 23 files, 100 tests passed.
- `npx ng build`: initial total 418.30 kB (before 422.47 kB); lazy: contexto-page 17.96 kB, datos-page 11.84 kB, chart 168.63 kB.
- Pending: manual browser check (desktop + mobile).
