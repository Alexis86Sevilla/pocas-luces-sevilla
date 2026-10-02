import { Routes } from '@angular/router';

import { HomePageComponent } from './pages/home/home-page.component';

export const routes: Routes = [
  {
    path: '',
    component: HomePageComponent,
    pathMatch: 'full',
    title: 'Sevilla Sin Luz — Cortes de luz en Sevilla en tiempo real',
    data: {
      description:
        'Datos reales de los cortes de luz en Sevilla: cortes activos en tiempo real, histórico mensual por barrio y comparativa anual.',
    },
  },
  {
    path: 'guia',
    loadComponent: () => import('./pages/guide/guide-page.component').then(m => m.GuidePageComponent),
    title: 'Qué hacer si te quedas sin luz — Sevilla Sin Luz',
    data: {
      description:
        'Guía paso a paso: a quién llamar, cómo reclamar y tus derechos si te quedas sin luz en Sevilla.',
    },
  },
  {
    path: 'mapa',
    loadComponent: () => import('./pages/map/map-page.component').then(m => m.MapPageComponent),
    title: 'Mapa de cortes — Sevilla Sin Luz',
    data: {
      description:
        'Mapa de los cortes de luz en Sevilla: cortes en directo y del mes seleccionado, sobre los distritos de la ciudad.',
    },
  },
  {
    path: 'contexto',
    loadComponent: () => import('./pages/context/context-page.component').then(m => m.ContextPageComponent),
    title: 'Contexto — Sevilla Sin Luz',
    data: {
      description:
        'Por qué se cae la luz en los barrios de Sevilla: contexto, reivindicaciones vecinales, cobertura de prensa y testimonios en vídeo.',
    },
  },
  {
    path: 'datos',
    loadComponent: () => import('./pages/data/data-page.component').then(m => m.DataPageComponent),
    title: 'Datos y metodología — Sevilla Sin Luz',
    data: {
      description:
        'De dónde salen los datos de Sevilla Sin Luz, qué es exacto, qué es aproximado y qué estima la distribuidora.',
    },
  },
  {
    path: '**',
    loadComponent: () => import('./pages/not-found/not-found-page.component').then(m => m.NotFoundPageComponent),
    title: 'Página no encontrada — Sevilla Sin Luz',
    data: { description: 'La página que buscas no existe. Vuelve al inicio de Sevilla Sin Luz.' },
  },
];
