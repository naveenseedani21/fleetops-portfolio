import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { Auth } from './auth';
export interface Page<T> {
  items: T[];
  nextCursor: string | null;
}
export interface Device {
  id: string;
  name: string;
  type: string;
  model: string;
  os: string;
  osVersion: string | null;
  serial: string;
  employee: string | null;
  status: string;
  compliance: string;
  revision: number;
  lastCheckIn: string | null;
  encryption: boolean | null;
  screenLock: boolean | null;
  firewall: boolean | null;
}
export interface Policy {
  id: string;
  name: string;
  description: string;
  targetTypes: string[];
  rules: { type: string; value: string }[];
  severity: string;
  enabled: boolean;
  version: number;
}
export interface Audit {
  id: string;
  timestamp: string;
  actorId: string;
  action: string;
  targetId: string;
  outcome: string;
}
export interface Evidence {
  id: string;
  status: string;
  evaluatedAt: string;
  policies: {
    policyId: string;
    policyVersion: number;
    severity: string;
    status: string;
    rules: { rule: string; status: string; reason: string }[];
  }[];
}
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(Auth);
  return next(
    request.url.startsWith('/api/') && auth.token
      ? request.clone({ setHeaders: { Authorization: `Bearer ${auth.token}` } })
      : request,
  );
};
@Injectable({ providedIn: 'root' })
export class Api {
  private readonly http = inject(HttpClient);
  readonly organization = signal('');
  readonly memberships = signal<Record<string, string>>({});
  get admin(): boolean {
    return this.memberships()[this.organization()] === 'ADMIN';
  }
  async initialize(): Promise<void> {
    const roles = await firstValueFrom(
      this.http.get<Record<string, string>>('/api/v1/organizations'),
    );
    this.memberships.set(roles);
    this.organization.set(
      Object.keys(roles)
        .sort()
        .find((k) => roles[k] !== 'INGESTOR') ?? '',
    );
  }
  url(path: string): string {
    return `/api/v1/organizations/${encodeURIComponent(this.organization())}/${path}`;
  }
  get<T>(path: string): Promise<T> {
    return firstValueFrom(this.http.get<T>(this.url(path)));
  }
  post<T>(path: string, body: unknown = {}): Promise<T> {
    return firstValueFrom(this.http.post<T>(this.url(path), body));
  }
  patch<T>(path: string, body: unknown): Promise<T> {
    return firstValueFrom(this.http.patch<T>(this.url(path), body));
  }
  put<T>(path: string, body: unknown): Promise<T> {
    return firstValueFrom(this.http.put<T>(this.url(path), body));
  }
}
export function errorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 401) return 'Your session expired. Sign out and sign in again.';
    if (error.status === 403) return 'Your role does not permit this operation.';
    if (error.status === 409)
      return 'This record changed or its identifier already exists. Refresh before trying again.';
    return (
      (error.error as { detail?: string })?.detail ??
      'The service could not complete the request. Try again.'
    );
  }
  return 'Something went wrong. Try again.';
}
