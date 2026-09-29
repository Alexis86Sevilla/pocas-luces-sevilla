import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { ApiOutageService, type EnelOutage, type LoadStatus } from '../../core/services/api-outage.service';
import { toOutageMarkers } from '../../core/utils/map-markers';
import { LEAFLET_LOADER, type LeafletModule } from './leaflet-loader';
import { buildPopupContent, MapaPageComponent } from './mapa-page.component';

function outage(overrides: Partial<EnelOutage>): EnelOutage {
  return {
    objectId: 1,
    affectedClients: 40,
    serviceType: 'LV',
    interruptionDate: '2026-09-10T10:00:00',
    repositionDate: '',
    neighborhoodName: 'Triana Casco Antiguo',
    districtName: 'Triana',
    latitude: 37.38,
    longitude: -6.0,
    fetchedAt: '2026-09-10T12:00:00',
    resolvedAt: null,
    ...overrides,
  };
}

/** Records circle markers; everything else is a chainable no-op. Leaflet cannot lay out in jsdom. */
function fakeLeaflet() {
  const drawn: { latlng: unknown; options: { fillColor?: string } }[] = [];
  const chain: Record<string, unknown> = {};
  for (const m of ['addTo', 'bindPopup', 'bindTooltip', 'bringToBack', 'clearLayers', 'remove']) {
    chain[m] = () => chain;
  }
  const L = {
    map: vi.fn(() => chain),
    tileLayer: vi.fn(() => chain),
    layerGroup: vi.fn(() => {
      const group: Record<string, unknown> = { clearLayers: () => drawn.splice(0) };
      group['addTo'] = () => group;
      return group;
    }),
    geoJSON: vi.fn(() => chain),
    circleMarker: (latlng: unknown, options: { fillColor?: string }) => {
      drawn.push({ latlng, options });
      return chain;
    },
  };
  return { L: L as unknown as LeafletModule, drawn, map: chain, mapFactory: L.map };
}

describe('MapaPageComponent', () => {
  const live = signal<EnelOutage[]>([]);
  const monthly = signal<EnelOutage[]>([]);
  const liveStatus = signal<LoadStatus>('success');
  const monthlyStatus = signal<LoadStatus>('success');
  const api = {
    deduplicatedLiveOutages: live,
    deduplicatedMonthlyOutages: monthly,
    liveStatus,
    monthlyStatus,
    selectedYear: () => 2026,
    selectedMonth: () => 9,
    loadLiveOutages: vi.fn(),
    loadMonthlyOutages: vi.fn(),
    setMonthFilter: vi.fn(),
  };

  async function setup() {
    const fake = fakeLeaflet();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ApiOutageService, useValue: api },
        { provide: LEAFLET_LOADER, useValue: async () => fake.L },
      ],
    });
    const fixture = TestBed.createComponent(MapaPageComponent);
    fixture.detectChanges();
    TestBed.inject(HttpTestingController).expectOne('/geo/distritos-sevilla.json').flush({ type: 'FeatureCollection', features: [] });
    await fixture.whenStable();
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement, fake };
  }

  beforeEach(() => {
    live.set([outage({})]);
    monthly.set([outage({ objectId: 2, latitude: 37.4 }), outage({ objectId: 3, latitude: null })]);
    liveStatus.set('success');
    monthlyStatus.set('success');
  });

  it('draws live markers in red and skips outages without position', async () => {
    const { el, fake } = await setup();
    expect(fake.drawn.length).toBe(1);
    expect(fake.drawn[0].options.fillColor).toBe('#dc2626');
    expect(el.textContent).toContain('1 corte en el mapa');
  });

  it('toggles to the month view with aria-pressed and redraws', async () => {
    const { fixture, el, fake } = await setup();
    const [liveBtn, monthBtn] = [...el.querySelectorAll<HTMLButtonElement>('[role="group"] button')];
    expect(liveBtn.getAttribute('aria-pressed')).toBe('true');

    monthBtn.click();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(monthBtn.getAttribute('aria-pressed')).toBe('true');
    expect(liveBtn.getAttribute('aria-pressed')).toBe('false');
    expect(fake.drawn.length).toBe(1);
    expect(fake.drawn[0].options.fillColor).toBe('#4f46e5');
    expect(el.textContent).toContain('1 sin posición publicada');
    expect(el.querySelector('app-date-filter')).not.toBeNull();
  });

  it('shows the attribution-bearing notes and legend', async () => {
    const { el } = await setup();
    expect(el.textContent).toContain('La posición es la del punto que publica la distribuidora; el barrio es aproximado.');
    expect(el.querySelector('ul[aria-label="Leyenda"]')).not.toBeNull();
  });

  it('shows a retry state on error', async () => {
    liveStatus.set('error');
    const { el } = await setup();
    expect(el.textContent).toContain('No se han podido cargar los datos');
  });

  it('builds popups with text nodes only (no HTML injection)', () => {
    const [marker] = toOutageMarkers([outage({ neighborhoodName: '<img src=x onerror=alert(1)>' })]);
    const popup = buildPopupContent(marker);
    expect(popup.querySelector('img')).toBeNull();
    expect(popup.textContent).toContain('<img src=x onerror=alert(1)>');
    expect(popup.textContent).toContain('en curso');
  });
});
