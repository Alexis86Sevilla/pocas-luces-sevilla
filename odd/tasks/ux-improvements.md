# UX/UI improvements

## Objective
Make the dashboard honest about its data state (errors, loading, freshness), faster on mobile, and more accessible, based on the 2026-09-28 UX review.

## Constraints
- Branch `feat/ux-improvements` from `main`. NO commits until the user tests (user preference).
- Delivery strategy: `exception-ok` — single branch merged locally, as in previous features (personal project, direct deploys from `main`).
- UI copy in Spanish (existing product language); code, comments, tests in English.
- TDD: not configured. Checks: `pnpm build`, `npx ng test --watch=false` (in `frontend/`).
- Route: delegated direct (writer trigger: 2+ non-trivial files).

## Tasks
### High
- [x] U1 Error states: per-resource error signals in `ApiOutageService`; each section shows a distinct error message with a retry button instead of "no outages".
- [x] U2 Loading states: skeletons for live and monthly sections bound to real request state.
- [x] U3 Images: hero image to modern format at display size with preload; og-image compressed (keep a social-compatible format).
- [x] U4 Data freshness: "Actualizado hace X min" from `max(fetchedAt)` in the live section.
- [x] U5 Chart accessibility: accessible data table/summary, fix low-contrast palette color, `aria-pressed` on district toggles, remove double re-render on toggle.
### Medium
- [x] U6 Locale number formatting (es-ES) for affected clients and totals.
- [x] U7 Explain "Avería" vs "Programado" (legend/tooltip).
- [x] U8 Year selector lists every year with data (from 2026 to current), not just the current year.
- [x] U9 Month/year filter synced with URL query params (shareable links).
- [x] U10 Back-to-top button on long scroll.
### Low
- [x] U11 JSON-LD structured data (Dataset) in `index.html`.
### Added mid-task (orchestrator correction, verified against production)
- [x] U12 `madrid-date.ts` bug: `parseMadridDate` treated offset-less backend strings as UTC, but the backend actually stores Europe/Madrid **local wall-clock** time. Fixed to convert Madrid wall-clock → correct UTC instant (DST-aware), and made the live section pass `Date` objects through the fixed util instead of raw strings to the `date` pipe (browser-timezone-independent).

- [x] U13 (data accuracy follow-up, parent inline) Frontend dedup key mirrors the new backend identity (coordinates + start + type, falling back to neighborhood when coordinates are missing); "Avería/Programado" uses Endesa's `cause` field with the LV heuristic as fallback (`core/utils/outage-category.ts`). Tests: 46/46, build 390.43 kB.

