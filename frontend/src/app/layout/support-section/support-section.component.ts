import { Component } from '@angular/core';
import { ShareButtonComponent } from '../../shared/ui/share-button/share-button.component';

@Component({
  selector: 'app-support-section',
  standalone: true,
  imports: [ShareButtonComponent],
  templateUrl: './support-section.component.html',
})
export class SupportSectionComponent {}
