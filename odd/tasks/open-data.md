# Open data download

## Objective
Let journalists and citizens download the outage dataset as CSV under CC BY 4.0, with honest column semantics.

## Constraints
- Branch `feat/open-data` from `main`. No commits until the user tests.
- License: CC BY 4.0 (user decision 2026-09-29). Attribution: "Sevilla Sin Luz (sevillasinluz.es), a partir de datos públicos de e-distribución (Grupo Endesa)". The license covers our compilation and processing; the source data belongs to the distributor.
- Public endpoint must not live under `/api/outages/export` (nginx admin rate limit) and must not expose raw responses or internal hashes.
- Datetimes are Europe/Madrid wall-clock; the suite runs under UTC and Europe/Madrid.
- UI copy Spanish; code and docs English. Checks: `./mvnw test`, `npx ng test --watch=false`, `npx ng build`.
- Route: delegated direct (2+ non-trivial files).

## Tasks
- [x] O1 Backend `GET /api/open-data/outages.csv` (optional `year`, `month`), streamed, UTF-8 BOM, formula-injection safe, `Cache-Control: public, max-age=300`, `Content-Disposition` filename, `Link` license header.
- [x] O2 Frontend download buttons (monthly section: selected month; methodology: full dataset) with license and citation text.
- [x] O3 Data dictionary in the methodology section and README.
- [x] O4 Excel (es-ES) variant `?format=excel`: `;` delimiter, decimal comma, `yyyy-MM-dd HH:mm:ss`; unknown format 400; standard CSV unchanged; second link in UI.

## Progress / evidence
- O1: OpenDataController + CsvWriter (shared with OutageExportDto) + OpenDataOutageRow; paged (500, ordered interruptionDate,id) streaming, no long transaction. Tests: OpenDataControllerTest, OpenDataOutageRowTest, CsvWriterTest. `./mvnw test`: 208 run, 0 failures in surefire-reports and surefire-reports-europe-madrid.
- O2: open-data-download component + core/utils/open-data-url; wired in monthly-section and methodology. O3: data dictionary in methodology, backend/README.md, root README.md, JSON-LD Dataset license/distribution.
- `npx ng test --watch=false`: 85 passed. `npx ng build`: ok.
- No commits made (per constraints).
- O4: CsvWriter.rowWith/field delimiter- and decimal-aware; OpenDataOutageRow.Variant (STANDARD/EXCEL); controller `format` param, filename `-excel`. Frontend: openDataCsvUrl format param, component shows CSV + Excel links (flex-wrap), methodology sentence. `./mvnw test`: 212 run, 0 failures in both surefire report dirs. `npx ng test --watch=false`: 87 passed. `npx ng build`: ok. No commits.
