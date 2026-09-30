/**
 * Helpers for interpreting backend LocalDateTime strings as Europe/Madrid
 * wall-clock time.
 *
 * IMPORTANT: The backend stores Europe/Madrid LOCAL wall-clock time in
 * LocalDateTime columns (no timezone conversion is applied — e.g. Endesa's
 * "28/09/2026 15:38" CEST is persisted verbatim as "2026-09-28T15:38:00").
 * These helpers interpret the stored components as Europe/Madrid wall-clock
 * time and convert them to the correct UTC instant, accounting for the
 * CET/CEST (winter/summer) offset — including the DST transition dates.
 */

const MADRID_TIME_ZONE = 'Europe/Madrid';

/**
 * Compute the UTC offset (in minutes, positive = ahead of UTC) that
 * `timeZone` observes at the given UTC instant.
 */
// Intl.DateTimeFormat construction is expensive; build each formatter once and reuse it.
const offsetFormatters = new Map<string, Intl.DateTimeFormat>();

function offsetFormatter(timeZone: string): Intl.DateTimeFormat {
  let formatter = offsetFormatters.get(timeZone);
  if (!formatter) {
    formatter = new Intl.DateTimeFormat('en-US', {
      timeZone,
      hourCycle: 'h23',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    });
    offsetFormatters.set(timeZone, formatter);
  }
  return formatter;
}

function timeZoneOffsetMinutes(instant: Date, timeZone: string): number {
  const parts = offsetFormatter(timeZone).formatToParts(instant);
  const get = (type: string) => Number(parts.find(p => p.type === type)?.value ?? 0);

  const asUtc = Date.UTC(
    get('year'), get('month') - 1, get('day'),
    get('hour'), get('minute'), get('second'),
  );
  return (asUtc - instant.getTime()) / 60_000;
}

/**
 * Convert wall-clock date/time components interpreted in `timeZone` into the
 * corresponding UTC instant. Uses the standard two-pass approach: guess the
 * offset from a first estimate, then re-derive it from the resulting instant
 * to stay correct across DST transitions.
 */
function zonedWallClockToUtc(
  year: number, monthIndex: number, day: number,
  hour: number, minute: number, second: number,
  timeZone: string,
): Date {
  const naiveUtcGuess = Date.UTC(year, monthIndex, day, hour, minute, second);
  const offset1 = timeZoneOffsetMinutes(new Date(naiveUtcGuess), timeZone);
  const offset2 = timeZoneOffsetMinutes(new Date(naiveUtcGuess - offset1 * 60_000), timeZone);
  return new Date(naiveUtcGuess - offset2 * 60_000);
}

const HAS_TIMEZONE = /(Z|[+-]\d{2}:?\d{2})$/;
const WALL_CLOCK_PATTERN = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?/;

/**
 * Parse an ISO-like datetime string from the backend (e.g. "2026-09-28T15:38:00")
 * as Europe/Madrid wall-clock time and return the equivalent UTC instant as a
 * Date. Strings that already carry a timezone (trailing "Z" or a numeric
 * offset) are parsed as-is.
 */
export function parseMadridDate(dateTime: string): Date {
  if (HAS_TIMEZONE.test(dateTime)) {
    return new Date(dateTime);
  }

  const match = WALL_CLOCK_PATTERN.exec(dateTime);
  if (!match) return new Date(dateTime);

  const [, year, month, day, hour, minute, second] = match;
  return zonedWallClockToUtc(
    Number(year), Number(month) - 1, Number(day),
    Number(hour), Number(minute), second ? Number(second) : 0,
    MADRID_TIME_ZONE,
  );
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
 * components. Supported pattern tokens: yyyy, MM, dd, HH, mm.
 */
const MADRID_DISPLAY_FORMATTER = new Intl.DateTimeFormat('en-GB', {
  timeZone: MADRID_TIME_ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

export function formatMadridDate(date: Date, pattern: string): string {
  const parts = MADRID_DISPLAY_FORMATTER.formatToParts(date);
  const get = (type: string) => parts.find(p => p.type === type)?.value ?? '';
  return pattern
    .replace('yyyy', get('year'))
    .replace('MM', get('month'))
    .replace('dd', get('day'))
    .replace('HH', get('hour'))
    .replace('mm', get('minute'));
}
