# Security hardening

## Objective
Fix the verified security findings from the 2026-09-28 audit, highest severity first.

## Constraints
- NO commits until the user has tested the changes (explicit user instruction). Work-unit commits are deferred.
- Do not ask the user questions during execution; take safe defaults and report them.
- TDD: not configured (source: none found). Ordinary functional checks apply.
- Runners: backend `./mvnw test` (in `backend/`), frontend `pnpm test` / `pnpm build` (in `frontend/`).
- Route: delegated direct (writer trigger: 2+ non-trivial files).

## Tasks

### High
- [x] H1 Default Spring profile is `prod`; `dev` (H2 + console) must be explicitly requested. Tests keep running on H2. Docs updated.
- [x] H2 `GlobalExceptionHandler` returns a generic message for unhandled exceptions (details only in logs).
- [x] H3 CI: pin third-party GitHub Actions to commit SHAs and add minimal `permissions:` block.

### Medium
- [x] M1 CSV/formula injection neutralized in `OutageExportDto` (`= + - @` and tab/CR prefixes).
- [x] M2 Constant-time API key comparison in `ApiKeyAuth` (`MessageDigest.isEqual`).
- [x] M3 Cooldown / rate limit on `POST /api/outages/fetch`.
- [x] M4 `year` validation on every branch of `GET /api/outages/enel`.
- [x] M5 `embedUrl` host allowlist (YouTube/Instagram) server-side and client-side before `bypassSecurityTrustResourceUrl`.

### Low
- [x] L1 Deploy workflow runs backend tests instead of `-DskipTests`.
- [x] L2 Fix stale comment in `application.yaml` about protected endpoints.
- Not in repo (report only): nginx security headers/CSP, port 8081 exposure, VPS systemd profile.

## Acceptance criteria
- `./mvnw test` passes; `pnpm build` and `pnpm test` pass.
- New behavior covered by focused tests.

## Progress / evidence
- All tasks implemented by one delegated writer (route: delegated direct). Uncommitted, awaiting user testing.
- `./mvnw test` (backend): 107 tests, 0 failures, 0 errors, 0 skipped (writer run + parent spot-check re-run). Testcontainers Postgres test ran.
- `pnpm build` (frontend): success. `npx ng test --watch=false`: 5/5 passed.
- Notes: `OutageDataScheduler.fetchAndSaveOutages()` made `synchronized` to serialize manual and scheduled runs (the lock is inside the `@Transactional` proxy, so commit happens after release; narrow window, not worse than before). Pinned action SHAs resolved with `git ls-remote`.
- Side effect: `frontend/pnpm-workspace.yaml` (untracked) created locally by `pnpm approve-builds`; not part of the change.
- Manual run (dev, port 8095, real Endesa data): testimonials 6/6 allowed, live/yearly/chart 200, /enel year=1800 -> 400, fetch no/wrong key -> 401, fetch ok -> 200 then 429, export CSV 200 with key / 401 without, bad type -> 400 generic, CORS ok. Default profile without flag -> prod (Postgres connect attempted).
- User tested in browser and approved. Commits on branch `fix/security-hardening` (not pushed):
  - 30e5d07 docs: correct outdated root and frontend READMEs
  - 41d6b2c fix(backend): stop leaking exception details in error responses (H2)
  - f6acdaa fix(backend): compare admin API key in constant time (M2)
  - fdcc61e fix(backend): neutralize formula injection in CSV export (M1)
  - f245832 fix: allowlist testimonial embed URL hosts (M5)
  - d008673 fix(backend): default to prod profile and guard manual fetch (H1, M3, M4, L2)
  - bb8a09e ci: pin actions to commit SHAs and run tests on deploy (H3, L1)
- VPS systemd verified by user: SPRING_PROFILES_ACTIVE=prod, DB_PASSWORD, ADMIN_API_KEY set.
- RDD assess on master..HEAD: risk high, review due.

## Next step
Native review of the branch, user decides push/merge, then UX/UI improvements.
