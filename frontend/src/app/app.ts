import { Component, VERSION, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './core/auth/auth.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  // Injecting it here is what starts the one-time checkAuth(), so the header knows
  // whether anyone is signed in before the first render settles.
  protected readonly auth = inject(AuthService);

  protected readonly storeName = 'Meridian Store';
  protected readonly angularVersion = VERSION.major;

  protected signIn(): void {
    this.auth.login();
  }

  protected signOut(): void {
    this.auth.logout();
  }
}
