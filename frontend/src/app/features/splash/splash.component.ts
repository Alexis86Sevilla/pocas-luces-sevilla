import { Component, output, signal } from '@angular/core';

type SplashPhase = 'color' | 'blackout' | 'done';

@Component({
  selector: 'app-splash',
  templateUrl: './splash.component.html',
  styleUrl: './splash.component.css',
})
export class SplashComponent {
  readonly finished = output<void>();

  protected readonly phase = signal<SplashPhase>('color');
  protected readonly reducedMotion = signal(false);

  constructor() {
    const media = globalThis.matchMedia?.('(prefers-reduced-motion: reduce)');
    this.reducedMotion.set(media?.matches ?? false);

    if (this.reducedMotion()) {
      this.phase.set('done');
      globalThis.setTimeout(() => this.finished.emit(), 0);
      return;
    }

    globalThis.setTimeout(() => this.phase.set('blackout'), 3000);
    globalThis.setTimeout(() => {
      this.phase.set('done');
      this.finished.emit();
    }, 4000);
  }
}
