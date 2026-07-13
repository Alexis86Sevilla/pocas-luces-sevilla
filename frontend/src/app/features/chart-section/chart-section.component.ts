import { Component, input } from '@angular/core';
import { OutageChartComponent } from '../outages/outage-chart.component';
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
}
