import { Component, DestroyRef, computed, inject, input, signal } from '@angular/core';

type ShareStatus = 'idle' | 'copied' | 'manual';

const COPIED_STATUS_DURATION_MS = 4000;

/**
 * Reusable "Compartir" button. Uses the native Web Share API when available;
 * otherwise copies the current URL to the clipboard, falling back to a
 * selectable text field if the clipboard write also fails.
 */
@Component({
  selector: 'app-share-button',
  imports: [],
  templateUrl: './share-button.component.html',
})
export class ShareButtonComponent {
  readonly shareTitle = input('Sevilla Sin Luz');
  readonly shareText = input('Cortes de luz en Sevilla: datos abiertos de Endesa por distrito');
  /** Visual theme: 'light' for white/light backgrounds, 'dark' for dark sections. */
  readonly variant = input<'light' | 'dark'>('light');

  protected readonly status = signal<ShareStatus>('idle');
  protected readonly shareUrl = signal('');

  protected readonly buttonClasses = computed(() => {
    const base =
      'inline-flex cursor-pointer items-center gap-2 rounded-lg border px-3 py-2 text-sm font-semibold transition focus:outline-none focus-visible:ring-2 focus-visible:ring-amber-400 focus-visible:ring-offset-2';
    const theme =
      this.variant() === 'dark'
        ? 'border-gray-600 bg-gray-700 text-gray-200 hover:bg-gray-600'
        : 'border-gray-300 bg-white text-gray-700 hover:bg-gray-50';
    return `${base} ${theme}`;
  });

  protected readonly statusTextClass = computed(() =>
    this.variant() === 'dark' ? 'text-emerald-400' : 'text-emerald-600'
  );

  protected readonly manualTextClass = computed(() => (this.variant() === 'dark' ? 'text-gray-300' : 'text-gray-500'));

  protected readonly manualInputClass = computed(() =>
    this.variant() === 'dark'
      ? 'w-48 rounded border border-gray-600 bg-gray-800 px-1 py-0.5 text-gray-200'
      : 'w-48 rounded border border-gray-300 bg-gray-50 px-1 py-0.5 text-gray-700'
  );

  private clearTimeoutId: ReturnType<typeof globalThis.setTimeout> | undefined;

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      if (this.clearTimeoutId !== undefined) globalThis.clearTimeout(this.clearTimeoutId);
    });
  }

  protected async share(): Promise<void> {
    const url = globalThis.location?.href ?? '';
    const nav = globalThis.navigator;

    if (nav?.share) {
      try {
        await nav.share({ title: this.shareTitle(), text: this.shareText(), url });
      } catch {
        // Cancelled by the user or unsupported at runtime; nothing else to do.
      }
      return;
    }

    if (!nav?.clipboard?.writeText) {
      this.showStatus('manual', url);
      return;
    }

    try {
      await nav.clipboard.writeText(url);
      this.showStatus('copied', url);
    } catch {
      this.showStatus('manual', url);
    }
  }

  protected selectInput(event: Event): void {
    (event.target as HTMLInputElement).select();
  }

  private showStatus(status: ShareStatus, url: string): void {
    this.status.set(status);
    this.shareUrl.set(url);
    if (this.clearTimeoutId !== undefined) globalThis.clearTimeout(this.clearTimeoutId);
    if (status === 'copied') {
      this.clearTimeoutId = globalThis.setTimeout(() => this.status.set('idle'), COPIED_STATUS_DURATION_MS);
    }
  }
}
