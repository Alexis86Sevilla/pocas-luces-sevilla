# Monthly ranking and outage map

## Objective
Give an at-a-glance, honest comparison of districts (ranking on Home) and a visual map of outages (new /mapa page), without growing the home bundle.

## Decisions
- Ranking metric: "horas de corte acumuladas" = sum of OBSERVED durations (resolvedAt − interruptionDate) of resolved outages per district in the selected month. Overlapping outages add up, so it is not "hours the whole district was without power" — the UI must say "acumuladas". Ongoing outages are excluded from hours. Also show outage count and supply points. Computed client-side from the monthly data already loaded.
- Map: Leaflet + OpenStreetMap standard tiles (https://tile.openstreetmap.org, attribution required), lazy route `/mapa`. District boundaries from `backend/src/main/resources/geojson/distritos-sevilla.json` (298 KB) simplified into a static asset. Markers are circle markers (no image assets). Popups: approximate neighborhood, start time, supply points, category/cause. Text notes: position is the distributor's published point; same data available in the lists and CSV.
- CSP: add `https://tile.openstreetmap.org` to `img-src` in `infra/nginx/security-headers.conf` (production CSP is still Report-Only; the user updates the VPS snippet).

## Constraints
- Branch `feat/ranking-map` from `main`. No commits until the user tests.
- Datetimes are Europe/Madrid wall-clock (use `core/utils/madrid-date.ts`, `core/utils/outage-duration.ts`).
- UI copy Spanish, impersonal; code English. Checks: `npx ng test --watch=false`, `npx ng build` (initial total must not grow meaningfully; Leaflet only in the lazy chunk).
- Route: delegated direct.

## Tasks
- [x] R1 Ranking component on Home (top 5, expandable to all), with honest labels and a link to the methodology.
- [x] R2 Methodology/data dictionary line explaining the ranking metric.
- [x] M1 `/mapa` page with Leaflet, district polygons, live and monthly markers, legend, month filter reuse, attribution.
- [x] M2 Nav link "Mapa", SEO title/description, simplified GeoJSON asset, CSP doc update.
- [x] T1 Specs.

## Progress / evidence
Route: delegated direct (one writer). No commits yet (user tests first).
- Deps: leaflet 1.9.4, @types/leaflet 1.9.22 (pnpm). angular.json: allowedCommonJsDependencies leaflet; anyComponentStyle budget 16kB warn / 24kB error (leaflet.css inlined in the lazy mapa component style).
- GeoJSON: 298,397 B -> 11,218 B (5 decimals, Douglas-Peucker 0.00015 deg), `frontend/public/geo/distritos-sevilla.json`, 11 districts, `name` kept.
- Nav: "Mapa" after "Qué hacer" (desktop + mobile).
- Popups built with DOM/textContent; tooltips likewise.
- CSP: img-src += https://tile.openstreetmap.org (security-headers.conf, docs/operations/nginx.md). VPS snippet must be updated by the user.
- Verification: `npx ng test --watch=false`: 29 files, 132 tests passed. `npx ng build`: initial total 434.59 kB (baseline 425.90; +8.7 kB from ranking on Home); lazy: leaflet-src 149.55 kB, mapa-page-component 20.74 kB (incl. leaflet CSS); leaflet only in those two lazy chunks (grep on dist).
- Next: user checks /mapa and the Home ranking in the browser, then commits.

### Parent correction (user feedback 2026-09-29)
- The user saw Distrito Norte overlapping Macarena on the map. Measured with shapely: the ORIGINAL backend GeoJSON has no overlaps (0.000 %), so backend district assignment is correct; the per-polygon Douglas–Peucker simplification introduced overlaps up to 0.18 % (Macarena×Triana, Macarena×Norte 0.13 %). Topology-preserving simplification (topojson) still left overlaps because shared borders in the source do not share identical vertices.
- Final asset: coordinates rounded to 5 decimals (~1 m), no simplification: 63,857 B raw / 14,786 B gzip, worst overlap 0.00000 %.
