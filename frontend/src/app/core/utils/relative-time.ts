/**
 * Pure helpers for rendering "how long ago" labels in Spanish, used to show
 * data freshness (e.g. "Actualizado hace 3 min"). Kept timezone-agnostic:
 * callers pass already-parsed Date instants (see madrid-date.ts).
 */

/**
 * Format the elapsed time between `target` and `now` as a short Spanish
 * relative-time label: "hace menos de 1 min", "hace X min", or "hace X h".
 * Negative elapsed time (clock skew) is clamped to zero.
 */
export function formatRelativeTime(target: Date, now: Date = new Date()): string {
  const diffMinutes = Math.max(0, Math.floor((now.getTime() - target.getTime()) / 60_000));

  if (diffMinutes < 1) return 'hace menos de 1 min';
  if (diffMinutes < 60) return `hace ${diffMinutes} min`;

  const diffHours = Math.floor(diffMinutes / 60);
  return `hace ${diffHours} h`;
}

/** Whether `target` is old enough that the data should be flagged as possibly stale. */
export function isStale(target: Date, now: Date = new Date(), thresholdMinutes = 30): boolean {
  return (now.getTime() - target.getTime()) / 60_000 > thresholdMinutes;
}
