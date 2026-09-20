import { InjectionToken } from '@angular/core';

/**
 * Base path for every API call.
 *
 * Relative on purpose. In development the Angular dev server proxies `/api` to
 * catalog-service on 8082 (see proxy.conf.json), which means the browser only ever
 * talks to one origin and CORS never enters the picture. From Phase 2 the same
 * relative path points at the API gateway instead - and no component changes.
 */
export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL', {
  providedIn: 'root',
  factory: () => '/api',
});
