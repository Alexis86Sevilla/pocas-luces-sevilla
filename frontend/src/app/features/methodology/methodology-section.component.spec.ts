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

  it('links to the open-source repository and reuses the footer contact email', () => {
    const fixture = TestBed.createComponent(MethodologySectionComponent);
    fixture.detectChanges();

    const githubLink: HTMLAnchorElement = fixture.nativeElement.querySelector('a[href*="github.com"]');
    expect(githubLink).toBeTruthy();
    expect(fixture.nativeElement.querySelector('a[href="mailto:info@sevillasinluz.es"]')).toBeTruthy();
  });
});
