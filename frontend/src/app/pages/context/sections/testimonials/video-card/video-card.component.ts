import { Component, computed, inject, input } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { VideoTestimonial } from '../../../../../core/models/video-testimonial.model';

// Keep in sync with the backend allowlist in EmbedUrlValidation.
const ALLOWED_EMBED_HOSTS = new Set([
  'youtube.com',
  'www.youtube.com',
  'youtube-nocookie.com',
  'www.youtube-nocookie.com',
  'instagram.com',
  'www.instagram.com',
]);

function isAllowedEmbedUrl(embedUrl: string): boolean {
  try {
    const url = new URL(embedUrl);
    return url.protocol === 'https:' && ALLOWED_EMBED_HOSTS.has(url.hostname.toLowerCase());
  } catch {
    return false;
  }
}

@Component({
  selector: 'app-video-card',
  imports: [],
  templateUrl: './video-card.component.html',
})
export class VideoCardComponent {
  private readonly sanitizer = inject(DomSanitizer);

  readonly video = input.required<VideoTestimonial>();

  protected readonly isYouTube = computed(
    () => this.video().platform === 'youtube' && isAllowedEmbedUrl(this.video().embedUrl),
  );

  protected readonly safeUrl = computed<SafeResourceUrl | null>(() => {
    const embedUrl = this.video().embedUrl;
    return isAllowedEmbedUrl(embedUrl) ? this.sanitizer.bypassSecurityTrustResourceUrl(embedUrl) : null;
  });
}
