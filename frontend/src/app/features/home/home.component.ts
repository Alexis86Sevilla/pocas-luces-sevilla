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
      const starts = outages.map(o => new Date(o.interruptionDate).getTime()).filter(t => !isNaN(t));
      const ends = outages.map(o => new Date(o.repositionDate).getTime()).filter(t => !isNaN(t));
      return {
        neighborhoodName,
        count: outages.length,
        affectedClients: outages.reduce((sum, o) => sum + o.affectedClients, 0),
        serviceCategories: [...new Set(outages.map(o => o.serviceType === 'LV' ? 'Programado' : 'Avería'))],
        earliestStart: new Date(Math.min(...starts)),
        latestEnd: new Date(Math.max(...ends)),
      } as LiveGroup;
    }).sort((a, b) => b.affectedClients - a.affectedClients);
  });

  ngOnInit(): void {
    this.api.loadAll();
  }

  protected onFilterChange(value: DateFilterValue): void {
    this.api.setMonthFilter(value.year, value.month);
  }

  protected monthlyOutagesForNeighborhood(name: string) {
    return this.monthlyOutages().filter(o => o.neighborhoodName === name);
  }
}
