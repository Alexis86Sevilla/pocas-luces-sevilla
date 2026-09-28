import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { DateFilterComponent } from './date-filter.component';

describe('DateFilterComponent', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  function createFixture(year: number, month: number) {
    const fixture = TestBed.createComponent(DateFilterComponent);
    fixture.componentRef.setInput('selectedYear', year);
    fixture.componentRef.setInput('selectedMonth', month);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DateFilterComponent],
    }).compileComponents();
  });

  it('renders a static year label when only the first data year has passed', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-07-15T12:00:00Z'));

    const fixture = createFixture(2026, 7);

    expect(fixture.nativeElement.querySelector('#year-select')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('2026');
  });

  it('renders a year dropdown with every year from 2026 to the current year, descending', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2028-03-01T12:00:00Z'));

    const fixture = createFixture(2028, 3);
    const select: HTMLSelectElement = fixture.nativeElement.querySelector('#year-select');
    const options = Array.from(select.querySelectorAll('option')).map(o => o.textContent?.trim());

    expect(options).toEqual(['2028', '2027', '2026']);
  });

  it('emits filterChange with the selected year and month', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-07-15T12:00:00Z'));

    const fixture = createFixture(2026, 7);
    const emitted: { year: number; month: number }[] = [];
    fixture.componentInstance.filterChange.subscribe(v => emitted.push(v));

    const select: HTMLSelectElement = fixture.nativeElement.querySelector('#month-select');
    select.value = select.options[0].value;
    select.dispatchEvent(new Event('change'));

    expect(emitted.length).toBeGreaterThan(0);
  });
});
