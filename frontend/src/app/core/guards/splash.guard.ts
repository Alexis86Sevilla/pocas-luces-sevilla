import { inject } from '@angular/core';
import { type CanActivateFn, Router } from '@angular/router';

export const SPLASH_DISMISSED_KEY = 'splash-dismissed';

export const splashGuard: CanActivateFn = () => {
  const router = inject(Router);
  const dismissed = globalThis.localStorage?.getItem(SPLASH_DISMISSED_KEY) === 'true';

  if (dismissed) {
    return true;
  }

  return router.createUrlTree(['/splash']);
};
