import type { EnelOutage } from '../services/api-outage.service';
import { toOutageMarkers } from './map-markers';

function outage(overrides: Partial<EnelOutage>): EnelOutage {
  return {
    objectId: 1,
    affectedClients: 12,
    serviceType: 'LV',
    interruptionDate: '2026-09-10T10:05:00',
    repositionDate: '',
    neighborhoodName: 'Triana Casco Antiguo',
    districtName: 'Triana',
    latitude: 37.38,
    longitude: -6.0,
    cause: 'Avería',
    fetchedAt: '2026-09-10T12:00:00',
    resolvedAt: null,
    ...overrides,
  };
}

describe('toOutageMarkers', () => {
  it('maps an ongoing outage', () => {
    const [m] = toOutageMarkers([outage({})]);
    expect(m).toMatchObject({
      lat: 37.38,
      lng: -6.0,
      neighborhood: 'Triana Casco Antiguo',
      district: 'Triana',
      startLabel: '10/09 10:05',
      endLabel: null,
      ongoing: true,
      affectedClients: 12,
      category: 'Avería',
    });
  });

  it('shows the observed end of a resolved outage', () => {
    const [m] = toOutageMarkers([
      outage({ resolvedAt: '2026-09-10T11:20:00', cause: 'Trabajos programados' }),
    ]);
    expect(m.endLabel).toBe('10/09 11:20');
    expect(m.ongoing).toBe(false);
    expect(m.category).toBe('Programado');
  });

  it('skips missing, zero and out-of-area coordinates', () => {
    const markers = toOutageMarkers([
      outage({ latitude: null }),
      outage({ longitude: undefined }),
      outage({ latitude: 0, longitude: 0 }),
      outage({ latitude: Number.NaN }),
      outage({ latitude: 40.4, longitude: -3.7 }),
      outage({}),
    ]);
    expect(markers.length).toBe(1);
  });
});
