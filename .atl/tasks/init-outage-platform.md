# Tasks: init-outage-platform

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 600–900 |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR 1: Foundation + Splash → PR 2: Home + Cards + Wiring |
| Delivery strategy | ask-on-risk |
| Chain strategy | pending |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: pending
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Domain models, service facade, splash guard/component | PR 1 | Base: main; includes animation + localStorage gate |
| 2 | Home page, outage/testimonial cards, route wiring, root cleanup | PR 2 | Base: PR 1 branch; integrates all features |

## Phase 1: Foundation

- [ ] 1.1 Create domain models
  - **Files**: `src/app/core/models/neighborhood.model.ts`, `src/app/core/models/outage-event.model.ts`, `src/app/core/models/testimonial.model.ts`, `src/app/core/models/index.ts`
  - **AC**: Types match DF-03; barrel exports all models.
  - **Deps**: None.

- [ ] 1.2 Create service interface + InjectionToken
  - **Files**: `src/app/core/services/outage.service.interface.ts`
  - **AC**: `IOutageService` exposes typed Signals; `OUTAGE_SERVICE` token exported.
  - **Deps**: 1.1.

- [ ] 1.3 Add mock data arrays
  - **Files**: `src/app/core/services/outage.mock-data.ts`
  - **AC**: San Pablo has ≥2 outages and ≥1 testimonial per DF-04/SC-DF03.
  - **Deps**: 1.1.

- [ ] 1.4 Implement `MockOutageService`
  - **Files**: `src/app/core/services/outage.service.ts`
  - **AC**: Root-provided service; `computed()` averages; `getOutagesForNeighborhood` and `getAverageOutage` return Signals per DF-01/SC-DF01.
  - **Deps**: 1.2, 1.3.

## Phase 2: Splash Gate

- [ ] 2.1 Create `SplashGuard`
  - **Files**: `src/app/core/guards/splash.guard.ts`
  - **AC**: Redirects to `/splash` when no flag; allows `/home` when flag exists per SP-01/SC-SP01/SC-SP02.
  - **Deps**: None.

- [ ] 2.2 Build `SplashComponent` logic
  - **Files**: `src/app/features/splash/splash.component.ts`
  - **AC**: Signal-driven phases; `prefers-reduced-motion` bypasses timeouts; skip sets flag and navigates per SP-03/SP-04/SC-SP03/SC-SP04.
  - **Deps**: None.

- [ ] 2.3 Build splash template + styles
  - **Files**: `src/app/features/splash/splash.component.html`, `src/app/features/splash/splash.component.css`
  - **AC**: Hero image, blackout overlay, "Saltar" button, grayscale keyframes, reduced-motion override per SC-SP04/SC-SP05.
  - **Deps**: 2.2.

## Phase 3: Home + Cards

- [ ] 3.1 Build `OutageCardComponent`
  - **Files**: `src/app/features/home/components/outage-card.component.ts`, `.html`, `.css`
  - **AC**: Receives `Neighborhood`; computes avg/count from service; expandable drill-down per OD-01/OD-02/OD-03/SC-OD01/SC-OD02/SC-OD03.
  - **Deps**: 1.4.

- [ ] 3.2 Build `TestimonialCardComponent`
  - **Files**: `src/app/features/home/components/testimonial-card.component.ts`, `.html`, `.css`
  - **AC**: Receives `Testimonial`; renders name, quote, source; accessible markup per TD-01/TD-04/SC-TD01/SC-TD03.
  - **Deps**: 1.1.

- [ ] 3.3 Build `HomeComponent`
  - **Files**: `src/app/features/home/home.component.ts`, `.html`, `.css`
  - **AC**: Injects `OUTAGE_SERVICE`; renders outage cards + testimonial cards; hero grayscale post-splash; Spanish empty states per OD-04/TD-03/SC-OD04/SC-TD02.
  - **Deps**: 2.1, 3.1, 3.2.

## Phase 4: Wiring + Cleanup

- [ ] 4.1 Update route config
  - **Files**: `src/app/app.routes.ts`
  - **AC**: `/splash` route, `/home` with `SplashGuard`, `**` redirect → `/splash`.
  - **Deps**: 2.1.

- [ ] 4.2 Clean root template
  - **Files**: `src/app/app.html`
  - **AC**: Contains only `<router-outlet />`.
  - **Deps**: None.

- [ ] 4.3 Update `index.html`
  - **Files**: `src/index.html`
  - **AC**: `lang="es"`, title "Pocas Luces Sevilla".
  - **Deps**: None.
