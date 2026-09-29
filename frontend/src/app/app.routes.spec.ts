import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { routes } from './app.routes';
import { ContextoPageComponent } from './features/contexto-page/contexto-page.component';
import { DatosPageComponent } from './features/datos-page/datos-page.component';

describe('app routes', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: [provideRouter(routes)] }));

  it('lazy-loads the contexto page', async () => {
    const harness = await RouterTestingHarness.create();
    const component = await harness.navigateByUrl('/contexto');
    expect(component).toBeInstanceOf(ContextoPageComponent);
  });

  it('lazy-loads the datos page', async () => {
    const harness = await RouterTestingHarness.create();
    const component = await harness.navigateByUrl('/datos#metodologia');
    expect(component).toBeInstanceOf(DatosPageComponent);
  });

  it('redirects unknown paths home, keeping query and fragment', async () => {
    await TestBed.inject(Router).navigateByUrl('/nope?anio=2026&mes=7#en-directo');
    expect(TestBed.inject(Router).url).toBe('/?anio=2026&mes=7#en-directo');
  });
});
