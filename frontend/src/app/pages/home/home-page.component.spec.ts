import { Location } from '@angular/common';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { of } from 'rxjs';
import { vi } from 'vitest';

import { HomePageComponent } from './home-page.component';
import { ApiOutageService } from '../../core/services/api-outage.service';

describe('HomePageComponent URL filter sync', () => {
  let httpMock: HttpTestingController;

  async function setup(queryParams: Record<string, string>, fragment: string | null = null) {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [HomePageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap(queryParams) }, fragment: of(fragment) },
        },
      ],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
  }

  function flushAllPending() {
    httpMock.match(() => true).forEach(req => req.flush([]));
  }

  afterEach(() => {
    httpMock.verify();
  });

  it('applies a valid ?anio=&mes= filter from the URL on init', async () => {
    await setup({ anio: '2026', mes: '7' });
    const fixture = TestBed.createComponent(HomePageComponent);
    fixture.detectChanges();

    const api = TestBed.inject(ApiOutageService);
    expect(api.selectedYear()).toBe(2026);
    expect(api.selectedMonth()).toBe(7);

    flushAllPending();
  });

  it('ignores an out-of-range URL year and keeps the default selection', async () => {
    await setup({ anio: '1999', mes: '7' });
    const api = TestBed.inject(ApiOutageService);
    const defaultYear = api.selectedYear();

    const fixture = TestBed.createComponent(HomePageComponent);
    fixture.detectChanges();

    expect(api.selectedYear()).toBe(defaultYear);
    flushAllPending();
  });

  it('reflects a filter change in the URL via Location.replaceState, without pushing history', async () => {
    await setup({});
    const fixture = TestBed.createComponent(HomePageComponent);
    fixture.detectChanges();
    flushAllPending();

    const location = TestBed.inject(Location);
    const replaceSpy = vi.spyOn(location, 'replaceState');

    (fixture.componentInstance as unknown as { onFilterChange: (v: { year: number; month: number }) => void })
      .onFilterChange({ year: 2026, month: 8 });

    httpMock.expectOne(r => r.url.includes('/outages/monthly')).flush([]);

    expect(replaceSpy).toHaveBeenCalledTimes(1);
    const [path] = replaceSpy.mock.calls[0];
    expect(path).toContain('anio=2026');
    expect(path).toContain('mes=8');
  });

  it('redirects the legacy /#metodologia fragment to /datos#metodologia', async () => {
    await setup({}, 'metodologia');
    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(HomePageComponent);
    fixture.detectChanges();
    flushAllPending();

    expect(navigateSpy).toHaveBeenCalledWith(['/datos'], { fragment: 'metodologia', replaceUrl: true });
  });

  it('does not redirect for other fragments such as #en-directo', async () => {
    await setup({}, 'en-directo');
    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(HomePageComponent);
    fixture.detectChanges();
    flushAllPending();

    expect(navigateSpy).not.toHaveBeenCalled();
  });
});
