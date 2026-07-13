import { Component, computed, inject, OnInit } from '@angular/core';

import { ApiOutageService, type EnelOutage } from '../../core/services/api-outage.service';
import { type DateFilterValue } from '../monthly-section/date-filter/date-filter.component';
import { HeroComponent } from '../hero/hero.component';
import { ContextSectionComponent } from '../context/context-section.component';
import { VideoCarouselComponent } from '../testimonials/video-carousel/video-carousel.component';
import { FooterComponent } from '../footer/footer.component';
import { LiveSectionComponent, type LiveGroup } from '../live/live-section.component';
import { ChartSectionComponent } from '../chart-section/chart-section.component';
import { MonthlySectionComponent } from '../monthly-section/monthly-section.component';
import { DonationSectionComponent } from '../donation-section/donation-section';
import { parseMadridDate } from '../../core/utils/madrid-date';

@Component({
  selector: 'app-home',
  imports: [HeroComponent, ContextSectionComponent, VideoCarouselComponent, FooterComponent,
            DonationSectionComponent, LiveSectionComponent, ChartSectionComponent, MonthlySectionComponent],
  templateUrl: './home.component.html',
  styleUrl: './home.component.css',
})
export class HomeComponent implements OnInit {
  readonly api = inject(ApiOutageService);

  protected readonly neighborhoods = this.api.derivedNeighborhoods;
  protected readonly yearlyOutages = this.api.deduplicatedYearlyOutages;
  protected readonly monthlyOutages = this.api.deduplicatedMonthlyOutages;
  protected readonly liveOutages = this.api.deduplicatedLiveOutages;

  protected readonly selectedMonth = this.api.selectedMonth;
  protected readonly selectedYear = this.api.selectedYear;

  protected readonly liveGroups = computed(() => {
    const groups = new Map<string, EnelOutage[]>();
    for (const outage of this.liveOutages()) {
      const key = outage.neighborhoodName ?? 'Zona no identificada';
      const list = groups.get(key) ?? [];
      list.push(outage);
      groups.set(key, list);
    }
    return [...groups.entries()].map(([neighborhoodName, outages]) => {
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
        neighborhoodName,
        count: outages.length,
        affectedClients: outages.reduce((sum, o) => sum + o.affectedClients, 0),
        serviceCategories: [...new Set(outages.map(o => o.serviceType === 'LV' ? 'Programado' : 'Avería'))],
        earliestDateStr: earliest.interruptionDate,
        latestDateStr: latest?.repositionDate ?? '',
      } as LiveGroup;
    }).sort((a, b) => b.affectedClients - a.affectedClients);
  });

  ngOnInit(): void {
    this.api.loadAll();
  }

  protected onFilterChange(value: DateFilterValue): void {
    this.api.setMonthFilter(value.year, value.month);
  }
}
