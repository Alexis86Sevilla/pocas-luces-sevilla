import { Component, DestroyRef, inject, signal } from '@angular/core';

@Component({
  selector: 'app-back-to-top',
  imports: [],
  templateUrl: './back-to-top.component.html',
})
export class BackToTopComponent {
  protected readonly visible = signal(false);

  constructor() {
    const onScroll = () => this.visible.set(globalThis.scrollY > globalThis.innerHeight);
    globalThis.addEventListener?.('scroll', onScroll, { passive: true });
    inject(DestroyRef).onDestroy(() => globalThis.removeEventListener?.('scroll', onScroll));
  }

  protected scrollToTop(): void {
    const reducedMotion = globalThis.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
    globalThis.scrollTo({ top: 0, behavior: reducedMotion ? 'auto' : 'smooth' });
  }
}
