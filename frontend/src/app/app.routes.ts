import { Routes } from '@angular/router';
export const routes: Routes = [
  { path: 'overview', loadComponent: () => import('./overview').then((m) => m.Overview) },
  { path: '', redirectTo: 'devices', pathMatch: 'full' },
  { path: 'devices', loadComponent: () => import('./inventory').then((m) => m.Inventory) },
  { path: 'devices/:id', loadComponent: () => import('./detail').then((m) => m.Detail) },
  { path: 'policies', loadComponent: () => import('./policies').then((m) => m.Policies) },
  { path: 'audit', loadComponent: () => import('./audit').then((m) => m.AuditView) },
  { path: '**', redirectTo: 'devices' },
];
