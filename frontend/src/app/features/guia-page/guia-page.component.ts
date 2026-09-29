import { Component, DestroyRef, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ApiOutageService } from '../../core/services/api-outage.service';
import { OpenDataDownloadComponent } from '../open-data-download/open-data-download.component';
import { ShareButtonComponent } from '../share-button/share-button.component';

type CopyStatus = 'idle' | 'copied' | 'manual';

const COPIED_STATUS_DURATION_MS = 4000;

export const CLAIM_TEMPLATE = `Asunto: Reclamación por cortes de suministro eléctrico

Estimados señores:

Yo, [Nombre], con DNI [DNI], titular del suministro situado en [Dirección del suministro] (CUPS o nº de contrato: [CUPS o nº de contrato — lo encuentras en tu factura]), les escribo por los cortes de luz que he sufrido en mi vivienda en las siguientes fechas y horas: [fechas y horas de los cortes].

Como consecuencia de estos cortes se han producido los siguientes daños: [daños]. Adjunto fotos y facturas o tickets como justificante.

Por todo ello, según la normativa de calidad de suministro (Real Decreto 1955/2000), les solicito:

1. El informe de las interrupciones de mi suministro en el periodo [periodo, por ejemplo: de enero a septiembre de 2026].
2. La indemnización de los daños causados.

Les pido también que me indiquen un número de referencia de esta reclamación y que me respondan por escrito.

Un saludo,
[Nombre]`;

@Component({
  selector: 'app-guia-page',
  imports: [RouterLink, OpenDataDownloadComponent, ShareButtonComponent],
  templateUrl: './guia-page.component.html',
})
export class GuiaPageComponent {
  protected readonly api = inject(ApiOutageService);

  protected readonly template = signal(CLAIM_TEMPLATE);
  protected readonly copyStatus = signal<CopyStatus>('idle');

  private clearTimeoutId: ReturnType<typeof globalThis.setTimeout> | undefined;

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      if (this.clearTimeoutId !== undefined) globalThis.clearTimeout(this.clearTimeoutId);
    });
  }

  protected onTemplateInput(event: Event): void {
    this.template.set((event.target as HTMLTextAreaElement).value);
  }

  protected async copyTemplate(): Promise<void> {
    const clipboard = globalThis.navigator?.clipboard;
    if (!clipboard?.writeText) {
      this.showStatus('manual');
      return;
    }
    try {
      await clipboard.writeText(this.template());
      this.showStatus('copied');
    } catch {
      this.showStatus('manual');
    }
  }

  private showStatus(status: CopyStatus): void {
    this.copyStatus.set(status);
    if (this.clearTimeoutId !== undefined) globalThis.clearTimeout(this.clearTimeoutId);
    if (status === 'copied') {
      this.clearTimeoutId = globalThis.setTimeout(() => this.copyStatus.set('idle'), COPIED_STATUS_DURATION_MS);
    }
  }
}
