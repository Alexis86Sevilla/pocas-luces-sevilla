import { Component, inject, signal } from '@angular/core';

import { OUTAGE_SERVICE } from '../../core/services/outage.service';
import { OutageCardComponent } from '../outages/outage-card.component';
import { SplashComponent } from '../splash/splash.component';
import { TestimonialCardComponent } from '../testimonials/testimonial-card.component';

const SPLASH_KEY = 'splash-dismissed';

@Component({
  selector: 'app-home',
  imports: [OutageCardComponent, SplashComponent, TestimonialCardComponent],
  templateUrl: './home.component.html',
  styleUrl: './home.component.css',
})
export class HomeComponent {
  private readonly outageService = inject(OUTAGE_SERVICE);

  protected readonly neighborhoods = this.outageService.neighborhoods;
  protected readonly testimonials = this.outageService.testimonials;

  protected readonly showSplash = signal(
    globalThis.localStorage?.getItem(SPLASH_KEY) !== 'true',
  );

  protected onSplashFinished(): void {
    globalThis.localStorage?.setItem(SPLASH_KEY, 'true');
    this.showSplash.set(false);
  }
}
