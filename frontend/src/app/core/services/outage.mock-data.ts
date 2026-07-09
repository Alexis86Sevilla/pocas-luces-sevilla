import type { Neighborhood, OutageEvent, Testimonial } from '../models';

export const MOCK_NEIGHBORHOODS: readonly Neighborhood[] = [
  { id: 'san-pablo', name: 'San Pablo' },
  { id: 'triana', name: 'Triana' },
  { id: 'macarena', name: 'La Macarena' },
  { id: 'nervion', name: 'Nervión' },
];

export const MOCK_OUTAGES: readonly OutageEvent[] = [
  {
    id: 'outage-001',
    date: new Date('2026-06-12T14:30:00'),
    durationMinutes: 90,
    neighborhoodId: 'san-pablo',
  },
  {
    id: 'outage-002',
    date: new Date('2026-06-18T22:15:00'),
    durationMinutes: 180,
    neighborhoodId: 'san-pablo',
  },
  {
    id: 'outage-003',
    date: new Date('2026-06-20T09:45:00'),
    durationMinutes: 45,
    neighborhoodId: 'san-pablo',
  },
  {
    id: 'outage-004',
    date: new Date('2026-06-14T11:00:00'),
    durationMinutes: 60,
    neighborhoodId: 'triana',
  },
  {
    id: 'outage-005',
    date: new Date('2026-06-22T19:30:00'),
    durationMinutes: 120,
    neighborhoodId: 'triana',
  },
  {
    id: 'outage-006',
    date: new Date('2026-06-15T07:20:00'),
    durationMinutes: 30,
    neighborhoodId: 'macarena',
  },
  {
    id: 'outage-007',
    date: new Date('2026-06-25T16:00:00'),
    durationMinutes: 75,
    neighborhoodId: 'nervion',
  },
];

export const MOCK_TESTIMONIALS: readonly Testimonial[] = [
  {
    id: 'testimonial-001',
    authorName: 'María López',
    quote:
      'Llevamos tres cortes esta semana en San Pablo. Los niños no pueden estudiar y la comida se estropea en la nevera.',
    source: 'X',
    sourceUrl: 'https://x.com/example',
  },
  {
    id: 'testimonial-002',
    authorName: 'Antonio Ruiz',
    quote:
      'Se fue la luz justo cuando mi madre necesitaba la concentradora de oxígeno. Es una situación muy grave.',
    source: 'Facebook',
    sourceUrl: null,
  },
  {
    id: 'testimonial-003',
    authorName: 'Carmen Vega',
    quote:
      'En Triana parece que los cortes son cada vez más frecuentes. Nadie nos explica por qué ocurre.',
    source: 'Instagram',
    sourceUrl: 'https://instagram.com/example',
  },
];
