import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { LiveSectionComponent, type LiveGroup } from './live-section.component';
import { ApiOutageService } from '../../core/services/api-outage.service';

describe('LiveSectionComponent', () => {
  let httpMock: HttpTestingController;
  let api: ApiOutageService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LiveSectionComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
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

    expect(fixture.nativeElement.textContent).toContain('No hemos podido cargar los datos');
    const retryButton: HTMLButtonElement = fixture.nativeElement.querySelector('button.bg-red-500');
    expect(retryButton).toBeTruthy();

    retryButton.click();
    httpMock.expectOne(r => r.url.endsWith('/outages/live')).flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('No hemos podido cargar');
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
});
