import { Component } from '@angular/core';

import { ShareButtonComponent } from '../share-button/share-button.component';

@Component({
  selector: 'app-footer',
  imports: [ShareButtonComponent],
  templateUrl: './footer.component.html',
})
export class FooterComponent {
  protected readonly year = new Date().getFullYear();
}
