import { Component, inject, signal, OnInit } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet, Router } from '@angular/router';
import { KeyValuePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { Auth } from './auth';
import { Api, errorMessage } from './api';
@Component({
  selector: 'app-root',
  imports: [RouterLink, RouterLinkActive, RouterOutlet, KeyValuePipe, MatButtonModule],
  templateUrl: './app.html',
})
export class App implements OnInit {
  readonly auth = inject(Auth);
  readonly api = inject(Api);
  private readonly router = inject(Router);
  readonly loading = signal(true);
  readonly error = signal('');
  async ngOnInit(): Promise<void> {
    await this.auth.initialize();
    if (this.auth.signedIn) {
      try {
        await this.api.initialize();
      } catch (e) {
        this.error.set(errorMessage(e));
      }
    }
    this.loading.set(false);
  }
  async select(event: Event): Promise<void> {
    this.api.organization.set((event.target as HTMLSelectElement).value);
    await this.router.navigateByUrl('/devices');
  }
}
