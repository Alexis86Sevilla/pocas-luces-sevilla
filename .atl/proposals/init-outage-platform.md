# Proposal: init-outage-platform

## Intent

Build a read-only web platform displaying power outage metrics and neighbor testimonials for humble neighborhoods in Sevilla. Target audience: non-tech-savvy residents who need simple, at-a-glance outage impact visibility. No user registration or reporting — all data is externally sourced (mocked in Phase 1).

## Scope

### In Scope
- Splash/hero with color → blackout animation → permanent grayscale (hero section only; rest of site stays full color)
- Outage stats per neighborhood (avg duration, frequency) — mock data via service facade
- Testimonial display from public social media content
- Simple, accessible UI for non-technical users; optional deeper data exploration
- Strict TDD with Vitest + Angular TestBed

### Out of Scope
- User registration, login, or user-submitted reports
- Backend API or real data source (Phase 2)
- Interactive maps, PWA, i18n, E2E tests

## Capabilities

### New Capabilities
- `splash-screen`: Hero section with phased animation (color → blackout → grayscale), skip button, localStorage persistence, `prefers-reduced-motion` respect
- `outage-display`: Per-neighborhood outage stats with computed averages, responsive data cards
- `testimonial-display`: Public testimonial listing from mock social media data, accessible layout
- `data-facade`: OutageService with Signal-based mock store; facade pattern for future HttpClient swap

### Modified Capabilities
None — greenfield project.

## Approach

Route-guard splash (exploration Approach B). Splash renders on first visit, sets `localStorage` flag, dismisses to `/home`. Hero-only grayscale via CSS class on hero container (not full-page). OutageService uses Angular Signals with mock array; `computed()` for averages. Service exposes read-only Signals — consumers are data-source agnostic. All UI copy in Spanish.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `src/app/core/` | New | Domain models, OutageService facade, splash guard |
| `src/app/features/splash/` | New | SplashScreenComponent with animation |
| `src/app/features/home/` | New | HomeComponent with stats dashboard |
| `src/app/features/testimonials/` | New | TestimonialComponent with listing |
| `src/app/app.routes.ts` | Modified | Add splash, home, testimonial routes |
| `src/app/app.config.ts` | Modified | Add provideHttpClient (future-ready) |
| `src/app/app.ts` | Modified | Render router-outlet only; no splash logic in root |
| `src/styles.css` | Modified | Hero grayscale utility, reduced-motion overrides |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Splash animation violates WCAG contrast/pause rules | Med | Skip button visible; `prefers-reduced-motion: reduce` disables animation |
| Grayscale CSS filter degrades on low-end devices | Low | `will-change: filter`; profile on throttled CPU |
| Project name mismatch (git remote `pocas-luces-sevilla` vs Engram `alcalde-luces`) | Low | Documented; no functional impact |

## Rollback Plan

Revert commits via `git revert`. All changes additive — only route config and app root template modified from original. Worst case: delete new directories, restore original `app.routes.ts` and `app.ts`.

## Dependencies

None — self-contained frontend with mock data.

## Success Criteria

- [ ] Splash animates on first visit, skipped on return, dismissible via skip button
- [ ] Outage stats render with computed averages from mock data
- [ ] Testimonials display correctly in list layout
- [ ] All components pass TDD tests (Vitest + TestBed)
- [ ] Hero section stays grayscale post-splash; rest of site in full color
