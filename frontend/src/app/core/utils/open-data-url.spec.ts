import { openDataCsvUrl } from './open-data-url';

describe('openDataCsvUrl', () => {
  const base = '/api';

  it('points at the full dataset when no scope is given', () => {
    expect(openDataCsvUrl({}, base)).toBe('/api/open-data/outages.csv');
  });

  it('adds only the year', () => {
    expect(openDataCsvUrl({ year: 2026 }, base)).toBe('/api/open-data/outages.csv?year=2026');
  });

  it('adds year and month', () => {
    expect(openDataCsvUrl({ year: 2026, month: 9 }, base)).toBe('/api/open-data/outages.csv?year=2026&month=9');
  });

  it('ignores a month without a year, which the API would reject', () => {
    expect(openDataCsvUrl({ month: 9 }, base)).toBe('/api/open-data/outages.csv');
  });

  it('never routes through the rate-limited admin export path', () => {
    expect(openDataCsvUrl({ year: 2026 }, 'https://api.sevillasinluz.es/api')).not.toContain('/outages/export');
  });

  it('adds format=excel after the scope and never for the standard CSV', () => {
    expect(openDataCsvUrl({}, base, 'excel')).toBe('/api/open-data/outages.csv?format=excel');
    expect(openDataCsvUrl({ year: 2026, month: 9 }, base, 'excel')).toBe('/api/open-data/outages.csv?year=2026&month=9&format=excel');
    expect(openDataCsvUrl({ year: 2026 }, base, 'csv')).toBe('/api/open-data/outages.csv?year=2026');
  });

  it('defaults to the environment API base', () => {
    expect(openDataCsvUrl()).toMatch(/\/api\/open-data\/outages\.csv$/);
  });
});
