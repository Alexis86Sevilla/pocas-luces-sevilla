import { TestBed } from '@angular/core/testing';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { SevilleNightComponent } from './seville-night.component';

/** A 2D context stand-in: every method is a spy and gradients are the same stub. */
function fakeContext() {
  const calls = { fillRect: 0, drawImage: 0 };
  const target: Record<string, unknown> = {};
  const ctx: unknown = new Proxy(target, {
    get(_t, prop: string) {
      if (prop === 'fillRect') return () => void calls.fillRect++;
      if (prop === 'drawImage') return () => void calls.drawImage++;
      if (prop in target) return target[prop];
      return () => ctx;
    },
    set(_t, prop: string, value) {
      target[prop] = value;
      return true;
    },
  });
  return { ctx, calls };
}

describe('SevilleNightComponent', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  const create = async () => {
    await TestBed.configureTestingModule({ imports: [SevilleNightComponent] }).compileComponents();
    const fixture = TestBed.createComponent(SevilleNightComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    return fixture;
  };

  it('is decorative and does nothing when the canvas has no 2D context', async () => {
    const getContext = vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(null);
    const fixture = await create();
    const canvas: HTMLCanvasElement = fixture.nativeElement.querySelector('canvas');
    expect(canvas.getAttribute('aria-hidden')).toBe('true');
    expect(canvas.width).toBe(1920);
    expect(canvas.height).toBe(1080);
    expect(getContext).toHaveBeenCalledWith('2d');
  });

  it('draws the final frame at once under reduced motion and schedules no animation frame', async () => {
    const { ctx, calls } = fakeContext();
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(ctx as CanvasRenderingContext2D);
    vi.stubGlobal('matchMedia', (query: string) => ({ matches: query.includes('reduce') }));
    const frames: FrameRequestCallback[] = [];
    vi.spyOn(globalThis, 'requestAnimationFrame').mockImplementation((cb) => frames.push(cb));
    await create();
    expect(calls.fillRect).toBeGreaterThan(0);
    const drawn = calls.fillRect;
    frames.forEach((cb) => cb(1000)); // Angular's own frames must not draw the scene
    expect(calls.fillRect).toBe(drawn);
  });

  it('animates with requestAnimationFrame and cancels it on destroy', async () => {
    const { ctx, calls } = fakeContext();
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(ctx as CanvasRenderingContext2D);
    vi.stubGlobal('matchMedia', () => ({ matches: false }));
    const frames: FrameRequestCallback[] = [];
    vi.spyOn(globalThis, 'requestAnimationFrame').mockImplementation((cb) => frames.push(cb));
    const cancel = vi.spyOn(globalThis, 'cancelAnimationFrame').mockImplementation(() => undefined);
    const fixture = await create();
    const before = calls.fillRect;
    frames.forEach((cb) => cb(0));
    expect(calls.fillRect).toBeGreaterThan(before);
    fixture.destroy();
    expect(cancel).toHaveBeenCalled();
  });
});
