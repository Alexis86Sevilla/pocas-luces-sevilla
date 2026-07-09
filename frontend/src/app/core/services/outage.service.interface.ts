import { InjectionToken, type Signal } from '@angular/core';

import { MockOutageService } from './outage.service';
import type { Neighborhood, OutageEvent, Testimonial } from '../models';

export interface IOutageService {
  readonly neighborhoods: Signal<readonly Neighborhood[]>;
  readonly testimonials: Signal<readonly Testimonial[]>;
  readonly outages: Signal<readonly OutageEvent[]>;

  getOutagesForNeighborhood(id: string): Signal<readonly OutageEvent[]>;
  getAverageOutage(neighborhoodId: string): Signal<number>;
}

export const OUTAGE_SERVICE: InjectionToken<IOutageService> = new InjectionToken<IOutageService>(
  'OUTAGE_SERVICE',
  {
    providedIn: 'root',
    factory: () => new MockOutageService(),
  }
);
