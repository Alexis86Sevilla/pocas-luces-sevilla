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
