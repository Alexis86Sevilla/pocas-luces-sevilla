import { Component, inject } from '@angular/core';

import { OUTAGE_SERVICE } from '../../core/services/outage.service';
import { DateFilterComponent, type DateFilterValue } from '../outages/date-filter.component';
import { OutageCardComponent } from '../outages/outage-card.component';
import { OutageChartComponent } from '../outages/outage-chart.component';
import { HeroComponent } from '../hero/hero.component';
import { VideoCarouselComponent } from '../testimonials/video-carousel.component';
import { FooterComponent } from '../footer/footer.component';

@Component({
  selector: 'app-home',
  imports: [
    HeroComponent,
    DateFilterComponent,
    OutageChartComponent,
    OutageCardComponent,
    VideoCarouselComponent,
    FooterComponent,
  ],
  templateUrl: './home.component.html',
  styleUrl: './home.component.css',
})
export class HomeComponent {
  private readonly outageService = inject(OUTAGE_SERVICE);

  protected readonly neighborhoods = this.outageService.neighborhoods;
  protected readonly outages = this.outageService.outages;
  protected readonly filteredOutages = this.outageService.filteredOutages;
  protected readonly videoTestimonials = this.outageService.videoTestimonials;
  protected readonly selectedMonth = this.outageService.selectedMonth;
  protected readonly selectedYear = this.outageService.selectedYear;

  protected onFilterChange(value: DateFilterValue): void {
    this.outageService.setMonthFilter(value.year, value.month);
  }
}
