/**
 * Returns the singular or plural form of a word based on `count`.
 * Zero uses the plural form (e.g. "0 cortes", not "0 corte").
 */
export function pluralize(count: number, singular: string, plural: string = `${singular}s`): string {
  return count === 1 ? singular : plural;
}
