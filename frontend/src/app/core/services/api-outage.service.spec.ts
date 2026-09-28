import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { ApiOutageService, type EnelOutage } from './api-outage.service';

describe('ApiOutageService', () => {
  let service: ApiOutageService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ApiOutageService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  const outage: EnelOutage = {
    objectId: 1,
    affectedClients: 10,
    serviceType: 'MT',
    interruptionDate: '2026-07-13T05:13:00',
    repositionDate: '2026-07-13T06:13:00',
    neighborhoodName: 'Triana',
    districtName: 'Triana',
    fetchedAt: '2026-07-13T05:20:00',
  };

  it('keeps distinct outages in the same neighborhood with the same start', () => {
    service.loadLiveOutages();
    const req = httpMock.expectOne(r => r.url.endsWith('/outages/live'));
    req.flush([
      { ...outage, objectId: 1, latitude: 37.38, longitude: -5.99 },
      { ...outage, objectId: 2, latitude: 37.39, longitude: -5.98 },
      { ...outage, objectId: 3, latitude: 37.39, longitude: -5.98, fetchedAt: '2026-07-13T05:25:00' },
    ]);

    expect(service.deduplicatedLiveOutages().map(o => o.objectId).sort()).toEqual([1, 3]);
  });

  it('tracks loading then success for live outages', () => {
    expect(service.liveLoading()).toBe(false);

    service.loadLiveOutages();
    expect(service.liveLoading()).toBe(true);
    expect(service.liveError()).toBe(false);

    const req = httpMock.expectOne(r => r.url.endsWith('/outages/live'));
    req.flush([outage]);

    expect(service.liveLoading()).toBe(false);
    expect(service.liveError()).toBe(false);
    expect(service.liveOutages()).toEqual([outage]);
  });

  it('tracks error state for live outages and keeps previous data untouched', () => {
    service.loadLiveOutages();
    const req = httpMock.expectOne(r => r.url.endsWith('/outages/live'));
    req.flush('boom', { status: 500, statusText: 'Server Error' });

    expect(service.liveLoading()).toBe(false);
    expect(service.liveError()).toBe(true);
    expect(service.liveOutages()).toEqual([]);
  });

  it('clears the error state on a successful retry', () => {
    service.loadMonthlyOutages(2026, 7);
    httpMock.expectOne(r => r.url.includes('/outages/monthly')).flush('boom', { status: 500, statusText: 'Server Error' });
    expect(service.monthlyError()).toBe(true);

    service.loadMonthlyOutages(2026, 7);
    expect(service.monthlyLoading()).toBe(true);
    httpMock.expectOne(r => r.url.includes('/outages/monthly')).flush([outage]);

    expect(service.monthlyError()).toBe(false);
    expect(service.monthlyOutages()).toEqual([outage]);
  });

  it('tracks yearly loading and error independently from other resources', () => {
    service.loadYearlyOutages(2026);
    expect(service.yearlyLoading()).toBe(true);
    expect(service.liveLoading()).toBe(false);

    httpMock.expectOne(r => r.url.includes('/outages/yearly')).flush('boom', { status: 500, statusText: 'Server Error' });

    expect(service.yearlyError()).toBe(true);
    expect(service.liveError()).toBe(false);
  });
});
