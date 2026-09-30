import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { filter } from 'rxjs';

import { TELEGRAM_CHANNEL_URL } from '../../core/config/social';

@Component({
  selector: 'app-nav',
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './nav.component.html',
  host: { '(document:keydown.escape)': 'closeMenu(true)' },
})
export class NavComponent {
  private readonly router = inject(Router);

  protected readonly menuOpen = signal(false);
  protected readonly telegramUrl = TELEGRAM_CHANNEL_URL;

  constructor() {
    this.router.events
      .pipe(filter(e => e instanceof NavigationEnd), takeUntilDestroyed())
      .subscribe(() => this.menuOpen.set(false));
  }

  protected toggleMenu(): void {
    this.menuOpen.update(open => !open);
  }

  protected closeMenu(restoreFocus = false): void {
    if (!this.menuOpen()) return;
    this.menuOpen.set(false);
    if (restoreFocus) globalThis.document?.getElementById('main-menu-button')?.focus();
  }

  /**
   * Fragment-only navigation to the URL we are already on is ignored by the router, so
   * a second click on "Apoyar" after scrolling away would do nothing; scroll directly then.
   */
  protected onSupportClick(): void {
    if (this.router.parseUrl(this.router.url).fragment === 'apoyar') {
      globalThis.document?.getElementById('apoyar')?.scrollIntoView({ behavior: 'auto', block: 'start' });
    }
    this.menuOpen.set(false);
  }
}
