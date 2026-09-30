import { formatMadridDate, parseMadridDate } from './madrid-date';

const WALL_CLOCK_MONTH = /^\d{4}-(\d{2})-\d{2}T/;

/**
 * Month index (0-11) of a backend datetime in Europe/Madrid.
 *
 * Backend strings are Madrid wall-clock ("2026-09-29T08:56:00"), so the month is read
 * straight from the text: no timezone maths, and independent of the browser's timezone
 * (Date#getMonth would use the visitor's local zone). Strings with an explicit offset
 * fall back to a Madrid conversion.
 */
export function madridMonthIndex(dateTime: string): number {
  const match = WALL_CLOCK_MONTH.exec(dateTime);
  if (match && !/(Z|[+-]\d{2}:?\d{2})$/.test(dateTime)) {
    return Number(match[1]) - 1;
  }
  return Number(formatMadridDate(parseMadridDate(dateTime), 'MM')) - 1;
}

/**
 * Outage counts per district name and month, computed in a single pass so charts
 * don't rescan every outage for each district and month.
 */
export function countByDistrictAndMonth(
  outages: readonly { districtName?: string | null; interruptionDate: string }[],
): ReadonlyMap<string, readonly number[]> {
  const counts = new Map<string, number[]>();
  for (const outage of outages) {
    const district = outage.districtName;
    if (!district) continue;
    let months = counts.get(district);
    if (!months) {
      months = new Array<number>(12).fill(0);
      counts.set(district, months);
    }
    months[madridMonthIndex(outage.interruptionDate)]++;
  }
  return counts;
}
