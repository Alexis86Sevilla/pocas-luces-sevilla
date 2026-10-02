import { computed, DestroyRef, inject, Injectable, signal } from '@angular/core';

import { formatMadridDate } from '../utils/madrid-date';

const TICK_MS = 60_000;

/**
 * One shared minute-resolution clock. `now` is an instant; `year` and `month` are the calendar
 * components in Europe/Madrid, so nothing depends on the browser's timezone. Tests can provide a
 * subclass/stub, or use fake timers and advance them.
 */
@Injectable({ providedIn: 'root' })
export class MadridClock {
  private readonly _now = signal(new Date());

  readonly now = this._now.asReadonly();
  readonly year = computed(() => Number(formatMadridDate(this._now(), 'yyyy')));
  /** 1-12. */
  readonly month = computed(() => Number(formatMadridDate(this._now(), 'MM')));

  constructor() {
    const intervalId = globalThis.setInterval(() => this._now.set(new Date()), TICK_MS);
    inject(DestroyRef).onDestroy(() => globalThis.clearInterval(intervalId));
  }
}
