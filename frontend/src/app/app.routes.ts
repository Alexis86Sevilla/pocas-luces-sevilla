import { Routes } from '@angular/router';

import { splashGuard } from './core/guards/splash.guard';
import { HomeComponent } from './features/home/home.component';
import { SplashComponent } from './features/splash/splash.component';

export const routes: Routes = [
  { path: '', redirectTo: '/home', pathMatch: 'full' },
  { path: 'home', component: HomeComponent, canActivate: [splashGuard] },
  { path: 'splash', component: SplashComponent },
  { path: '**', redirectTo: '/home' },
];
