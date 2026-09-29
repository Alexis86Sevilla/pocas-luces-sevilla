import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ShareButtonComponent } from '../share-button/share-button.component';

@Component({
  selector: 'app-footer',
  imports: [ShareButtonComponent, RouterLink],
  templateUrl: './footer.component.html',
})
export class FooterComponent {
  protected readonly year = new Date().getFullYear();
}
