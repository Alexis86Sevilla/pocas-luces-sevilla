/**
 * Helpers for interpreting backend LocalDateTime strings as Europe/Madrid
 * wall-clock time. The server stores outage timestamps in Europe/Madrid, so
 * the UI must group/sort/display them using that timezone regardless of the
 * browser's locale.
 */

/**
 * Parse an ISO-like datetime string (e.g. "2026-07-10T08:30:00") as
 * Europe/Madrid wall-clock time and return a Date whose UTC components equal
 * that wall-clock value. This makes Date.getHours/getDate return the Madrid
 * values without being shifted by the browser timezone.
 */
export function parseMadridDate(dateTime: string): Date {
  const [datePart, timePart = '00:00:00'] = dateTime.split('T');
  const [year, month, day] = datePart.split('-').map(Number);
  const [hour, minute, secondRaw = '0'] = timePart.split(':');
  const second = Number(secondRaw.split('.')[0]);
  // Interpret the Madrid wall-clock components as a local Date so that
  // DatePipe (which formats in the browser's local timezone) shows the
  // same Madrid time regardless of the user's system timezone.
  return new Date(year, month - 1, day, Number(hour), Number(minute), second);
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
