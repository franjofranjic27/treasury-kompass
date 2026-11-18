import { Routes } from '@angular/router';

/** Titles are translation keys, resolved by TranslatedTitleStrategy. */
export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./pages/start-page').then((m) => m.StartPage),
    title: 'title.start',
  },
  {
    path: 'clients/:id/profilierung',
    loadComponent: () => import('./pages/profilierung-page').then((m) => m.ProfilierungPage),
    title: 'title.profilierung',
  },
  {
    path: 'clients/:id/beratung',
    loadComponent: () => import('./pages/beratung-page').then((m) => m.BeratungPage),
    title: 'title.beratung',
  },
  {
    path: 'clients/:id',
    loadComponent: () => import('./pages/dashboard-page').then((m) => m.DashboardPage),
    title: 'title.dashboard',
  },
  { path: '**', redirectTo: '' },
];
