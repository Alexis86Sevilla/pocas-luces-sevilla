import { Component, computed, inject, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DateFilterComponent, type DateFilterValue } from './date-filter/date-filter.component';
import { OutageCardComponent } from './outage-card/outage-card.component';
import { OpenDataDownloadComponent } from '../open-data-download/open-data-download.component';
import { ShareButtonComponent } from '../share-button/share-button.component';
import type { District } from '../../core/models';
import { ApiOutageService, type EnelOutage } from '../../core/services/api-outage.service';

@Component({
  selector: 'app-monthly-section',
  imports: [RouterLink, DateFilterComponent, OutageCardComponent, ShareButtonComponent, OpenDataDownloadComponent],
  templateUrl: './monthly-section.component.html',
})
export class MonthlySectionComponent {
  readonly districts = input.required<readonly District[]>();
  readonly monthlyOutages = input.required<readonly EnelOutage[]>();
  readonly selectedMonth = input.required<number>();
  readonly selectedYear = input.required<number>();

  readonly filterChange = output<DateFilterValue>();

  protected readonly api = inject(ApiOutageService);

  /** Placeholder rows for the loading skeleton (count only, values are unused). */
  protected readonly skeletonRows = [0, 1, 2, 3] as const;

  private readonly byDistrict = computed(() => {
    const map = new Map<string, EnelOutage[]>();
    for (const o of this.monthlyOutages()) {
      const id = this.districtId(o.districtName ?? 'Zona no identificada');
      const list = map.get(id) ?? [];
      list.push(o);
      map.set(id, list);
    }
    return map;
  });

  protected readonly isEmpty = computed(() => this.monthlyOutages().length === 0);

  protected outagesFor(id: string): EnelOutage[] {
    return this.byDistrict().get(id) ?? [];
  }

  protected retry(): void {
    this.api.loadMonthlyOutages(this.selectedYear(), this.selectedMonth());
  }

  private districtId(name: string): string {
    return name
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '');
  }
}
