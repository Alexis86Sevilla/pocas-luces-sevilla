import { Component, computed, input, output } from '@angular/core';
import { DateFilterComponent, type DateFilterValue } from './date-filter/date-filter.component';
import { OutageCardComponent } from './outage-card/outage-card.component';
import type { Neighborhood } from '../../core/models';
import type { EnelOutage } from '../../core/services/api-outage.service';

@Component({
  selector: 'app-monthly-section',
  imports: [DateFilterComponent, OutageCardComponent],
  templateUrl: './monthly-section.component.html',
})
export class MonthlySectionComponent {
  readonly neighborhoods = input.required<readonly Neighborhood[]>();
  readonly monthlyOutages = input.required<readonly EnelOutage[]>();
  readonly selectedMonth = input.required<number>();
  readonly selectedYear = input.required<number>();

  readonly filterChange = output<DateFilterValue>();

  private readonly byNeighborhood = computed(() => {
    const map = new Map<string, EnelOutage[]>();
    for (const o of this.monthlyOutages()) {
      const id = this.neighborhoodId(o.neighborhoodName ?? 'Zona no identificada');
      const list = map.get(id) ?? [];
      list.push(o);
      map.set(id, list);
    }
    return map;
  });

  protected readonly isEmpty = computed(() => this.monthlyOutages().length === 0);

  protected outagesFor(id: string): EnelOutage[] {
    return this.byNeighborhood().get(id) ?? [];
  }

  private neighborhoodId(name: string): string {
    return name
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '');
  }
}
