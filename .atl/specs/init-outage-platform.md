# Specs: init-outage-platform (synced with implementation)

> 4 new capabilities. Spanish UI. Non-tech-savvy audience. Splash embedded in HomeComponent, not a separate route.

## splash-screen

| ID | Requirement | Strength | Summary |
|----|------------|----------|---------|
| SP-01 | First-visit gate | MUST | Splash shown on first visit only; flag stored in localStorage |
| SP-02 | Hero grayscale transition | MUST | Full-color → blackout → grayscale on hero photo only; rest of site normal color |
| SP-03 | Reduced motion | MUST | `prefers-reduced-motion: reduce` disables animation; direct grayscale |
| ~~SP-03~~ | ~~Skip button~~ | ~~REMOVED~~ | ~~User decision: animation is short, splash is entry point — no skip needed~~ |

- **SC-SP01**: GIVEN no localStorage flag → WHEN HomeComponent loads → THEN splash animation shown
- **SC-SP02**: GIVEN flag is `true` → WHEN HomeComponent loads → THEN content shown directly, no splash
- **SC-SP03**: GIVEN `prefers-reduced-motion: reduce` → WHEN splash loads → THEN no animation, instant grayscale
- **SC-SP04**: GIVEN post-splash → WHEN page renders → THEN hero `<img>` is grayscale; nav and content are full color

## outage-display

| ID | Requirement | Strength | Summary |
|----|------------|----------|---------|
| OD-01 | Neighborhood cards | MUST | Card per neighborhood: avg duration + outage count |
| OD-02 | Computed metrics | MUST | Averages from `OutageEvent[]` per neighborhood |
| OD-03 | Drill-down | SHOULD | Click card to expand event history |
| OD-04 | Empty state | MUST | Spanish "No hay datos de cortes disponibles" when empty |

- **SC-OD01**: GIVEN 3 neighborhoods with events → WHEN home loads → THEN one card each with avg and count
- **SC-OD02**: GIVEN outages of 30/60/90 min → WHEN card renders → THEN "60 min" avg, "3 cortes" count
- **SC-OD03**: GIVEN cards visible → WHEN user clicks card → THEN expanded view shows dated outage events
- **SC-OD04**: GIVEN empty neighborhood list → WHEN home renders → THEN Spanish empty message displayed

## testimonial-display

| ID | Requirement | Strength | Summary |
|----|------------|----------|---------|
| TD-01 | Testimonial list | MUST | Cards with name, quote, and social media source |
| TD-02 | Read-only | MUST | No submission; content from mock data only |
| TD-03 | Empty state | MUST | "Aún no hay testimonios disponibles" when empty |
| TD-04 | Accessibility | MUST | Semantic HTML, keyboard-operable, screen-reader friendly |

- **SC-TD01**: GIVEN 5 mock testimonials → WHEN page loads → THEN each card shows name, quote, platform
- **SC-TD02**: GIVEN no testimonials → WHEN page loads → THEN Spanish empty placeholder shown
- **SC-TD03**: GIVEN keyboard/screen-reader user → WHEN traversing list → THEN cards are operable without mouse

## data-facade

| ID | Requirement | Strength | Summary |
|----|------------|----------|---------|
| DF-01 | Signal store | MUST | `OutageService` exposes `Signal<Neighborhood[]>`, `Signal<Testimonial[]>` |
| DF-02 | Interface contract | MUST | `IOutageService` injection token; consumers never depend on concrete class |
| DF-03 | Domain types | MUST | `Neighborhood`, `OutageEvent { date, durationMinutes, neighborhoodId }`, `Testimonial { authorName, quote, source, sourceUrl }` |
| DF-04 | Mock data | MUST | San Pablo ≥2 outages + ≥1 testimonial; structure extensible |

- **SC-DF01**: GIVEN service provided in root → WHEN component reads signal → THEN receives typed mock data
- **SC-DF02**: GIVEN `IOutageService` as token → WHEN Phase 2 swaps to `HttpOutageService` → THEN no consumer changes needed
- **SC-DF03**: GIVEN mock data loaded → WHEN querying neighborhoods → THEN San Pablo present with required data
