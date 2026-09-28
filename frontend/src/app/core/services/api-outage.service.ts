import { HttpClient } from '@angular/common/http';
import { computed, Injectable, signal } from '@angular/core';

import { environment } from '../../../environments/environment';
import type { District, DistrictStats } from '../models';
import { ErrorLogService } from './error-log.service';

export interface EnelOutage {
  objectId: number;
  affectedClients: number;
  serviceType: string;
  interruptionDate: string;
  repositionDate: string;
  neighborhoodName: string | null;
  districtName: string | null;
  latitude?: number | null;
  longitude?: number | null;
  /** Endesa's own cause text (des_cause_es), e.g. "Avería" or "Trabajos programados". */
  cause?: string | null;
  fetchedAt: string;
  /**
   * When the outage was last observed active before it stopped being reported, or null
   * while it is still ongoing. Accurate to within one polling interval (~5 minutes) —
   * see core/utils/outage-duration.ts.
   */
  resolvedAt?: string | null;
}

/** Per-resource request lifecycle, used to drive loading skeletons and error states. */
export type LoadStatus = 'idle' | 'loading' | 'success' | 'error';

@Injectable({ providedIn: 'root' })
export class ApiOutageService {
  private readonly apiUrl = environment.apiBaseUrl;

  private readonly _yearlyOutages = signal<readonly EnelOutage[]>([]);
  private readonly _monthlyOutages = signal<readonly EnelOutage[]>([]);
  private readonly _liveOutages = signal<readonly EnelOutage[]>([]);
  private readonly _districtStats = signal<readonly DistrictStats[]>([]);

  readonly yearlyOutages = this._yearlyOutages.asReadonly();
  readonly monthlyOutages = this._monthlyOutages.asReadonly();
  readonly liveOutages = this._liveOutages.asReadonly();
  readonly districtStats = this._districtStats.asReadonly();

  // Deduplicated views for the UI: keep the most recent fetched record per natural key.
  readonly deduplicatedYearlyOutages = computed(() => this.deduplicate(this._yearlyOutages()));
  readonly deduplicatedMonthlyOutages = computed(() => this.deduplicate(this._monthlyOutages()));
  readonly deduplicatedLiveOutages = computed(() => this.deduplicate(this._liveOutages()));

  private readonly _selectedYear = signal(new Date().getFullYear());
  private readonly _selectedMonth = signal(new Date().getMonth() + 1);

  readonly selectedYear = this._selectedYear.asReadonly();
  readonly selectedMonth = this._selectedMonth.asReadonly();

  // Per-resource request status, so each section can show its own loading skeleton
  // or error state instead of sharing one global flag.
  private readonly _yearlyStatus = signal<LoadStatus>('idle');
  private readonly _monthlyStatus = signal<LoadStatus>('idle');
  private readonly _liveStatus = signal<LoadStatus>('idle');

  readonly yearlyStatus = this._yearlyStatus.asReadonly();
  readonly monthlyStatus = this._monthlyStatus.asReadonly();
  readonly liveStatus = this._liveStatus.asReadonly();

  readonly yearlyLoading = computed(() => this._yearlyStatus() === 'loading');
  readonly monthlyLoading = computed(() => this._monthlyStatus() === 'loading');
  readonly liveLoading = computed(() => this._liveStatus() === 'loading');

  readonly yearlyError = computed(() => this._yearlyStatus() === 'error');
  readonly monthlyError = computed(() => this._monthlyStatus() === 'error');
  readonly liveError = computed(() => this._liveStatus() === 'error');

  // Derive districts from yearly data using a stable id derived from the name.
  readonly derivedDistricts = computed((): readonly District[] => {
    const names = [...new Set(this._yearlyOutages().map(o => o.districtName).filter(Boolean))];
    return names
      .map(name => name!)
      .sort((a, b) => a.localeCompare(b))
      .map(name => ({ id: this.districtId(name), name }));
  });

  constructor(
    private http: HttpClient,
    private errorLog: ErrorLogService,
  ) {}

  loadAll(): void {
    this.loadYearlyOutages();
    this.loadMonthlyOutages();
    this.loadLiveOutages();
  }

  // ── Yearly (for chart) ──
  loadYearlyOutages(year?: number): void {
    const y = year ?? this._selectedYear();
    this._yearlyStatus.set('loading');
    this.http.get<EnelOutage[]>(`${this.apiUrl}/outages/yearly?year=${y}`).subscribe({
      next: data => {
        this._yearlyOutages.set(data);
        this._yearlyStatus.set('success');
      },
      error: err => {
        this._yearlyStatus.set('error');
        this.errorLog.log('API Yearly', err);
      },
    });
  }

  // ── Monthly (for cards) ──
  loadMonthlyOutages(year?: number, month?: number): void {
    const y = year ?? this._selectedYear();
    const m = month ?? this._selectedMonth();
    this._monthlyStatus.set('loading');
    this.http.get<EnelOutage[]>(`${this.apiUrl}/outages/monthly?year=${y}&month=${m}`).subscribe({
      next: data => {
        this._monthlyOutages.set(data);
        this._monthlyStatus.set('success');
      },
      error: err => {
        this._monthlyStatus.set('error');
        this.errorLog.log('API Monthly', err);
      },
    });
  }

  // ── Live ──
  loadLiveOutages(): void {
    this._liveStatus.set('loading');
    this.http.get<EnelOutage[]>(`${this.apiUrl}/outages/live`).subscribe({
      next: data => {
        this._liveOutages.set(data);
        this._liveStatus.set('success');
      },
      error: err => {
        this._liveStatus.set('error');
        this.errorLog.log('API Live', err);
      },
    });
  }

  // ── District statistics ──
  loadDistrictStats(year?: number): void {
    const y = year ?? this._selectedYear();
    this.http.get<DistrictStats[]>(`${this.apiUrl}/stats?year=${y}`).subscribe({
      next: data => this._districtStats.set(data),
      error: err => this.errorLog.log('API Stats', err),
    });
  }

  setMonthFilter(year: number, month: number): void {
    this._selectedYear.set(year);
    this._selectedMonth.set(month);
    this.loadMonthlyOutages(year, month);
  }

  private deduplicate(outages: readonly EnelOutage[]): EnelOutage[] {
    const map = new Map<string, EnelOutage>();
    for (const outage of outages) {
      // Mirrors the backend identity key: location + start time + service type.
      const location = outage.latitude != null && outage.longitude != null
        ? `${outage.latitude},${outage.longitude}`
        : outage.neighborhoodName ?? 'Zona no identificada';
      const key = `${location}|${outage.interruptionDate}|${outage.serviceType ?? 'UNKNOWN'}`;
      const existing = map.get(key);
      if (!existing || new Date(outage.fetchedAt).getTime() > new Date(existing.fetchedAt).getTime()) {
        map.set(key, outage);
      }
    }
    return [...map.values()];
  }

  private districtId(name: string): string {
    return name
      .normalize('NFD')
      .replace(/[̀-ͯ]/g, '')
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '');
  }
}
