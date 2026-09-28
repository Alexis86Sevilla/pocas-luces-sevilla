import { Routes } from '@angular/router';

import { HomeComponent } from './features/home/home.component';

export const routes: Routes = [
  { path: '', component: HomeComponent, pathMatch: 'full' },
  // Angular's redirectTo preserves query params and the URL fragment by default,
  // so shared links like ?anio=2026&mes=7#en-directo keep working through this redirect.
  { path: '**', redirectTo: '' },
];
