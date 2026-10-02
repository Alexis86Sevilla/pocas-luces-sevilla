import { DOCUMENT } from '@angular/common';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { NotFoundPageComponent } from './not-found-page.component';

describe('NotFoundPageComponent', () => {
  const robots = () => TestBed.inject(DOCUMENT).head.querySelectorAll('meta[name="robots"]');

  beforeEach(() => TestBed.configureTestingModule({ imports: [NotFoundPageComponent], providers: [provideRouter([])] }));

  it('renders one h1 and a link home', () => {
    const fixture = TestBed.createComponent(NotFoundPageComponent);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelectorAll('h1').length).toBe(1);
    expect(el.querySelector('h1')?.textContent).toContain('Página no encontrada');
    expect(el.querySelector('a')?.getAttribute('href')).toBe('/');
  });

  it('sets noindex while active and removes it when destroyed', () => {
    const fixture = TestBed.createComponent(NotFoundPageComponent);
    fixture.detectChanges();
    expect(robots().length).toBe(1);
    expect(robots()[0].getAttribute('content')).toBe('noindex');
    fixture.destroy();
    expect(robots().length).toBe(0);
  });

  it('restores the existing robots tag when destroyed', () => {
    const doc = TestBed.inject(DOCUMENT);
    const tag = doc.createElement('meta');
    tag.setAttribute('name', 'robots');
    tag.setAttribute('content', 'index, follow');
    doc.head.appendChild(tag);
    const fixture = TestBed.createComponent(NotFoundPageComponent);
    fixture.detectChanges();
    expect(robots()[0].getAttribute('content')).toBe('noindex');
    fixture.destroy();
    expect(robots()[0].getAttribute('content')).toBe('index, follow');
    tag.remove();
  });
});
