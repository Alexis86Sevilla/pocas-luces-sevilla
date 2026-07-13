import { Component, input } from '@angular/core';
import { OutageChartComponent } from './outage-chart/outage-chart.component';
import type { Neighborhood } from '../../core/models';
import type { EnelOutage } from '../../core/services/api-outage.service';

@Component({
  selector: 'app-chart-section',
  imports: [OutageChartComponent],
  templateUrl: './chart-section.component.html',
})
export class ChartSectionComponent {
  readonly neighborhoods = input.required<readonly Neighborhood[]>();
  readonly yearlyOutages = input.required<readonly EnelOutage[]>();

  protected scrollTo(sectionId: string): void {
    globalThis.document?.getElementById(sectionId)?.scrollIntoView({ behavior: 'smooth' });
  }
}
