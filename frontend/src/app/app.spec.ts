import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';
import { AuthService } from './core/auth/auth.service';

/**
 * Stand-in for the real AuthService.
 *
 * <p>The real one injects OidcSecurityService, which drags the whole OIDC stack into
 * the test - a StsConfigLoader, periodic token checks, the lot. The shell only cares
 * about three signals, so faking them keeps this test about the header rather than
 * about the library.
 */
function fakeAuth(options: { authenticated: boolean; checked?: boolean; name?: string | null }) {
  return {
    isAuthenticated: signal(options.authenticated),
    displayName: signal(options.name ?? null),
    checked: signal(options.checked ?? true),
    login: () => {},
    logout: () => {},
  };
}

function configure(auth: ReturnType<typeof fakeAuth>) {
  return TestBed.configureTestingModule({
    imports: [App],
    providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
  }).compileComponents();
}

describe('App', () => {
  it('creates the shell', async () => {
    await configure(fakeAuth({ authenticated: false }));
    expect(TestBed.createComponent(App).componentInstance).toBeTruthy();
  });

  it('renders the store name and catalog link', async () => {
    await configure(fakeAuth({ authenticated: false }));
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('.brand__text')?.textContent).toContain('Meridian Store');
    expect(element.querySelector('.shell__nav a')?.textContent).toContain('Catalog');
  });

  it('offers sign-in and hides Account when signed out', async () => {
    await configure(fakeAuth({ authenticated: false }));
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('.shell__auth')?.textContent).toContain('Sign in');
    expect(element.textContent).not.toContain('Account');
  });

  it('shows the user and Account link when signed in', async () => {
    await configure(fakeAuth({ authenticated: true, name: 'Test' }));
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('.shell__user')?.textContent).toContain('Test');
    expect(element.querySelector('.shell__auth')?.textContent).toContain('Sign out');
    expect(element.textContent).toContain('Account');
  });

  it('shows neither button until the auth check has settled', async () => {
    // Otherwise a signed-in user sees "Sign in" flash on every page load.
    await configure(fakeAuth({ authenticated: false, checked: false }));
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();

    const auth = (fixture.nativeElement as HTMLElement).querySelector('.shell__auth');
    expect(auth?.textContent).not.toContain('Sign in');
    expect(auth?.textContent).not.toContain('Sign out');
  });
});
