import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', loadComponent: () => import('./pages/start-page').then((m) => m.StartPage), title: 'Kunden · Treasury Kompass' },
  {
    path: 'clients/:id/profilierung',
    loadComponent: () => import('./pages/profilierung-page').then((m) => m.ProfilierungPage),
    title: 'Profilierung · Treasury Kompass'
  },
  {
    path: 'clients/:id/beratung',
    loadComponent: () => import('./pages/beratung-page').then((m) => m.BeratungPage),
    title: 'Beratung · Treasury Kompass'
  },
  { path: 'clients/:id', loadComponent: () => import('./pages/dashboard-page').then((m) => m.DashboardPage), title: 'Dashboard · Treasury Kompass' },
  { path: '**', redirectTo: '' }
];
