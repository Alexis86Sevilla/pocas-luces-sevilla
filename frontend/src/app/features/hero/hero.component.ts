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

    if (reducedMotion || hasSeen) {
      this.animate = false;
      this.isGrayscale.set(true);
    } else {
      this.animate = true;
      globalThis.localStorage?.setItem(HERO_SEEN_KEY, 'true');
      // Sync with CSS animation duration (3s)
      globalThis.setTimeout(() => this.isGrayscale.set(true), 3000);
    }
  }

  protected scrollToOutages(): void {
    globalThis.document?.getElementById('outages')?.scrollIntoView({ behavior: 'smooth' });
  }
}
