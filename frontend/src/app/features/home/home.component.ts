import { Component, computed, inject, OnInit } from '@angular/core';
import { DatePipe } from '@angular/common';

import { ApiOutageService, type EnelOutage } from '../../core/services/api-outage.service';
import { DateFilterComponent, type DateFilterValue } from '../outages/date-filter.component';
import { OutageCardComponent } from '../outages/outage-card.component';
import { OutageChartComponent } from '../outages/outage-chart.component';
import { HeroComponent } from '../hero/hero.component';
import { ContextSectionComponent } from '../context/context-section.component';
import { VideoCarouselComponent } from '../testimonials/video-carousel.component';
import { FooterComponent } from '../footer/footer.component';
import { LiveSectionComponent, type LiveGroup } from '../live/live-section.component';
import { parseMadridDate } from '../../core/utils/madrid-date';
import { DonationSectionComponent } from '../donation-section/donation-section';

@Component({
  selector: 'app-home',
  imports: [HeroComponent, ContextSectionComponent, DateFilterComponent, OutageChartComponent,
            OutageCardComponent, VideoCarouselComponent, FooterComponent, DonationSectionComponent,
            LiveSectionComponent, DatePipe],
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
      // Find the actual outage with the earliest start (for raw string display).
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

  // Memoized selector: map neighborhood id -> outages for the current month.
  protected readonly monthlyOutagesByNeighborhoodId = computed(() => {
    const map = new Map<string, EnelOutage[]>();
    for (const outage of this.monthlyOutages()) {
      const name = outage.neighborhoodName ?? 'Zona no identificada';
      const id = this.neighborhoodId(name);
      const list = map.get(id) ?? [];
      list.push(outage);
      map.set(id, list);
    }
    return map;
  });

  protected monthlyOutagesForNeighborhood(id: string): EnelOutage[] {
    return this.monthlyOutagesByNeighborhoodId().get(id) ?? [];
  }

  private neighborhoodId(name: string): string {
    return name
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '');
  }

  ngOnInit(): void {
    this.api.loadAll();
  }

  protected onFilterChange(value: DateFilterValue): void {
    this.api.setMonthFilter(value.year, value.month);
  }
}
