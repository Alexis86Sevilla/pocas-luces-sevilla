import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { routes } from './app.routes';
import { ContextPageComponent } from './pages/context/context-page.component';
import { DataPageComponent } from './pages/data/data-page.component';
import { NotFoundPageComponent } from './pages/not-found/not-found-page.component';
import { MapPageComponent } from './pages/map/map-page.component';

describe('app routes', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: [provideRouter(routes)] }));

  it('lazy-loads the contexto page', async () => {
    const harness = await RouterTestingHarness.create();
    const component = await harness.navigateByUrl('/contexto');
    expect(component).toBeInstanceOf(ContextPageComponent);
  });

  it('lazy-loads the mapa page', async () => {
    const harness = await RouterTestingHarness.create();
    const component = await harness.navigateByUrl('/mapa');
    expect(component).toBeInstanceOf(MapPageComponent);
  });

  it('lazy-loads the datos page', async () => {
    const harness = await RouterTestingHarness.create();
    const component = await harness.navigateByUrl('/datos#metodologia');
    expect(component).toBeInstanceOf(DataPageComponent);
  });

  it('renders the not-found page for unknown paths without redirecting', async () => {
    const harness = await RouterTestingHarness.create();
    const component = await harness.navigateByUrl('/nope?anio=2026');
    expect(component).toBeInstanceOf(NotFoundPageComponent);
    expect(TestBed.inject(Router).url).toBe('/nope?anio=2026');
  });
});
