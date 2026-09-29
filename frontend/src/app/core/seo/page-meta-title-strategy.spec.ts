import { DOCUMENT } from '@angular/common';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Title } from '@angular/platform-browser';
import { provideRouter, Router, TitleStrategy } from '@angular/router';

import { PageMetaTitleStrategy } from './page-meta-title-strategy';

@Component({ selector: 'app-stub', template: '' })
class StubComponent {}

describe('PageMetaTitleStrategy', () => {
  let router: Router;
  let doc: Document;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: '', component: StubComponent, title: 'Inicio', data: { description: 'Desc inicio' } },
          { path: 'datos', component: StubComponent, title: 'Datos', data: { description: 'Desc datos' } },
        ]),
        { provide: TitleStrategy, useClass: PageMetaTitleStrategy },
      ],
    });
    router = TestBed.inject(Router);
    doc = TestBed.inject(DOCUMENT);
  });

  const canonical = () => doc.head.querySelector('link[rel="canonical"]')?.getAttribute('href');
  const description = () => doc.head.querySelector('meta[name="description"]')?.getAttribute('content');

  it('sets title, description and canonical for the home page', async () => {
    await router.navigateByUrl('/?anio=2026&mes=7#en-directo');
    expect(TestBed.inject(Title).getTitle()).toBe('Inicio');
    expect(description()).toBe('Desc inicio');
    expect(canonical()).toBe('https://sevillasinluz.es/');
  });

  it('updates all three on navigation and ignores query and fragment in the canonical', async () => {
    await router.navigateByUrl('/datos#metodologia');
    expect(TestBed.inject(Title).getTitle()).toBe('Datos');
    expect(description()).toBe('Desc datos');
    expect(canonical()).toBe('https://sevillasinluz.es/datos');
  });
});
