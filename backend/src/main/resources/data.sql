-- Barrios de Sevilla
INSERT INTO neighborhoods (name, postal_code, category) VALUES
('San Pablo', '41015', 'humilde'),
('Polígono Sur', '41013', 'humilde'),
('Torreblanca', '41016', 'humilde'),
('Los Pajaritos', '41006', 'humilde'),
('Palmete', '41006', 'humilde'),
('Cerro del Águila', '41006', 'humilde'),
('Triana', '41010', 'medio'),
('La Macarena', '41009', 'medio'),
('Nervión', '41005', 'medio'),
('San Bernardo', '41005', 'medio'),
('Los Remedios', '41011', 'acomodado'),
('Santa Cruz', '41004', 'acomodado');

-- Cortes 2026 — barrios humildes (muchos y largos)
INSERT INTO outages (neighborhood_id, date, duration_minutes) VALUES
(1, '2026-01-15', 120), (1, '2026-02-20', 90), (1, '2026-03-10', 60),
(1, '2026-04-05', 150), (1, '2026-05-12', 75), (1, '2026-06-03', 110),
(1, '2026-06-22', 45), (1, '2026-07-08', 180),
(2, '2026-01-08', 180), (2, '2026-02-14', 95), (2, '2026-03-25', 70),
(2, '2026-04-18', 200), (2, '2026-05-02', 85), (2, '2026-06-10', 130),
(2, '2026-07-15', 160),
(3, '2026-01-22', 90), (3, '2026-02-05', 140), (3, '2026-03-18', 55),
(3, '2026-05-28', 100), (3, '2026-06-17', 75), (3, '2026-07-04', 120),
(4, '2026-01-12', 110), (4, '2026-03-08', 80), (4, '2026-04-22', 65),
(4, '2026-06-30', 95), (4, '2026-07-12', 140),
(5, '2026-02-10', 85), (5, '2026-04-15', 130), (5, '2026-06-08', 60),
(5, '2026-07-20', 95),
(6, '2026-01-28', 70), (6, '2026-03-14', 105), (6, '2026-05-20', 55),
(6, '2026-07-02', 90);

-- Cortes 2026 — barrios medios (menos y más cortos)
INSERT INTO outages (neighborhood_id, date, duration_minutes) VALUES
(7, '2026-01-18', 60), (7, '2026-04-08', 45), (7, '2026-07-25', 30),
(8, '2026-03-05', 40), (8, '2026-06-12', 55),
(9, '2026-02-25', 35), (9, '2026-05-15', 50),
(10, '2026-01-20', 25), (10, '2026-07-18', 40);

-- Cortes 2026 — barrios acomodados (casi nada)
INSERT INTO outages (neighborhood_id, date, duration_minutes) VALUES
(11, '2026-04-20', 20),
(12, '2026-06-05', 15);

-- Testimonios
INSERT INTO testimonials (author_name, embed_url, platform) VALUES
('@soy_rociomartin - San Pablo resiste. Nueve días sin luz no son una avería: son abandono.', 'https://www.instagram.com/reel/DafWrbQNbXH/', 'instagram'),
('Manifestación Barrios Hartos — 27 jun 2025', 'https://www.youtube.com/embed/c64M0NsCAns', 'youtube'),
('Nueva protesta contra los apagones — Barrios Hartos', 'https://www.youtube.com/embed/07zNGlQJM0w', 'youtube'),
('Trailer documental: A Dos Velas', 'https://www.youtube.com/embed/-LMI8thfU-0', 'youtube'),
('Torreblanca se echa a la calle de nuevo por los cortes de luz - 6 de julio', 'https://www.youtube.com/embed/A7JM8Oh8fhc', 'youtube'),
('Manifestación cortes de luz - 8 julio 2025', 'https://www.youtube.com/embed/7FqLxT14-7U', 'youtube');
