import { Component } from '@angular/core';

import { MethodologySectionComponent } from '../methodology/methodology-section.component';

@Component({
  selector: 'app-datos-page',
  imports: [MethodologySectionComponent],
  template: `
    <main class="bg-white">
      <app-methodology-section />
    </main>
  `,
})
export class DatosPageComponent {}
