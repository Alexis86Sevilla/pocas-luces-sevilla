import type { EnelOutage } from '../services/api-outage.service';
import { rankDistricts } from './district-ranking';

function outage(overrides: Partial<EnelOutage>): EnelOutage {
  return {
    objectId: 1,
    affectedClients: 10,
    serviceType: 'LV',
    interruptionDate: '2026-09-10T10:00:00',
    repositionDate: '',
    neighborhoodName: null,
    districtName: 'Triana',
    fetchedAt: '2026-09-10T12:00:00',
    resolvedAt: null,
    ...overrides,
  };
}

describe('rankDistricts', () => {
  it('sums the hours of overlapping outages instead of merging them', () => {
    const [entry] = rankDistricts([
      outage({ resolvedAt: '2026-09-10T11:00:00' }),
      outage({ objectId: 2, resolvedAt: '2026-09-10T11:30:00' }),
    ]);
    expect(entry.accumulatedHours).toBeCloseTo(2.5);
    expect(entry.outageCount).toBe(2);
    expect(entry.affectedClients).toBe(20);
  });

  it('counts ongoing outages but adds no hours for them', () => {
    const [entry] = rankDistricts([
      outage({ resolvedAt: '2026-09-10T12:00:00' }),
      outage({ objectId: 2, resolvedAt: null, affectedClients: 5 }),
    ]);
    expect(entry.accumulatedHours).toBeCloseTo(2);
    expect(entry.outageCount).toBe(2);
    expect(entry.affectedClients).toBe(15);
  });

  it('sorts by hours descending', () => {
    const ranking = rankDistricts([
      outage({ districtName: 'Nervión', resolvedAt: '2026-09-10T11:00:00' }),
      outage({ districtName: 'Triana', resolvedAt: '2026-09-10T14:00:00' }),
    ]);
    expect(ranking.map(r => r.districtName)).toEqual(['Triana', 'Nervión']);
  });

  it('breaks hour ties by outage count, then by name', () => {
    const ranking = rankDistricts([
      outage({ districtName: 'Sur', resolvedAt: null }),
      outage({ districtName: 'Norte', resolvedAt: null }),
      outage({ districtName: 'Norte', objectId: 2, resolvedAt: null }),
      outage({ districtName: 'Este', resolvedAt: null }),
    ]);
    expect(ranking.map(r => r.districtName)).toEqual(['Norte', 'Este', 'Sur']);
  });

  it('groups outages without district under a placeholder and handles empty input', () => {
    expect(rankDistricts([outage({ districtName: null })])[0].districtName).toBe('Zona no identificada');
    expect(rankDistricts([])).toEqual([]);
  });
});
