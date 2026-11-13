import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { Api, authInterceptor } from './api';
import { Auth } from './auth';
import { HttpClient } from '@angular/common/http';
describe('API tenant and credential boundaries', () => {
  beforeEach(() =>
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: Auth, useValue: { token: 'test-access-token' } },
      ],
    }),
  );
  afterEach(() => TestBed.inject(HttpTestingController).verify());
  it('encodes the tenant path and adds API credentials', async () => {
    const api = TestBed.inject(Api);
    api.organization.set('tenant/a');
    const result = api.get('devices');
    const request = TestBed.inject(HttpTestingController).expectOne(
      '/api/v1/organizations/tenant%2Fa/devices',
    );
    expect(request.request.headers.get('Authorization')).toBe('Bearer test-access-token');
    request.flush({ items: [], nextCursor: null });
    await result;
  });
  it('never sends access tokens to third-party URLs', () => {
    TestBed.inject(HttpClient).get('https://example.com/resource').subscribe();
    const request = TestBed.inject(HttpTestingController).expectOne('https://example.com/resource');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({});
  });
});
