import { vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { Detail } from './detail';
import { Api, Device, Evidence, Page } from './api';
const device: Device = {
  id: 'mac-1',
  name: 'MacBook',
  type: 'MAC',
  model: 'MacBook Pro',
  os: 'macOS',
  osVersion: '15.2',
  serial: 'MAC-001',
  employee: null,
  status: 'ACTIVE',
  compliance: 'COMPLIANT',
  revision: 1,
  lastCheckIn: null,
  encryption: true,
  screenLock: true,
  firewall: true,
};
const evidence: Evidence = {
  id: 'recent',
  status: 'COMPLIANT',
  evaluatedAt: '2026-11-12T10:00:00Z',
  policies: [],
};
async function setup() {
  const api = {
    organization: signal('team'),
    admin: true,
    get: vi
      .fn()
      .mockImplementation((path: string) =>
        Promise.resolve(
          path.includes('evaluations') ? { items: [evidence], nextCursor: 'next+/=' } : device,
        ),
      ),
  };
  TestBed.configureTestingModule({
    imports: [Detail],
    providers: [
      provideRouter([]),
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { paramMap: convertToParamMap({ id: 'mac-1' }) } },
      },
      { provide: Api, useValue: api },
    ],
  });
  const fixture = TestBed.createComponent(Detail);
  fixture.detectChanges();
  await fixture.whenStable();
  return { page: fixture.componentInstance, api, fixture };
}
describe('Device evidence history', () => {
  it('appends older evidence and removes pagination at the end', async () => {
    const { page, api } = await setup();
    api.get.mockResolvedValueOnce({ items: [{ ...evidence, id: 'older' }], nextCursor: null });
    await page.loadOlder();
    expect(api.get).toHaveBeenLastCalledWith(
      'devices/mac-1/evaluations?limit=25&cursor=next%2B%2F%3D',
    );
    expect(page.history().map((e) => e.id)).toEqual(['recent', 'older']);
    expect(page.historyNext()).toBeNull();
  });
  it('preserves loaded evidence and allows retry after a failed page', async () => {
    const { page, api } = await setup();
    api.get.mockRejectedValueOnce(new Error('offline'));
    await page.loadOlder();
    expect(page.device()?.id).toBe('mac-1');
    expect(page.history()).toEqual([evidence]);
    expect(page.historyNext()).toBe('next+/=');
    expect(page.historyError()).toBeTruthy();
    api.get.mockResolvedValueOnce({ items: [{ ...evidence, id: 'older' }], nextCursor: null });
    await page.loadOlder();
    expect(page.historyError()).toBe('');
    expect(page.history().length).toBe(2);
  });
  it('discards a delayed page when the latest evidence is refreshed', async () => {
    const { page, api } = await setup();
    let resolve!: (value: Page<Evidence>) => void;
    api.get.mockReturnValueOnce(
      new Promise<Page<Evidence>>((r) => {
        resolve = r;
      }),
    );
    const pending = page.loadOlder();
    await page.load();
    resolve({ items: [{ ...evidence, id: 'stale' }], nextCursor: null });
    await pending;
    expect(page.history()).toEqual([evidence]);
    expect(page.historyNext()).toBe('next+/=');
  });
});

describe('Device lifecycle presentation', () => {
  it('shows active lifecycle alongside failed compliance', async () => {
    const { page, fixture } = await setup();
    page.device.set({ ...device, status: 'ACTIVE', compliance: 'NONCOMPLIANT' });
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('ACTIVE');
    expect(text).toContain('NONCOMPLIANT');
    expect(text).not.toContain('Lifecycle needs review');
  });
  it('flags ambiguous legacy lifecycle without masking compliance', async () => {
    const { page, fixture } = await setup();
    page.device.set({ ...device, status: 'NONCOMPLIANT', compliance: 'NONCOMPLIANT' });
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Needs review');
    expect(text).toContain('Lifecycle needs review');
    expect(text).toContain('NONCOMPLIANT');
  });
});
