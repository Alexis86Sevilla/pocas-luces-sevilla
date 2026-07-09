# Delta Specs: Landing Page Redesign

> Modifies: splash-screen (REMOVED), testimonial-display (REMOVED), outage-display, data-facade
> Adds: hero-animation, video-testimonials, outage-date-filter, footer

## ADDED Requirements

### hero-animation

| ID | Req | S | Summary |
|----|-----|---|---------|
| HA-01 | Lightbulb arrangement | MUST | 3-5 SVG bulbs around/above mayor photo |
| HA-02 | Bulb cascade | MUST | Flicker, then sequential off; last-bulb-off triggers grayscale |
| HA-03 | Grayscale transition | MUST | Photo color→grayscale at cascade end, permanent |
| HA-04 | Tagline | MUST | "Un alcalde con pocas luces" |
| HA-05 | CTA | MUST | "Ver cortes" button scrolls to outage section |
| HA-06 | First-visit gate | MUST | localStorage flag; animation only on first visit |
| HA-07 | Return visits | MUST | Static grayscale hero when flag exists |
| HA-08 | Reduced motion | MUST | `prefers-reduced-motion: reduce` → instant grayscale |
| HA-09 | CSS-only | MUST | @keyframes only, no JS libs |
| HA-10 | Mobile | MUST | Prioritize face + tagline; bulbs may overflow |

- **SC-HA01**: GIVEN first visit, no flag → WHEN HomeComponent loads → THEN bulbs cascade, photo goes grayscale
- **SC-HA02**: GIVEN `prefers-reduced-motion: reduce` → WHEN hero loads → THEN instant grayscale
- **SC-HA03**: GIVEN return visit (flag set) → THEN static grayscale hero
- **SC-HA04**: GIVEN hero visible → WHEN "Ver cortes" clicked → THEN scroll to outage section

### video-testimonials

| ID | Req | S | Summary |
|----|-----|---|---------|
| VT-01 | Video carousel | MUST | Horizontal carousel of iframe embeds |
| VT-02 | Sources | MUST | YouTube + Instagram iframes |
| VT-03 | Safe URLs | MUST | DomSanitizer.bypassSecurityTrustResourceUrl |
| VT-04 | Model | MUST | Testimonial.embedUrl: string |
| VT-05 | Responsive | MUST | Adapts to viewport width |
| VT-06 | Empty state | MUST | "Aún no hay testimonios disponibles" |

- **SC-VT01**: GIVEN testimonials with embedUrl → WHEN page loads → THEN iframe carousel rendered
- **SC-VT02**: GIVEN empty list → THEN Spanish empty message shown
- **SC-VT03**: GIVEN mobile viewport → THEN videos scale, horizontal scroll

### outage-date-filter

| ID | Req | S | Summary |
|----|-----|---|---------|
| ODF-01 | Month/year selector | MUST | Selector above outage cards |
| ODF-02 | Default | MUST | Current month/year on load |
| ODF-03 | Filter | MUST | Cards filtered to selected period |
| ODF-04 | Empty state | MUST | "No hay cortes para este período" |
| ODF-05 | Smooth update | SHOULD | Visual transition on filter change |

- **SC-ODF01**: GIVEN current month July 2026 → WHEN loads → THEN filter defaults to July 2026
- **SC-ODF02**: GIVEN period with no outages → THEN empty state message shown
- **SC-ODF03**: GIVEN filter on July → WHEN user selects March → THEN cards update

### footer

| ID | Req | S | Summary |
|----|-----|---|---------|
| FT-01 | Attribution | MUST | "Alexis GM" plain text |
| FT-02 | Dynamic year | MUST | `new Date().getFullYear()` |
| FT-03 | No links | MUST | Plain text, no `<a>` elements |
| FT-04 | Position | MUST | At page bottom |

- **SC-FT01**: GIVEN page renders → THEN "Alexis GM" + current year shown, no links

## MODIFIED Requirements

### outage-display

(Previously: requirements without date filtering; OD-04 was sole empty state)

| ID | Req | S | Summary |
|----|-----|---|---------|
| OD-01 | Cards | MUST | Neighborhood card: avg duration + count, filtered by date |
| OD-02 | Metrics | MUST | Computed from filtered OutageEvent[] |
| OD-03 | Drill-down | SHOULD | Expand to show filtered event history |
| OD-04 | Empty (no data) | MUST | "No hay datos de cortes disponibles" |
| OD-05 | Empty (filtered) | MUST | "No hay cortes para este período" when filter yields no results |

- **SC-OD01**: GIVEN 3 neighborhoods, current month → THEN one card each, filtered avg + count
- **SC-OD02**: GIVEN 30/60/90 min outages → THEN "60 min" avg, "3 cortes"
- **SC-OD03**: GIVEN card click → THEN expanded filtered event history
- **SC-OD04**: GIVEN empty neighborhood list → THEN OD-04 message shown
- **SC-OD05**: GIVEN filter month with no outages → THEN OD-05 message shown

### data-facade

(Previously: two signals, no filter method, Testimonial lacked embedUrl, single-month data, no DF-05)

| ID | Req | S | Summary |
|----|-----|---|---------|
| DF-01 | Signals | MUST | `Signal<Neighborhood[]>`, `Signal<Testimonial[]>`, filtered outage signal |
| DF-02 | Interface | MUST | `IOutageService` with `filterOutagesByMonthYear(m, y)` |
| DF-03 | Types | MUST | Testimonial + `embedUrl: string`; OutageEvent unchanged |
| DF-04 | Mock data | MUST | San Pablo ≥2 outages multi-month + ≥1 testimonial with embedUrl |
| DF-05 | Filter method | MUST | Returns `Signal<Neighborhood[]>` filtered to month/year |

- **SC-DF01**: GIVEN service → THEN typed signals including filtered outages
- **SC-DF02**: GIVEN `IOutageService` token → THEN backend swap needs no consumer changes
- **SC-DF03**: GIVEN mock data → THEN San Pablo present with multi-month data
- **SC-DF04**: GIVEN testimonials → THEN each includes `embedUrl`

## REMOVED Requirements

### testimonial-display

| ID | Req | Summary |
|----|-----|---------|
| TD-01 | List | Text cards: name, quote, source |
| TD-02 | Read-only | Mock data only |
| TD-03 | Empty | "Aún no hay testimonios disponibles" |
| TD-04 | A11y | Semantic HTML, keyboard-operable |

**Reason**: Replaced by video-testimonials (iframe carousel replacing text cards).
**Migration**: Remove `TestimonialCardComponent`. Use `VideoTestimonialComponent`. Model gains `embedUrl` per DF-03.

### splash-screen

| ID | Req | Summary |
|----|-----|---------|
| SP-01 | Gate | First-visit localStorage flag |
| SP-02 | Grayscale | Color→blackout→grayscale on hero |
| SP-03 | A11y | `prefers-reduced-motion` disables animation |

**Reason**: Replaced by hero-animation (lightbulb cascade instead of overlay splash).
**Migration**: Remove `SplashComponent`. Use `HeroComponent`. First-visit gate preserved in HA-06/HA-07/HA-08.
