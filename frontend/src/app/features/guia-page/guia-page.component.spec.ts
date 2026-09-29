import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { vi } from 'vitest';

import { routes } from '../../app.routes';
import { CLAIM_TEMPLATE, GuiaPageComponent } from './guia-page.component';

@Component({ selector: 'app-stub', template: '' })
class StubComponent {}

describe('GuiaPageComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [GuiaPageComponent],
      providers: [provideRouter([{ path: '', component: StubComponent }]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  afterEach(() => {
    delete (globalThis.navigator as unknown as { clipboard?: unknown }).clipboard;
    vi.useRealTimers();
  });

  function createFixture() {
    const fixture = TestBed.createComponent(GuiaPageComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('renders the nine steps in order', () => {
    const el: HTMLElement = createFixture().nativeElement;
    const steps = [...el.querySelectorAll('[data-step]')];
    expect(steps.map(s => s.getAttribute('data-step'))).toEqual(['1', '2', '3', '4', '5', '6', '7', '8', '9']);
    expect(el.querySelector('h1')?.textContent).toContain('Qué hacer si te quedas sin luz');
  });

  it('exposes tap-to-call and mailto links with the verified contacts', () => {
    const el: HTMLElement = createFixture().nativeElement;
    const hrefs = [...el.querySelectorAll('a')].map(a => a.getAttribute('href'));
    for (const expected of [
      'tel:+34900850840',
      'tel:+34900878119',
      'tel:+34900215080',
      'tel:+34955472982',
      'tel:+34955472985',
      'tel:+34955472987',
      'tel:112',
      'mailto:omic.consumo@sevilla.org',
    ]) {
      expect(hrefs).toContain(expected);
    }
  });

  it('opens external sources safely and shows the disclaimer and consultation date', () => {
    const el: HTMLElement = createFixture().nativeElement;
    const external = [...el.querySelectorAll<HTMLAnchorElement>('#fuentes-titulo ~ ul a')];
    expect(external.length).toBe(10);
    for (const a of external) {
      expect(a.getAttribute('target')).toBe('_blank');
      expect(a.getAttribute('rel')).toBe('noopener noreferrer');
    }
    expect(el.textContent).toContain('Consultado el 29/09/2026');
    expect(el.textContent).toContain('no sustituye el asesoramiento legal');
  });

  it('links the CSV download of the selected month', () => {
    const el: HTMLElement = createFixture().nativeElement;
    const csv = el.querySelector<HTMLAnchorElement>('[data-step="4"] a[download]');
    expect(csv?.getAttribute('href')).toContain('/open-data');
  });

  it('copies the template and confirms with "Copiado"', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(globalThis.navigator, 'clipboard', { value: { writeText }, configurable: true });
    const fixture = createFixture();
    const button = [...(fixture.nativeElement as HTMLElement).querySelectorAll('button')].find(b =>
      b.textContent?.includes('Copiar texto'),
    )!;
    button.click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(writeText).toHaveBeenCalledWith(CLAIM_TEMPLATE);
    expect(fixture.nativeElement.querySelector('[aria-live="polite"]').textContent).toContain('Copiado');
  });

  it('falls back to manual copy when the clipboard is unavailable', async () => {
    const fixture = createFixture();
    const button = [...(fixture.nativeElement as HTMLElement).querySelectorAll('button')].find(b =>
      b.textContent?.includes('Copiar texto'),
    )!;
    button.click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Selecciona el texto');
    expect(fixture.nativeElement.querySelector('textarea')).not.toBeNull();
  });

  it('resolves the /guia route lazily', async () => {
    const route = routes.find(r => r.path === 'guia')!;
    expect(route.loadComponent).toBeTypeOf('function');
    expect(route.title).toContain('Qué hacer');
    const loaded = await (route.loadComponent as () => Promise<unknown>)();
    expect(loaded).toBe(GuiaPageComponent);
    expect(TestBed.inject(Router)).toBeTruthy();
  });
});
