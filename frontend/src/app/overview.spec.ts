import { vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { Api } from './api';
import { Overview } from './overview';

it('renders lifecycle, compliance and unresolved legacy counts independently', async () => {
  const summary = {
    total: 3,
    retired: 0,
    active: 2,
    stale: 1,
    noncompliant: 3,
    unknown: 0,
    evaluated: 3,
    compliancePercent: 0,
    lifecycleReviewRequired: 1,
    operatingSystems: { macOS: 3 },
    compliance: { NONCOMPLIANT: 3 },
    calculatedAt: '2026-11-13T00:00:00Z',
  };
  TestBed.configureTestingModule({
    imports: [Overview],
    providers: [
      provideRouter([]),
      {
        provide: Api,
        useValue: {
          organization: signal('team'),
          get: vi
            .fn()
            .mockImplementation((path: string) =>
              Promise.resolve(
                path === 'dashboard/summary' ? summary : { items: [], nextCursor: null },
              ),
            ),
        },
      },
    ],
  });
  const fixture = TestBed.createComponent(Overview);
  fixture.detectChanges();
  await fixture.whenStable();
  await fixture.componentInstance.load();
  fixture.detectChanges();
  const metrics = Array.from(
    fixture.nativeElement.querySelectorAll('.metric') as NodeListOf<HTMLElement>,
  ).map((element) => element.textContent?.replace(/\s+/g, ' ').trim());
  expect(metrics[1]).toMatch(/Active devices\s*2/);
  expect(metrics[2]).toMatch(/Noncompliant\s*3/);
  expect(fixture.nativeElement.textContent).toMatch(/1\s+device needs lifecycle review/);
  expect(fixture.nativeElement.querySelector('.notice a').getAttribute('href')).toBe('/devices');
});
