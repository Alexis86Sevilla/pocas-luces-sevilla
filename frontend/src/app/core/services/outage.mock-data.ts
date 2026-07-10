import type { Neighborhood, OutageEvent, VideoTestimonial } from '../models';

// Barrios de Sevilla capital — mezcla de humildes, medios y acomodados
export const MOCK_NEIGHBORHOODS: readonly Neighborhood[] = [
  // Barrios humildes — históricamente más afectados
  { id: 'san-pablo', name: 'San Pablo' },
  { id: 'poligono-sur', name: 'Polígono Sur' },
  { id: 'torreblanca', name: 'Torreblanca' },
  { id: 'los-pajaritos', name: 'Los Pajaritos' },
  { id: 'palmete', name: 'Palmete' },
  { id: 'cerro-aguila', name: 'Cerro del Águila' },

  // Barrios de clase media
  { id: 'triana', name: 'Triana' },
  { id: 'macarena', name: 'La Macarena' },
  { id: 'nervion', name: 'Nervión' },
  { id: 'san-bernardo', name: 'San Bernardo' },

  // Barrios acomodados — referencia para comparar
  { id: 'los-remedios', name: 'Los Remedios' },
  { id: 'santa-cruz', name: 'Santa Cruz' },
];

// Helper para fechas: año 2026, mes 0-11
const d = (month: number, day: number, hours: number, minutes: number) =>
  new Date(2026, month, day, hours, minutes);

