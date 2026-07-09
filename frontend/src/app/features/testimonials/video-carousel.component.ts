import { Component, input } from '@angular/core';

import { VideoCardComponent } from './video-card.component';
import type { VideoTestimonial } from '../../core/models';

@Component({
  selector: 'app-video-carousel',
  imports: [VideoCardComponent],
  templateUrl: './video-carousel.component.html',
})
export class VideoCarouselComponent {
  readonly testimonials = input.required<readonly VideoTestimonial[]>();
}
