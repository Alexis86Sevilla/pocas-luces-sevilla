# Design: init-outage-platform

## Technical Approach

Angular 21 standalone SPA. Four capabilities delivered as feature directories under `src/app/features/`. Route guard (`canActivate`) on `/home` redirects first-visit users to `/splash`; localStorage flag skips subsequent visits. Signal-based mock service behind an InjectionToken contract for Phase 2 HttpClient swap. CSS-only phased animation (color → blackout → grayscale) on hero image, respecting `prefers-reduced-motion`. All copy in Spanish. No tests per user decision.

## Architecture Decisions

| ID | Choice | Rejected | Rationale |
|----|--------|----------|-----------|
| AD-01 | Route guard on `/home` (not catch-all) | `**` wildcard with guard redirect | Simpler, follows Angular conventions, `/splash` remains directly addressable |
| AD-02 | CSS-only animation via signal-driven classes | Angular animations API | Lighter bundle; no BrowserAnimationsModule; sufficient for 3-phase linear transition |
| AD-03 | Single `HomeComponent` renders both outages + testimonials | Separate `/testimonials` route | Quick landing page — separate route is over-engineering for read-only listing |
| AD-04 | `InjectionToken<IOutageService>` + `providedIn: 'root'` mock | Direct class injection | Interface contract ready for Phase 2; no provider wiring in config needed until swap |
| AD-05 | `computed()` per neighborhood for averages | Pre-calculated mock data | Exercises actual business logic; validates that real API data will work identically |
| AD-06 | `localStorage.getItem('splash-dismissed')` as flag | Cookie or sessionStorage | Persistent across sessions; spec requires skip on return visits |

## Data Flow

```
SplashGuard (reads localStorage)
     │
     ├─ no flag → redirect /splash
     │                │
     │       SplashComponent (animation phases via setTimeout + signal)
     │                │  skip() or 4s timeout
     │                ▼
     │       localStorage.setItem('splash-dismissed', 'true')
     │       router.navigate('/home')
     │
     └─ flag exists → allow /home
                        │
               HomeComponent injects OUTAGE_SERVICE
                        │
          reads Signal<Neighborhood[]> → OutageCardComponent (computed avg)
          reads Signal<Testimonial[]> → TestimonialCardComponent
```

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `src/app/core/models/neighborhood.model.ts` | Create | `Neighborhood { id, name }` |
| `src/app/core/models/outage-event.model.ts` | Create | `OutageEvent { date, durationMinutes, neighborhoodId }` |
| `src/app/core/models/testimonial.model.ts` | Create | `Testimonial { authorName, quote, source, sourceUrl }` |
| `src/app/core/models/index.ts` | Create | Barrel export |
| `src/app/core/services/outage.service.interface.ts` | Create | `IOutageService` interface + `OUTAGE_SERVICE` InjectionToken |
| `src/app/core/services/outage.mock-data.ts` | Create | Static mock arrays (≥2 outages for San Pablo, ≥1 testimonial) |
| `src/app/core/services/outage.service.ts` | Create | `MockOutageService` with Signal store + computed averages |
| `src/app/core/guards/splash.guard.ts` | Create | Functional guard — redirects to `/splash` if no flag |
| `src/app/features/splash/splash.component.ts` | Create | Phased animation controller; skip handler; signal for phase |
| `src/app/features/splash/splash.component.html` | Create | Hero photo, blackout overlay, "Saltar" button |
| `src/app/features/splash/splash.component.css` | Create | Keyframes for overlay fade; `will-change: filter` on img; reduced-motion override |
| `src/app/features/home/home.component.ts` | Create | Container injecting `OUTAGE_SERVICE` |
| `src/app/features/home/home.component.html` | Create | Layout: hero section + outage cards + testimonial cards |
| `src/app/features/home/home.component.css` | Create | Hero grayscale class (post-splash), responsive grid |
| `src/app/features/home/components/outage-card.component.ts` | Create | Presentational: receives `Neighborhood`, computes avg from injected service |
| `src/app/features/home/components/outage-card.component.html` | Create | Card with avg duration, count, expandable drill-down |
| `src/app/features/home/components/testimonial-card.component.ts` | Create | Presentational: receives `Testimonial` |
| `src/app/features/home/components/testimonial-card.component.html` | Create | Card with name, quote text, source platform |
| `src/app/app.routes.ts` | Modify | Add `/splash`, `/home` with guard, `**` redirect → `/splash` |
| `src/app/app.config.ts` | Modify | No changes needed (guard is functional, service is root-provided) |
| `src/app/app.html` | Modify | Replace placeholder; keep `<router-outlet />` only |
| `src/index.html` | Modify | `lang="es"`, title to "Pocas Luces Sevilla" |

## Interfaces / Contracts

```typescript
// core/services/outage.service.interface.ts
export interface IOutageService {
  readonly neighborhoods: Signal<Neighborhood[]>;
  readonly testimonials: Signal<Testimonial[]>;
  readonly outages: Signal<OutageEvent[]>;
  getOutagesForNeighborhood(id: string): Signal<OutageEvent[]>;
  getAverageOutage(neighborhoodId: string): Signal<number>;
}

export const OUTAGE_SERVICE = new InjectionToken<IOutageService>('OUTAGE_SERVICE');
```

The mock implementation uses `signal()` + `computed()` internally. Components only inject `OUTAGE_SERVICE` — never the concrete class.

## Animation Strategy

CSS-only, three phases managed by a `phase` signal (`'color' | 'blackout' | 'done'`):

1. **color** (0–3s): Full-color hero photo. `setTimeout` advances to blackout.
2. **blackout** (3–4s): Dark overlay fades in via `opacity` transition on `.overlay.active`.
3. **done** (4s+): Remove overlay; apply `grayscale(100%)` class to hero `<img>`. Navigate to `/home`.

Skip button: sets `phase = 'done'`, writes localStorage, navigates immediately.

`@media (prefers-reduced-motion: reduce)` targets all transitions: hero goes instant-grayscale, no overlay animation. Detected via `window.matchMedia` in component's constructor, sets a separate `reducedMotion` signal to bypass timeouts.

## Directory Structure

```
src/app/
├── core/
│   ├── guards/splash.guard.ts
│   ├── models/{neighborhood,outage-event,testimonial}.model.ts, index.ts
│   └── services/{outage.service,outage.mock-data,outage.service.interface}.ts
├── features/
│   ├── splash/{splash.component}.{ts,html,css}
│   └── home/
│       ├── home.component.{ts,html,css}
│       └── components/
│           ├── outage-card.component.{ts,html,css}
│           └── testimonial-card.component.{ts,html,css}
├── app.{ts,html,css,routes,config}.ts
└── main.ts (unchanged)
```

## Open Questions

- [ ] Mayor photo source URL — placeholder needed until real asset provided
- [ ] Outage card drill-down: expand inline (accordion) vs modal? Accordion is lighter for landing page. Defer to implementer.
