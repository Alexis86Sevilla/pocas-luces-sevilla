import type { Signal } from '@angular/core';

import type { Neighborhood, OutageEvent, VideoTestimonial } from '../models';

export interface IOutageService {
  readonly neighborhoods: Signal<readonly Neighborhood[]>;
  readonly outages: Signal<readonly OutageEvent[]>;
  readonly videoTestimonials: Signal<readonly VideoTestimonial[]>;
  readonly selectedMonth: Signal<number>;
  readonly selectedYear: Signal<number>;
  readonly filteredOutages: Signal<readonly OutageEvent[]>;

  getOutagesForNeighborhood(id: string): Signal<readonly OutageEvent[]>;
  getAverageOutage(neighborhoodId: string): Signal<number>;
  getOutagesForMonth(): Signal<readonly OutageEvent[]>;
  filteredNeighborhoods(): Signal<readonly Neighborhood[]>;
  setMonthFilter(year: number, month: number): void;
}
