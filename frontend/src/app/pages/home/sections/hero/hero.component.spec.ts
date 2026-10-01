import { LOCALE_ID } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localeEs from '@angular/common/locales/es';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { HeroComponent } from './hero.component';
import { ApiOutageService, type EnelOutage } from '../../../../core/services/api-outage.service';

registerLocaleData(localeEs);

describe('HeroComponent live strip', () => {
  let httpMock: HttpTestingController;

  const outage = (overrides: Partial<EnelOutage>): EnelOutage => ({
    objectId: 1,
    affectedClients: 10,
    serviceType: 'GB',
    interruptionDate: '2026-09-29T10:59:00',
    repositionDate: '2026-09-29T12:30:00',
    neighborhoodName: 'La Macarena',
    districtName: 'Macarena',
    latitude: 37.4,
    longitude: -5.98,
    cause: 'Avería',
    fetchedAt: '2026-09-29T11:00:00',
    ...overrides,
  });

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HeroComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: LOCALE_ID, useValue: 'es-ES' }],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
  });

  function render() {
    const fixture = TestBed.createComponent(HeroComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('shows a loading message while live data is requested', () => {
    const fixture = render();
    TestBed.inject(ApiOutageService).loadLiveOutages();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Cargando datos en directo');
    httpMock.expectOne(r => r.url.endsWith('/outages/live')).flush([]);
  });

  it('summarizes active outages and supply points', () => {
    const fixture = render();
    TestBed.inject(ApiOutageService).loadLiveOutages();
    httpMock.expectOne(r => r.url.endsWith('/outages/live')).flush([
      outage({ objectId: 1, affectedClients: 1200, latitude: 37.4 }),
      outage({ objectId: 2, affectedClients: 34, latitude: 37.38 }),
    ]);
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent.replace(/\s+/g, ' ');
    expect(text).toContain('2 cortes activos');
    expect(text).toContain('1.234 suministros afectados');
  });

  it('says the distributor publishes no active outages when the list is empty', () => {
    const fixture = render();
    TestBed.inject(ApiOutageService).loadLiveOutages();
    httpMock.expectOne(r => r.url.endsWith('/outages/live')).flush([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('no publica cortes activos');
  });

  it('never claims zero outages when the request failed', () => {
    const fixture = render();
    TestBed.inject(ApiOutageService).loadLiveOutages();
    httpMock.expectOne(r => r.url.endsWith('/outages/live')).flush('boom', { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No se han podido cargar los datos en directo');
    expect(fixture.nativeElement.textContent).not.toContain('no publica cortes activos');
  });
});

describe('HeroComponent copy and monthly counter', () => {
  let httpMock: HttpTestingController;

  const now = new Date();
  const madridYear = Number(new Intl.DateTimeFormat('en-GB', { timeZone: 'Europe/Madrid', year: 'numeric' }).format(now));
  const madridMonth = Number(new Intl.DateTimeFormat('en-GB', { timeZone: 'Europe/Madrid', month: 'numeric' }).format(now));

  const monthOutages = (n: number): EnelOutage[] =>
    Array.from({ length: n }, (_, i) => ({
      objectId: i,
      affectedClients: 5,
      serviceType: 'GB',
      interruptionDate: `2026-09-${String((i % 28) + 1).padStart(2, '0')}T10:00:00`,
      repositionDate: '2026-09-29T12:30:00',
      neighborhoodName: `Barrio ${i}`,
      districtName: 'Macarena',
      latitude: 37 + i / 1000,
      longitude: -5.98,
      cause: 'Avería',
      fetchedAt: '2026-09-29T11:00:00',
    }));

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HeroComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: LOCALE_ID, useValue: 'es-ES' }],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
  });

  function render() {
    const fixture = TestBed.createComponent(HeroComponent);
    fixture.detectChanges();
    return fixture;
  }

  const counterText = (fixture: ReturnType<typeof render>): string | null => {
    const el = fixture.nativeElement.querySelector('[data-testid="month-count"]');
    return el ? el.textContent.replace(/\s+/g, ' ').trim() : null;
  };

  const loadMonth = (n: number) => {
    TestBed.inject(ApiOutageService).setMonthFilter(madridYear, madridMonth);
    httpMock.expectOne(r => r.url.includes('/outages/monthly')).flush(monthOutages(n));
  };

  it('shows the neutral headline, subtitle and no photo or partisan copy', () => {
    const fixture = render();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('h1')?.textContent?.trim()).toBe('Se fue la luz. Otra vez.');
    const text = el.textContent ?? '';
    expect(text).toContain('Los cortes de luz de Sevilla, barrio a barrio y en tiempo real, con los datos de e-distribución.');
    expect(text).not.toMatch(/alcalde|dejadez|Europa Press/i);
    expect(el.querySelector('img, [style*="background-image"]')).toBeNull();
    const scene = el.querySelector('app-seville-night canvas');
    expect(scene?.getAttribute('aria-hidden')).toBe('true');
  });

  it('reserves the line without a number while loading', () => {
    const fixture = render();
    TestBed.inject(ApiOutageService).setMonthFilter(madridYear, madridMonth);
    fixture.detectChanges();
    expect(counterText(fixture)).toBe('');
    httpMock.expectOne(r => r.url.includes('/outages/monthly')).flush([]);
  });

  it('says how many outages this month for N > 1 with es-ES grouping', () => {
    const fixture = render();
    loadMonth(1234);
    fixture.detectChanges();
    expect(counterText(fixture)).toBe('Y van 1.234 cortes este mes en Sevilla.');
  });

  it('uses the singular for one outage', () => {
    const fixture = render();
    loadMonth(1);
    fixture.detectChanges();
    expect(counterText(fixture)).toBe('Y va 1 corte este mes en Sevilla.');
  });

  it('says no outages yet when the month is empty', () => {
    const fixture = render();
    loadMonth(0);
    fixture.detectChanges();
    expect(counterText(fixture)).toBe('Este mes, de momento, ningún corte en Sevilla.');
  });

  it('hides the line when the request failed', () => {
    const fixture = render();
    TestBed.inject(ApiOutageService).setMonthFilter(madridYear, madridMonth);
    httpMock.expectOne(r => r.url.includes('/outages/monthly')).flush('boom', { status: 500, statusText: 'Error' });
    fixture.detectChanges();
    expect(counterText(fixture)).toBeNull();
  });

  it('fetches the current month on its own when the filter shows another month', () => {
    const api = TestBed.inject(ApiOutageService);
    api.setMonthFilter(2020, 1);
    httpMock.expectOne(r => r.url.includes('/outages/monthly?year=2020&month=1')).flush(monthOutages(9));
    const fixture = render();
    httpMock
      .expectOne(r => r.url.includes(`/outages/monthly?year=${madridYear}&month=${madridMonth}`))
      .flush(monthOutages(3));
    fixture.detectChanges();
    expect(counterText(fixture)).toBe('Y van 3 cortes este mes en Sevilla.');
  });
});
