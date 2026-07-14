import { HttpClient } from '@angular/common/http';
import { computed, Injectable, signal } from '@angular/core';
import { finalize } from 'rxjs';

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
  fetchedAt: string;
}

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

  private readonly _activeRequests = signal(0);
  readonly loading = computed(() => this._activeRequests() > 0);

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
    this.trackRequest(
      this.http.get<EnelOutage[]>(`${this.apiUrl}/outages/yearly?year=${y}`)
    ).subscribe({
      next: data => this._yearlyOutages.set(data),
      error: err => this.errorLog.log('API Yearly', err),
    });
  }

  // ── Monthly (for cards) ──
  loadMonthlyOutages(year?: number, month?: number): void {
    const y = year ?? this._selectedYear();
    const m = month ?? this._selectedMonth();
    this.trackRequest(
      this.http.get<EnelOutage[]>(`${this.apiUrl}/outages/monthly?year=${y}&month=${m}`)
    ).subscribe({
      next: data => this._monthlyOutages.set(data),
      error: err => this.errorLog.log('API Monthly', err),
    });
  }

  // ── Live ──
  loadLiveOutages(): void {
    this.trackRequest(
      this.http.get<EnelOutage[]>(`${this.apiUrl}/outages/live`)
    ).subscribe({
      next: data => this._liveOutages.set(data),
      error: err => this.errorLog.log('API Live', err),
    });
  }

  // ── District statistics ──
  loadDistrictStats(year?: number): void {
    const y = year ?? this._selectedYear();
    this.trackRequest(
      this.http.get<DistrictStats[]>(`${this.apiUrl}/stats?year=${y}`)
    ).subscribe({
      next: data => this._districtStats.set(data),
      error: err => this.errorLog.log('API Stats', err),
    });
  }

  private trackRequest<T>(request: import('rxjs').Observable<T>) {
    this._activeRequests.update(count => count + 1);
    return request.pipe(finalize(() => this._activeRequests.update(count => count - 1)));
  }

  setMonthFilter(year: number, month: number): void {
    this._selectedYear.set(year);
    this._selectedMonth.set(month);
    this.loadMonthlyOutages(year, month);
  }

  private deduplicate(outages: readonly EnelOutage[]): EnelOutage[] {
    const map = new Map<string, EnelOutage>();
    for (const outage of outages) {
      const key = `${outage.neighborhoodName ?? 'Zona no identificada'}|${outage.interruptionDate}|${outage.serviceType ?? 'UNKNOWN'}`;
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
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '');
  }
}
