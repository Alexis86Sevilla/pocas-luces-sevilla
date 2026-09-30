# Frontend

Angular 21 dashboard for **Sevilla Sin Luz**. It visualizes power outage data consumed from the Spring Boot backend.

## Requirements

- Node.js 22
- pnpm 10

## Development server

Install dependencies:

```bash
pnpm install
```

Start the dev server with the configured proxy to the backend:

```bash
pnpm start
```

Open http://localhost:4200. The proxy configuration forwards `/api/**` requests to `http://localhost:8080`.

## Build

Production build:

```bash
pnpm build
```

Artifacts are written to `dist/frontend/browser/`.

## Project structure

```
src/app/
├── core/                  Models, API services and date/outage utilities
├── layout/                Site shell: nav, footer, back-to-top, support section
├── shared/ui/             Reusable UI: share-button, open-data-download
└── pages/                 One folder per route, with its sections
    ├── home/              /      hero, live, monthly-ranking, monthly, chart
    ├── context/           /contexto   context-section, testimonials
    ├── data/              /datos      methodology
    ├── guide/             /guia       what to do during an outage
    └── map/               /mapa       outage map (Leaflet, loaded lazily)
```

## Tests

```bash
pnpm test
```

## Environment configuration

- `src/environments/environment.ts` — development values
- `src/environments/environment.prod.ts` — production values (API base URL)

The production file is swapped in automatically by the Angular CLI build configuration in `angular.json`.

## Notes

- The chart uses Chart.js with a 12-color stable palette, one color per district.
- The monthly filter defaults to the current month and year.
- Live outages are grouped by district in the UI.
- The backend returns timestamps in UTC; `core/utils/madrid-date.ts` renders them in Europe/Madrid time.
