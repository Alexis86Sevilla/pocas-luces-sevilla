import { provideRouter } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { MonthlySectionComponent } from './monthly-section.component';
import { ApiOutageService } from '../../core/services/api-outage.service';
import type { District } from '../../core/models';

describe('MonthlySectionComponent', () => {
  let httpMock: HttpTestingController;
  let api: ApiOutageService;

  const districts: readonly District[] = [{ id: 'triana', name: 'Triana' }];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MonthlySectionComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    api = TestBed.inject(ApiOutageService);
  });

  afterEach(() => httpMock.verify());

  function createFixture() {
    const fixture = TestBed.createComponent(MonthlySectionComponent);
    fixture.componentRef.setInput('districts', districts);
    fixture.componentRef.setInput('monthlyOutages', []);
    fixture.componentRef.setInput('selectedMonth', 7);
    fixture.componentRef.setInput('selectedYear', 2026);
    fixture.detectChanges();
    return fixture;
  }

  it('shows an error message with a retry button that reloads the selected month', () => {
    const fixture = createFixture();

    api.loadMonthlyOutages(2026, 7);
    httpMock.expectOne(r => r.url.includes('/outages/monthly'))
      .flush('boom', { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No se han podido cargar los datos');
    const retryButton: HTMLButtonElement = fixture.nativeElement.querySelector('button.bg-red-600');
    retryButton.click();

    const retryReq = httpMock.expectOne(r => r.url.includes('/outages/monthly'));
    expect(retryReq.request.url).toContain('year=2026');
    expect(retryReq.request.url).toContain('month=7');
    retryReq.flush([]);
  });

  it('shows a loading skeleton while the month request is in flight with no data yet', () => {
    const fixture = createFixture();

    api.loadMonthlyOutages(2026, 7);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[aria-hidden="true"]')).toBeTruthy();

    httpMock.expectOne(r => r.url.includes('/outages/monthly')).flush([]);
  });

  it('shows the empty state when idle with no outages for the month', () => {
    const fixture = createFixture();
    expect(fixture.nativeElement.textContent).toContain('No hay cortes registrados este mes.');
  });

  it('links the selected month to the CSV download with the license notice', () => {
    const fixture = createFixture();
    const a: HTMLAnchorElement = fixture.nativeElement.querySelector('a[download]');

    const excel: HTMLAnchorElement = fixture.nativeElement.querySelectorAll('a[download]')[1];

    expect(a.getAttribute('href')).toContain('/open-data/outages.csv?year=2026&month=7');
    expect(excel.getAttribute('href')).toContain('/open-data/outages.csv?year=2026&month=7&format=excel');
    expect(fixture.nativeElement.textContent).toContain('CC BY 4.0');
  });
});
