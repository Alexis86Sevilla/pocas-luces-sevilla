import { DOCUMENT } from '@angular/common';
import { Component, DestroyRef, inject } from '@angular/core';
import { Meta } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-not-found-page',
  imports: [RouterLink],
  template: `
    <main class="mx-auto max-w-2xl px-6 py-24 text-center">
      <h1 tabindex="-1" class="text-3xl font-extrabold text-gray-900 outline-none">Página no encontrada</h1>
      <p class="mt-4 text-gray-600">La dirección que buscas no existe o ha cambiado de sitio.</p>
      <p class="mt-8">
        <a routerLink="/" class="rounded-lg bg-amber-500 px-5 py-2.5 text-sm font-semibold text-gray-900 transition hover:bg-amber-400 focus:outline-none focus-visible:ring-2 focus-visible:ring-amber-600">Volver al inicio</a>
      </p>
    </main>
  `,
})
export class NotFoundPageComponent {
  constructor() {
    const meta = inject(Meta);
    // Flip the robots tag from index.html instead of adding a second one, and put back
    // exactly what was there when the page is left.
    const original = inject(DOCUMENT).head.querySelector('meta[name="robots"]')?.getAttribute('content');
    meta.updateTag({ name: 'robots', content: 'noindex' });
    inject(DestroyRef).onDestroy(() => {
      if (original != null) meta.updateTag({ name: 'robots', content: original });
      else meta.removeTag('name="robots"');
    });
  }
}
