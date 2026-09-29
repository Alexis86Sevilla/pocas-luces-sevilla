import { Component, computed, input } from '@angular/core';

import { openDataCsvUrl } from '../../core/utils/open-data-url';

/**
 * Plain download links to the public CSV (standard and Excel variant) (no JS: the API answers with Content-Disposition:
 * attachment). Optionally shows the CC BY 4.0 notice and the citation to use.
 */
@Component({
  selector: 'app-open-data-download',
  imports: [],
  templateUrl: './open-data-download.component.html',
})
export class OpenDataDownloadComponent {
  readonly year = input<number | null>(null);
  readonly month = input<number | null>(null);
  readonly label = input('Descargar CSV');
  /** Accessible name; defaults to the visible label. */
  readonly ariaLabel = input<string | null>(null);
  /** Visible label of the Excel (Spanish locale) variant link. */
  readonly excelLabel = input('CSV para Excel');
  /** Accessible name of the Excel link; defaults to the visible label. */
  readonly excelAriaLabel = input<string | null>(null);
  readonly showLicense = input(false);

  protected readonly href = computed(() => openDataCsvUrl({ year: this.year(), month: this.month() }));
  protected readonly excelHref = computed(() =>
    openDataCsvUrl({ year: this.year(), month: this.month() }, undefined, 'excel'),
  );
}
