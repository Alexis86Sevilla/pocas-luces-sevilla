import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { VideoCarouselComponent } from './video-carousel.component';

describe('VideoCarouselComponent', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [VideoCarouselComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('shows a short error message with a retry button when the testimonials request fails', () => {
    const fixture = TestBed.createComponent(VideoCarouselComponent);
    fixture.detectChanges();

    httpMock.expectOne(r => r.url.endsWith('/testimonials'))
      .flush('boom', { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No se han podido cargar los testimonios');
    expect(fixture.nativeElement.textContent).not.toContain('Aún no hay testimonios');

    const retryButton: HTMLButtonElement = fixture.nativeElement.querySelector('button');
    retryButton.click();

    httpMock.expectOne(r => r.url.endsWith('/testimonials')).flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('No se han podido cargar');
  });

  it('shows the plain empty state (not an error) when the request succeeds with no testimonials', () => {
    const fixture = TestBed.createComponent(VideoCarouselComponent);
    fixture.detectChanges();

    httpMock.expectOne(r => r.url.endsWith('/testimonials')).flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Aún no hay testimonios disponibles.');
  });
});
