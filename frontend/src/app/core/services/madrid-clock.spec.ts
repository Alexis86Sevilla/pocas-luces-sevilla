import { TestBed } from '@angular/core/testing';

import { MadridClock } from './madrid-clock';

describe('MadridClock', () => {
  afterEach(() => vi.useRealTimers());

  it('exposes the Europe/Madrid year and month, not the UTC ones', () => {
    vi.useFakeTimers();
    // 23:30 UTC on 30 Sep is already 1 Oct in Madrid (CEST, UTC+2).
    vi.setSystemTime(new Date('2026-09-30T23:30:00Z'));
    const clock = TestBed.inject(MadridClock);
    expect(clock.year()).toBe(2026);
    expect(clock.month()).toBe(10);
  });

  it('ticks every minute and rolls the month over', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-10-31T22:59:30Z')); // 23:59:30 in Madrid
    const clock = TestBed.inject(MadridClock);
    expect(clock.month()).toBe(10);
    vi.advanceTimersByTime(60_000);
    expect(clock.month()).toBe(11);
  });

  it('clears its interval on destroy', () => {
    vi.useFakeTimers();
    TestBed.inject(MadridClock);
    expect(vi.getTimerCount()).toBe(1);
    TestBed.resetTestingModule();
    expect(vi.getTimerCount()).toBe(0);
  });
});
