import { Component, DestroyRef, computed, inject, input } from '@angular/core';
import { DOCUMENT, DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';

import { ApiOutageService } from '../../../../core/services/api-outage.service';
import { MadridClock } from '../../../../core/services/madrid-clock';
import { parseMadridDate } from '../../../../core/utils/madrid-date';
import { formatRelativeTime, isStale } from '../../../../core/utils/relative-time';
import { pluralize } from '../../../../core/utils/pluralize';
import { TelegramLinkComponent } from '../../../../shared/ui/telegram-link/telegram-link.component';

export interface LiveGroup {
  readonly districtName: string;
  readonly count: number;
  readonly affectedClients: number;
  readonly serviceCategories: readonly string[];
  /** Parsed via parseMadridDate so display doesn't depend on the browser's timezone. */
  readonly earliestDate: Date;
  readonly latestDate: Date | null;
}

/** How often /live is refreshed while the tab is visible (the backend polls Endesa every 5 minutes). */
export const LIVE_REFRESH_MS = 5 * 60_000;

@Component({
  selector: 'app-live-section',
  imports: [DatePipe, DecimalPipe, RouterLink, TelegramLinkComponent],
  templateUrl: './live-section.component.html',
})
export class LiveSectionComponent {
  readonly liveGroups = input.required<readonly LiveGroup[]>();
  readonly api = inject(ApiOutageService);

  protected readonly pluralize = pluralize;

  /** Placeholder rows for the loading skeleton (count only, values are unused). */
  protected readonly skeletonRows = [0, 1, 2] as const;

  private readonly now = inject(MadridClock).now;
  private readonly document = inject(DOCUMENT);

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
    const intervalId = globalThis.setInterval(() => {
      if (this.isVisible()) this.api.loadLiveOutages(true);
    }, LIVE_REFRESH_MS);

    // A background tab throttles timers, so catch up as soon as the tab is shown again.
    const onVisibilityChange = () => {
      const loadedAt = this.api.liveLoadedAt();
      if (this.isVisible() && (loadedAt === null || Date.now() - loadedAt >= LIVE_REFRESH_MS)) {
        this.api.loadLiveOutages(true);
      }
    };
    this.document.addEventListener('visibilitychange', onVisibilityChange);

    inject(DestroyRef).onDestroy(() => {
      globalThis.clearInterval(intervalId);
      this.document.removeEventListener('visibilitychange', onVisibilityChange);
    });
  }

  private isVisible(): boolean {
    return this.document.visibilityState === 'visible';
  }
}
