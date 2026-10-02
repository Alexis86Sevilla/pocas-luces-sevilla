import { afterNextRender, Component, DestroyRef, inject, Injector, signal } from '@angular/core';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter, pairwise, startWith, take } from 'rxjs';

import { BackToTopComponent } from './layout/back-to-top/back-to-top.component';
import { SupportSectionComponent } from './layout/support-section/support-section.component';
import { FooterComponent } from './layout/footer/footer.component';
import { NavComponent } from './layout/nav/nav.component';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, BackToTopComponent, SupportSectionComponent, FooterComponent, NavComponent],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  protected readonly title = signal('frontend');

  constructor() {
    // index.html paints the page dark on '/' until the first route has rendered (see boot.js),
    // so the home hero never flashes white. Once that first navigation ends, hand the page
    // background back to the regular styles.
    const subscription = inject(Router).events
      .pipe(filter(event => event instanceof NavigationEnd), take(1))
      .subscribe(() => globalThis.document?.documentElement.classList.remove('boot-home'));
    const destroyRef = inject(DestroyRef);
    destroyRef.onDestroy(() => subscription.unsubscribe());

    // Screen-reader and keyboard users get no page load on client-side navigation, so move focus
    // to the new page's h1. Skipped on the first navigation (do not steal focus on load) and when
    // only the query string or fragment changed (e.g. the month filter, in-page anchors).
    const injector = inject(Injector);
    const focusSubscription = inject(Router).events
      .pipe(
        filter((event): event is NavigationEnd => event instanceof NavigationEnd),
        startWith(null),
        pairwise(),
      )
      .subscribe(([previous, current]) => {
        if (previous === null || current === null) return;
        if (pathOf(previous.urlAfterRedirects) === pathOf(current.urlAfterRedirects)) return;
        afterNextRender(() => this.focusHeading(), { injector });
      });
    destroyRef.onDestroy(() => focusSubscription.unsubscribe());
  }

  /** Skip link: a plain #hash href would be routed, so focus the content wrapper directly. */
  protected skipToContent(event: Event): void {
    event.preventDefault();
    globalThis.document?.getElementById('contenido')?.focus();
  }

  private focusHeading(): void {
    const heading = globalThis.document?.querySelector<HTMLElement>('#contenido h1');
    if (!heading) return;
    if (!heading.hasAttribute('tabindex')) heading.setAttribute('tabindex', '-1');
    heading.focus({ preventScroll: true });
  }
}

function pathOf(url: string): string {
  return url.split(/[?#]/)[0];
}
