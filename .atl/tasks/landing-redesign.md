# Tasks: Landing Page Redesign

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 900–1,100 |
| 400-line budget risk | High |
| Chained PRs recommended | Yes |
| Suggested split | PR 1 (data+filter) → PR 2 (hero+splash) → PR 3 (video/footer+home) |
| Delivery strategy | ask-on-risk |
| Chain strategy | size:exception (single PR approved by user) |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: pending
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Foundation: video model, service filter, mock data, date filter, outage-card empty states | PR 1 | Base: `main`; keeps existing Home UI working |
| 2 | Hero: lightbulb animation, splash removal, wire hero into Home | PR 2 | Base: `main` after PR 1 |
| 3 | Video carousel, footer, final Home layout | PR 3 | Base: `main` after PR 2 |

## Phase 1: Foundation / Data & Filter

- [x] 1.1 Create `frontend/src/app/core/models/video-testimonial.model.ts` and export it from `frontend/src/app/core/models/index.ts`.
- [x] 1.2 Update `frontend/src/app/core/services/outage.service.interface.ts` to add the month-year filter contract and filtered outage signals.
- [x] 1.3 Update `frontend/src/app/core/services/outage.service.ts` to implement `activeFilter`, `setMonthFilter`, filtered getters, and expose video testimonials.
- [x] 1.4 Update `frontend/src/app/core/services/outage.mock-data.ts` with multi-month outage dates and `VideoTestimonial` entries including `embedUrl` and `platform`.
- [x] 1.5 Create `frontend/src/app/features/outages/date-filter.component.{ts,html}` with month/year selects and a `filterChange` output.
- [x] 1.6 Modify `frontend/src/app/features/outages/outage-card.component.{ts,html}` to accept an `isFilterActive` input and show the correct empty message for filtered vs. no-data states.

## Phase 2: Hero Animation

- [x] 2.1 Delete `frontend/src/app/features/splash/` and all Splash references.
- [x] 2.2 Create `frontend/src/app/features/hero/hero.component.{ts,html,css}` with SVG bulbs, CSS `@keyframes` cascade, mayor photo, Spanish tagline "Un alcalde con pocas luces", and a "Ver cortes" CTA.
- [x] 2.3 Add first-visit `localStorage` gate and a `prefers-reduced-motion` instant-grayscale fallback in `HeroComponent`.

## Phase 3: Video Testimonials & Footer

- [x] 3.1 Delete `frontend/src/app/features/testimonials/testimonial-card.component.*`.
- [x] 3.2 Create `frontend/src/app/features/testimonials/video-card.component.{ts,html}` using `DomSanitizer.bypassSecurityTrustResourceUrl` for the iframe `src`.
- [x] 3.3 Create `frontend/src/app/features/testimonials/video-carousel.component.{ts,html}` as a horizontal scroll-snap container with an empty state.
- [x] 3.4 Create `frontend/src/app/features/footer/footer.component.{ts,html}` showing "Alexis GM" and the current year, with no links.

## Phase 4: Home Integration

- [x] 4.1 Rewrite `frontend/src/app/features/home/home.component.ts` to import `HeroComponent`, `DateFilterComponent`, `VideoCarouselComponent`, and `FooterComponent`, and to manage filter state and scroll target.
- [x] 4.2 Rewrite `frontend/src/app/features/home/home.component.html` to render hero → outages with filter → video carousel → footer, preserving Spanish copy.
- [x] 4.3 Rewrite `frontend/src/app/features/home/home.component.css` for the hero grayscale transition and section spacing.
- [x] 4.4 Remove unused `Testimonial` references once `VideoTestimonial` is wired.

## Phase 5: Verification

- [x] 5.1 Run `pnpm build` in `frontend/` and resolve all TypeScript strict errors.
- [x] 5.2 Manual QA against scenarios: SC-HA01-04, SC-ODF01-03/OD05, SC-VT01-03, SC-FT01, SC-OD01-05.
