import { Component, signal } from '@angular/core';

const HERO_SEEN_KEY = 'hero-animation-seen';

@Component({
  selector: 'app-hero',
  imports: [],
  templateUrl: './hero.component.html',
  styleUrl: './hero.component.css',
})
export class HeroComponent {
  protected readonly animate: boolean;
  protected readonly isGrayscale = signal(false);

  constructor() {
    const reducedMotion = globalThis.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
    const hasSeen = globalThis.localStorage?.getItem(HERO_SEEN_KEY) === 'true';

    this.animate = !reducedMotion && !hasSeen;
    this.isGrayscale.set(reducedMotion || hasSeen);

    if (this.animate) {
      globalThis.localStorage?.setItem(HERO_SEEN_KEY, 'true');
    }
  }

  protected onLastBulbFinished(): void {
    this.isGrayscale.set(true);
  }

  protected scrollToOutages(): void {
    globalThis.document?.getElementById('outages')?.scrollIntoView({ behavior: 'smooth' });
  }
}
