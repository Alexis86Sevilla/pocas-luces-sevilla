import { Component, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { BackToTopComponent } from './features/back-to-top/back-to-top.component';
import { DonationSectionComponent } from './features/donation-section/donation-section';
import { FooterComponent } from './features/footer/footer.component';
import { NavComponent } from './features/nav/nav.component';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, BackToTopComponent, DonationSectionComponent, FooterComponent, NavComponent],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  protected readonly title = signal('frontend');
}
