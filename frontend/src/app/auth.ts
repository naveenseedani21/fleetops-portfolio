import { Injectable, inject, signal } from '@angular/core';
import { OAuthService } from 'angular-oauth2-oidc';
@Injectable({ providedIn: 'root' })
export class Auth {
  private readonly oauth = inject(OAuthService);
  readonly ready = signal(false);
  readonly error = signal('');
  get signedIn(): boolean {
    return this.oauth.hasValidAccessToken();
  }
  get token(): string {
    return this.oauth.getAccessToken();
  }
  async initialize(): Promise<void> {
    try {
      const response = await fetch('/config.json');
      if (!response.ok) throw new Error('Configuration unavailable');
      const config = (await response.json()) as {
        issuer: string;
        clientId: string;
        requireHttps: boolean;
      };
      this.oauth.configure({
        issuer: config.issuer,
        clientId: config.clientId,
        redirectUri: window.location.origin + '/',
        responseType: 'code',
        scope: 'openid profile',
        requireHttps: config.requireHttps,
        strictDiscoveryDocumentValidation: true,
        showDebugInformation: false,
      });
      this.oauth.setStorage(sessionStorage);
      await this.oauth.loadDiscoveryDocumentAndTryLogin();
      if (!this.oauth.hasValidAccessToken() && this.oauth.getRefreshToken()) {
        await this.oauth.refreshToken();
      }
      this.oauth.setupAutomaticSilentRefresh();
    } catch {
      this.error.set('Identity service unavailable. Check the local Keycloak service and reload.');
    } finally {
      this.ready.set(true);
    }
  }
  login(): void {
    this.oauth.initCodeFlow();
  }
  logout(): void {
    this.oauth.logOut();
  }
}
