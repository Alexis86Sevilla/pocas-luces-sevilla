import { Component, input } from '@angular/core';
import { ChartComponent } from './chart/chart.component';
import type { District } from '../../core/models';
import type { EnelOutage } from '../../core/services/api-outage.service';

@Component({
  selector: 'app-chart-section',
  imports: [ChartComponent],
  templateUrl: './chart-section.component.html',
})
export class ChartSectionComponent {
  readonly districts = input.required<readonly District[]>();
  readonly yearlyOutages = input.required<readonly EnelOutage[]>();

  protected scrollTo(sectionId: string): void {
    globalThis.document?.getElementById(sectionId)?.scrollIntoView({ behavior: 'smooth' });
  }
}
