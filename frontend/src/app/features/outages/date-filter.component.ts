import { Component, input, output } from '@angular/core';

export interface DateFilterValue {
  readonly year: number;
  readonly month: number;
}

@Component({
  selector: 'app-date-filter',
  imports: [],
  templateUrl: './date-filter.component.html',
})
export class DateFilterComponent {
  readonly selectedMonth = input.required<number>();
  readonly selectedYear = input.required<number>();
  readonly filterChange = output<DateFilterValue>();

  protected readonly months = [
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

  protected readonly years = [2025, 2026, 2027] as const;

  protected onMonthChange(event: Event): void {
    const month = Number((event.target as HTMLSelectElement).value);
    this.filterChange.emit({ year: this.selectedYear(), month });
  }

  protected onYearChange(event: Event): void {
    const year = Number((event.target as HTMLSelectElement).value);
    this.filterChange.emit({ year, month: this.selectedMonth() });
  }
}
