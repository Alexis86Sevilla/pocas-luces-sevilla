import { TestBed } from '@angular/core/testing';

import { TELEGRAM_CHANNEL_URL } from '../../../core/config/social';
import { TelegramLinkComponent } from './telegram-link.component';

describe('TelegramLinkComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TelegramLinkComponent],
    }).compileComponents();
  });

  function createLink(variant?: 'light' | 'dark'): HTMLAnchorElement {
    const fixture = TestBed.createComponent(TelegramLinkComponent);
    if (variant) fixture.componentRef.setInput('variant', variant);
    fixture.detectChanges();
    return fixture.nativeElement.querySelector('a');
  }

  it('links to the public channel as a safe external link', () => {
    const link = createLink();
    expect(link.getAttribute('href')).toBe(TELEGRAM_CHANNEL_URL);
    expect(link.getAttribute('target')).toBe('_blank');
    expect(link.getAttribute('rel')).toBe('noopener noreferrer');
    expect(link.textContent).toContain('Recibe avisos en Telegram');
  });

  it('uses the dark theme when requested', () => {
    expect(createLink('dark').className).toContain('bg-gray-700');
    expect(createLink('light').className).toContain('bg-white');
  });
});
