import { Component, computed, input, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';

import type { EnelOutage } from '../../../core/services/api-outage.service';
import type { District } from '../../../core/models';
import { formatMadridDate, parseMadridDate, toMadridDateKey } from '../../../core/utils/madrid-date';
import { outageCategory } from '../../../core/utils/outage-category';
import { pluralize } from '../../../core/utils/pluralize';
import { realDurationMinutes } from '../../../core/utils/outage-duration';

export interface DailyOutageGroup {
  readonly dateKey: string;
  readonly date: Date;
  readonly displayDate: string;
  readonly count: number;
  readonly totalAffected: number;
  readonly outages: readonly EnelOutage[];
}

const HISTORY_PAGE_SIZE = 7;

@Component({
  selector: 'app-outage-card',
  imports: [DatePipe, DecimalPipe],
  templateUrl: './outage-card.component.html',
})
export class OutageCardComponent {
  readonly district = input.required<District>();
  readonly outages = input.required<readonly EnelOutage[]>();

  protected readonly category = outageCategory;
  protected readonly pluralize = pluralize;
  protected readonly expanded = signal(false);
  protected readonly expandedDay = signal<string | null>(null);
  /** Number of most recent days shown in the history; "Ver más" reveals another page. */
  protected readonly visibleDayCount = signal(HISTORY_PAGE_SIZE);

  protected readonly count = computed(() => this.outages().length);

  protected readonly totalAffected = computed(() =>
    this.outages().reduce((sum, o) => sum + o.affectedClients, 0)
  );

  /**
   * Average REAL duration (resolvedAt - interruptionDate) of resolved outages only.
   * Ongoing outages have no resolvedAt yet and are excluded, so this never mixes in
   * Endesa's estimated restoration time. Null when no outage in the list is resolved.
   */
  protected readonly avgDuration = computed<number | null>(() => {
    const durations = this.outages()
      .map(o => realDurationMinutes(o))
      .filter((d): d is number => d !== null);
    if (durations.length === 0) return null;
    const total = durations.reduce((sum, d) => sum + d, 0);
    return Math.round(total / durations.length);
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

  protected readonly visibleGroups = computed(() =>
    this.dailyGroups().slice(0, this.visibleDayCount())
  );

  protected readonly hiddenDayCount = computed(() =>
    Math.max(0, this.dailyGroups().length - this.visibleDayCount())
  );

  toggleExpanded(): void {
    this.expanded.update(v => !v);
    this.visibleDayCount.set(HISTORY_PAGE_SIZE);
  }

  protected showMoreDays(): void {
    this.visibleDayCount.update(n => n + HISTORY_PAGE_SIZE);
  }

  protected parseDate(dateStr: string): Date {
    return parseMadridDate(dateStr);
  }

  /**
   * "Duración: X min" for a resolved outage (real, measured duration), or
   * "Reposición estimada: HH:mm" while it is still ongoing (Endesa's own estimate).
   */
  protected durationDisplay(outage: EnelOutage): string {
    const real = realDurationMinutes(outage);
    if (real !== null) {
      return `Duración: ${Math.round(real)} min`;
    }
    if (outage.resolvedAt) {
      // Ended, but last seen no later than its start (e.g. announced works that vanished):
      // there is no observed duration to report.
      return 'Terminado (duración no medible)';
    }
    if (outage.repositionDate) {
      return `Reposición estimada: ${formatMadridDate(parseMadridDate(outage.repositionDate), 'HH:mm')}`;
    }
    return 'En curso';
  }

  toggleDay(dateKey: string): void {
    this.expandedDay.update(current => current === dateKey ? null : dateKey);
  }
}


