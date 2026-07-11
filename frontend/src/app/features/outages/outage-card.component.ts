import { Component, computed, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';

import type { EnelOutage } from '../../core/services/api-outage.service';
import type { Neighborhood } from '../../core/models';
import { formatMadridDate, parseMadridDate, toMadridDateKey } from '../../core/utils/madrid-date';

export interface DailyOutageGroup {
  readonly dateKey: string;
  readonly date: Date;
  readonly displayDate: string;
  readonly count: number;
  readonly totalAffected: number;
  readonly outages: readonly EnelOutage[];
}

@Component({
  selector: 'app-outage-card',
  imports: [DatePipe],
  templateUrl: './outage-card.component.html',
})
export class OutageCardComponent {
  readonly neighborhood = input.required<Neighborhood>();
  readonly outages = input.required<readonly EnelOutage[]>();

  protected readonly expanded = signal(false);
  protected readonly expandedDay = signal<string | null>(null);

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
      const start = parseMadridDate(o.interruptionDate).getTime();
      const end = o.repositionDate ? parseMadridDate(o.repositionDate).getTime() : 0;
      if (start && end && end > start) {
        total += (end - start) / 60000;
        count++;
      }
    }
    return count > 0 ? Math.round(total / count) : 0;
  });

  protected readonly dailyGroups = computed((): readonly DailyOutageGroup[] => {
    const groups = new Map<string, EnelOutage[]>();
    for (const outage of this.outages()) {
      const key = toMadridDateKey(outage.interruptionDate);
      const list = groups.get(key) ?? [];
      list.push(outage);
      groups.set(key, list);
    }

    return [...groups.entries()]
      .map(([dateKey, list]) => {
        const sorted = [...list].sort((a, b) =>
          parseMadridDate(a.interruptionDate).getTime() - parseMadridDate(b.interruptionDate).getTime()
        );
        const date = parseMadridDate(dateKey + 'T00:00:00');
        return {
          dateKey,
          date,
          displayDate: formatMadridDate(date, 'dd/MM'),
          count: sorted.length,
          totalAffected: sorted.reduce((sum, o) => sum + o.affectedClients, 0),
          outages: sorted,
        } as DailyOutageGroup;
      })
      .sort((a, b) => b.date.getTime() - a.date.getTime());
  });

  toggleExpanded(): void {
    this.expanded.update(v => !v);
  }

  toggleDay(dateKey: string): void {
    this.expandedDay.update(current => current === dateKey ? null : dateKey);
  }
}