export const MOCK_OUTAGES: readonly OutageEvent[] = [
  // ── Barrios humildes (muchos cortes, larga duración) ──

  // San Pablo — 8 cortes
  { id: 'o-01', date: d(0, 15, 14, 30), durationMinutes: 120, neighborhoodId: 'san-pablo' },
  { id: 'o-02', date: d(1, 20, 22, 15), durationMinutes: 90, neighborhoodId: 'san-pablo' },
  { id: 'o-03', date: d(2, 10, 9, 45), durationMinutes: 60, neighborhoodId: 'san-pablo' },
  { id: 'o-04', date: d(3, 5, 18, 0), durationMinutes: 150, neighborhoodId: 'san-pablo' },
  { id: 'o-05', date: d(4, 12, 7, 30), durationMinutes: 75, neighborhoodId: 'san-pablo' },
  { id: 'o-06', date: d(5, 3, 21, 0), durationMinutes: 110, neighborhoodId: 'san-pablo' },
  { id: 'o-07', date: d(5, 22, 16, 45), durationMinutes: 45, neighborhoodId: 'san-pablo' },
  { id: 'o-08', date: d(6, 8, 12, 0), durationMinutes: 180, neighborhoodId: 'san-pablo' },

  // Polígono Sur — 7 cortes
  { id: 'o-09', date: d(0, 8, 20, 0), durationMinutes: 180, neighborhoodId: 'poligono-sur' },
  { id: 'o-10', date: d(1, 14, 15, 30), durationMinutes: 95, neighborhoodId: 'poligono-sur' },
  { id: 'o-11', date: d(2, 25, 11, 0), durationMinutes: 70, neighborhoodId: 'poligono-sur' },
  { id: 'o-12', date: d(3, 18, 19, 15), durationMinutes: 200, neighborhoodId: 'poligono-sur' },
  { id: 'o-13', date: d(4, 2, 8, 45), durationMinutes: 85, neighborhoodId: 'poligono-sur' },
  { id: 'o-14', date: d(5, 10, 23, 0), durationMinutes: 130, neighborhoodId: 'poligono-sur' },
  { id: 'o-15', date: d(6, 15, 17, 0), durationMinutes: 160, neighborhoodId: 'poligono-sur' },

  // Torreblanca — 6 cortes
  { id: 'o-16', date: d(0, 22, 10, 0), durationMinutes: 90, neighborhoodId: 'torreblanca' },
  { id: 'o-17', date: d(1, 5, 19, 30), durationMinutes: 140, neighborhoodId: 'torreblanca' },
  { id: 'o-18', date: d(2, 18, 14, 0), durationMinutes: 55, neighborhoodId: 'torreblanca' },
  { id: 'o-19', date: d(4, 28, 21, 15), durationMinutes: 100, neighborhoodId: 'torreblanca' },
  { id: 'o-20', date: d(5, 17, 8, 0), durationMinutes: 75, neighborhoodId: 'torreblanca' },
  { id: 'o-21', date: d(6, 4, 16, 30), durationMinutes: 120, neighborhoodId: 'torreblanca' },

  // Los Pajaritos — 5 cortes
  { id: 'o-22', date: d(0, 12, 16, 0), durationMinutes: 110, neighborhoodId: 'los-pajaritos' },
  { id: 'o-23', date: d(2, 8, 22, 0), durationMinutes: 80, neighborhoodId: 'los-pajaritos' },
  { id: 'o-24', date: d(3, 22, 13, 45), durationMinutes: 65, neighborhoodId: 'los-pajaritos' },
  { id: 'o-25', date: d(5, 30, 20, 0), durationMinutes: 95, neighborhoodId: 'los-pajaritos' },
  { id: 'o-26', date: d(6, 12, 11, 0), durationMinutes: 140, neighborhoodId: 'los-pajaritos' },

  // Palmete — 4 cortes
  { id: 'o-27', date: d(1, 10, 17, 0), durationMinutes: 85, neighborhoodId: 'palmete' },
  { id: 'o-28', date: d(3, 15, 21, 30), durationMinutes: 130, neighborhoodId: 'palmete' },
  { id: 'o-29', date: d(5, 8, 14, 0), durationMinutes: 60, neighborhoodId: 'palmete' },
  { id: 'o-30', date: d(6, 20, 9, 0), durationMinutes: 95, neighborhoodId: 'palmete' },

  // Cerro del Águila — 4 cortes
  { id: 'o-31', date: d(0, 28, 12, 0), durationMinutes: 70, neighborhoodId: 'cerro-aguila' },
  { id: 'o-32', date: d(2, 14, 19, 0), durationMinutes: 105, neighborhoodId: 'cerro-aguila' },
  { id: 'o-33', date: d(4, 20, 23, 0), durationMinutes: 55, neighborhoodId: 'cerro-aguila' },
  { id: 'o-34', date: d(6, 2, 15, 0), durationMinutes: 90, neighborhoodId: 'cerro-aguila' },

  // ── Barrios clase media (menos cortes, más cortos) ──

  // Triana — 3 cortes
  { id: 'o-35', date: d(0, 18, 11, 0), durationMinutes: 60, neighborhoodId: 'triana' },
  { id: 'o-36', date: d(3, 8, 20, 0), durationMinutes: 45, neighborhoodId: 'triana' },
  { id: 'o-37', date: d(6, 25, 16, 30), durationMinutes: 30, neighborhoodId: 'triana' },

  // La Macarena — 2 cortes
  { id: 'o-38', date: d(2, 5, 15, 0), durationMinutes: 40, neighborhoodId: 'macarena' },
  { id: 'o-39', date: d(5, 12, 12, 0), durationMinutes: 55, neighborhoodId: 'macarena' },

  // Nervión — 2 cortes
  { id: 'o-40', date: d(1, 25, 16, 0), durationMinutes: 35, neighborhoodId: 'nervion' },
  { id: 'o-41', date: d(4, 15, 19, 0), durationMinutes: 50, neighborhoodId: 'nervion' },

  // San Bernardo — 2 cortes
  { id: 'o-42', date: d(0, 20, 13, 0), durationMinutes: 25, neighborhoodId: 'san-bernardo' },
  { id: 'o-43', date: d(6, 18, 14, 0), durationMinutes: 40, neighborhoodId: 'san-bernardo' },

  // ── Barrios acomodados (casi sin cortes) ──

  // Los Remedios — 1 corte
  { id: 'o-44', date: d(3, 20, 10, 0), durationMinutes: 20, neighborhoodId: 'los-remedios' },

  // Santa Cruz — 1 corte
  { id: 'o-45', date: d(5, 5, 18, 0), durationMinutes: 15, neighborhoodId: 'santa-cruz' },
];

export const MOCK_VIDEO_TESTIMONIALS: readonly VideoTestimonial[] = [
  {
    id: 'video-001',
    authorName: 'María López',
    embedUrl: 'https://www.youtube.com/embed/dQw4w9WgXcQ',
    platform: 'youtube',
  },
  {
    id: 'video-002',
    authorName: 'Antonio Ruiz',
    embedUrl: 'https://www.youtube.com/embed/9bZkp7q19f0',
    platform: 'youtube',
  },
  {
    id: 'video-003',
    authorName: 'Carmen Vega',
    embedUrl: 'https://www.instagram.com/p/ABC123/embed/',
    platform: 'instagram',
  },
];
