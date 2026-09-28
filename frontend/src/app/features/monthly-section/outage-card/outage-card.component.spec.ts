import { TestBed } from '@angular/core/testing';

import { OutageCardComponent } from './outage-card.component';
import type { EnelOutage } from '../../../core/services/api-outage.service';
import type { District } from '../../../core/models';

describe('OutageCardComponent', () => {
  const district: District = { id: 'triana', name: 'Triana' };

  function outage(overrides: Partial<EnelOutage> = {}): EnelOutage {
    return {
      objectId: 1,
      affectedClients: 10,
      serviceType: 'MT',
      interruptionDate: '2026-07-01T10:00:00',
      repositionDate: '2026-07-01T10:30:00',
      neighborhoodName: 'Los Remedios',
      districtName: 'Triana',
      cause: 'Avería',
      fetchedAt: '2026-07-01T10:00:00',
      resolvedAt: null,
      ...overrides,
    };
  }

  function createFixture(outages: readonly EnelOutage[]) {
    const fixture = TestBed.createComponent(OutageCardComponent);
    fixture.componentRef.setInput('district', district);
    fixture.componentRef.setInput('outages', outages);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OutageCardComponent],
    }).compileComponents();
  });

  it('shows three equal-weight stat tiles: affected, outages, average duration', () => {
    const fixture = createFixture([outage({ affectedClients: 250 }), outage({ affectedClients: 50, objectId: 2 })]);
    const text = fixture.nativeElement.textContent;

    expect(text).toContain('300');
    expect(text).toContain('afectados');
    expect(text).toContain('2');
    expect(text).toContain('cortes');
    expect(text).toContain('min de media');
    expect(text).not.toContain('afectados en total');
  });

  it('uses the singular noun for exactly one outage and one affected person', () => {
    const fixture = createFixture([outage({ affectedClients: 1 })]);
    const tiles = fixture.nativeElement.querySelectorAll('.grid > div');

    expect(tiles[0].textContent).toContain('afectado');
    expect(tiles[0].textContent).not.toContain('afectados');

    expect(tiles[1].textContent).toContain('corte');
    expect(tiles[1].textContent).not.toContain('cortes');
  });

  it('pluralizes zero outages as plural', () => {
    const fixture = createFixture([]);
    const countTile = fixture.nativeElement.querySelectorAll('.grid > div')[1];

    expect(countTile.textContent).toContain('0');
    expect(countTile.textContent).toContain('cortes');
  });

  it('shows a dash for the average duration when no outage in the list is resolved', () => {
    const fixture = createFixture([outage({ resolvedAt: null })]);
    const durationTile = fixture.nativeElement.querySelectorAll('.grid > div')[2];

    expect(durationTile.textContent).toContain('—');
    expect(durationTile.textContent).toContain('min de media (real)');
  });

  it('averages only the real duration of resolved outages, ignoring ongoing ones and repositionDate', () => {
    const resolved = outage({
      objectId: 2,
      interruptionDate: '2026-07-01T10:00:00',
      resolvedAt: '2026-07-01T10:20:00',
      repositionDate: '2026-07-01T23:00:00', // must be ignored now that it is resolved
    });
    const ongoing = outage({ objectId: 3, resolvedAt: null });
    const fixture = createFixture([resolved, ongoing]);
    const durationTile = fixture.nativeElement.querySelectorAll('.grid > div')[2];

    // Only the resolved outage counts: 20 minutes, not averaged with the ongoing one.
    expect(durationTile.textContent).toContain('20');
  });

  it('shows the real duration for a resolved outage in the daily history', () => {
    const resolved = outage({
      objectId: 2,
      interruptionDate: '2026-07-01T10:00:00',
      resolvedAt: '2026-07-01T10:15:00',
    });
    const fixture = createFixture([resolved]);
    fixture.nativeElement.querySelector('button.bg-gray-900').click();
    fixture.detectChanges();
    fixture.nativeElement.querySelector('li > button').click();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Duración: 15 min');
  });

  it('shows the estimated restoration time for an ongoing outage in the daily history', () => {
    const ongoing = outage({
      objectId: 3,
      interruptionDate: '2026-07-02T09:00:00',
      repositionDate: '2026-07-02T09:45:00',
      resolvedAt: null,
    });
    const fixture = createFixture([ongoing]);
    fixture.nativeElement.querySelector('button.bg-gray-900').click();
    fixture.detectChanges();
    fixture.nativeElement.querySelector('li > button').click();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Reposición estimada: 09:45');
  });

  it('never shows an estimate for a resolved outage without an observable duration', () => {
    const vanished = outage({
      objectId: 4,
      interruptionDate: '2026-07-02T09:00:00',
      repositionDate: '2026-07-02T09:45:00',
      resolvedAt: '2026-07-02T08:30:00',
    });
    const fixture = createFixture([vanished]);
    fixture.nativeElement.querySelector('button.bg-gray-900').click();
    fixture.detectChanges();
    fixture.nativeElement.querySelector('li > button').click();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Terminado (duración no medible)');
    expect(fixture.nativeElement.textContent).not.toContain('Reposición estimada');
  });
});
