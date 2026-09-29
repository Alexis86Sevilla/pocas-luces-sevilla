import { Component } from '@angular/core';

import { ContextSectionComponent } from '../context/context-section.component';
import { VideoCarouselComponent } from '../testimonials/video-carousel/video-carousel.component';

@Component({
  selector: 'app-contexto-page',
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
export class ContextoPageComponent {}
