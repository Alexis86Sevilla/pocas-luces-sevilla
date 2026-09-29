import type { EnelOutage } from '../services/api-outage.service';
import { realDurationMinutes } from './outage-duration';

export const UNKNOWN_DISTRICT = 'Zona no identificada';

export interface DistrictRankingEntry {
  readonly districtName: string;
  /** Sum of the observed durations of resolved outages, in hours. Overlapping outages add up. */
  readonly accumulatedHours: number;
  /** All outages of the district in the period, including ongoing ones. */
  readonly outageCount: number;
  /** Sum of affected supply points over all outages (not people). */
  readonly affectedClients: number;
}

/**
 * Ranks districts by accumulated observed outage hours.
 *
 * - Hours: sum of realDurationMinutes of resolved outages / 60. Ongoing outages
 *   (no observed end) contribute no hours. Simultaneous outages add up.
 * - Outage count and supply points include every outage, ongoing or not.
 * - Sorted by hours desc, then outage count desc, then name (deterministic).
 */
export function rankDistricts(outages: readonly EnelOutage[]): DistrictRankingEntry[] {
  const byDistrict = new Map<string, { minutes: number; count: number; clients: number }>();
  for (const outage of outages) {
    const name = outage.districtName?.trim() || UNKNOWN_DISTRICT;
    const acc = byDistrict.get(name) ?? { minutes: 0, count: 0, clients: 0 };
    acc.minutes += realDurationMinutes(outage) ?? 0;
    acc.count += 1;
    acc.clients += outage.affectedClients ?? 0;
    byDistrict.set(name, acc);
  }
  return [...byDistrict.entries()]
    .map(([districtName, acc]) => ({
      districtName,
      accumulatedHours: acc.minutes / 60,
      outageCount: acc.count,
      affectedClients: acc.clients,
    }))
    .sort(
      (a, b) =>
        b.accumulatedHours - a.accumulatedHours ||
        b.outageCount - a.outageCount ||
        a.districtName.localeCompare(b.districtName, 'es'),
    );
}
