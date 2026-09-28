import { formatRelativeTime, isStale } from './relative-time';

describe('formatRelativeTime', () => {
  it('shows "menos de 1 min" for anything under a minute', () => {
    const now = new Date('2026-07-13T10:00:00Z');
    expect(formatRelativeTime(new Date('2026-07-13T09:59:30Z'), now)).toBe('hace menos de 1 min');
  });

  it('shows whole minutes under an hour', () => {
    const now = new Date('2026-07-13T10:00:00Z');
    expect(formatRelativeTime(new Date('2026-07-13T09:45:00Z'), now)).toBe('hace 15 min');
  });

  it('shows whole hours at or above 60 minutes', () => {
    const now = new Date('2026-07-13T12:30:00Z');
    expect(formatRelativeTime(new Date('2026-07-13T10:00:00Z'), now)).toBe('hace 2 h');
  });

  it('clamps negative elapsed time (clock skew) to "menos de 1 min"', () => {
    const now = new Date('2026-07-13T10:00:00Z');
    expect(formatRelativeTime(new Date('2026-07-13T10:05:00Z'), now)).toBe('hace menos de 1 min');
  });
});

describe('isStale', () => {
  it('is false under the default 30 min threshold', () => {
    const now = new Date('2026-07-13T10:00:00Z');
    expect(isStale(new Date('2026-07-13T09:45:00Z'), now)).toBe(false);
  });

  it('is true past the default 30 min threshold', () => {
    const now = new Date('2026-07-13T10:31:00Z');
    expect(isStale(new Date('2026-07-13T10:00:00Z'), now)).toBe(true);
  });
});
