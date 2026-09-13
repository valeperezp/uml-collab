import { Routes } from '@angular/router';
import { authGuard } from './core/services/auth.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'diagrams' },
  { path: 'login', loadComponent: () => import('./pages/login/login.component').then((m) => m.LoginComponent) },
  {
    path: 'diagrams',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/dashboard/dashboard.component').then((m) => m.DashboardComponent),
  },
  {
    path: 'diagrams/:id',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/diagram/diagram.component').then((m) => m.DiagramComponent),
  },
  { path: '**', redirectTo: 'diagrams' },
];
