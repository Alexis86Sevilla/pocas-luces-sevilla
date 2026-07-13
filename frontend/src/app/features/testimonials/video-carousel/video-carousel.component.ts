import { afterNextRender, Component, ElementRef, inject, OnInit, signal, viewChild } from '@angular/core';
import { HttpClient } from '@angular/common/http';

import { VideoCardComponent } from '../video-card/video-card.component';
import type { VideoTestimonial } from '../../../core/models';
import { environment } from '../../../../environments/environment';

@Component({
  selector: 'app-video-carousel',
  imports: [VideoCardComponent],
  templateUrl: './video-carousel.component.html',
  styleUrl: './video-carousel.component.css',
})
export class VideoCarouselComponent implements OnInit {
  private readonly http = inject(HttpClient);

  protected readonly testimonials = signal<readonly VideoTestimonial[]>([]);

  private readonly scrollContainer = viewChild<ElementRef<HTMLDivElement>>('scrollContainer');

  protected readonly hasContent = signal(false);

  ngOnInit(): void {
    this.http.get<VideoTestimonial[]>(`${environment.apiBaseUrl}/testimonials`)
      .subscribe(data => {
        this.testimonials.set(data);
        this.hasContent.set(data.length > 0);
      });
  }

  constructor() {
    afterNextRender(() => {
      const container = this.scrollContainer()?.nativeElement;
      if (container) {
        new ResizeObserver(() => {
          this.hasContent.set(
            this.testimonials().length > 0
            && container.scrollWidth > container.clientWidth + 5
          );
        }).observe(container);
      }
    });
  }

  protected scrollLeft(): void {
    this.scrollContainer()?.nativeElement?.scrollBy({ left: -360, behavior: 'smooth' });
  }

  protected scrollRight(): void {
    this.scrollContainer()?.nativeElement?.scrollBy({ left: 360, behavior: 'smooth' });
  }
}
