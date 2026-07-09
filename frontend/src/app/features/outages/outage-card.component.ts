import { Component, computed, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';

import { OUTAGE_SERVICE } from '../../core/services/outage.service';
import type { Neighborhood } from '../../core/models';

@Component({
  selector: 'app-outage-card',
  imports: [DatePipe],
  templateUrl: './outage-card.component.html',
  styleUrl: './outage-card.component.css',
})
export class OutageCardComponent {
  private readonly outageService = inject(OUTAGE_SERVICE);

  readonly neighborhood = input.required<Neighborhood>();
  readonly isFilterActive = input<boolean>(false);
  protected readonly expanded = signal(false);

  protected readonly outages = computed(() =>
    this.outageService.getOutagesForNeighborhood(this.neighborhood().id)()
  );

  protected readonly average = computed(() =>
    this.outageService.getAverageOutage(this.neighborhood().id)()
  );

  protected readonly count = computed(() => this.outages().length);

  toggleExpanded(): void {
    this.expanded.update((value) => !value);
  }
}
