# Design: init-outage-platform (synced with implementation)

## Technical Approach

Angular 21 standalone SPA. Four capabilities delivered as feature directories under `src/app/features/`. Splash is an **embedded element** inside HomeComponent — shown on first visit via `@if`, hidden after animation completes via `(finished)` output. localStorage flag skips subsequent visits. Signal-based mock service behind an InjectionToken contract for Phase 2 HttpClient swap. CSS-only phased animation (color → blackout → grayscale) on hero image, respecting `prefers-reduced-motion`. All copy in Spanish. No tests per user decision.

## Architecture Decisions

| ID | Choice | Rejected | Rationale |
|----|--------|----------|-----------|
| AD-01 | Embedded splash element in HomeComponent | Route guard + `/splash` route | User feedback: splash is a UI element, not a page. Simpler, no guard needed. |
| AD-02 | CSS-only animation via signal-driven classes | Angular animations API | Lighter bundle; sufficient for 3-phase linear transition |
| AD-03 | Single `HomeComponent` renders both outages + testimonials | Separate `/testimonials` route | Landing page — separate route over-engineering for read-only listing |
| AD-04 | `InjectionToken<IOutageService>` + `providedIn: 'root'` mock | Direct class injection | Interface contract ready for Phase 2 |
| AD-05 | `computed()` per neighborhood for averages | Pre-calculated mock data | Validates that real API data will work identically |
| AD-06 | `localStorage.getItem('splash-dismissed')` as flag | Cookie or sessionStorage | Persistent across sessions |

## Data Flow

```
HomeComponent loads
     │
     ├─ localStorage flag missing → @if (showSplash) → <app-splash />
     │                                                     │
     │                                          SplashComponent (animation phases)
     │                                                     │
     │                                          output.finished.emit()
     │                                                     │
     │                                          HomeComponent.onSplashFinished()
     │                                          localStorage.setItem(...)
     │                                          showSplash.set(false)
     │                                                     │
     └─ localStorage flag exists ──────────→ @else → main content
                                                       │
                                            OutageCardComponent, TestimonialCardComponent
```

## Routes

```
/       → HomeComponent (splash embedded, shown on first visit)
**      → redirect to /
```

## Directory Structure

```
src/app/
├── core/
│   ├── models/{neighborhood,outage-event,testimonial}.model.ts, index.ts
│   └── services/{outage.service,outage.mock-data,outage.service.interface}.ts
├── features/
│   ├── splash/{splash.component}.{ts,html,css}
│   ├── outages/outage-card.component.{ts,html,css}
│   ├── testimonials/testimonial-card.component.{ts,html,css}
│   └── home/home.component.{ts,html,css}
├── app.{ts,html,css,routes,config}.ts
└── main.ts
```

## Open Questions

- [ ] Mayor photo source URL — placeholder used until real asset provided
- [ ] Outage card drill-down: accordion implemented