### Added: visual design polish pass (2026-09-28, delegated direct writer)
- [x] V1 Pluralization: new `core/utils/pluralize.ts` helper (`pluralize(count, singular, plural?)`, plural on zero), used consistently instead of the buggy `> 1 ? 's' : ''` pattern (which wrongly showed singular for 0). Applied in outage-card stat tiles ("corte/cortes", "afectado/afectados"), outage-card daily group rows, and live-section active-outage counts ("corte(s) activo(s)").
- [x] V2 Share: new reusable `features/share-button/` component. `navigator.share({title, text, url: location.href})` when available; else `navigator.clipboard.writeText` with a transient "Enlace copiado" in an `aria-live="polite"` region (4s); if clipboard also fails, shows the URL in a selectable readonly input. `variant` input (`light`/`dark`) themes it for light sections vs the dark footer. Placed in the monthly section header (next to the date filter), under the chart section intro, and in the footer. Text: "Cortes de luz en Sevilla: datos abiertos de Endesa por distrito". Spec mocks `navigator.share`/`clipboard`.
- [x] V3 Outage card hierarchy: replaced the 2-tile (duración/cortes) + separate gray "afectados en total" row with 3 equal-weight tiles (afectados, cortes, min de media) in `grid-cols-3` at all breakpoints, using smaller text/padding below `sm` ("3 compact tiles" option — cards already go full-width at 1 column below `sm`, so this was simpler than restructuring layout for that breakpoint only). Redundant gray row removed.
- [x] V4 Grid empty space: monthly cards grid (and its skeleton) changed from fixed `sm:grid-cols-2 lg:grid-cols-4` to `grid-cols-[repeat(auto-fit,minmax(240px,280px))] justify-center` — caps each card at 280px and centers the row, so 1/3 districts don't stretch into oversized cards or leave a lopsided gap, while 4/8 districts still wrap normally.
- [x] V5 Date filter alignment: the static single-year `<span>` now matches the year `<select>` exactly (`rounded-lg border border-transparent bg-gray-100 px-3 py-2 text-sm font-semibold text-gray-600`), same height/padding/radius, aligned via the parent's `flex items-center`.
- [x] V6 Color semantics: red reserved for errors and the live "urgent" badge only. Outage-card tiles recolored to a coherent non-red 3-tone set — afectados = indigo (bg-indigo-50/text-indigo-700, contrast ~7:1), cortes = slate (bg-slate-100/text-slate-700, neutral — it's just a count), duración = amber (unchanged). All pass ≥4.5:1 contrast (verified indigo-700-on-indigo-50 ≈7:1; slate/amber follow the same 700-on-50/100 pattern already used elsewhere).
- [x] V7 Small gray text: bumped informative `text-xs text-gray-400` on light backgrounds to `text-gray-600` (donation-section footnote, monthly-section legend + its `<strong>` labels to `text-gray-700`). Bumped legend sizing to `text-sm sm:text-xs` on mobile in monthly-section (~44-47) and live-section (~23-26); live-section legend color left as-is (already `text-gray-500`/`text-gray-400` on a dark background, which passes contrast there). Hero untouched.

**Decisions/defaults chosen (no questions asked, per instructions):**
- Pluralization: a tiny pure helper (`pluralize`) over `I18nPluralPipe`, called from components the same way `outageCategory` already is (`protected readonly pluralize = pluralize;`) — consistent with existing codebase style, no new pipe registration needed.
- Outage-card mobile layout: kept `grid-cols-3` uniformly (compact sizing below `sm`) rather than a full-width-affected + 2-below mobile layout, since mobile cards are single-column and full-width already.
- Share text is identical across all 3 placements (not customized per-section) per the task's example copy.
- Contrast for the new indigo/slate tiles verified via manual WCAG luminance calculation (not just eyeballing), following the existing "700 text on 50 bg" pattern already used for amber.

**Verification:**
- `npx ng test --watch=false`: 56/56 tests passed (16 test files, all green; includes 3 new spec files: `pluralize.spec.ts`, `share-button.component.spec.ts`, `outage-card.component.spec.ts`).
- `npx ng build`: succeeds, no budget warnings. Initial bundle 397.75 kB raw / 103.89 kB transfer (up from 390.43 kB, expected from the new share-button component used in 3 places + pluralize helper).
- `git status --short` (repo root): only frontend files touched by this pass plus the pre-existing uncommitted U1-U13 work; `.atl/`, `backend/`, and the worktree were not touched.

**What to look at in the browser:**
- Monthly section: outage cards now show 3 tiles (afectados/cortes/min de media) in indigo/slate/amber, no more gray "afectados en total" row; a "Compartir" button sits next to the month/year filter.
- Try 1 district vs 4+ districts in the monthly grid — cards should stay a reasonable width and center instead of stretching or leaving empty space.
- Date filter: when only one year exists, the "2026" label box should look like a twin of the month dropdown (same height/border/padding).
- Chart section: a "Compartir" button appears centered under the intro text.
- Footer: a dark-themed "Compartir" button under the tagline.
- Click "Compartir" on desktop Chrome (no native share sheet) — should copy the URL and briefly show "Enlace copiado".
- Live section and outage-card history: "1 corte activo" vs "2 cortes activos" vs "0 cortes" (zero case) render correctly.

## Acceptance criteria
- API down ⇒ user sees an error with retry, never "no hay cortes".
- `pnpm build` and tests pass; new behavior covered by focused specs.

## Progress / evidence

All 12 tasks implemented and verified. See the writer's final report (delivered via SubagentHandback) for full file list, decisions, and verification output. Summary:

- `pnpm build`: succeeds, no budget warnings (initial 389.51 kB / 500 kB warning threshold).
- `npx ng test --watch=false`: see final verification run for the updated total (added `video-carousel.component.spec.ts` after this note was first written). New specs: `api-outage.service.spec.ts`, `madrid-date.spec.ts`, `relative-time.spec.ts`, `live-section.component.spec.ts`, `monthly-section.component.spec.ts`, `date-filter.component.spec.ts`, `chart.component.spec.ts`, `home.component.spec.ts`, `back-to-top.component.spec.ts`, `video-carousel.component.spec.ts`.
- U1 testimonials decision: chose to show a short error message + "Reintentar" button (consistent with the other sections) rather than silently hiding the section, so the user is never shown a false "no testimonials" state on API failure.
- `dist/frontend/browser/index.html`: only `<script type="application/ld+json">` and the module bootstrap `<script src="main-*.js" type="module">`; no inline event handlers.
- Images: `alcalde-micro.png` (1.9 MB, 1960×1308) → `alcalde-micro.webp` (81.7 KB, 1600w, q78); `og-image.png` (1.2 MB) → `og-image.jpg` (63.3 KB, 1200×630). Both original PNGs deleted (replaced references entirely; no `<picture>` fallback — WebP support is universal by 2026, decision reported to user).
- U5: replaced `#808080` with `#1f2937`; removed the double `updateChart()` call (effect is now the single re-render path); added `aria-pressed`, `role="img"` + `aria-label` on canvas, and a `<details>` "Ver datos en tabla" accessible table (chosen over a permanently visible table, for transparency without adding visual clutter).
- U9 param names: `anio`/`mes` (Spanish, per task instructions' preference), read via `ActivatedRoute.snapshot.queryParamMap` on init (validated against `[FIRST_DATA_YEAR, currentYear]` and `[1,12]`), written via `Location.replaceState` (no history entry, no navigation/scroll side effects).
- U12 (mid-task correction): `core/utils/madrid-date.ts` rewritten with a DST-aware wall-clock→UTC conversion (two-pass offset resolution via `Intl.DateTimeFormat`); doc comments corrected; `LiveGroup.earliestDateStr`/`latestDateStr` (raw strings fed to the `date` pipe) replaced with `earliestDate`/`latestDate` (`Date`, parsed via the fixed util) in `home.component.ts` / `live-section.component.ts`/`.html`. Backend `OutageExportDto.toMadridWallClock` has the same bug but is explicitly out of scope (backend/ excluded).
- Not done / left for the user: `infra/nginx/security-headers.conf` and `docs/operations/nginx.md` still mention `alcalde-micro.png`/`og-image.png` by name in comments (CSP itself, `img-src 'self' data:`, is unaffected — these are just stale doc/comment references); out of scope for this frontend-only writer.

## Next step
User to manually test in the browser (see writer's report for a checklist), then decide on committing.
