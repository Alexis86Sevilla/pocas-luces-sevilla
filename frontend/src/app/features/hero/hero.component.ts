import { Component, OnDestroy, signal } from '@angular/core';

@Component({
  selector: 'app-hero',
  imports: [],
  templateUrl: './hero.component.html',
  styleUrl: './hero.component.css',
})
export class HeroComponent implements OnDestroy {
  protected readonly isGrayscale = signal(false);

  private timerId: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    const reducedMotion = globalThis.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;

    if (reducedMotion) {
      this.isGrayscale.set(true);
    } else {
      this.timerId = globalThis.setTimeout(() => this.isGrayscale.set(true), 4000);
    }
  }

  ngOnDestroy(): void {
    if (this.timerId !== null) {
      clearTimeout(this.timerId);
    }
  }

  protected scrollToOutages(): void {
    globalThis.document?.getElementById('outages')?.scrollIntoView({ behavior: 'smooth' });
  }
}
