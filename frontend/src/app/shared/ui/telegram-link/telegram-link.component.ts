import { Component, computed, input } from '@angular/core';

import { TELEGRAM_CHANNEL_URL } from '../../../core/config/social';

/**
 * "Recibe avisos en Telegram": external link to the public channel where new outages
 * and restorations are announced. Opens in a new tab with `rel="noopener noreferrer"`.
 */
@Component({
  selector: 'app-telegram-link',
  imports: [],
  templateUrl: './telegram-link.component.html',
})
export class TelegramLinkComponent {
  /** Visual theme: 'light' for white/light backgrounds, 'dark' for dark sections. */
  readonly variant = input<'light' | 'dark'>('light');

  protected readonly href = TELEGRAM_CHANNEL_URL;

  protected readonly linkClasses = computed(() => {
    const base =
      'inline-flex items-center gap-2 rounded-lg border px-3 py-2 text-sm font-semibold transition focus:outline-none focus-visible:ring-2 focus-visible:ring-amber-400 focus-visible:ring-offset-2';
    const theme =
      this.variant() === 'dark'
        ? 'border-gray-600 bg-gray-700 text-gray-200 hover:bg-gray-600'
        : 'border-gray-300 bg-white text-gray-700 hover:bg-gray-50';
    return `${base} ${theme}`;
  });
}
