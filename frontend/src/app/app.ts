import { Component, DestroyRef, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter, take } from 'rxjs';

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
    inject(DestroyRef).onDestroy(() => subscription.unsubscribe());
  }
}
