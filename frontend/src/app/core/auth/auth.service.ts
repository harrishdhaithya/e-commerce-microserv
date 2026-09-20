import { Injectable, inject, signal } from '@angular/core';
import { OidcSecurityService } from 'angular-auth-oidc-client';

/**
 * Thin signal-facing wrapper over the OIDC library.
 *
 * <p>Two reasons it exists rather than injecting OidcSecurityService everywhere:
 * components get signals instead of observables, which suits a zoneless app, and the
 * rest of the codebase never names the library - so replacing it later touches one
 * file.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly oidc = inject(OidcSecurityService);

  readonly isAuthenticated = signal(false);
  readonly displayName = signal<string | null>(null);
  readonly checked = signal(false);

  constructor() {
    // Runs once at app start. Handles both cases: completing a login when the
    // browser has just come back from Keycloak with a code, and restoring an
    // existing session on a plain page load.
    this.oidc.checkAuth().subscribe((response) => {
      this.isAuthenticated.set(response.isAuthenticated);
      this.displayName.set(this.nameFrom(response.userData));
      this.checked.set(true);
    });

    this.oidc.isAuthenticated$.subscribe(({ isAuthenticated }) =>
      this.isAuthenticated.set(isAuthenticated),
    );

    this.oidc.userData$.subscribe(({ userData }) =>
      this.displayName.set(this.nameFrom(userData)),
    );
  }

  login(): void {
    this.oidc.authorize();
  }

  logout(): void {
    // Ends the session at Keycloak too, not just locally. A local-only sign-out
    // leaves the Keycloak session alive, so the next "Sign in" silently logs you
    // straight back in and looks broken.
    this.oidc.logoff().subscribe();
  }

  private nameFrom(userData: unknown): string | null {
    if (!userData || typeof userData !== 'object') {
      return null;
    }
    const claims = userData as Record<string, unknown>;
    const given = typeof claims['given_name'] === 'string' ? claims['given_name'] : null;
    const email = typeof claims['email'] === 'string' ? claims['email'] : null;
    return given ?? email;
  }
}
