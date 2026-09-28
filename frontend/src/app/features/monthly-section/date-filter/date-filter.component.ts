import { Component, effect, input, model, output } from '@angular/core';
import { FormsModule } from '@angular/forms';

export interface DateFilterValue {
  readonly year: number;
  readonly month: number;
}

/** First calendar year with recorded outage data (data collection started July 2026). */
export const FIRST_DATA_YEAR = 2026;

const ALL_MONTHS = [
  { value: 1, label: 'Enero' },
  { value: 2, label: 'Febrero' },
  { value: 3, label: 'Marzo' },
  { value: 4, label: 'Abril' },
  { value: 5, label: 'Mayo' },
  { value: 6, label: 'Junio' },
  { value: 7, label: 'Julio' },
  { value: 8, label: 'Agosto' },
  { value: 9, label: 'Septiembre' },
  { value: 10, label: 'Octubre' },
  { value: 11, label: 'Noviembre' },
  { value: 12, label: 'Diciembre' },
] as const;

@Component({
  selector: 'app-date-filter',
  imports: [FormsModule],
  templateUrl: './date-filter.component.html',
})
export class DateFilterComponent {
  readonly selectedMonth = input.required<number>();
  readonly selectedYear = input.required<number>();
  readonly filterChange = output<DateFilterValue>();

  protected readonly month = model(0);
  protected readonly year = model(0);

  private readonly now = new Date();
  private readonly currentYear = this.now.getFullYear();
  private readonly currentMonth = this.now.getMonth() + 1;

  // Descending so the current year appears first.
  protected readonly years = Array.from(
    { length: Math.max(1, this.currentYear - FIRST_DATA_YEAR + 1) },
    (_, i) => this.currentYear - i,
  );
  protected readonly months = ALL_MONTHS.filter(m => m.value <= this.currentMonth);

  constructor() {
    effect(() => {
      this.month.set(this.selectedMonth());
      this.year.set(this.selectedYear());
    });
  }

  protected onMonthChange(): void {
    this.filterChange.emit({ year: this.year(), month: this.month() });
  }

  protected onYearChange(): void {
    this.filterChange.emit({ year: this.year(), month: this.month() });
  }
}
