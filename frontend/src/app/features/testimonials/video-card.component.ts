import { Component, computed, inject, input } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';

import type { VideoTestimonial } from '../../core/models';

@Component({
  selector: 'app-video-card',
  imports: [],
  templateUrl: './video-card.component.html',
})
export class VideoCardComponent {
  private readonly sanitizer = inject(DomSanitizer);

  readonly video = input.required<VideoTestimonial>();

  protected readonly safeUrl = computed(() =>
    this.sanitizer.bypassSecurityTrustResourceUrl(this.video().embedUrl),
  );
}
