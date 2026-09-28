import { Component, inject, input } from '@angular/core';
import { ChartComponent } from './chart/chart.component';
import { ShareButtonComponent } from '../share-button/share-button.component';
import type { District } from '../../core/models';
import { ApiOutageService, type EnelOutage } from '../../core/services/api-outage.service';

@Component({
  selector: 'app-chart-section',
  imports: [ChartComponent, ShareButtonComponent],
  templateUrl: './chart-section.component.html',
})
export class ChartSectionComponent {
  readonly districts = input.required<readonly District[]>();
  readonly yearlyOutages = input.required<readonly EnelOutage[]>();

  protected readonly api = inject(ApiOutageService);

  protected scrollTo(sectionId: string): void {
    globalThis.document?.getElementById(sectionId)?.scrollIntoView({ behavior: 'smooth' });
  }
}
