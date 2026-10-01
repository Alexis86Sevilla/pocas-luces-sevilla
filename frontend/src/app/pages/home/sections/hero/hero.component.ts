import { Component, computed, effect, inject, untracked } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { ApiOutageService } from '../../../../core/services/api-outage.service';
import { pluralize } from '../../../../core/utils/pluralize';
import { TELEGRAM_CHANNEL_URL } from '../../../../core/config/social';
import { SevilleNightComponent } from './seville-night/seville-night.component';

@Component({
  selector: 'app-hero',
  imports: [DecimalPipe, SevilleNightComponent],
  templateUrl: './hero.component.html',
  styleUrl: './hero.component.css',
})
export class HeroComponent {
  protected readonly pluralize = pluralize;
  protected readonly telegramUrl = TELEGRAM_CHANNEL_URL;

  private readonly api = inject(ApiOutageService);
  protected readonly liveLoading = this.api.liveLoading;
  protected readonly liveError = this.api.liveError;
  protected readonly liveCount = computed(() => this.api.deduplicatedLiveOutages().length);
  protected readonly liveSupplyPoints = computed(() =>
    this.api.deduplicatedLiveOutages().reduce((sum, o) => sum + o.affectedClients, 0)
  );

  /** Current Madrid month: shares the monthly section's data when it shows that month. */
  protected readonly monthCount = this.api.currentMonthCount;
  protected readonly monthStatus = this.api.currentMonthStatus;

  constructor() {
    // The home page loads the monthly data for the selected month; when that is not the current
    // month (month filter / ?anio&mes link) fetch the current one separately, once.
    effect(() => {
      if (!this.api.monthlyIsCurrentMonth() && untracked(() => this.api.currentMonthStatus()) === 'idle') {
        untracked(() => this.api.loadCurrentMonthOutages());
      }
    });
  }

  protected scrollTo(sectionId: string): void {
    globalThis.document?.getElementById(sectionId)?.scrollIntoView({ behavior: 'smooth' });
  }
}
