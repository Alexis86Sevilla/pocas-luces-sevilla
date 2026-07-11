import { Injectable } from '@angular/core';

import { environment } from '../../../environments/environment';

/**
 * Central sink for runtime errors. In production it silently swallows them;
 * in development it forwards to the console so engineers can debug.
 */
@Injectable({ providedIn: 'root' })
export class ErrorLogService {
  log(context: string, error: unknown): void {
    if (!environment.production) {
      // eslint-disable-next-line no-console
      console.error(`[${context}]`, error);
    }
  }
}
