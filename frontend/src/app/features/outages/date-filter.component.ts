import { Component, effect, input, model, output } from '@angular/core';
import { FormsModule } from '@angular/forms';

export interface DateFilterValue {
  readonly year: number;
  readonly month: number;
}

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

  protected readonly years = [this.currentYear];
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
