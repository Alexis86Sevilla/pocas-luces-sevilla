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
    globalThis.scrollTo({ top: 0, behavior: this.scrollBehavior() });
  }

  /** Smooth-scrolls to the support section; the plain #apoyar href remains the fallback. */
  protected scrollToSupport(event: Event): void {
    const target = globalThis.document?.getElementById('apoyar');
    if (!target) return;
    event.preventDefault();
    target.scrollIntoView({ behavior: this.scrollBehavior(), block: 'start' });
  }

  private scrollBehavior(): ScrollBehavior {
    const reducedMotion = globalThis.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
    return reducedMotion ? 'auto' : 'smooth';
  }
}
