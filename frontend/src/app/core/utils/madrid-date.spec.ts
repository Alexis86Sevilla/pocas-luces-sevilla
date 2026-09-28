import { formatMadridDate, parseMadridDate, toMadridDateKey } from './madrid-date';

describe('parseMadridDate', () => {
  it('interprets an offset-less summer (CEST, UTC+2) wall-clock time correctly', () => {
    // Endesa: "28/09/2026 15:38" CEST -> stored verbatim as "2026-09-28T15:38:00".
    const result = parseMadridDate('2026-09-28T15:38:00');
    expect(result.toISOString()).toBe('2026-09-28T13:38:00.000Z');
  });

  it('interprets an offset-less winter (CET, UTC+1) wall-clock time correctly', () => {
    const result = parseMadridDate('2026-01-15T09:00:00');
    expect(result.toISOString()).toBe('2026-01-15T08:00:00.000Z');
  });

  it('keeps the same Madrid calendar date for a late-night (23:30) timestamp', () => {
    const result = parseMadridDate('2026-09-28T23:30:00');
    expect(toMadridDateKey('2026-09-28T23:30:00')).toBe('2026-09-28');
    // Sanity: 23:30 CEST is 21:30 UTC, still the same UTC calendar day.
    expect(result.toISOString()).toBe('2026-09-28T21:30:00.000Z');
  });

  it('resolves the correct offset either side of the October DST transition', () => {
    // 2026-10-25: clocks go back from CEST (+2) to CET (+1) at 03:00 local
    // (01:00 UTC). Times picked outside the 02:00-03:00 fold-back hour, which
    // is inherently ambiguous (it occurs twice) and out of scope here.
    const beforeTransition = parseMadridDate('2026-10-25T01:30:00'); // still CEST (+2)
    const afterTransition = parseMadridDate('2026-10-25T04:00:00'); // already CET (+1)

    expect(beforeTransition.toISOString()).toBe('2026-10-24T23:30:00.000Z');
    expect(afterTransition.toISOString()).toBe('2026-10-25T03:00:00.000Z');
  });

  it('parses strings that already carry a timezone as-is', () => {
    expect(parseMadridDate('2026-09-28T13:38:00Z').toISOString()).toBe('2026-09-28T13:38:00.000Z');
    expect(parseMadridDate('2026-09-28T15:38:00+02:00').toISOString()).toBe('2026-09-28T13:38:00.000Z');
  });
});

describe('formatMadridDate', () => {
  it('formats a UTC instant using Madrid wall-clock calendar components', () => {
    const instant = parseMadridDate('2026-09-28T15:38:00');
    expect(formatMadridDate(instant, 'dd/MM/yyyy HH:mm')).toBe('28/09/2026 15:38');
  });
});
