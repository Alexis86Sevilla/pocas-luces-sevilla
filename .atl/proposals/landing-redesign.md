# Proposal: Landing Page Redesign

## Intent

Replace the basic splash-based landing page with a professional, approachable experience. The current page relies on a full-screen splash overlay and static text testimonials — too basic for a public-facing site targeting non-tech-savvy neighbors.

## Scope

### In Scope
- Lightbulb hero animation (3-5 bulbs flicker → sequential turn-off → mayor photo to grayscale). CSS/SVG, first-visit-only via localStorage. Static grayscale on return visits.
- "Ver cortes" CTA button scrolling to outage section
- Month/year date filter for outage cards (default to current month, empty state when no results)
- Video testimonials: YouTube/Instagram `<iframe>` embeds in horizontal carousel (replaces `TestimonialCardComponent`)
- Footer: "Alexis GM" + dynamic year, plain text
- Remove `SplashComponent` and `TestimonialCardComponent` entirely
- `prefers-reduced-motion` fallback: static grayscale hero, no animation

### Out of Scope
- Real API integration, backend changes, user auth, map integration, comment submission, animation replay button

## Capabilities

### New Capabilities
- `hero-animation`: Multi-lightbulb entrance animation with grayscale transition, localStorage first-visit gate
- `video-testimonials`: Embedded video carousel replacing text-based testimonial cards
- `outage-date-filter`: Month/year selector filtering outage cards with empty state

### Modified Capabilities
- `splash-screen`: **REMOVED** — replaced by `hero-animation`
- `testimonial-display`: **MODIFIED** — text quotes → video iframe embeds in carousel
- `outage-display`: **MODIFIED** — cards now filterable by month/year
- `data-facade`: **MODIFIED** — `Testimonial` gains `videoUrl: string`; `IOutageService` gains `filterOutagesByMonthYear(month, year)` returning computed signal

## Approach

Incremental rewiring of `HomeComponent`: remove splash, build hero with CSS/SVG lightbulb animation, swap text testimonials for iframe carousel (Angular `DomSanitizer`), add month/year filter above outage cards, append footer. Zero new npm dependencies. Angular 21 standalone + Tailwind CSS 4 + pnpm. No tests (TDD disabled).

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `features/splash/` | Removed | Replaced by hero animation |
| `features/testimonials/testimonial-card.component.*` | Removed | Replaced by video carousel |
| `features/hero/hero.component.*` | New | Lightbulb SVG + CSS animation |
| `features/date-filter/date-filter.component.*` | New | Month/year selector UI |
| `features/video-testimonials/video-testimonial.component.*` | New | Iframe carousel with DomSanitizer |
| `features/footer/footer.component.*` | New | Static credit footer |
| `features/home/home.component.*` | Modified | Rewire all sections, new layout |
| `core/models/testimonial.model.ts` | Modified | Add `videoUrl` field |
| `core/services/outage.service.*` | Modified | Add month/year filter method |
| `core/services/outage.mock-data.ts` | Modified | Video URLs, date diversity |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| CSS animation timing fragile across browsers (Safari) | Med | `animationend` event for grayscale trigger; `prefers-reduced-motion` fallback |
| Iframe embeds blocked by CSP or ad-blockers | Low | DomSanitizer handles security; mock data uses public YouTube embed URLs |
| Mobile: bulb positioning with mayor photo | Low | Responsive CSS; mobile prioritizes face + tagline per user decision |
| Review budget (400 lines) likely exceeded | Med | Chained PRs if needed (ask-always strategy active) |

## Rollback Plan

Revert `HomeComponent` to previous commit (splash + text testimonials). Hero, date-filter, video-testimonial, and footer components are additive — removing their imports and deleting new directories restores original state. No database or backend rollback needed.

## Dependencies

None. All mock data self-contained. Zero new npm packages.

## Success Criteria

- [ ] Hero animation plays on first visit, static grayscale on return visits
- [ ] "Ver cortes" button scrolls to outage section
- [ ] Month/year filter filters outage cards; empty state when no results
- [ ] Video carousel renders YouTube/Instagram iframes responsively
- [ ] Footer displays "Alexis GM" + current year
- [ ] `SplashComponent` and `TestimonialCardComponent` fully removed
- [ ] `prefers-reduced-motion` users see static grayscale hero
- [ ] Build passes cleanly, no new dependencies
