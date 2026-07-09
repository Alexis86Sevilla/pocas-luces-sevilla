import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import { SPLASH_DISMISSED_KEY } from '../../core/guards/splash.guard';

type SplashPhase = 'color' | 'blackout' | 'done';

@Component({
  selector: 'app-splash',
  templateUrl: './splash.component.html',
  styleUrl: './splash.component.css',
})
export class SplashComponent {
  private readonly router = inject(Router);

  protected readonly phase = signal<SplashPhase>('color');
  protected readonly reducedMotion = signal(false);

  constructor() {
    const media = globalThis.matchMedia?.('(prefers-reduced-motion: reduce)');
    this.reducedMotion.set(media?.matches ?? false);

    if (this.reducedMotion()) {
      this.phase.set('done');
      this.dismiss();
      return;
    }

    this.startAnimation();
  }

  private startAnimation(): void {
    globalThis.setTimeout(() => this.phase.set('blackout'), 3000);
    globalThis.setTimeout(() => {
      this.phase.set('done');
      this.dismiss();
    }, 4000);
  }

  private dismiss(): void {
    globalThis.localStorage?.setItem(SPLASH_DISMISSED_KEY, 'true');
    void this.router.navigate(['/home']);
  }
}
