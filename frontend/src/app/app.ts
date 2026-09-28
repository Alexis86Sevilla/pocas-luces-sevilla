import { Component, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { BackToTopComponent } from './features/back-to-top/back-to-top.component';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, BackToTopComponent],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {
  protected readonly title = signal('frontend');
}
