# Neutral hero

## Objective
Keep a hooky, humorous home hero without anything that could be read as partisan, so the site can be promoted widely and shown on a CV.

## Decisions (user, 2026-10-01)
- Remove the mayor photo (`public/alcalde-micro.webp`), its aria-label, and the slogan "Un alcalde con pocas luces".
- Remove the subtitle "Sacamos a la luz los cortes que la dejadez municipal esconde en los barrios humildes."
- New h1: "Se fue la luz. Otra vez."
- Live line under it: "Y van N cortes este mes en Sevilla." (N = outages of the current Madrid calendar month, same data/count as the monthly section, brief ones included). N = 1 → "Y va 1 corte este mes en Sevilla."; N = 0 → "Este mes, de momento, ningún corte en Sevilla."; loading → no number (no layout jump); error → line hidden.
- New subtitle: "Los cortes de luz de Sevilla, barrio a barrio y en tiempo real, con los datos de e-distribución."
- Background: own inline SVG illustration of Seville at night (Giralda silhouette and city skyline) with lit windows that switch off over time; static under `prefers-reduced-motion`. Visual language consistent with `public/og-image.jpg` (dark navy, warm amber glow).
- Keep the live "Ahora mismo" strip and the Telegram link. Name "Sevilla Sin Luz" stays. Context page not in scope.

## Constraints
- Branch feat/neutral-hero. No commit until the user reviews locally (port 4300 proxied to prod).
- Route: delegated direct (one writer, 2+ non-trivial files). TDD off.
- Checks: `npx ng test --watch=false`, `npx ng build` (initial budget unchanged), CSP still clean (inline SVG only, no external assets, no inline scripts).
- Accessibility: decorative SVG aria-hidden; text contrast AA over the illustration.

## Tasks
- [x] H1 Replace hero content and background; delete the photo; update specs.
- [x] H2 Monthly counter line with all states + tests.
- [x] H3 Local review by the user.

## Progress / evidence
- Route: delegated direct (one writer). Hero rewritten with inline SVG skyline (Giralda, Torre del Oro, rooftops); windows generated in `skyline-windows.ts`, staggered CSS switch-off (static under reduced motion); `isGrayscale` timer removed (pure CSS now). Photo removed with `git rm`; stale mentions removed from `docs/operations/nginx.md` and `infra/nginx/security-headers.conf`.
- Counter: `ApiOutageService.currentMonthCount/currentMonthStatus` reuse the shared monthly signal when it shows the current Madrid month (no extra request); otherwise one extra request to `/outages/monthly` for the current month.
- `npx ng test --watch=false`: 152 passed (31 files). `npx ng build`: ok, initial total 443.89 kB raw, no budget warnings.
- Revision (2026-10-01): the user rejected the inline SVG skyline ("me parece horrible") and asked to use their own canvas animation "Sevilla se apaga". Route: delegated direct (one writer). `skyline-windows.ts`, the SVG markup and its CSS were deleted; new standalone `seville-night/` component (`seville-night.component.ts` + typed `seville-night-scene.ts`, faithful port with seeded rng and offscreen layer caching) is the hero background. Runs once via rAF then rests on t = END; reduced motion or hidden tab draws only the final frame; no 2D context (jsdom) does nothing; rAF cancelled on destroy. Canvas `object-fit: cover; object-position: 32% 40%` (phone 390x844 shows about x 440-980 of 1920: Giralda x 585 + cathedral). Hero vignette gradient strengthened at the bottom for text contrast. Neutral copy and counter unchanged.
- H3: user approved locally 2026-10-01 after two tweaks: faster blackout (END 10.8 s -> 4.8 s, lights off from ~2 s) and lighter final dimming (max 0.15 + 0.16 moonlight floor on monuments).
