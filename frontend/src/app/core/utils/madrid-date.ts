/**
 * Helpers for interpreting backend LocalDateTime strings as Europe/Madrid
 * wall-clock time.
 *
 * IMPORTANT: The backend stores UTC-equivalent instants in LocalDateTime
 * columns (the scheduler converts Endesa API times from CEST to UTC before
 * persisting). These helpers treat the stored components as UTC and convert
 * to Europe/Madrid for display, grouping, and sorting.
 */

/**
 * Parse an ISO-like datetime string from the backend (e.g. "2026-07-13T05:13:00")
 * as a UTC instant and return a Date. Combined with {@link formatMadridDate},
 * this produces the correct Europe/Madrid wall-clock display.
 */
export function parseMadridDate(dateTime: string): Date {
  // The backend stores UTC-equivalent instants in LocalDateTime columns
  // (e.g. Endesa API "07:13 CEST" → stored as "05:13:00"). We treat the
  // stored components as UTC so formatMadridDate can correctly convert to
  // Europe/Madrid wall-clock for display.
  const utcString = dateTime.endsWith('Z') || dateTime.includes('+') ? dateTime : `${dateTime}Z`;
  return new Date(utcString);
}

/**
 * Return the ISO calendar date key (yyyy-MM-dd) for the given datetime string
 * interpreted in Europe/Madrid.
 */
export function toMadridDateKey(dateTime: string): string {
  return formatMadridDate(parseMadridDate(dateTime), 'yyyy-MM-dd');
}

/**
 * Format a Date (built with parseMadridDate) using Europe/Madrid calendar
 * components. Supported pattern tokens: yyyy, MM, dd.
 */
export function formatMadridDate(date: Date, pattern: string): string {
  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Europe/Madrid',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(date);
  const get = (type: string) => parts.find(p => p.type === type)?.value ?? '';
  return pattern.replace('yyyy', get('year')).replace('MM', get('month')).replace('dd', get('day'));
}
