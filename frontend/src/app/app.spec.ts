import { Component } from '@angular/core';
import { provideRouter, Router } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { App } from './app';

@Component({ selector: 'app-page-a', template: '<h1>Página A</h1>' })
class PageAComponent {}

@Component({ selector: 'app-page-b', template: '<h1>Página B</h1>' })
class PageBComponent {}

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([
          { path: '', component: PageAComponent },
          { path: 'b', component: PageBComponent },
        ]),
      ],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('renders a skip link that focuses the content wrapper', () => {
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    document.body.appendChild(el);
    const link = el.querySelector<HTMLAnchorElement>('a[href="#contenido"]');
    expect(link?.textContent).toContain('Saltar al contenido');
    link!.click();
    expect(document.activeElement?.id).toBe('contenido');
    el.remove();
  });

  it('focuses the h1 after a later navigation to another page, but not on the first one', async () => {
    const fixture = TestBed.createComponent(App);
    const el: HTMLElement = fixture.nativeElement;
    document.body.appendChild(el);
    const router = TestBed.inject(Router);

    await router.navigateByUrl('/');
    await fixture.whenStable();
    expect(el.querySelector('h1')).not.toBe(document.activeElement);

    await router.navigateByUrl('/b');
    await fixture.whenStable();
    fixture.detectChanges();
    await fixture.whenStable();
    const heading = el.querySelector('h1');
    expect(heading?.textContent).toBe('Página B');
    expect(document.activeElement).toBe(heading);
    el.remove();
  });

  it('does not move focus when only the query string changes', async () => {
    const fixture = TestBed.createComponent(App);
    const el: HTMLElement = fixture.nativeElement;
    document.body.appendChild(el);
    const router = TestBed.inject(Router);

    await router.navigateByUrl('/b');
    await router.navigateByUrl('/b?anio=2026');
    await fixture.whenStable();
    fixture.detectChanges();
    expect(document.activeElement).not.toBe(el.querySelector('h1'));
    el.remove();
  });
});
