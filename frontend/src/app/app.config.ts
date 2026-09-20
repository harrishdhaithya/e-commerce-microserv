import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { authInterceptor, provideAuth } from 'angular-auth-oidc-client';

import { routes } from './app.routes';
import { authConfig } from './core/auth/auth.config';
import { httpErrorInterceptor } from './core/http-error.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    provideAuth(authConfig),
    provideHttpClient(
      // fetch instead of XHR: the modern transport, and required if this app ever
      // gains SSR.
      withFetch(),
      withInterceptors([
        // Order matters. authInterceptor attaches the bearer token on the way out -
        // and only to the paths listed in secureRoutes, so the token never reaches a
        // third-party host. httpErrorInterceptor then handles failures on the way
        // back.
        authInterceptor(),
        httpErrorInterceptor,
      ]),
    ),
  ],
};
