import { afterNextRender, ChangeDetectionStrategy, Component, DestroyRef, ElementRef, inject, viewChild } from '@angular/core';
import { createSevilleNightScene, SCENE_END, SCENE_HEIGHT, SCENE_WIDTH } from './seville-night-scene';

/**
 * Decorative hero background: Seville at night whose lights flicker and switch off once, then rest
 * on the final moonlit frame. Reduced motion, hidden tabs and a missing canvas context draw (or do)
 * nothing animated.
 */
@Component({
  selector: 'app-seville-night',
  template: `<canvas
    #canvas
    class="scene"
    [attr.width]="width"
    [attr.height]="height"
    aria-hidden="true"
  ></canvas>`,
  styles: `
    :host {
      display: block;
      pointer-events: none;
    }
    .scene {
      display: block;
      width: 100%;
      height: 100%;
      object-fit: cover;
      /* Phone portrait shows about x 440-980 of the 1920px scene: Giralda (x 585) and the cathedral. */
      object-position: 32% 40%;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SevilleNightComponent {
  protected readonly width = SCENE_WIDTH;
  protected readonly height = SCENE_HEIGHT;

  private readonly canvas = viewChild.required<ElementRef<HTMLCanvasElement>>('canvas');
  private raf = 0;

  constructor() {
    inject(DestroyRef).onDestroy(() => cancelAnimationFrame(this.raf));

    afterNextRender(() => {
      const ctx = this.canvas().nativeElement.getContext('2d');
      if (!ctx) return; // no 2D canvas (test DOM): nothing to draw
      const scene = createSevilleNightScene(ctx);

      const reduced = globalThis.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
      if (reduced || document.hidden) {
        scene.render(SCENE_END);
        return;
      }

      let t0: number | null = null;
      const frame = (now: number): void => {
        if (document.hidden) {
          scene.render(SCENE_END);
          return;
        }
        t0 ??= now;
        const t = (now - t0) / 1000;
        scene.render(Math.min(t, SCENE_END));
        if (t < SCENE_END) this.raf = requestAnimationFrame(frame);
      };
      this.raf = requestAnimationFrame(frame);
    });
  }
}
