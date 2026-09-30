import { Component, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { BackToTopComponent } from './layout/back-to-top/back-to-top.component';
import { SupportSectionComponent } from './layout/support-section/support-section.component';
import { FooterComponent } from './layout/footer/footer.component';
import { NavComponent } from './layout/nav/nav.component';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, BackToTopComponent, SupportSectionComponent, FooterComponent, NavComponent],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  protected readonly title = signal('frontend');
}
