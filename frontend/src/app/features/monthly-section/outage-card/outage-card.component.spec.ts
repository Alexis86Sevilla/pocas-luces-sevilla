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
});
