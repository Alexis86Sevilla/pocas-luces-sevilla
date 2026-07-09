import { computed, InjectionToken, signal } from '@angular/core';

import type { Neighborhood, OutageEvent, Testimonial } from '../models';
import type { IOutageService } from './outage.service.interface';
import { MOCK_NEIGHBORHOODS, MOCK_OUTAGES, MOCK_TESTIMONIALS } from './outage.mock-data';

export class MockOutageService implements IOutageService {
  public readonly neighborhoods = signal<readonly Neighborhood[]>(MOCK_NEIGHBORHOODS).asReadonly();
  public readonly testimonials = signal<readonly Testimonial[]>(MOCK_TESTIMONIALS).asReadonly();
  public readonly outages = signal<readonly OutageEvent[]>(MOCK_OUTAGES).asReadonly();

  public getOutagesForNeighborhood(id: string) {
    return computed(() => this.outages().filter((outage) => outage.neighborhoodId === id));
  }

  public getAverageOutage(neighborhoodId: string) {
    return computed(() => {
      const events = this.outages().filter((outage) => outage.neighborhoodId === neighborhoodId);

      if (events.length === 0) {
        return 0;
      }

      const totalMinutes = events.reduce((sum, outage) => sum + outage.durationMinutes, 0);

      return Math.round(totalMinutes / events.length);
    });
  }
}

export const OUTAGE_SERVICE = new InjectionToken<IOutageService>('OUTAGE_SERVICE', {
  providedIn: 'root',
  factory: () => new MockOutageService(),
});
