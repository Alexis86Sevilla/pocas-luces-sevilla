import { Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { ApiOutageService } from '../../../../core/services/api-outage.service';
import { pluralize } from '../../../../core/utils/pluralize';

@Component({
  selector: 'app-hero',
  imports: [DecimalPipe],
  templateUrl: './hero.component.html',
  styleUrl: './hero.component.css',
})
export class HeroComponent implements OnDestroy {
  protected readonly isGrayscale = signal(false);
  protected readonly pluralize = pluralize;

  private readonly api = inject(ApiOutageService);
  protected readonly liveLoading = this.api.liveLoading;
  protected readonly liveError = this.api.liveError;
  protected readonly liveCount = computed(() => this.api.deduplicatedLiveOutages().length);
  protected readonly liveSupplyPoints = computed(() =>
    this.api.deduplicatedLiveOutages().reduce((sum, o) => sum + o.affectedClients, 0)
  );

  private timerId: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    const reducedMotion = globalThis.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;

    if (reducedMotion) {
      this.isGrayscale.set(true);
    } else {
      this.timerId = globalThis.setTimeout(() => this.isGrayscale.set(true), 4000);
    }
  }

  ngOnDestroy(): void {
    if (this.timerId !== null) {
      clearTimeout(this.timerId);
    }
  }

  protected scrollTo(sectionId: string): void {
    globalThis.document?.getElementById(sectionId)?.scrollIntoView({ behavior: 'smooth' });
  }
}
