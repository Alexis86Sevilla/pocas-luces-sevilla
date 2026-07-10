import { Component, computed, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';

import type { EnelOutage } from '../../core/services/api-outage.service';
import type { Neighborhood } from '../../core/models';

@Component({
  selector: 'app-outage-card',
  imports: [DatePipe],
  templateUrl: './outage-card.component.html',
  styleUrl: './outage-card.component.css',
})
export class OutageCardComponent {
  readonly neighborhood = input.required<Neighborhood>();
  readonly outages = input.required<readonly EnelOutage[]>();

  protected readonly expanded = signal(false);

  protected readonly count = computed(() => this.outages().length);

  protected readonly totalAffected = computed(() =>
    this.outages().reduce((sum, o) => sum + o.affectedClients, 0)
  );

  protected readonly avgDuration = computed(() => {
    const list = this.outages();
    if (list.length === 0) return 0;
    let total = 0;
    let count = 0;
    for (const o of list) {
      const start = new Date(o.interruptionDate).getTime();
      const end = new Date(o.repositionDate).getTime();
      if (start && end && end > start) {
        total += (end - start) / 60000;
        count++;
      }
    }
    return count > 0 ? Math.round(total / count) : 0;
  });

  toggleExpanded(): void {
    this.expanded.update(v => !v);
  }
}
