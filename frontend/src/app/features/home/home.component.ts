import { Component, inject } from '@angular/core';

import { OUTAGE_SERVICE } from '../../core/services/outage.service';
import { OutageCardComponent } from '../outages/outage-card.component';
import { TestimonialCardComponent } from '../testimonials/testimonial-card.component';

@Component({
  selector: 'app-home',
  imports: [OutageCardComponent, TestimonialCardComponent],
  templateUrl: './home.component.html',
  styleUrl: './home.component.css',
})
export class HomeComponent {
  private readonly outageService = inject(OUTAGE_SERVICE);

  protected readonly neighborhoods = this.outageService.neighborhoods;
  protected readonly testimonials = this.outageService.testimonials;
}
