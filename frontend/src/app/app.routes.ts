import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  {
    path: 'products',
    // Lazy-loaded from the start. With one feature it changes nothing; by the time
    // there are catalog, cart, checkout, orders, account and admin routes it is the
    // difference between a fast first paint and shipping the admin UI to every
    // anonymous visitor.
    loadComponent: () =>
      import('./features/catalog/product-list/product-list').then((m) => m.ProductList),
  },
  {
    path: 'account',
    canActivate: [authGuard],
    loadComponent: () => import('./features/account/account').then((m) => m.Account),
  },
  {
    // Where Keycloak sends the browser back to. Must match redirectUrl in
    // auth.config.ts and the redirect URIs registered on the ecom-web client.
    path: 'callback',
    loadComponent: () => import('./features/auth/callback').then((m) => m.Callback),
  },
  { path: '', redirectTo: 'products', pathMatch: 'full' },
  { path: '**', redirectTo: 'products' },
];
