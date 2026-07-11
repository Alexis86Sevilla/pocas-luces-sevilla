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
├── core/           Models and API services
├── features/
│   ├── context/    Context and citizen demands section
│   ├── hero/       Landing hero
│   ├── home/       Main dashboard page
│   ├── outages/    Chart, cards and date filter
│   └── testimonials/ Video carousel
└── ...
```

## Environment configuration

- `src/environments/environment.ts` — development values
- `src/environments/environment.prod.ts` — production values (API base URL)

The production file is swapped in automatically by the Angular CLI build configuration in `angular.json`.

## Notes

- The chart uses Chart.js with a 12-color stable palette, one color per neighborhood.
- The monthly filter defaults to the current month and year.
- Live outages are grouped by neighborhood in the UI.
