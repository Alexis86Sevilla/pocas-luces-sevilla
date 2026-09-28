import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { BackToTopComponent } from './back-to-top.component';

describe('BackToTopComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BackToTopComponent],
    }).compileComponents();
    Object.defineProperty(globalThis, 'scrollY', { value: 0, writable: true, configurable: true });
    Object.defineProperty(globalThis, 'innerHeight', { value: 800, writable: true, configurable: true });
  });

  function createFixture() {
    const fixture = TestBed.createComponent(BackToTopComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('is hidden before scrolling past one viewport', () => {
    const fixture = createFixture();
    expect(fixture.nativeElement.querySelector('button')).toBeNull();
  });

  it('appears once scrolled past one viewport height', () => {
    const fixture = createFixture();
    Object.defineProperty(globalThis, 'scrollY', { value: 900, configurable: true });
    globalThis.dispatchEvent(new Event('scroll'));
    fixture.detectChanges();

    const button: HTMLButtonElement = fixture.nativeElement.querySelector('button');
    expect(button).toBeTruthy();
    expect(button.getAttribute('aria-label')).toBe('Volver arriba');
  });

  it('scrolls back to top with a reduced-motion-aware behavior', () => {
    const fixture = createFixture();
    Object.defineProperty(globalThis, 'scrollY', { value: 900, configurable: true });
    globalThis.dispatchEvent(new Event('scroll'));
    fixture.detectChanges();

    const scrollToSpy = vi.spyOn(globalThis, 'scrollTo').mockImplementation(() => {});
    const button: HTMLButtonElement = fixture.nativeElement.querySelector('button');
    button.click();

    expect(scrollToSpy).toHaveBeenCalledWith(expect.objectContaining({ top: 0 }));
  });
});
