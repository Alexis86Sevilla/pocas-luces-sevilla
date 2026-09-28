# Sevilla Sin Luz

A citizen dashboard that tracks and visualizes power outages in Seville neighborhoods using real data from Endesa's public API.

- **Live site:** https://sevillasinluz.es
- **API:** https://api.sevillasinluz.es

## What it does

- Fetches real-time outage data from Endesa every 5 minutes.
- Maps coordinates to Seville neighborhoods and official districts.
- Shows an annual comparison chart by district, monthly detail cards and a live outage feed.
- Provides context about the situation and citizen demands.

## Repository structure

```
.
├── backend/   Spring Boot 3 API
├── frontend/  Angular 21 dashboard
└── .github/   GitHub Actions deployment workflows
```

## Tech stack

- **Frontend:** Angular 21, TypeScript, Tailwind CSS, Chart.js, pnpm
- **Backend:** Spring Boot 3.5, Java 21, Maven, PostgreSQL (production), H2 (development)
- **Infrastructure:** Ubuntu VPS, nginx, Let's Encrypt, systemd, GitHub Actions

## Development

See the individual README files:

- [backend/README.md](backend/README.md)
- [frontend/README.md](frontend/README.md)

## Deployment

Pushes to `main` trigger GitHub Actions which deploy the backend JAR and the frontend build to the VPS.

Required repository secrets:

- `VPS_HOST`
- `VPS_USER`
- `VPS_SSH_KEY`

## Operations

Infrastructure hardening runbooks (nginx, VPS, PostgreSQL, monitoring, branch rename) live in
[`docs/operations/README.md`](docs/operations/README.md).

## License

This is a civic project. Use it, fork it and improve it.
