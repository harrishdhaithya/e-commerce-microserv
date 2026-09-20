import { Component, effect, inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

/**
 * Where Keycloak sends the browser back to after a successful login.
 *
 * <p>The URL arrives carrying {@code ?code=} and {@code ?state=}. AuthService's
 * one-time checkAuth() exchanges the code for tokens; this component just waits for
 * that to finish and moves on, so the user never sees the query string.
 */
@Component({
  selector: 'app-callback',
  template: `
    <div class="callback">
      <p>Signing you in&hellip;</p>
    </div>
  `,
  styles: [
    `
      .callback {
        display: grid;
        place-items: center;
        min-height: 12rem;
        color: var(--text-muted);
      }
    `,
  ],
})
export class Callback {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  constructor() {
    effect(() => {
      // `checked` flips once checkAuth has settled, whether or not it succeeded.
      // Without waiting for it, a failed login would sit here forever.
      if (!this.auth.checked()) {
        return;
      }
      const target = this.auth.isAuthenticated() ? '/account' : '/products';
      // replaceUrl so the callback URL, code and all, is not left in history for
      // the back button to return to.
      void this.router.navigateByUrl(target, { replaceUrl: true });
    });
  }
}
