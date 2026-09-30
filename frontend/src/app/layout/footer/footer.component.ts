import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

import { TELEGRAM_CHANNEL_URL } from '../../core/config/social';
import { ShareButtonComponent } from '../../shared/ui/share-button/share-button.component';

@Component({
  selector: 'app-footer',
  imports: [ShareButtonComponent, RouterLink],
  templateUrl: './footer.component.html',
})
export class FooterComponent {
  protected readonly year = new Date().getFullYear();
  protected readonly telegramUrl = TELEGRAM_CHANNEL_URL;
}
