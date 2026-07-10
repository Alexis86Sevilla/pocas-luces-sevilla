import { HttpClient } from '@angular/common/http';
import { computed, Injectable, signal } from '@angular/core';
import { tap } from 'rxjs';

import { environment } from '../../../environments/environment';
import type { Neighborhood } from '../models';

export interface EnelOutage {
  objectId: number;
  affectedClients: number;
  serviceType: string;
  interruptionDate: string;
  repositionDate: string;
  neighborhoodName: string | null;
}

@Injectable({ providedIn: 'root' })
export class ApiOutageService {
  private readonly apiUrl = environment.apiBaseUrl;

  private readonly _neighborhoods = signal<readonly Neighborhood[]>([]);
  private readonly _yearlyOutages = signal<readonly EnelOutage[]>([]);
  private readonly _monthlyOutages = signal<readonly EnelOutage[]>([]);
  private readonly _liveOutages = signal<readonly EnelOutage[]>([]);

  readonly neighborhoods = this._neighborhoods.asReadonly();
  readonly yearlyOutages = this._yearlyOutages.asReadonly();
  readonly monthlyOutages = this._monthlyOutages.asReadonly();
  readonly liveOutages = this._liveOutages.asReadonly();

  private readonly _selectedYear = signal(new Date().getFullYear());
  private readonly _selectedMonth = signal(new Date().getMonth() + 1);

  readonly selectedYear = this._selectedYear.asReadonly();
  readonly selectedMonth = this._selectedMonth.asReadonly();

  readonly loading = signal(false);

  // Derive neighborhoods from yearly data
  readonly derivedNeighborhoods = computed(() => {
    const names = [...new Set(this._yearlyOutages().map(o => o.neighborhoodName).filter(Boolean))];
    return names.map((name, i) => ({ id: String(i), name: name! }));
  });

  constructor(private http: HttpClient) {}

  loadAll(): void {
    this.loading.set(true);
    this.loadYearlyOutages();
    this.loadMonthlyOutages();
    this.loadLiveOutages();
  }

  // ── Yearly (for chart) ──
  loadYearlyOutages(year?: number): void {
    const y = year ?? this._selectedYear();
    this.http.get<EnelOutage[]>(`${this.apiUrl}/outages/yearly?year=${y}`).subscribe({
      next: data => {
        this._yearlyOutages.set(data);
        console.log('[API] Yearly outages:', data.length);
      },
      error: err => console.error('[API] Yearly:', err.message),
    });
  }

  // ── Monthly (for cards) ──
  loadMonthlyOutages(year?: number, month?: number): void {
    const y = year ?? this._selectedYear();
    const m = month ?? this._selectedMonth();
    this.http.get<EnelOutage[]>(`${this.apiUrl}/outages/monthly?year=${y}&month=${m}`).pipe(
      tap(data => console.log('[API] Monthly:', data.length))
    ).subscribe({
      next: data => this._monthlyOutages.set(data),
      error: err => console.error('[API] Monthly:', err.message),
    });
  }

  // ── Live ──
  loadLiveOutages(): void {
    this.http.get<EnelOutage[]>(`${this.apiUrl}/outages/live`).pipe(
      tap(data => console.log('[API] Live:', data.length))
    ).subscribe({
      next: data => { this._liveOutages.set(data); this.loading.set(false); },
      error: err => { console.error('[API] Live:', err.message); this.loading.set(false); },
    });
  }

  setMonthFilter(year: number, month: number): void {
    this._selectedYear.set(year);
    this._selectedMonth.set(month);
    this.loadMonthlyOutages(year, month);
  }
}
