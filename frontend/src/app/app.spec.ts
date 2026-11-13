import { vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { signal, Component } from '@angular/core';
import { App } from './app';
import { Api } from './api';
import { Auth } from './auth';

@Component({ template: '' })
class EmptyPage {}

describe('Organization selector', () => {
  it('shows the API tenant even when options render in a different order', async () => {
    const api = {
      organization: signal(''),
      memberships: signal<Record<string, string>>({}),
      initialize: async () => {
        api.memberships.set({ lab: 'ADMIN', demo: 'ADMIN' });
        api.organization.set('lab');
      },
    };
    TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([{ path: 'devices', component: EmptyPage }]),
        { provide: Api, useValue: api },
        {
          provide: Auth,
          useValue: { initialize: async () => {}, signedIn: true, logout: () => {} },
        },
      ],
    });
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    await vi.waitFor(() => expect(fixture.componentInstance.loading()).toBe(false));
    fixture.detectChanges();
    await fixture.whenStable();
    const select: HTMLSelectElement = fixture.nativeElement.querySelector('select');
    expect(select.value).toBe('lab');
    select.value = 'demo';
    select.dispatchEvent(new Event('change'));
    await fixture.whenStable();
    expect(api.organization()).toBe('demo');
  });
});
