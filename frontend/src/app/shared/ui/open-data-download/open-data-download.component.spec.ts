import { TestBed } from '@angular/core/testing';

import { OpenDataDownloadComponent } from './open-data-download.component';

describe('OpenDataDownloadComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [OpenDataDownloadComponent] }).compileComponents();
  });

  function create(inputs: Record<string, unknown>) {
    const fixture = TestBed.createComponent(OpenDataDownloadComponent);
    for (const [k, v] of Object.entries(inputs)) fixture.componentRef.setInput(k, v);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('links to the selected year and month with an accessible label', () => {
    const el = create({ year: 2026, month: 9, label: 'Descargar CSV del mes', ariaLabel: 'Descargar los cortes de septiembre de 2026 en CSV' });
    const [a, excel] = Array.from(el.querySelectorAll<HTMLAnchorElement>('a[download]'));

    expect(a.getAttribute('href')).toMatch(/\/open-data\/outages\.csv\?year=2026&month=9$/);
    expect(a.getAttribute('aria-label')).toBe('Descargar los cortes de septiembre de 2026 en CSV');
    expect(excel.getAttribute('href')).toMatch(/\/open-data\/outages\.csv\?year=2026&month=9&format=excel$/);
  });

  it('shows a second link for the Excel variant with its own accessible label', () => {
    const el = create({ excelLabel: 'Excel', excelAriaLabel: 'Descargar la versión para Excel' });
    const links = Array.from(el.querySelectorAll<HTMLAnchorElement>('a[download]'));

    expect(links.length).toBe(2);
    expect(links[0].textContent).toContain('Descargar CSV');
    expect(links[1].textContent).toContain('Excel');
    expect(links[1].getAttribute('aria-label')).toBe('Descargar la versión para Excel');
    expect(links[1].getAttribute('href')).toMatch(/\/open-data\/outages\.csv\?format=excel$/);
    expect(create({}).querySelectorAll('a[download]')[1].getAttribute('aria-label')).toBe('CSV para Excel');
  });

  it('links to the full dataset when no scope is set', () => {
    const el = create({});
    expect((el.querySelector('a[download]') as HTMLAnchorElement).getAttribute('href')).toMatch(/\/open-data\/outages\.csv$/);
  });

  it('shows the CC BY 4.0 notice and citation only when asked', () => {
    expect(create({}).textContent).not.toContain('CC BY 4.0');

    const el = create({ showLicense: true });
    const license = el.querySelector('a[href="https://creativecommons.org/licenses/by/4.0/deed.es"]') as HTMLAnchorElement;
    expect(license.getAttribute('rel')).toBe('noopener noreferrer');
    expect(license.getAttribute('target')).toBe('_blank');
    expect(el.textContent).toContain('«Sevilla Sin Luz (sevillasinluz.es), a partir de datos públicos de e-distribución (Grupo Endesa)»');
  });
});
