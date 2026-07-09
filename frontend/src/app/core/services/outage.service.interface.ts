import type { Signal } from '@angular/core';

import type { Neighborhood, OutageEvent, Testimonial } from '../models';

export interface IOutageService {
  readonly neighborhoods: Signal<readonly Neighborhood[]>;
  readonly testimonials: Signal<readonly Testimonial[]>;
  readonly outages: Signal<readonly OutageEvent[]>;

  getOutagesForNeighborhood(id: string): Signal<readonly OutageEvent[]>;
  getAverageOutage(neighborhoodId: string): Signal<number>;
}
