import { DecimalPipe } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ApiOutageService, type EnelOutage } from '../../../../core/services/api-outage.service';
import { rankDistricts } from '../../../../core/utils/district-ranking';
import { pluralize } from '../../../../core/utils/pluralize';

const TOP_COUNT = 5;

@Component({
  selector: 'app-monthly-ranking',
  imports: [DecimalPipe, RouterLink],
  templateUrl: './monthly-ranking.component.html',
})
export class MonthlyRankingComponent {
  readonly monthlyOutages = input.required<readonly EnelOutage[]>();
  readonly selectedMonth = input.required<number>();
  readonly selectedYear = input.required<number>();

  protected readonly api = inject(ApiOutageService);
  protected readonly expanded = signal(false);
  protected readonly topCount = TOP_COUNT;

  protected readonly ranking = computed(() => rankDistricts(this.monthlyOutages()));
  protected readonly visible = computed(() =>
    this.expanded() ? this.ranking() : this.ranking().slice(0, TOP_COUNT),
  );
  protected readonly maxHours = computed(() =>
    Math.max(0, ...this.ranking().map(r => r.accumulatedHours)),
  );
  protected readonly canExpand = computed(() => this.ranking().length > TOP_COUNT);
  protected readonly isEmpty = computed(() => this.ranking().length === 0);

  protected readonly periodLabel = computed(() => {
    const month = new Date(this.selectedYear(), this.selectedMonth() - 1, 1).toLocaleDateString('es-ES', {
      month: 'long',
    });
    return `${month} de ${this.selectedYear()}`;
  });

  protected barWidth(hours: number): number {
    const max = this.maxHours();
    return max > 0 ? Math.max(hours > 0 ? 2 : 0, (hours / max) * 100) : 0;
  }

  protected outagesLabel(count: number): string {
    return `${count} ${pluralize(count, 'corte')}`;
  }

  protected clientsLabel(count: number): string {
    return `${count.toLocaleString('es-ES')} ${pluralize(count, 'suministro')}`;
  }

  protected toggle(): void {
    this.expanded.update(v => !v);
  }
}
