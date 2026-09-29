import { Routes } from '@angular/router';

import { HomeComponent } from './features/home/home.component';

export const routes: Routes = [
  {
    path: '',
    component: HomeComponent,
    pathMatch: 'full',
    title: 'Sevilla Sin Luz — Cortes de luz en Sevilla en tiempo real',
    data: {
      description:
        'Datos reales de los cortes de luz en Sevilla: cortes activos en tiempo real, histórico mensual por barrio y comparativa anual.',
    },
  },
  {
    path: 'contexto',
    loadComponent: () => import('./features/contexto-page/contexto-page.component').then(m => m.ContextoPageComponent),
    title: 'Contexto — Sevilla Sin Luz',
    data: {
      description:
        'Por qué se cae la luz en los barrios de Sevilla: contexto, reivindicaciones vecinales, cobertura de prensa y testimonios en vídeo.',
    },
  },
  {
    path: 'datos',
    loadComponent: () => import('./features/datos-page/datos-page.component').then(m => m.DatosPageComponent),
    title: 'Datos y metodología — Sevilla Sin Luz',
    data: {
      description:
        'De dónde salen los datos de Sevilla Sin Luz, qué es exacto, qué es aproximado y qué estima la distribuidora.',
    },
  },
  // Angular's redirectTo preserves query params and the URL fragment by default,
  // so shared links like ?anio=2026&mes=7#en-directo keep working through this redirect.
  { path: '**', redirectTo: '' },
];
