import { Location } from '@angular/common';
import { Component, computed, inject, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { ApiOutageService, type EnelOutage } from '../../core/services/api-outage.service';
import { FIRST_DATA_YEAR, type DateFilterValue } from '../monthly-section/date-filter/date-filter.component';
import { HeroComponent } from '../hero/hero.component';
import { ContextSectionComponent } from '../context/context-section.component';
import { VideoCarouselComponent } from '../testimonials/video-carousel/video-carousel.component';
import { FooterComponent } from '../footer/footer.component';
import { LiveSectionComponent, type LiveGroup } from '../live/live-section.component';
import { ChartSectionComponent } from '../chart-section/chart-section.component';
import { MonthlySectionComponent } from '../monthly-section/monthly-section.component';
import { DonationSectionComponent } from '../donation-section/donation-section';
import { MethodologySectionComponent } from '../methodology/methodology-section.component';
import { parseMadridDate } from '../../core/utils/madrid-date';
import { outageCategory } from '../../core/utils/outage-category';

@Component({
  selector: 'app-home',
  imports: [HeroComponent, ContextSectionComponent, VideoCarouselComponent, FooterComponent,
            DonationSectionComponent, LiveSectionComponent, ChartSectionComponent, MonthlySectionComponent,
            MethodologySectionComponent],
  templateUrl: './home.component.html',
  styleUrl: './home.component.css',
})
export class HomeComponent implements OnInit {
  readonly api = inject(ApiOutageService);
  private readonly route = inject(ActivatedRoute);
  private readonly location = inject(Location);

  protected readonly districts = this.api.derivedDistricts;
  protected readonly yearlyOutages = this.api.deduplicatedYearlyOutages;
  protected readonly monthlyOutages = this.api.deduplicatedMonthlyOutages;
  protected readonly liveOutages = this.api.deduplicatedLiveOutages;

  protected readonly selectedMonth = this.api.selectedMonth;
  protected readonly selectedYear = this.api.selectedYear;

  protected readonly liveGroups = computed(() => {
    const groups = new Map<string, EnelOutage[]>();
    for (const outage of this.liveOutages()) {
      const key = outage.districtName ?? 'Zona no identificada';
      const list = groups.get(key) ?? [];
      list.push(outage);
      groups.set(key, list);
    }
    return [...groups.entries()].map(([districtName, outages]) => {
      const earliest = outages.reduce((a, b) =>
        parseMadridDate(a.interruptionDate).getTime() < parseMadridDate(b.interruptionDate).getTime() ? a : b
      );
      const latest = outages
        .filter(o => o.repositionDate)
        .reduce((a, b) =>
          parseMadridDate(a.repositionDate).getTime() > parseMadridDate(b.repositionDate).getTime() ? a : b,
          outages.find(o => o.repositionDate) ?? outages[0]
        );

      return {
        districtName,
        count: outages.length,
        affectedClients: outages.reduce((sum, o) => sum + o.affectedClients, 0),
        serviceCategories: [...new Set(outages.map(o => outageCategory(o)))],
        earliestDate: parseMadridDate(earliest.interruptionDate),
        latestDate: latest?.repositionDate ? parseMadridDate(latest.repositionDate) : null,
      } as LiveGroup;
    }).sort((a, b) => b.affectedClients - a.affectedClients);
  });

  ngOnInit(): void {
    const urlFilter = this.readUrlFilter();
    if (urlFilter) {
      this.api.setMonthFilter(urlFilter.year, urlFilter.month);
      this.api.loadYearlyOutages();
      this.api.loadLiveOutages();
    } else {
      this.api.loadAll();
    }
  }

  protected onFilterChange(value: DateFilterValue): void {
    this.api.setMonthFilter(value.year, value.month);
    this.updateUrlFilter(value.year, value.month);
  }

  /** Read `?anio=YYYY&mes=M` from the URL, validating ranges. Invalid/missing values are ignored. */
  private readUrlFilter(): DateFilterValue | null {
    const params = this.route.snapshot.queryParamMap;
    const year = Number(params.get('anio'));
    const month = Number(params.get('mes'));
    const currentYear = new Date().getFullYear();

    const validYear = Number.isInteger(year) && year >= FIRST_DATA_YEAR && year <= currentYear;
    const validMonth = Number.isInteger(month) && month >= 1 && month <= 12;

    return validYear && validMonth ? { year, month } : null;
  }

  /** Reflect the current filter in the URL without pushing a history entry or scrolling. */
  private updateUrlFilter(year: number, month: number): void {
    const search = new URLSearchParams(globalThis.location?.search ?? '');
    search.set('anio', String(year));
    search.set('mes', String(month));
    const path = globalThis.location?.pathname ?? '/';
    const hash = globalThis.location?.hash ?? '';
    this.location.replaceState(`${path}?${search.toString()}${hash}`);
  }
}
