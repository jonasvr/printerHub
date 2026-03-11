import { Routes } from '@angular/router';

/**
 * Top-level routes.
 * Using lazy loading (loadComponent) means Angular only downloads the
 * DashboardComponent bundle when the user navigates to '/'.
 * This keeps the initial page load fast even as the app grows.
 */
export const routes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./dashboard/dashboard.component').then(m => m.DashboardComponent),
    title: 'Dashboard — PrinterHub'
  },
  {
    path: '**',
    redirectTo: ''   // catch-all → home
  }
];
