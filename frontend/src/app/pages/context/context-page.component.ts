import { Component } from '@angular/core';

import { ContextSectionComponent } from './sections/context-section/context-section.component';
import { VideoCarouselComponent } from './sections/testimonials/video-carousel/video-carousel.component';

@Component({
  selector: 'app-context-page',
  imports: [ContextSectionComponent, VideoCarouselComponent],
  template: `
    <main class="bg-gray-50">
      <app-context-section />

      <section class="bg-gray-50 py-16">
        <div class="mx-auto max-w-6xl px-6">
          <app-video-carousel />
        </div>
      </section>
    </main>
  `,
})
export class ContextPageComponent {}
