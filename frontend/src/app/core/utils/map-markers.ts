import type { EnelOutage } from '../services/api-outage.service';
import { formatMadridDate, parseMadridDate } from './madrid-date';
import { outageCategory, type OutageCategory } from './outage-category';

export interface OutageMarker {
  readonly id: string;
  readonly lat: number;
  readonly lng: number;
  readonly neighborhood: string;
  readonly district: string;
  /** dd/MM HH:mm, Europe/Madrid. */
  readonly startLabel: string;
  /** Observed end (dd/MM HH:mm) or null while the outage is ongoing. */
  readonly endLabel: string | null;
  readonly ongoing: boolean;
  readonly affectedClients: number;
  readonly category: OutageCategory;
}

/** Generous box around Seville: rejects obviously wrong coordinates (0/0, swapped, other cities). */
const SEVILLE_BOUNDS = { minLat: 36.9, maxLat: 37.7, minLng: -6.3, maxLng: -5.6 };

function hasValidPosition(o: EnelOutage): boolean {
  const { latitude: lat, longitude: lng } = o;
  if (lat == null || lng == null || !Number.isFinite(lat) || !Number.isFinite(lng)) return false;
  return (
    lat >= SEVILLE_BOUNDS.minLat && lat <= SEVILLE_BOUNDS.maxLat &&
    lng >= SEVILLE_BOUNDS.minLng && lng <= SEVILLE_BOUNDS.maxLng
  );
}

/** Converts outages to marker view-models, skipping those without a usable position. */
export function toOutageMarkers(outages: readonly EnelOutage[]): OutageMarker[] {
  return outages.filter(hasValidPosition).map(o => ({
    id: `${o.latitude},${o.longitude}|${o.interruptionDate}|${o.serviceType}`,
    lat: o.latitude as number,
    lng: o.longitude as number,
    neighborhood: o.neighborhoodName?.trim() || 'Sin identificar',
    district: o.districtName?.trim() || 'Sin identificar',
    startLabel: formatMadridDate(parseMadridDate(o.interruptionDate), 'dd/MM HH:mm'),
    endLabel: o.resolvedAt ? formatMadridDate(parseMadridDate(o.resolvedAt), 'dd/MM HH:mm') : null,
    ongoing: !o.resolvedAt,
    affectedClients: o.affectedClients ?? 0,
    category: outageCategory(o),
  }));
}
