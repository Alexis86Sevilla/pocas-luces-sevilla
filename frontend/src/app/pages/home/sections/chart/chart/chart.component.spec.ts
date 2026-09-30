import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

// jsdom has no real 2D canvas context, so Chart.js can't actually render in
// this environment. Mock it out: the accessible-table/aria-pressed logic
// under test here doesn't depend on real chart rendering.
vi.mock('chart.js', () => {
  class FakeChart {
    static register = vi.fn();
    data: unknown;
    update = vi.fn();
    constructor(_canvas: unknown, config: { data: unknown }) {
      this.data = config.data;
    }
  }
  return {
    Chart: FakeChart,
    CategoryScale: {},
    Legend: {},
    LinearScale: {},
    LineController: {},
    LineElement: {},
    PointElement: {},
    Tooltip: {},
  };
});

import { ChartComponent } from './chart.component';
import type { District } from '../../../../../core/models';
import type { EnelOutage } from '../../../../../core/services/api-outage.service';

describe('ChartComponent', () => {
  const districts: readonly District[] = [
    { id: 'triana', name: 'Triana' },
    { id: 'macarena', name: 'Macarena' },
  ];

  const outages: readonly EnelOutage[] = [
    {
      objectId: 1,
      affectedClients: 5,
      serviceType: 'MT',
      interruptionDate: '2026-07-05T10:00:00',
      repositionDate: '2026-07-05T11:00:00',
      neighborhoodName: 'Los Remedios',
      districtName: 'Triana',
      fetchedAt: '2026-07-05T10:05:00',
    },
    {
      objectId: 2,
      affectedClients: 3,
      serviceType: 'LV',
      interruptionDate: '2026-08-01T09:00:00',
      repositionDate: '2026-08-01T09:30:00',
      neighborhoodName: 'Norte',
      districtName: 'Macarena',
      fetchedAt: '2026-08-01T09:05:00',
    },
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ChartComponent],
    }).compileComponents();
  });

  function createFixture() {
    const fixture = TestBed.createComponent(ChartComponent);
    fixture.componentRef.setInput('districts', districts);
    fixture.componentRef.setInput('outages', outages);
    fixture.detectChanges();
    return fixture;
  }

  it('marks the selected district toggle with aria-pressed="true" and others as "false"', () => {
    const fixture = createFixture();
    const component = fixture.componentInstance;
    component['selectedIds'].set(new Set(['triana']));
    fixture.detectChanges();

    const buttons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('button'));
    const trianaButton = buttons.find(b => b.textContent?.trim() === 'Triana')!;
    const macarenaButton = buttons.find(b => b.textContent?.trim() === 'Macarena')!;

    expect(trianaButton.getAttribute('aria-pressed')).toBe('true');
    expect(macarenaButton.getAttribute('aria-pressed')).toBe('false');
  });

  it('builds an accessible monthly table restricted to the selected districts', () => {
    const fixture = createFixture();
    const component = fixture.componentInstance;
    component['selectedIds'].set(new Set(['triana', 'macarena']));
    fixture.detectChanges();

    const rows = component['tableRows']();
    expect(rows).toHaveLength(12);
    expect(rows[6]).toEqual({ month: 'Jul', counts: [1, 0] }); // July: 1 Triana outage
    expect(rows[7]).toEqual({ month: 'Ago', counts: [0, 1] }); // August: 1 Macarena outage
  });

  it('exposes the canvas with a descriptive role and label instead of only a bare canvas', () => {
    const fixture = createFixture();
    const canvas: HTMLCanvasElement = fixture.nativeElement.querySelector('canvas');
    expect(canvas.getAttribute('role')).toBe('img');
    expect(canvas.getAttribute('aria-label')).toBeTruthy();
  });
});
