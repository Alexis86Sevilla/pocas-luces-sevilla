import { Component, DestroyRef, computed, inject, input, signal } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';

import { ApiOutageService } from '../../core/services/api-outage.service';
import { parseMadridDate } from '../../core/utils/madrid-date';
import { formatRelativeTime, isStale } from '../../core/utils/relative-time';
import { pluralize } from '../../core/utils/pluralize';

export interface LiveGroup {
  readonly districtName: string;
  readonly count: number;
  readonly affectedClients: number;
  readonly serviceCategories: readonly string[];
  /** Parsed via parseMadridDate so display doesn't depend on the browser's timezone. */
  readonly earliestDate: Date;
  readonly latestDate: Date | null;
}

const FRESHNESS_TICK_MS = 60_000;

@Component({
  selector: 'app-live-section',
  imports: [DatePipe, DecimalPipe, RouterLink],
  templateUrl: './live-section.component.html',
})
export class LiveSectionComponent {
  readonly liveGroups = input.required<readonly LiveGroup[]>();
  readonly api = inject(ApiOutageService);

  protected readonly pluralize = pluralize;

  /** Placeholder rows for the loading skeleton (count only, values are unused). */
  protected readonly skeletonRows = [0, 1, 2] as const;

  private readonly now = signal(new Date());

  private readonly latestFetchedAt = computed(() => {
    const outages = this.api.liveOutages();
    if (outages.length === 0) return null;
    return outages.reduce(
      (latest, o) => {
        const fetchedAt = parseMadridDate(o.fetchedAt);
        return fetchedAt.getTime() > latest.getTime() ? fetchedAt : latest;
      },
      parseMadridDate(outages[0].fetchedAt),
    );
  });

  protected readonly freshnessLabel = computed(() => {
    const latest = this.latestFetchedAt();
    return latest ? formatRelativeTime(latest, this.now()) : null;
  });

  protected readonly isDataStale = computed(() => {
    const latest = this.latestFetchedAt();
    return latest !== null && isStale(latest, this.now());
  });

  constructor() {
    const intervalId = globalThis.setInterval(() => this.now.set(new Date()), FRESHNESS_TICK_MS);
    inject(DestroyRef).onDestroy(() => globalThis.clearInterval(intervalId));
  }
}
