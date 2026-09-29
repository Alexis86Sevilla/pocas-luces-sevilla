# Guide: "¿Qué hago si me quedo sin luz?"

## Objective
A very plain-language, step-by-step guide for neighbors who do not know how to act or claim during and after an outage.

## Constraints
- Only facts verified in primary sources on 2026-09-29 (list below). Anything not listed must not be published.
- Branch `feat/guide` from `main`. No commits until the user reviews. Lazy route `/guia`, nav link "Qué hacer".
- UI copy Spanish, very simple; code English. Checks: `npx ng test --watch=false`, `npx ng build`.

## Verified facts (source → fact)
- edistribucion.com/es/faq.html: averías Andalucía 900 850 840; atención al cliente 900 878 119; daños: "Contacta directamente con tu comercializadora. O llámanos al 900 878 119".
- edistribucion.com/es/averias/cortes-programados-luz-hoy.html: "Siempre avisamos con al menos 24 horas de antelación"; electrodependientes: comunicarlo a la comercializadora; si hay corte programado llamar al 900 878 119.
- BOE RD 1955/2000 art. 104.2 (consolidado 12/02/2026): baja tensión, zona urbana: 5 horas / 10 interrupciones por año natural, interrupciones imprevistas mayores de tres minutos. Art. 99.4: capitales de provincia = zona urbana. Art. 105.2: descuento en la facturación dentro del primer trimestre del año siguiente. Art. 105.3: tope 10 % de la facturación anual. Art. 101.4: las programadas no dan descuento salvo que se incumplan los requisitos de aviso.
- MITECO reclamaciones: reclamar a comercializadora o distribuidora; respuesta en 15 días hábiles; después defensor del cliente (si existe), juntas arbitrales de consumo o servicios de consumo autonómicos.
- Consumo Responde (Junta): primero a la comercializadora; calidad/cortes → órgano de energía de la Junta en la provincia; hoja de quejas y reclamaciones en papel o app/web Hoj@; empresa responde en 10 días hábiles; después OMIC o Servicio Provincial de Consumo; tel. 900 215 080 (L-V 8-20, S 8-15), consumoresponde@juntadeandalucia.es.
- BOJA 59, 25/03/2024 (Orden 18/03/2024): reclamación de calidad ante la Junta preferentemente electrónica; resolución en tres meses (silencio desestimatorio); se limita a constatar deficiencias, no cuantifica daños.
- sevilla.org OMIC: Plaza del Monte Pirolo, s/n (Edificio de la antigua Hispano-Aviación, Triana), 41010; 955 472 982 / 955 472 985 / 955 472 987; omic.consumo@sevilla.org.
- Junta de Andalucía / 112 (nota 28/04/2025): persona con respiradores u otros aparatos → avisar de inmediato al 112; desconectar aparatos; dejar una luz encendida; no abrir y cerrar la nevera; desechar alimentos y medicamentos que hayan perdido la cadena de frío (más de 4 °C durante más de 2 horas).
- BOE Ley 24/2013 art. 52.4.i: suministro esencial con constancia documental médica de equipo indispensable para mantener con vida; no puede suspenderse (protección frente a suspensión, p. ej. impago; no evita averías).
- Juntas arbitrales de consumo: gratuitas; requieren que la empresa esté adherida o acepte.

## Do NOT publish
Prescription periods (say "cuanto antes"); a state electrodependency registry (only a draft); phone opening hours of 900 878 119; that the site's data is official evidence (say "apoyo, no prueba oficial"); the Junta Arbitral address; that retailers must pass on the discount.

## Tasks
- [x] G1 `/guia` page with steps, template letter (copy button), month CSV link, sources section, legal disclaimer.
- [x] G2 Nav link, SEO title/description, entry points from the live section and hero.
- [x] G3 Specs.

## Progress / evidence
- Route: inline-delegated writer (single). Files: features/guia-page (ts, html, spec), app.routes.ts, nav (desktop+mobile), live section link, specs.
- `npx ng test --watch=false`: 25 files, 113 tests passed. `npx ng build`: initial total 425.90 kB (112.85 kB transfer); new lazy chunk guia-page-component 18.61 kB (5.31 kB).
- Hero: no entry point added (kept clean; live section link + nav suffice).
- Not committed (awaiting user review).
