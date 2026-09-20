import { Routes } from '@angular/router';

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
  { path: '', redirectTo: 'products', pathMatch: 'full' },
  { path: '**', redirectTo: 'products' },
];
