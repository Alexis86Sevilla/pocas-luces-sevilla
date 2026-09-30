import { TestBed } from '@angular/core/testing';
import { VideoCardComponent } from './video-card.component';
import { VideoTestimonial } from '../../../../../core/models/video-testimonial.model';

function createComponent(video: VideoTestimonial) {
  const fixture = TestBed.createComponent(VideoCardComponent);
  fixture.componentRef.setInput('video', video);
  fixture.detectChanges();
  return fixture;
}

describe('VideoCardComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [VideoCardComponent],
    }).compileComponents();
  });

  it('should trust and embed an allowed YouTube URL', () => {
    const fixture = createComponent({
      id: 1,
      authorName: 'Test',
      embedUrl: 'https://www.youtube.com/embed/abc123',
      platform: 'youtube',
    });

    const component = fixture.componentInstance as any;
    expect(component.isYouTube()).toBe(true);
    expect(component.safeUrl()).toBeTruthy();
  });

  it('should not treat a disallowed host as embeddable even when platform says youtube', () => {
    const fixture = createComponent({
      id: 2,
      authorName: 'Test',
      embedUrl: 'https://evil.com/embed/abc123',
      platform: 'youtube',
    });

    const component = fixture.componentInstance as any;
    expect(component.isYouTube()).toBe(false);
    expect(component.safeUrl()).toBeNull();
  });

  it('should not treat a non-https URL as embeddable', () => {
    const fixture = createComponent({
      id: 3,
      authorName: 'Test',
      embedUrl: 'http://www.youtube.com/embed/abc123',
      platform: 'youtube',
    });

    const component = fixture.componentInstance as any;
    expect(component.isYouTube()).toBe(false);
    expect(component.safeUrl()).toBeNull();
  });

  it('should not mark an instagram testimonial as youtube embeddable', () => {
    const fixture = createComponent({
      id: 4,
      authorName: 'Test',
      embedUrl: 'https://www.instagram.com/reel/abc123/',
      platform: 'instagram',
    });

    const component = fixture.componentInstance as any;
    expect(component.isYouTube()).toBe(false);
  });
});
