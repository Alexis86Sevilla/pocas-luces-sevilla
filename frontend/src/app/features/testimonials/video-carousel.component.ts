import { afterNextRender, Component, ElementRef, input, signal, viewChild } from '@angular/core';

import { VideoCardComponent } from './video-card.component';
import type { VideoTestimonial } from '../../core/models';

@Component({
  selector: 'app-video-carousel',
  imports: [VideoCardComponent],
  templateUrl: './video-carousel.component.html',
  styleUrl: './video-carousel.component.css',
})
export class VideoCarouselComponent {
  readonly testimonials = input.required<readonly VideoTestimonial[]>();

  private readonly scrollContainer = viewChild<ElementRef<HTMLDivElement>>('scrollContainer');

  protected readonly canScrollLeft = signal(false);
  protected readonly canScrollRight = signal(false);

  constructor() {
    afterNextRender(() => {
      this.updateScrollState();
      // Recalculate when the container's dimensions settle (flexbox, images, etc.).
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
