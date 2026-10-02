# Web polish (audit 2026-10-02, items 8, 10, 11)

## Objective
Make the site findable and shareable per page, keep the "live" promise honest, and fix accessibility gaps that affect real users.

## Scope
- SEO/sharing (8): `public/robots.txt`, `public/sitemap.xml` (5 routes); per-route og:title/og:description/og:url/twitter:* updated by the title strategy; a real not-found route (noindex, link home) instead of `'**' -> ''`.
- Live/time (10): initial year/month from Europe/Madrid (formatMadridDate), not the browser clock; `monthlyIsCurrentMonth` reacts to a minute tick; live section refreshes /live every 5 min while the tab is visible (and the hero counter's current month), or the "Cada 5 min" text is reworded if refresh isn't done.
- A11y (11): chart `<a role="button">` -> real button or link with keyboard; skip link + focus main heading on route change; h1 on /contexto and /datos (no visual change unless needed); text-gray-400 on light -> 600, text-gray-500 on dark -> 400 where contrast fails; `animate-ping` -> `motion-safe:`.
- Also: remove dead `/api/stats` call in the frontend if unused by the UI.

## Constraints
- Branch feat/web-polish. No commit until the user reviews locally.
- Route: delegated direct (one writer). TDD off. Checks: frontend tests + build; CSP stays clean.
- Copy in Spanish (neutral, single author / impersonal).

## Tasks
- [x] P1 SEO/sharing. (delegated direct, single writer)
- [x] P2 Live refresh + Madrid time. (delegated direct)
- [x] P3 Accessibility. (delegated direct)
- [ ] P4 Local review, review, deploy.

## Progress / evidence

- P1-P3 implemented, uncommitted. `ng test --watch=false`: 34 files, 173 tests passed (155 before). `ng build`: initial total 460.39 kB raw, no warnings; robots.txt and sitemap.xml present in dist.
- text-gray-400 in html: 15 -> 8 (remaining are dark backgrounds, footer/live section, plus one chevron icon).
- Decisions: MadridClock (core/services/madrid-clock.ts) is the shared minute clock; live refresh is silent (no skeleton, errors keep data); hero month count is not refreshed periodically (it follows the clock/month rollover only); not-found flips the existing robots meta to noindex and restores "index, follow"; /api/stats call removed (unused by UI).
