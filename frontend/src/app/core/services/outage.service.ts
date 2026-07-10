import { computed, InjectionToken, signal, type Signal } from '@angular/core';

import type { Neighborhood, OutageEvent, VideoTestimonial } from '../models';
import type { IOutageService } from './outage.service.interface';
import { MOCK_NEIGHBORHOODS, MOCK_OUTAGES, MOCK_VIDEO_TESTIMONIALS } from './outage.mock-data';

export class MockOutageService implements IOutageService {
  public readonly neighborhoods = signal<readonly Neighborhood[]>(MOCK_NEIGHBORHOODS).asReadonly();
  public readonly outages = signal<readonly OutageEvent[]>(MOCK_OUTAGES).asReadonly();
  public readonly videoTestimonials = signal<readonly VideoTestimonial[]>(MOCK_VIDEO_TESTIMONIALS).asReadonly();

  private readonly _selectedYear = signal<number>(new Date().getFullYear());
  private readonly _selectedMonth = signal<number>(new Date().getMonth() + 1);

  public readonly selectedYear = this._selectedYear.asReadonly();
  public readonly selectedMonth = this._selectedMonth.asReadonly();

  public readonly filteredOutages = computed(() => {
    const year = this._selectedYear();
    const month = this._selectedMonth();
    return this.outages().filter((outage) => {
      const date = outage.date;
      return date.getFullYear() === year && date.getMonth() + 1 === month;
    });
  });

  public setMonthFilter(year: number, month: number): void {
    this._selectedYear.set(year);
    this._selectedMonth.set(month);
  }

  public getOutagesForMonth(): Signal<readonly OutageEvent[]> {
    return this.filteredOutages;
  }

  public filteredNeighborhoods(): Signal<readonly Neighborhood[]> {
    return computed(() => {
      const activeIds = new Set(this.filteredOutages().map((outage) => outage.neighborhoodId));
      return this.neighborhoods().filter((neighborhood) => activeIds.has(neighborhood.id));
    });
  }

  public getOutagesForNeighborhood(id: string) {
    return computed(() => this.filteredOutages().filter((outage) => outage.neighborhoodId === id));
  }

  public getAverageOutage(neighborhoodId: string) {
    return computed(() => {
      const events = this.filteredOutages().filter((outage) => outage.neighborhoodId === neighborhoodId);

      if (events.length === 0) {
        return 0;
      }

      const totalMinutes = events.reduce((sum, outage) => sum + outage.durationMinutes, 0);

      return Math.round(totalMinutes / events.length);
    });
  }
}

// Kept for backward compatibility — now points to ApiOutageService
export const OUTAGE_SERVICE = new InjectionToken<IOutageService>('OUTAGE_SERVICE');
