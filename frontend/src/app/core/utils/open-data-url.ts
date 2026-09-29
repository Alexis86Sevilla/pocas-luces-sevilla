import { environment } from '../../../environments/environment';

export type OpenDataFormat = 'csv' | 'excel';

/**
 * Builds the public open-data CSV URL. `month` only makes sense together with `year`
 * (the API rejects a month without a year), so it is ignored when `year` is missing.
 * `format: 'excel'` selects the semicolon / decimal-comma variant; the standard CSV adds no param.
 */
export function openDataCsvUrl(
  scope: { year?: number | null; month?: number | null } = {},
  apiBaseUrl: string = environment.apiBaseUrl,
  format: OpenDataFormat = 'csv',
): string {
  const base = `${apiBaseUrl}/open-data/outages.csv`;
  const { year, month } = scope;
  const params: string[] = [];
  if (year != null) {
    params.push(`year=${year}`);
    if (month != null) params.push(`month=${month}`);
  }
  if (format === 'excel') params.push('format=excel');
  return params.length ? `${base}?${params.join('&')}` : base;
}
