import { registerLocaleData } from '@angular/common';
import localeEs from '@angular/common/locales/es';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { ApiOutageService, type EnelOutage, type LoadStatus } from '../../../../core/services/api-outage.service';
import { MonthlyRankingComponent } from './monthly-ranking.component';

function outage(districtName: string, hours: number, id = 1): EnelOutage {
  const start = new Date(Date.UTC(2026, 8, 10, 8, 0));
  const end = new Date(start.getTime() + hours * 3_600_000);
  const iso = (d: Date) => d.toISOString().slice(0, 19) + 'Z';
  return {
    objectId: id,
    affectedClients: 100,
    serviceType: 'LV',
    interruptionDate: iso(start),
    repositionDate: '',
    neighborhoodName: null,
    districtName,
    fetchedAt: iso(end),
    resolvedAt: iso(end),
  };
}

registerLocaleData(localeEs);

describe('MonthlyRankingComponent', () => {
  const monthlyStatus = signal<LoadStatus>('success');

  function setup(outages: EnelOutage[]) {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: ApiOutageService,
          useValue: {
            monthlyError: () => monthlyStatus() === 'error',
            monthlyLoading: () => monthlyStatus() === 'loading',
          },
        },
      ],
    });
    const fixture = TestBed.createComponent(MonthlyRankingComponent);
    fixture.componentRef.setInput('monthlyOutages', outages);
    fixture.componentRef.setInput('selectedMonth', 9);
    fixture.componentRef.setInput('selectedYear', 2026);
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  const names = (el: HTMLElement) => [...el.querySelectorAll('ol li p:first-child')].map(p => p.textContent?.trim());

  beforeEach(() => monthlyStatus.set('success'));

  it('shows the title, the selected period and the honest footnote', () => {
    const { el } = setup([outage('Triana', 2)]);
    expect(el.querySelector('h3')?.textContent).toContain('Distritos con más horas de corte este mes');
    expect(el.textContent).toContain('septiembre de 2026');
    expect(el.textContent).toContain('Los cortes en curso no cuentan hasta que terminan');
    expect(el.querySelector('a[href="/datos#metodologia"]')?.textContent).toContain('¿Cómo se calcula?');
  });

  it('shows the top 5 with es-ES formatted hours and counts', () => {
    const outages = ['A', 'B', 'C', 'D', 'E', 'F', 'G'].map((n, i) => outage(n, 12.5 - i, i));
    const { el } = setup(outages);
    expect(names(el).length).toBe(5);
    expect(names(el)[0]).toContain('A');
    expect(el.textContent).toContain('12,5 h');
    expect(el.textContent).toContain('1 corte · 100 suministros');
  });

  it('expands to all districts and collapses back', () => {
    const outages = ['A', 'B', 'C', 'D', 'E', 'F', 'G'].map((n, i) => outage(n, 10 - i, i));
    const { fixture, el } = setup(outages);
    const button = () => el.querySelector('button')!;
    expect(button().textContent).toContain('Ver todos los distritos (7)');
    expect(button().getAttribute('aria-expanded')).toBe('false');

    button().click();
    fixture.detectChanges();
    expect(names(el).length).toBe(7);
    expect(button().getAttribute('aria-expanded')).toBe('true');

    button().click();
    fixture.detectChanges();
    expect(names(el).length).toBe(5);
  });

  it('hides the expand button when there are 5 districts or fewer', () => {
    const { el } = setup([outage('A', 1), outage('B', 2, 2)]);
    expect(el.querySelector('button')).toBeNull();
  });

  it('shows an empty state without data', () => {
    const { el } = setup([]);
    expect(el.textContent).toContain('No hay cortes registrados este mes.');
    expect(el.querySelector('ol')).toBeNull();
  });
});
