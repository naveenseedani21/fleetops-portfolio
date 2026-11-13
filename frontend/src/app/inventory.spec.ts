import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';
import { Api, Device, Page } from './api';
import { Inventory } from './inventory';

const device = (id: string, status = 'ACTIVE', compliance = 'NONCOMPLIANT'): Device => ({
  id,
  name: id,
  type: 'MAC',
  model: 'MacBook',
  os: 'macOS',
  osVersion: '17.0',
  serial: 'SERIAL-' + id,
  employee: null,
  status,
  compliance,
  revision: 1,
  lastCheckIn: null,
  encryption: null,
  screenLock: null,
  firewall: null,
});

async function setup() {
  const api = {
    organization: signal('demo'),
    admin: false,
    get: vi.fn().mockResolvedValue({
      items: [
        device('active'),
        device('retired', 'RETIRED', 'COMPLIANT'),
        device('legacy', 'NONCOMPLIANT', 'UNKNOWN'),
      ],
      nextCursor: null,
    }),
  };
  TestBed.configureTestingModule({
    imports: [Inventory],
    providers: [provideRouter([]), { provide: Api, useValue: api }],
  });
  const fixture = TestBed.createComponent(Inventory);
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
  return { api, fixture, component: fixture.componentInstance };
}

describe('Operations device inspector', () => {
  it('selects locally without reloading and keeps lifecycle, compliance and unknown telemetry distinct', async () => {
    const { api, fixture, component } = await setup();
    expect(component.activeCount()).toBe(1);
    expect(component.noncompliantCount()).toBe(1);
    expect(fixture.nativeElement.querySelector('.device-inspector').textContent).toContain(
      'Unknown',
    );
    const buttons = fixture.nativeElement.querySelectorAll(
      '.device-select',
    ) as NodeListOf<HTMLButtonElement>;
    buttons[1].click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(component.selected()?.id).toBe('retired');
    expect(api.get).toHaveBeenCalledTimes(1);
    const inspector = fixture.nativeElement.querySelector('.device-inspector');
    expect(inspector.textContent).toContain('RETIRED');
    expect(inspector.textContent).toContain('COMPLIANT');
    expect(inspector.querySelector('a').getAttribute('href')).toBe('/devices/retired');
    buttons[2].click();
    fixture.detectChanges();
    expect(inspector.textContent).toContain('Needs review');
  });

  it('clears the previous tenant selection while the new organization loads', async () => {
    const { api, fixture, component } = await setup();
    let finish!: (page: Page<Device>) => void;
    api.get.mockImplementationOnce(
      () =>
        new Promise<Page<Device>>((resolve) => {
          finish = resolve;
        }),
    );
    api.organization.set('lab');
    fixture.detectChanges();
    expect(component.selected()).toBeNull();
    expect(component.devices()).toEqual([]);
    expect(fixture.nativeElement.querySelector('.device-inspector').textContent).not.toContain(
      'SERIAL-active',
    );
    finish({ items: [device('lab-device')], nextCursor: null });
    await fixture.whenStable();
    fixture.detectChanges();
    expect(component.selected()?.id).toBe('lab-device');
  });

  it('does not restore a stale page or selection when requests finish out of order', async () => {
    const { api, component } = await setup();
    let finishOld!: (page: Page<Device>) => void;
    let finishNew!: (page: Page<Device>) => void;
    api.get.mockImplementationOnce(
      () =>
        new Promise<Page<Device>>((resolve) => {
          finishOld = resolve;
        }),
    );
    api.get.mockImplementationOnce(
      () =>
        new Promise<Page<Device>>((resolve) => {
          finishNew = resolve;
        }),
    );
    const oldPage = component.load();
    const newPage = component.load('next-page');
    finishNew({ items: [device('new')], nextCursor: null });
    await newPage;
    finishOld({ items: [device('old')], nextCursor: 'stale' });
    await oldPage;
    expect(component.selected()?.id).toBe('new');
    expect(component.next()).toBeNull();
  });
});
