import { Component } from '@angular/core';

import { MethodologySectionComponent } from './sections/methodology/methodology-section.component';

@Component({
  selector: 'app-data-page',
  imports: [MethodologySectionComponent],
  template: `
    <main class="bg-white">
      <app-methodology-section />
    </main>
  `,
})
export class DataPageComponent {}
