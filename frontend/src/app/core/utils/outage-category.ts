export type OutageCategory = 'Avería' | 'Programado';

/**
 * Classifies an outage using Endesa's own cause field (`des_cause_es`) when present.
 * Falls back to the service-type heuristic (LV = scheduled works) for records
 * stored before the backend exposed the cause.
 */
export function outageCategory(outage: { cause?: string | null; serviceType: string }): OutageCategory {
  if (outage.cause === 'Trabajos programados') return 'Programado';
  if (outage.cause === 'Avería') return 'Avería';
  return outage.serviceType === 'LV' ? 'Programado' : 'Avería';
}
