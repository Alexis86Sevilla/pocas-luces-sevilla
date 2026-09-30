import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import { NavComponent } from './nav.component';

@Component({ selector: 'app-stub', template: '' })
class StubComponent {}

describe('NavComponent', () => {
  async function setup() {
    await TestBed.configureTestingModule({
      imports: [NavComponent],
      providers: [
        provideRouter([
          { path: '', component: StubComponent },
          { path: 'guia', component: StubComponent },
          { path: 'mapa', component: StubComponent },
          { path: 'contexto', component: StubComponent },
          { path: 'datos', component: StubComponent },
        ]),
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(NavComponent);
    fixture.detectChanges();
    return { fixture, router: TestBed.inject(Router), el: fixture.nativeElement as HTMLElement };
  }

  const button = (el: HTMLElement) => el.querySelector<HTMLButtonElement>('#main-menu-button')!;

  it('exposes a labelled main navigation with the page links', async () => {
    const { el } = await setup();
    expect(el.querySelector('nav')?.getAttribute('aria-label')).toBe('Principal');
    const hrefs = [...el.querySelectorAll('ul a')].map(a => a.getAttribute('href'));
    expect(hrefs).toContain('/');
    expect(hrefs).toContain('/guia');
    expect(hrefs).toContain('/mapa');
    expect(hrefs).toContain('/contexto');
    expect(hrefs).toContain('/datos');
  });

  it('links to the Telegram alerts channel in a new tab', async () => {
    const { el } = await setup();
    const links = [...el.querySelectorAll<HTMLAnchorElement>('a[href="https://t.me/SevillaSinLuz"]')];
    expect(links.length).toBeGreaterThan(0);
    for (const link of links) {
      expect(link.target).toBe('_blank');
      expect(link.rel).toContain('noopener');
    }
  });

  it('lists Qué hacer and Mapa between Inicio and Contexto, also in the mobile menu', async () => {
    const { fixture, el } = await setup();
    const desktop = [...el.querySelectorAll('ul a')].map(a => a.textContent?.trim());
    expect(desktop.slice(0, 4)).toEqual(['Inicio', 'Qué hacer', 'Mapa', 'Contexto']);
    button(el).click();
    fixture.detectChanges();
    const mobile = [...el.querySelectorAll('#main-menu a')].map(a => a.textContent?.trim());
    expect(mobile.slice(0, 4)).toEqual(['Inicio', 'Qué hacer', 'Mapa', 'Contexto']);
  });

  it('marks only the current page link with aria-current="page"', async () => {
    const { fixture, router, el } = await setup();
    await router.navigateByUrl('/contexto');
    fixture.detectChanges();
    const current = [...el.querySelectorAll('a[aria-current="page"]')];
    expect(current.length).toBe(1);
    expect(current[0].getAttribute('href')).toBe('/contexto');
  });

  it('toggles the mobile menu with aria-expanded and aria-controls', async () => {
    const { fixture, el } = await setup();
    expect(button(el).getAttribute('aria-expanded')).toBe('false');
    expect(el.querySelector('#main-menu')).toBeNull();

    button(el).click();
    fixture.detectChanges();
    expect(button(el).getAttribute('aria-expanded')).toBe('true');
    expect(el.querySelector('#' + button(el).getAttribute('aria-controls'))).not.toBeNull();
  });

  it('closes the mobile menu on Escape', async () => {
    const { fixture, el } = await setup();
    button(el).click();
    fixture.detectChanges();

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    expect(button(el).getAttribute('aria-expanded')).toBe('false');
  });

  it('closes the mobile menu after navigating', async () => {
    const { fixture, router, el } = await setup();
    button(el).click();
    fixture.detectChanges();

    await router.navigateByUrl('/datos');
    fixture.detectChanges();
    expect(button(el).getAttribute('aria-expanded')).toBe('false');
  });
});
