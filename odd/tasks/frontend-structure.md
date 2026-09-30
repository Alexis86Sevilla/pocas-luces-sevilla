# Frontend folder structure

## Objective
Replace the flat `features/` folder (pages, page sections, layout and shared UI mixed at one level, Spanish/English names mixed) with a structure that "screams" the site: pages and their sections, a layout shell and shared UI. Pure refactor: no behavior, markup, style or URL change.

## Target structure (paths relative to `frontend/src/app/`)
| From | To |
|---|---|
| features/nav | layout/nav |
| features/footer | layout/footer |
| features/back-to-top | layout/back-to-top |
| features/donation-section (donation-section.ts/html, DonationSectionComponent) | layout/support-section (support-section.component.ts/html, SupportSectionComponent, selector app-support-section) |
| features/share-button | shared/ui/share-button |
| features/open-data-download | shared/ui/open-data-download |
| features/home (HomeComponent) | pages/home/home-page.component.* (HomePageComponent) |
| features/hero | pages/home/sections/hero |
| features/live | pages/home/sections/live |
| features/monthly-ranking | pages/home/sections/monthly-ranking |
| features/monthly-section (+ date-filter, outage-card) | pages/home/sections/monthly (+ date-filter, outage-card) |
| features/chart-section (+ chart) | pages/home/sections/chart (+ chart) |
| features/contexto-page | pages/context/context-page.component.ts |
| features/context | pages/context/sections/context-section |
| features/testimonials (video-card, video-carousel) | pages/context/sections/testimonials |
| features/datos-page | pages/data/data-page.component.ts |
| features/methodology | pages/data/sections/methodology |
| features/guia-page | pages/guide/guide-page.component.* |
| features/mapa-page (+ leaflet-loader.ts) | pages/map/map-page.component.* (+ leaflet-loader.ts) |

`core/` stays as is. Route paths stay in Spanish (`/contexto`, `/datos`, `/guia`, `/mapa`).

## Constraints
- Branch `refactor/frontend-structure` from `main`. No commits until the user reviews.
- Use `git mv` so file history is preserved. Code identifiers in English; class renames only where listed.
- No template, style or logic changes beyond import paths, class/selector renames listed above and spec describe names.
- Checks: `npx ng test --watch=false` (same test count as before: 132), `npx ng build` (initial total ≈ same as before, 434.59 kB), all routes still resolve.
- Route: delegated direct (mechanical multi-file move).

## Tasks
- [x] S1 Move files per the table with `git mv`; delete the empty `features/` folder.
- [x] S2 Update every import, `loadComponent` path, class/selector rename and spec.
- [x] S3 Update `frontend/README.md` project structure section.
- [x] S4 Verify tests, build and routes.

## Progress / evidence
- Baseline: 132 tests passed (29 files); build initial total 434.59 kB.
- After: 132 tests passed (29 files); build initial total 434.58 kB; lazy chunks renamed (map/guide/context/data-page-component), sizes equal.
- `grep -rn "features/" frontend/src`: no matches; `features/` removed. git status: 39 R + 28 RM (renames preserved), 7 M.
- Route: delegated direct (one writer). Not committed, pending user review.
- Parent verification: two full test runs failed with every vitest fork timing out ("Failed to start forks worker ... Timeout waiting for worker to respond", 0 tests, 29 errors) while the map spec passed alone. `main` passed 132/132 under the same conditions, so the stale `.angular/cache` (old `features/` paths) was the cause: after `rm -rf frontend/.angular/cache` two consecutive runs passed 132/132. CI builds from a clean checkout, so it is not affected.
