import { lifecycleLabel, lifecycleNeedsReview } from './lifecycle';
describe('Independent lifecycle display', () => {
  it('keeps an active, noncompliant device active', () => {
    const device = { status: 'ACTIVE', compliance: 'NONCOMPLIANT' };
    expect(lifecycleLabel(device)).toBe('ACTIVE');
    expect(lifecycleNeedsReview(device)).toBe(false);
  });
  it('flags ambiguous legacy and unrecognized states without guessing', () => {
    expect(lifecycleLabel({ status: 'NONCOMPLIANT' })).toBe('Needs review');
    expect(lifecycleNeedsReview({ status: 'NONCOMPLIANT' })).toBe(true);
    expect(lifecycleNeedsReview({ status: 'unexpected' })).toBe(true);
  });
  it('preserves inactive and retired labels', () => {
    expect(lifecycleLabel({ status: 'INACTIVE' })).toBe('INACTIVE');
    expect(lifecycleLabel({ status: 'RETIRED' })).toBe('RETIRED');
  });
});
