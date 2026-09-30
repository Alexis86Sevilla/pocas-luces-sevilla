import { TestBed } from '@angular/core/testing';

import { MethodologySectionComponent } from './methodology-section.component';

describe('MethodologySectionComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MethodologySectionComponent],
    }).compileComponents();
  });

  it('renders under the #metodologia anchor so it can be linked to directly', () => {
    const fixture = TestBed.createComponent(MethodologySectionComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('#metodologia')).toBeTruthy();
  });

  it('explains what is exact, approximate and estimated', () => {
    const fixture = TestBed.createComponent(MethodologySectionComponent);
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent;

    expect(text).toContain('Exacto');
    expect(text).toContain('Aproximado');
    expect(text).toContain('Estimación de Endesa');
    expect(text).toContain('suministro');
  });

  it('discloses the known limitations, including the pre-28/09/2026 undercount', () => {
    const fixture = TestBed.createComponent(MethodologySectionComponent);
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent;

    expect(text).toContain('menos de 5 minutos');
    expect(text).toContain('28/09/2026');
  });

  it('explains the brief outage label: kept, counted, and not announced on Telegram', () => {
    const fixture = TestBed.createComponent(MethodologySectionComponent);
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent;

    expect(text).toContain('Corte breve');
    expect(text).toContain('cuenta en todos los totales');
    expect(text).toContain('dos consultas seguidas');
    expect(text).not.toContain('nosotros');
    const dictionary = fixture.nativeElement.querySelector('#diccionario-datos');
    expect(dictionary.textContent).toContain('brief');
  });

  it('links to the open-source repository and reuses the footer contact email', () => {
    const fixture = TestBed.createComponent(MethodologySectionComponent);
    fixture.detectChanges();

    const githubLink: HTMLAnchorElement = fixture.nativeElement.querySelector('a[href*="github.com"]');
    expect(githubLink).toBeTruthy();
    expect(fixture.nativeElement.querySelector('a[href="mailto:info@sevillasinluz.es"]')).toBeTruthy();
  });

  it('offers the CSV downloads with license, citation and a data dictionary', () => {
    const fixture = TestBed.createComponent(MethodologySectionComponent);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;

    const hrefs = Array.from(el.querySelectorAll<HTMLAnchorElement>('a[download]')).map(a => a.getAttribute('href'));
    expect(hrefs.length).toBe(4);
    expect(hrefs[0]).toMatch(/\/open-data\/outages\.csv$/);
    expect(hrefs[1]).toMatch(/\/open-data\/outages\.csv\?format=excel$/);
    expect(hrefs[2]).toMatch(/\/open-data\/outages\.csv\?year=\d{4}$/);
    expect(hrefs[3]).toMatch(/\/open-data\/outages\.csv\?year=\d{4}&format=excel$/);
    expect(el.textContent).toContain('abrirse con doble clic en Excel en español');
    expect(el.querySelector('a[href="https://creativecommons.org/licenses/by/4.0/deed.es"]')).toBeTruthy();
    expect(el.textContent).toContain('Diccionario de datos (CSV)');
    expect(el.querySelectorAll('#diccionario-datos tbody tr').length).toBe(16);
    expect(el.textContent).toContain('observed_end');
  });
});
