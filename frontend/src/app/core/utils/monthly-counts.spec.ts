import { countByDistrictAndMonth, madridMonthIndex } from './monthly-counts';

describe('madridMonthIndex', () => {
  it('reads the month from a Madrid wall-clock string', () => {
    expect(madridMonthIndex('2026-09-29T08:56:00')).toBe(8);
    expect(madridMonthIndex('2026-01-01T00:10:00')).toBe(0);
  });

  it('keeps late-evening outages in their own month regardless of the browser timezone', () => {
    expect(madridMonthIndex('2026-08-31T23:30:00')).toBe(7);
  });

  it('converts strings with an explicit offset to Madrid time', () => {
    // 22:30 UTC on 31 August is 00:30 on 1 September in Madrid (CEST).
    expect(madridMonthIndex('2026-08-31T22:30:00Z')).toBe(8);
  });
});

describe('countByDistrictAndMonth', () => {
  it('counts outages per district and month in one pass', () => {
    const counts = countByDistrictAndMonth([
      { districtName: 'Sur', interruptionDate: '2026-07-01T10:00:00' },
      { districtName: 'Sur', interruptionDate: '2026-07-15T10:00:00' },
      { districtName: 'Sur', interruptionDate: '2026-09-01T10:00:00' },
      { districtName: 'Triana', interruptionDate: '2026-09-02T10:00:00' },
      { districtName: null, interruptionDate: '2026-09-02T10:00:00' },
    ]);
    expect(counts.get('Sur')?.[6]).toBe(2);
    expect(counts.get('Sur')?.[8]).toBe(1);
    expect(counts.get('Triana')?.[8]).toBe(1);
    expect(counts.get('Triana')?.[6]).toBe(0);
    expect(counts.size).toBe(2);
  });
});
