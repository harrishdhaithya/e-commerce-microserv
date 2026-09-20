import { LogLevel, PassedInitialConfig } from 'angular-auth-oidc-client';

/**
 * OIDC configuration for the `ecom-web` Keycloak client.
 *
 * <p>Authorization code flow with PKCE. A single-page app cannot keep a client
 * secret - anything shipped to the browser is readable - so the client is public and
 * PKCE is what stops an intercepted authorization code from being redeemed by
 * someone else.
 */
export const authConfig: PassedInitialConfig = {
  config: {
    // Must match the issuer Keycloak stamps into tokens, which infra/docker-compose.yml
    // pins via KC_HOSTNAME. The library discovers endpoints from /.well-known here.
    authority: 'http://localhost:8180/realms/ecommerce',

    // A dedicated route rather than the site root: the callback arrives with ?code=
    // and ?state= query parameters, and routing away from the root before the library
    // has read them loses the login.
    redirectUrl: `${window.location.origin}/callback`,
    postLogoutRedirectUri: window.location.origin,

    clientId: 'ecom-web',
    scope: 'openid profile email',
    responseType: 'code',

    // PKCE. The realm JSON sets pkce.code.challenge.method to S256 on this client,
    // so Keycloak rejects a plain challenge.
    useRefreshToken: true,
    silentRenew: true,
    renewTimeBeforeTokenExpiresInSeconds: 30,

    /**
     * The token is attached ONLY to requests whose URL starts with one of these.
     *
     * This is the setting that stops an access token being sent to a third-party
     * host. A blanket interceptor that adds Authorization to every outgoing request
     * will happily hand your token to any CDN or analytics endpoint the app talks to.
     */
    secureRoutes: ['/api/'],

    // Tokens live in memory only. localStorage would survive a tab close, but is
    // readable by any script that gets injected into the page.
    logLevel: LogLevel.Warn,
  },
};
