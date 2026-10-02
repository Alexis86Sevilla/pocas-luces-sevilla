import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { LIVE_REFRESH_MS, LiveSectionComponent, type LiveGroup } from './live-section.component';
import { ApiOutageService } from '../../../../core/services/api-outage.service';
import { TELEGRAM_CHANNEL_URL } from '../../../../core/config/social';

describe('LiveSectionComponent', () => {
  let httpMock: HttpTestingController;
  let api: ApiOutageService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LiveSectionComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    api = TestBed.inject(ApiOutageService);
  });

  afterEach(() => httpMock.verify());

  function createFixture(groups: readonly LiveGroup[] = []) {
    const fixture = TestBed.createComponent(LiveSectionComponent);
    fixture.componentRef.setInput('liveGroups', groups);
    fixture.detectChanges();
    return fixture;
  }

  it('shows an error message with a retry button on failure, and clears it on a successful retry', () => {
    const fixture = createFixture();

    api.loadLiveOutages();
    httpMock.expectOne(r => r.url.endsWith('/outages/live'))
      .flush('boom', { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No se han podido cargar los datos');
    const retryButton: HTMLButtonElement = fixture.nativeElement.querySelector('button.bg-red-500');
    expect(retryButton).toBeTruthy();

    retryButton.click();
    httpMock.expectOne(r => r.url.endsWith('/outages/live')).flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('No se han podido cargar');
  });

  it('shows a loading skeleton while the request is in flight and no data is present yet', () => {
    const fixture = createFixture();

    api.loadLiveOutages();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[aria-hidden="true"]')).toBeTruthy();
    expect(fixture.nativeElement.textContent).not.toContain('No hay cortes activos');

    httpMock.expectOne(r => r.url.endsWith('/outages/live')).flush([]);
  });

  it('shows the empty state when idle with no active outages', () => {
    const fixture = createFixture();
    expect(fixture.nativeElement.textContent).toContain('No hay cortes activos.');
  });

  it('shows a freshness label derived from the newest fetchedAt', () => {
    const fixture = createFixture();
    const now = new Date();
    const fetchedAt = new Date(now.getTime() - 2 * 60_000).toISOString().replace('Z', '');

    api.loadLiveOutages();
    httpMock.expectOne(r => r.url.endsWith('/outages/live')).flush([{
      objectId: 1,
      affectedClients: 5,
      serviceType: 'MT',
      interruptionDate: fetchedAt,
      repositionDate: '',
      neighborhoodName: 'Triana',
      districtName: 'Triana',
      fetchedAt,
    }]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Actualizado hace');
  });

  it('links to the citizen guide', () => {
    const fixture = createFixture();
    const link: HTMLAnchorElement | null = fixture.nativeElement.querySelector('a[href="/guia"]');
    expect(link?.textContent).toContain('Qué hacer');
  });

  it('links to the public Telegram channel', () => {
    const fixture = createFixture();
    const link: HTMLAnchorElement | null = fixture.nativeElement.querySelector(`a[href="${TELEGRAM_CHANNEL_URL}"]`);
    expect(link?.textContent).toContain('Recibe avisos en Telegram');
    expect(link?.getAttribute('rel')).toBe('noopener noreferrer');
  });

  describe('background refresh', () => {
    let visibility: DocumentVisibilityState;
    const isLive = (r: { url: string }) => r.url.endsWith('/outages/live');

    beforeEach(() => {
      visibility = 'visible';
      vi.useFakeTimers();
      vi.setSystemTime(new Date('2026-10-02T10:00:00Z'));
      Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => visibility });
    });

    afterEach(() => {
      vi.useRealTimers();
      delete (document as unknown as Record<string, unknown>)['visibilityState'];
    });

    it('refreshes /live every 5 minutes while the tab is visible, without a loading state', () => {
      const fixture = createFixture();
      vi.advanceTimersByTime(LIVE_REFRESH_MS);
      httpMock.expectOne(isLive).flush([]);
      expect(api.liveLoading()).toBe(false);

      vi.advanceTimersByTime(LIVE_REFRESH_MS);
      httpMock.expectOne(isLive).flush([]);
      fixture.destroy();
    });

    it('skips the timed refresh while hidden, then catches up when visible again if the data is old', () => {
      const fixture = createFixture();
      api.loadLiveOutages();
      httpMock.expectOne(isLive).flush([]);

      visibility = 'hidden';
      vi.advanceTimersByTime(LIVE_REFRESH_MS);
      httpMock.expectNone(isLive);

      visibility = 'visible';
      document.dispatchEvent(new Event('visibilitychange'));
      httpMock.expectOne(isLive).flush([]);
      fixture.destroy();
    });

    it('does not refetch on becoming visible when the data is fresh', () => {
      const fixture = createFixture();
      api.loadLiveOutages();
      httpMock.expectOne(isLive).flush([]);

      vi.advanceTimersByTime(60_000);
      document.dispatchEvent(new Event('visibilitychange'));
      httpMock.expectNone(isLive);
      fixture.destroy();
    });

    it('keeps the data on screen when a background refresh fails', () => {
      const fixture = createFixture();
      api.loadLiveOutages();
      httpMock.expectOne(isLive).flush([]);

      vi.advanceTimersByTime(LIVE_REFRESH_MS);
      httpMock.expectOne(isLive).flush('boom', { status: 500, statusText: 'Server Error' });
      expect(api.liveError()).toBe(false);
      fixture.destroy();
    });

    it('stops refreshing and listening once destroyed', () => {
      const fixture = createFixture();
      fixture.destroy();
      vi.advanceTimersByTime(LIVE_REFRESH_MS * 2);
      document.dispatchEvent(new Event('visibilitychange'));
      httpMock.expectNone(isLive);
    });
  });
});
