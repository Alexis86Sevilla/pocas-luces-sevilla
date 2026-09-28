import { afterNextRender, Component, ElementRef, inject, OnInit, signal, viewChild } from '@angular/core';
import { HttpClient } from '@angular/common/http';

import { VideoCardComponent } from '../video-card/video-card.component';
import type { VideoTestimonial } from '../../../core/models';
import { ErrorLogService } from '../../../core/services/error-log.service';
import { environment } from '../../../../environments/environment';

@Component({
  selector: 'app-video-carousel',
  imports: [VideoCardComponent],
  templateUrl: './video-carousel.component.html',
  styleUrl: './video-carousel.component.css',
})
export class VideoCarouselComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly errorLog = inject(ErrorLogService);

  protected readonly testimonials = signal<readonly VideoTestimonial[]>([]);
  protected readonly error = signal(false);

  private readonly scrollContainer = viewChild<ElementRef<HTMLDivElement>>('scrollContainer');

  protected readonly hasContent = signal(false);

  ngOnInit(): void {
    this.loadTestimonials();
  }

  protected loadTestimonials(): void {
    this.error.set(false);
    this.http.get<VideoTestimonial[]>(`${environment.apiBaseUrl}/testimonials`)
      .subscribe({
        next: data => {
          this.testimonials.set(data);
          this.hasContent.set(data.length > 0);
        },
        error: err => {
          this.error.set(true);
          this.hasContent.set(false);
          this.errorLog.log('API Testimonials', err);
        },
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
