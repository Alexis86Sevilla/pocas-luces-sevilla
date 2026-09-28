import type { EnelOutage } from '../services/api-outage.service';
import { parseMadridDate } from './madrid-date';

/**
 * The observed duration of an outage in minutes, or null when it is not resolved yet
 * (still ongoing) or the timestamps don't make sense (resolvedAt not after the start).
 *
 * This is the real, measured duration: resolvedAt is when the outage was last seen
 * active before it stopped being reported, accurate to within one polling interval
 * (~5 minutes) — see EnelOutage.resolvedAt.
 */
export function realDurationMinutes(outage: EnelOutage): number | null {
  if (!outage.resolvedAt) return null;
  const start = parseMadridDate(outage.interruptionDate).getTime();
  const end = parseMadridDate(outage.resolvedAt).getTime();
  if (!start || !end || end <= start) return null;
  return (end - start) / 60_000;
}

/**
 * Endesa's own *estimated* restoration time minus the start, in minutes, or null when
 * unavailable. This is not a measured duration: repositionDate is the distributor's own
 * estimate and can change between polls (more so while the outage is still ongoing).
 */
export function estimatedDurationMinutes(outage: EnelOutage): number | null {
  const start = parseMadridDate(outage.interruptionDate).getTime();
  const end = outage.repositionDate ? parseMadridDate(outage.repositionDate).getTime() : 0;
  if (!start || !end || end <= start) return null;
  return (end - start) / 60_000;
}
