import { vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { Policies } from './policies';
import { Api, Page, Policy } from './api';
const baseline: Policy = {
  id: 'baseline',
  name: 'Device baseline',
  description: 'Core requirements',
  targetTypes: ['MAC', 'IPAD'],
  severity: 'HIGH',
  enabled: true,
  version: 4,
  rules: [
    { type: 'ENCRYPTION', value: 'true' },
    { type: 'MIN_OS', value: '15.2' },
  ],
  updatedAt: '2026-11-12T12:00:00Z',
};
async function setup(admin = true) {
  const api = {
    organization: signal('team'),
    admin,
    get: vi.fn().mockResolvedValue({ items: [baseline], nextCursor: null }),
    put: vi.fn().mockResolvedValue({}),
    post: vi.fn().mockResolvedValue({}),
  };
  TestBed.configureTestingModule({
    imports: [Policies],
    providers: [{ provide: Api, useValue: api }],
  });
  const fixture = TestBed.createComponent(Policies);
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
  return { fixture, page: fixture.componentInstance, api };
}
describe('Policy editor', () => {
  it('preserves compound rules, targets and revision when editing', async () => {
    const { fixture, page, api } = await setup();
    page.edit(baseline);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('.rule-editor').length).toBe(2);
    page.form.controls.name.setValue('Updated baseline');
    page.addRule();
    await page.save();
    expect(api.put).toHaveBeenCalledWith('policies/baseline', {
      name: 'Updated baseline',
      description: 'Core requirements',
      targetTypes: ['MAC', 'IPAD'],
      severity: 'HIGH',
      enabled: true,
      version: 4,
      rules: [...baseline.rules, { type: 'SCREEN_LOCK', value: 'true' }],
    });
  });
  it('blocks invalid values, missing targets and duplicate rules', async () => {
    const { page, api } = await setup();
    page.edit(baseline);
    page.rules.at(1).controls.value.setValue('Sequoia');
    await page.save();
    expect(api.put).not.toHaveBeenCalled();
    page.rules.at(1).controls.value.setValue('15.3');
    page.form.controls.targets.setValue({ MAC: false, IPHONE: false, IPAD: false });
    await page.save();
    expect(api.put).not.toHaveBeenCalled();
    page.form.controls.targets.controls.MAC.setValue(true);
    page.rules.at(1).setValue({ type: 'ENCRYPTION', value: 'false' });
    await page.save();
    expect(page.rules.hasError('duplicate')).toBe(true);
    expect(api.put).not.toHaveBeenCalled();
  });
  it('retains unsaved edits on server failure', async () => {
    const { page, api } = await setup();
    page.edit(baseline);
    page.form.controls.name.setValue('Unsaved change');
    api.put.mockRejectedValue(new Error('offline'));
    await page.save();
    expect(page.open()).toBe(true);
    expect(page.form.controls.name.value).toBe('Unsaved change');
    expect(page.error()).toBeTruthy();
    expect(page.saving()).toBe(false);
  });
  it('lets viewers read paginated history without editing actions', async () => {
    const { fixture, page, api } = await setup(false);
    expect(fixture.nativeElement.textContent).not.toContain('+ Create policy');
    expect(fixture.nativeElement.querySelector('[aria-label="Edit Device baseline"]')).toBeNull();
    api.get.mockResolvedValueOnce({ items: [baseline], nextCursor: 'older+/=' });
    await page.showHistory(baseline);
    api.get.mockResolvedValueOnce({ items: [{ ...baseline, version: 3 }], nextCursor: null });
    await page.showHistory(baseline, page.historyNext()!);
    expect(api.get).toHaveBeenLastCalledWith(
      'policies/baseline/history?limit=10&cursor=older%2B%2F%3D',
    );
    expect(page.versions().map((p) => p.version)).toEqual([4, 3]);
    page.edit(baseline);
    await page.save();
    expect(api.put).not.toHaveBeenCalled();
  });
  it('ignores delayed history from a previously selected policy', async () => {
    const { page, api } = await setup();
    let resolve!: (p: Page<Policy>) => void;
    api.get.mockReturnValueOnce(
      new Promise<Page<Policy>>((r) => {
        resolve = r;
      }),
    );
    const old = page.showHistory(baseline);
    const other = { ...baseline, id: 'other', name: 'Other policy' };
    api.get.mockResolvedValueOnce({ items: [other], nextCursor: null });
    await page.showHistory(other);
    resolve({ items: [baseline], nextCursor: 'stale' });
    await old;
    expect(page.historyPolicy()?.id).toBe('other');
    expect(page.versions()[0].id).toBe('other');
    expect(page.historyNext()).toBeNull();
  });
});
