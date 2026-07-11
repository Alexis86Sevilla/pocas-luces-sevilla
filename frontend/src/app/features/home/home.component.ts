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
import type { VideoTestimonial } from '../../core/models';
import { parseMadridDate } from '../../core/utils/madrid-date';

export interface LiveGroup {
  readonly neighborhoodName: string;
  readonly count: number;
  readonly affectedClients: number;
  readonly serviceCategories: readonly string[];
  readonly earliestStart: Date;
  readonly latestEnd: Date;
}

@Component({
  selector: 'app-home',
  imports: [HeroComponent, ContextSectionComponent, DateFilterComponent, OutageChartComponent,
            OutageCardComponent, VideoCarouselComponent, FooterComponent, DatePipe],
  templateUrl: './home.component.html',
  styleUrl: './home.component.css',
})
export class HomeComponent implements OnInit {
  readonly api = inject(ApiOutageService);

  protected readonly neighborhoods = this.api.derivedNeighborhoods;
  protected readonly yearlyOutages = this.api.deduplicatedYearlyOutages;
  protected readonly monthlyOutages = this.api.deduplicatedMonthlyOutages;
  protected readonly liveOutages = this.api.deduplicatedLiveOutages;
  protected readonly videoTestimonials: readonly VideoTestimonial[] = [
    {
      id: 'ig-reel-DafWrbQNbXH',
      authorName: 'Reel de vecino/a (Instagram)',
      embedUrl: 'https://www.instagram.com/reel/DafWrbQNbXH/',
      platform: 'instagram',
    },
    {
      id: 'yt-barrios-hartos-2025',
      authorName: 'Manifestación Barrios Hartos — 27 jun 2025',
      embedUrl: 'https://www.youtube.com/embed/c64M0NsCAns',
      platform: 'youtube',
    },
    {
      id: 'yt-nueva-protesta',
      authorName: 'Nueva protesta contra los apagones — Barrios Hartos',
      embedUrl: 'https://www.youtube.com/embed/07zNGlQJM0w',
      platform: 'youtube',
    },
    {
      id: 'yt-extra-1',
      authorName: 'Trailer documental: A Dos Velas',
      embedUrl: 'https://www.youtube.com/embed/-LMI8thfU-0',
      platform: 'youtube',
    },
    {
      id: 'yt-extra-2',
      authorName: 'Vecinos afectados por cortes de luz',
      embedUrl: 'https://www.youtube.com/embed/A7JM8Oh8fhc',
      platform: 'youtube',
    },
  ];

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
      const starts = outages.map(o => parseMadridDate(o.interruptionDate).getTime()).filter(t => !isNaN(t));
      const ends = outages
        .filter(o => o.repositionDate)
        .map(o => parseMadridDate(o.repositionDate).getTime())
        .filter(t => !isNaN(t));
      return {
        neighborhoodName,
        count: outages.length,
        affectedClients: outages.reduce((sum, o) => sum + o.affectedClients, 0),
        serviceCategories: [...new Set(outages.map(o => o.serviceType === 'LV' ? 'Programado' : 'Avería'))],
        earliestStart: starts.length > 0 ? new Date(Math.min(...starts)) : new Date(),
        latestEnd: ends.length > 0 ? new Date(Math.max(...ends)) : new Date(),
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
