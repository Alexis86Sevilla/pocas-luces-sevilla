import type { Neighborhood, OutageEvent, VideoTestimonial } from '../models';

export const MOCK_NEIGHBORHOODS: readonly Neighborhood[] = [
  { id: 'san-pablo', name: 'San Pablo' },
  { id: 'triana', name: 'Triana' },
  { id: 'macarena', name: 'La Macarena' },
  { id: 'nervion', name: 'Nervión' },
];

export const MOCK_OUTAGES: readonly OutageEvent[] = [
  // San Pablo
  {
    id: 'outage-001',
    date: new Date('2026-06-12T14:30:00'),
    durationMinutes: 90,
    neighborhoodId: 'san-pablo',
  },
  {
    id: 'outage-002',
    date: new Date('2026-07-03T22:15:00'),
    durationMinutes: 120,
    neighborhoodId: 'san-pablo',
  },
  {
    id: 'outage-003',
    date: new Date('2026-03-10T09:45:00'),
    durationMinutes: 60,
    neighborhoodId: 'san-pablo',
  },

  // Triana
  {
    id: 'outage-004',
    date: new Date('2026-06-14T11:00:00'),
    durationMinutes: 60,
    neighborhoodId: 'triana',
  },
  {
    id: 'outage-005',
    date: new Date('2026-07-08T19:30:00'),
    durationMinutes: 45,
    neighborhoodId: 'triana',
  },
  {
    id: 'outage-006',
    date: new Date('2026-03-22T21:00:00'),
    durationMinutes: 30,
    neighborhoodId: 'triana',
  },

  // Macarena
  {
    id: 'outage-007',
    date: new Date('2026-06-15T07:20:00'),
    durationMinutes: 30,
    neighborhoodId: 'macarena',
  },
  {
    id: 'outage-008',
    date: new Date('2026-07-05T16:00:00'),
    durationMinutes: 90,
    neighborhoodId: 'macarena',
  },

  // Nervión
  {
    id: 'outage-009',
    date: new Date('2026-06-25T16:00:00'),
    durationMinutes: 75,
    neighborhoodId: 'nervion',
  },
  {
    id: 'outage-010',
    date: new Date('2026-07-01T13:10:00'),
    durationMinutes: 50,
    neighborhoodId: 'nervion',
  },
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
