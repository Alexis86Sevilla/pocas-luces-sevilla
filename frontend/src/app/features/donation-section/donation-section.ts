import { Component } from '@angular/core';
import { ShareButtonComponent } from '../share-button/share-button.component';

@Component({
  selector: 'app-donation-section',
  standalone: true,
  imports: [ShareButtonComponent],
  templateUrl: './donation-section.html',
})
export class DonationSectionComponent {}
