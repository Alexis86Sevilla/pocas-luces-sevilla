import { afterNextRender, Component, ElementRef, inject, OnInit, signal, viewChild } from '@angular/core';
import { HttpClient } from '@angular/common/http';

import { VideoCardComponent } from './video-card.component';
import type { VideoTestimonial } from '../../core/models';
import { environment } from '../../../environments/environment';

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

  protected readonly canScrollLeft = signal(false);
  protected readonly canScrollRight = signal(false);

  ngOnInit(): void {
    this.http.get<VideoTestimonial[]>(`${environment.apiBaseUrl}/testimonials`)
      .subscribe(data => this.testimonials.set(data));
  }

  constructor() {
    afterNextRender(() => {
      this.updateScrollState();
      const container = this.scrollContainer()?.nativeElement;
      if (container) {
        const observer = new ResizeObserver(() => this.updateScrollState());
        observer.observe(container);
      }
    });
  }

  protected scrollLeft(): void {
    const container = this.scrollContainer()?.nativeElement;
    if (!container) return;
    container.scrollBy({ left: -container.clientWidth * 0.85, behavior: 'smooth' });
  }

  protected scrollRight(): void {
    const container = this.scrollContainer()?.nativeElement;
    if (!container) return;
    container.scrollBy({ left: container.clientWidth * 0.85, behavior: 'smooth' });
  }

  protected onScroll(): void {
    this.updateScrollState();
  }

  private updateScrollState(): void {
    const container = this.scrollContainer()?.nativeElement;
    if (!container) return;
    this.canScrollLeft.set(container.scrollLeft > 10);
    this.canScrollRight.set(
      container.scrollLeft + container.clientWidth < container.scrollWidth - 10,
    );
  }
}
