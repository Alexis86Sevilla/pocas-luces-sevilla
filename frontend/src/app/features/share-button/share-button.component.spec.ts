import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { ShareButtonComponent } from './share-button.component';

describe('ShareButtonComponent', () => {
  function createFixture() {
    const fixture = TestBed.createComponent(ShareButtonComponent);
    fixture.detectChanges();
    return fixture;
  }

  function clickShare(fixture: ReturnType<typeof createFixture>) {
    const button: HTMLButtonElement = fixture.nativeElement.querySelector('button');
    button.click();
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ShareButtonComponent],
    }).compileComponents();
  });

  afterEach(() => {
    delete (globalThis.navigator as unknown as { share?: unknown }).share;
    delete (globalThis.navigator as unknown as { clipboard?: unknown }).clipboard;
    vi.useRealTimers();
  });

  it('uses the native share sheet when available', async () => {
    const shareSpy = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(globalThis.navigator, 'share', { value: shareSpy, configurable: true });

    const fixture = createFixture();
    clickShare(fixture);
    await fixture.whenStable();

    expect(shareSpy).toHaveBeenCalledWith(
      expect.objectContaining({ title: 'Sevilla Sin Luz', url: globalThis.location.href })
    );
  });

  it('copies the URL and shows a transient confirmation when share is unavailable', async () => {
    vi.useFakeTimers();
    const writeTextSpy = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(globalThis.navigator, 'clipboard', { value: { writeText: writeTextSpy }, configurable: true });

    const fixture = createFixture();
    clickShare(fixture);
    await Promise.resolve();
    fixture.detectChanges();

    expect(writeTextSpy).toHaveBeenCalledWith(globalThis.location.href);
    expect(fixture.nativeElement.textContent).toContain('Enlace copiado');

    vi.advanceTimersByTime(4000);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('Enlace copiado');
  });

  it('shows a selectable URL when both share and clipboard are unavailable', async () => {
    const fixture = createFixture();
    clickShare(fixture);
    await Promise.resolve();
    fixture.detectChanges();

    const input: HTMLInputElement = fixture.nativeElement.querySelector('input');
    expect(input.value).toBe(globalThis.location.href);
  });
});
