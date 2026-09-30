import { HttpClient } from '@angular/common/http';
import {
  afterNextRender,
  Component,
  computed,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  OnInit,
  signal,
  viewChild,
  ViewEncapsulation,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import type { LayerGroup, Map as LeafletMap } from 'leaflet';

import { ApiOutageService } from '../../core/services/api-outage.service';
import { toOutageMarkers, type OutageMarker } from '../../core/utils/map-markers';
import { pluralize } from '../../core/utils/pluralize';
import { DateFilterComponent, type DateFilterValue } from '../home/sections/monthly/date-filter/date-filter.component';
import { LEAFLET_LOADER, type LeafletModule } from './leaflet-loader';

export type MapMode = 'live' | 'month';

type DistrictShapes = Parameters<LeafletModule['geoJSON']>[0];

const SEVILLE_CENTER: [number, number] = [37.3886, -5.9823];
const OSM_TILES = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
const OSM_ATTRIBUTION = '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors';
const LIVE_COLOR = '#dc2626';
const MONTH_COLOR = '#4f46e5';

/** Popup body built with DOM nodes and textContent: no untrusted string is ever parsed as HTML. */
export function buildPopupContent(marker: OutageMarker): HTMLElement {
  const root = document.createElement('div');
  const line = (label: string, value: string, strong = false) => {
    const p = document.createElement('p');
    p.style.margin = '2px 0';
    const l = document.createElement(strong ? 'strong' : 'span');
    l.textContent = label;
    p.append(l, document.createTextNode(` ${value}`));
    root.append(p);
  };
  line('Barrio (aproximado):', marker.neighborhood, true);
  line('Distrito:', marker.district);
  line('Inicio:', marker.startLabel);
  line('Fin observado:', marker.endLabel ?? 'en curso');
  line('Suministros afectados:', marker.affectedClients.toLocaleString('es-ES'));
  line('Categoría:', marker.category);
  return root;
}

const DISTRICT_STYLE = { color: '#6b7280', weight: 1.5, fillColor: '#9ca3af', fillOpacity: 0.04 };

@Component({
  selector: 'app-map-page',
  imports: [RouterLink, DateFilterComponent],
  templateUrl: './map-page.component.html',
  styleUrl: './map-page.component.css',
  // Leaflet creates its DOM imperatively, so its stylesheet must not be view-encapsulated.
  encapsulation: ViewEncapsulation.None,
})
export class MapPageComponent implements OnInit {
  protected readonly api = inject(ApiOutageService);
  private readonly http = inject(HttpClient);
  private readonly loadLeaflet = inject(LEAFLET_LOADER);
  private readonly destroyRef = inject(DestroyRef);

  private readonly mapEl = viewChild<ElementRef<HTMLElement>>('map');

  protected readonly mode = signal<MapMode>('live');
  protected readonly mapFailed = signal(false);

  private readonly districtShapes = signal<DistrictShapes | null>(null);
  private readonly leafletReady = signal(false);

  private leaflet?: LeafletModule;
  private map?: LeafletMap;
  private markerLayer?: LayerGroup;
  private districtsDrawn = false;
  private destroyed = false;

  private readonly sourceOutages = computed(() =>
    this.mode() === 'live' ? this.api.deduplicatedLiveOutages() : this.api.deduplicatedMonthlyOutages(),
  );

  protected readonly markers = computed(() => toOutageMarkers(this.sourceOutages()));
  protected readonly status = computed(() =>
    this.mode() === 'live' ? this.api.liveStatus() : this.api.monthlyStatus(),
  );

  protected readonly periodLabel = computed(() => {
    const month = new Date(this.api.selectedYear(), this.api.selectedMonth() - 1, 1).toLocaleDateString('es-ES', {
      month: 'long',
    });
    return `${month} de ${this.api.selectedYear()}`;
  });

  protected readonly summary = computed(() => {
    const shown = this.markers().length;
    const total = this.sourceOutages().length;
    const base = `${shown} ${pluralize(shown, 'corte')} en el mapa`;
    return total > shown ? `${base} (${total - shown} sin posición publicada)` : base;
  });

  constructor() {
    this.destroyRef.onDestroy(() => {
      this.destroyed = true;
      this.map?.remove();
      this.map = undefined;
    });

    afterNextRender(() => void this.initMap());

    effect(() => {
      const markers = this.markers();
      if (this.leafletReady()) this.drawMarkers(markers, this.mode());
    });

    effect(() => {
      const shapes = this.districtShapes();
      if (this.leafletReady() && shapes) this.drawDistricts(shapes);
    });
  }

  ngOnInit(): void {
    this.api.loadLiveOutages();
    if (this.api.monthlyStatus() === 'idle') this.api.loadMonthlyOutages();
    this.http.get<DistrictShapes>('/geo/distritos-sevilla.json').subscribe({
      next: data => this.districtShapes.set(data),
      // Boundaries are decoration: the map still works without them.
      error: () => this.districtShapes.set(null),
    });
  }

  protected setMode(mode: MapMode): void {
    this.mode.set(mode);
  }

  protected onFilterChange(value: DateFilterValue): void {
    this.api.setMonthFilter(value.year, value.month);
  }

  protected retry(): void {
    if (this.mode() === 'live') this.api.loadLiveOutages();
    else this.api.loadMonthlyOutages(this.api.selectedYear(), this.api.selectedMonth());
  }

  private async initMap(): Promise<void> {
    const container = this.mapEl()?.nativeElement;
    if (!container) return;
    try {
      const L = await this.loadLeaflet();
      if (this.destroyed) return;
      this.leaflet = L;
      const map = L.map(container, { center: SEVILLE_CENTER, zoom: 12, maxZoom: 19 });
      L.tileLayer(OSM_TILES, { maxZoom: 19, attribution: OSM_ATTRIBUTION }).addTo(map);
      this.markerLayer = L.layerGroup().addTo(map);
      this.map = map;
      this.leafletReady.set(true);
    } catch {
      this.mapFailed.set(true);
    }
  }

  private drawDistricts(shapes: DistrictShapes): void {
    const L = this.leaflet;
    const map = this.map;
    if (!L || !map || this.districtsDrawn) return;
    this.districtsDrawn = true;
    L.geoJSON(shapes, {
      style: DISTRICT_STYLE,
      onEachFeature: (feature, layer) => {
        const tip = document.createElement('span');
        tip.textContent = String(feature.properties?.['name'] ?? '');
        layer.bindTooltip(tip, { sticky: true });
        // Highlight on hover instead of the browser focus outline, which Chrome draws
        // as a rectangle around the whole district's bounding box.
        const path = layer as import('leaflet').Path;
        layer.on('mouseover', () => path.setStyle({ weight: 3, color: '#374151', fillOpacity: 0.1 }));
        layer.on('mouseout', () => path.setStyle(DISTRICT_STYLE));
      },
    })
      .addTo(map)
      .bringToBack();
  }

  private drawMarkers(markers: readonly OutageMarker[], mode: MapMode): void {
    const L = this.leaflet;
    const layer = this.markerLayer;
    if (!L || !layer) return;
    layer.clearLayers();
    const live = mode === 'live';
    for (const m of markers) {
      L.circleMarker([m.lat, m.lng], {
        radius: live ? 9 : 6,
        color: '#ffffff',
        weight: 1.5,
        fillColor: live ? LIVE_COLOR : MONTH_COLOR,
        fillOpacity: live ? 0.9 : 0.7,
      })
        .bindPopup(buildPopupContent(m))
        .addTo(layer);
    }
  }
}
