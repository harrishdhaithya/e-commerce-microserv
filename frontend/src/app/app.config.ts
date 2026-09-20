import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';

import { routes } from './app.routes';
import { httpErrorInterceptor } from './core/http-error.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(
      // fetch instead of XHR: the modern transport, and required if this app ever
      // gains SSR.
      withFetch(),
      // One interceptor, so no component ever parses an error body itself.
      withInterceptors([httpErrorInterceptor]),
    ),
  ],
};
