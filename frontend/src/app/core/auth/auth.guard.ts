import { inject } from '@angular/core';
import { CanActivateFn } from '@angular/router';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { map, take } from 'rxjs';

/**
 * Blocks a route until the user is signed in, and starts the login if they are not.
 *
 * <p>Note what this is and is not. It improves the experience - no flash of an empty
 * account page before a redirect - but it is not a security control. Guards run in
 * the browser, where anyone can edit them. The real enforcement is the 401 the
 * resource server returns for a request without a valid token.
 */
export const authGuard: CanActivateFn = () => {
  const oidc = inject(OidcSecurityService);

  return oidc.isAuthenticated$.pipe(
    take(1),
    map(({ isAuthenticated }) => {
      if (isAuthenticated) {
        return true;
      }
      // Sends the browser to Keycloak. Returning false as well would queue a
      // navigation that never completes, so the redirect is the whole answer.
      oidc.authorize();
      return false;
    }),
  );
};
