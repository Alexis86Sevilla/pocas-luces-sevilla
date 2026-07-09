import { Component, input } from '@angular/core';

import type { Testimonial } from '../../core/models';

@Component({
  selector: 'app-testimonial-card',
  templateUrl: './testimonial-card.component.html',
  styleUrl: './testimonial-card.component.css',
})
export class TestimonialCardComponent {
  readonly testimonial = input.required<Testimonial>();
}
