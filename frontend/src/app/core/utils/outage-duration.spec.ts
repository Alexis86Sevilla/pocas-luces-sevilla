import { estimatedDurationMinutes, realDurationMinutes } from './outage-duration';
import type { EnelOutage } from '../services/api-outage.service';

describe('outage-duration', () => {
  function outage(overrides: Partial<EnelOutage> = {}): EnelOutage {
    return {
      objectId: 1,
      affectedClients: 10,
      serviceType: 'MT',
      interruptionDate: '2026-07-01T10:00:00',
      repositionDate: '2026-07-01T10:30:00',
      neighborhoodName: 'Los Remedios',
      districtName: 'Triana',
      fetchedAt: '2026-07-01T10:00:00',
      resolvedAt: null,
      ...overrides,
    };
  }

  describe('realDurationMinutes', () => {
    it('returns the minutes between interruptionDate and resolvedAt when resolved', () => {
      const o = outage({ resolvedAt: '2026-07-01T10:25:00' });
      expect(realDurationMinutes(o)).toBe(25);
    });

    it('returns null when the outage is still active (no resolvedAt)', () => {
      const o = outage({ resolvedAt: null });
      expect(realDurationMinutes(o)).toBeNull();
    });

    it('returns null when resolvedAt is not after interruptionDate', () => {
      const o = outage({ interruptionDate: '2026-07-01T10:00:00', resolvedAt: '2026-07-01T10:00:00' });
      expect(realDurationMinutes(o)).toBeNull();
    });

    it('ignores repositionDate entirely', () => {
      const o = outage({ repositionDate: '2026-07-01T23:00:00', resolvedAt: '2026-07-01T10:10:00' });
      expect(realDurationMinutes(o)).toBe(10);
    });
  });

  describe('estimatedDurationMinutes', () => {
    it('returns the minutes between interruptionDate and repositionDate', () => {
      const o = outage({ repositionDate: '2026-07-01T10:30:00' });
      expect(estimatedDurationMinutes(o)).toBe(30);
    });

    it('returns null when repositionDate is missing', () => {
      const o = outage({ repositionDate: '' });
      expect(estimatedDurationMinutes(o)).toBeNull();
    });

    it('returns null when repositionDate is not after interruptionDate', () => {
      const o = outage({ interruptionDate: '2026-07-01T10:00:00', repositionDate: '2026-07-01T09:00:00' });
      expect(estimatedDurationMinutes(o)).toBeNull();
    });

    it('ignores resolvedAt entirely', () => {
      const o = outage({ repositionDate: '2026-07-01T10:20:00', resolvedAt: '2026-07-01T23:00:00' });
      expect(estimatedDurationMinutes(o)).toBe(20);
    });
  });
});
