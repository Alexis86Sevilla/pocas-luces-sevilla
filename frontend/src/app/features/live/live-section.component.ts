import { Component, inject, input } from '@angular/core';
import { DatePipe } from '@angular/common';

import { ApiOutageService } from '../../core/services/api-outage.service';

export interface LiveGroup {
  readonly neighborhoodName: string;
  readonly count: number;
  readonly affectedClients: number;
  readonly serviceCategories: readonly string[];
  readonly earliestDateStr: string;
  readonly latestDateStr: string;
}

@Component({
  selector: 'app-live-section',
  imports: [DatePipe],
  templateUrl: './live-section.component.html',
})
export class LiveSectionComponent {
  readonly liveGroups = input.required<readonly LiveGroup[]>();
  readonly api = inject(ApiOutageService);
}
