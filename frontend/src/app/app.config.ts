import { registerLocaleData } from '@angular/common';
import localeEs from '@angular/common/locales/es';
import { ApplicationConfig, LOCALE_ID, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter, TitleStrategy, withInMemoryScrolling, withViewTransitions } from '@angular/router';

import { routes } from './app.routes';
import { PageMetaTitleStrategy } from './core/seo/page-meta-title-strategy';

// Registered once at bootstrap so DecimalPipe/CurrencyPipe render with Spanish
// grouping/decimal separators. Date formatting is unaffected: dates go through
// parseMadridDate + an explicit 'Europe/Madrid' timezone, not the locale.
registerLocaleData(localeEs);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideHttpClient(),
    provideRouter(
      routes,
      withInMemoryScrolling({ anchorScrolling: 'enabled', scrollPositionRestoration: 'enabled' }),
      // Cross-fade between pages where the View Transitions API exists; skipped for reduced motion.
      withViewTransitions({
        // The first render has no previous page to fade from; animating it only flashes the shell.
        skipInitialTransition: true,
        onViewTransitionCreated: ({ transition }) => {
          if (globalThis.matchMedia?.('(prefers-reduced-motion: reduce)').matches) {
            transition.skipTransition();
          }
        },
      }),
    ),
    { provide: TitleStrategy, useClass: PageMetaTitleStrategy },
    { provide: LOCALE_ID, useValue: 'es-ES' },
  ]
};
