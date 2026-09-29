import { InjectionToken } from '@angular/core';

export type LeafletModule = typeof import('leaflet');

/**
 * Loads Leaflet on demand so it stays in the lazy /mapa chunk and never runs on the server.
 * Overridable in tests (jsdom has no layout, so the real map is not rendered there).
 */
export const LEAFLET_LOADER = new InjectionToken<() => Promise<LeafletModule>>('LEAFLET_LOADER', {
  providedIn: 'root',
  factory: () => async () => {
    const mod = await import('leaflet');
    // Leaflet ships as UMD; depending on the bundler interop the API may sit under `default`.
    return ((mod as unknown as { default?: LeafletModule }).default ?? mod) as LeafletModule;
  },
});
