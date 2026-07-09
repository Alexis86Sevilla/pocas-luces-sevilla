# Design: Landing Page Redesign

## Technical Approach

Incremental rewiring of `HomeComponent`. Remove `SplashComponent` overlay → build `HeroComponent` with SVG lightbulb CSS @keyframes cascade. Replace `TestimonialCardComponent` → `VideoCarouselComponent` + `VideoCardComponent` with iframe embeds. Add `DateFilterComponent` above outage cards, wired through `MockOutageService`. Append `FooterComponent`. Zero new npm deps. Angular 21 standalone + Tailwind CSS 4 + pnpm. All copy in Spanish.

## Architecture Decisions

| Decision | Choice | Rejected | Rationale |
|----------|--------|----------|-----------|
| AD-07: Hero animation | CSS @keyframes + inline SVG bulbs, signal-driven phases | Lottie, canvas, Angular animations | User mandate (HA-09). No deps. `animationend` on last bulb triggers grayscale class toggle. |
| AD-08: Video embeds | YouTube/Instagram iframes + DomSanitizer | HTML5 `<video>` | User preflight specifies iframe embeds. DomSanitizer required for `[src]` binding per VT-03. |
| AD-09: Date filter | Two native `<select>` elements emitting `{year, month}` | Date range picker, custom dropdown | Month-level granularity only. Simpler, accessible, no custom JS. |
| AD-10: Filter data flow | Service-internal `activeFilter` signal; `getOutagesForNeighborhood` recomputes from filtered set | Pass filtered arrays as props | Preserves existing `OutageCardComponent` contract (service injection). Only adds `isFilterActive` input for empty-state disambiguation. |
| AD-11: VideoTestimonial model | New `VideoTestimonial` interface: `{ id, authorName, embedUrl, platform }` | Extend existing `Testimonial` | Different shape (no `quote`, `source`, `sourceUrl`; adds `embedUrl`, `platform`). Cleaner separation. Old `Testimonial` unused after `TestimonialCardComponent` removal. |

## Data Flow

```
HomeComponent loads
  │
  ├─ localStorage gate → HeroComponent
  │   ├─ first visit: bulbs cascade → animationend → grayscale
  │   └─ return visit: static grayscale
  │
  ├─ "Ver cortes" click → scrollIntoView(#outages)
  │
  ├─ DateFilterComponent emits {year, month}
  │   └─ HomeComponent → outageService.setMonthFilter(y, m)
  │       └─ getOutagesForNeighborhood(id) recomputes from filtered set
  │           └─ OutageCardComponent (reads via service, isFilterActive input)
  │
  ├─ VideoCarouselComponent
  │   └─ VideoCardComponent (sanitized iframe)
  │
  └─ FooterComponent
```

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `features/splash/splash.component.*` | **Delete** | Replaced by HeroComponent |
| `features/testimonials/testimonial-card.component.*` | **Delete** | Replaced by VideoCarouselComponent + VideoCardComponent |
| `features/hero/hero.component.{ts,html,css}` | **Create** | SVG bulbs + CSS @keyframes cascade |
| `features/testimonials/video-carousel.component.{ts,html}` | **Create** | Scroll-snap container for VideoCardComponent list |
| `features/testimonials/video-card.component.{ts,html}` | **Create** | Sanitized iframe embed card |
| `features/outages/date-filter.component.{ts,html}` | **Create** | Month/year `<select>` pair, emits `{year, month}` |
| `features/footer/footer.component.{ts,html}` | **Create** | "Alexis GM" + `getFullYear()` |
| `features/home/home.component.{ts,html,css}` | **Modify** | Remove splash/tetimonial logic; wire hero/video/date-filter/footer |
| `features/outages/outage-card.component.{ts,html}` | **Modify** | Add `isFilterActive` input; conditional empty messages (OD-04 vs OD-05) |
| `core/models/video-testimonial.model.ts` | **Create** | `VideoTestimonial { id, authorName, embedUrl, platform }` |
| `core/models/index.ts` | **Modify** | Export `VideoTestimonial` |
| `core/services/outage.service.ts` | **Modify** | Add `setMonthFilter(y, m)`, internal `activeFilter` signal |
| `core/services/outage.service.interface.ts` | **Modify** | Add `setMonthFilter` + `filteredOutages` signal |
| `core/services/outage.mock-data.ts` | **Modify** | Multi-month outage data; video testimonials with embed URLs |

## Interfaces / Contracts

```typescript
// core/models/video-testimonial.model.ts
export interface VideoTestimonial {
  readonly id: string;
  readonly authorName: string;
  readonly embedUrl: string;
  readonly platform: 'youtube' | 'instagram';
}

// core/services/outage.service.interface.ts (additions)
export interface IOutageService {
  // ... existing ...
  setMonthFilter(year: number, month: number): void;
  readonly filteredOutages: Signal<readonly OutageEvent[]>;
}
```

## Testing Strategy

| Layer | What to Test | Approach |
|-------|-------------|----------|
| None | All layers | **TDD disabled** — manual visual QA only |

## Migration / Rollout

No migration required. Additive change: new components coexist; old components removed after rewiring. Rollback: revert `HomeComponent` imports + template; delete new directories. No data/backend impact.

## Open Questions

- [ ] Mayor photo URL — currently `placehold.co`; need real asset
- [ ] Video embed URLs — mock data needs actual YouTube/Instagram embed links for visual QA
