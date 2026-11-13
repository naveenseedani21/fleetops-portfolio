import { test, expect, Page, APIRequestContext } from '@playwright/test';

const api = process.env.FLEETOPS_E2E_API || 'http://localhost:8080';
const issuer = process.env.FLEETOPS_E2E_ISSUER || 'http://localhost:8180/realms/fleetops';
const organization = api + '/api/v1/organizations/demo';
const registration = (serial: string) => ({
  name: 'Reviewer Mac ' + serial, type: 'MAC', model: 'Synthetic MacBook',
  os: 'macOS', osVersion: '16.0', serial,
});
const policyInput = {
  version: 0, name: 'Reviewer security baseline', description: '', targetTypes: ['MAC'],
  severity: 'HIGH', enabled: true,
  rules: [{ type: 'MIN_OS', value: '17.0' }, { type: 'ENCRYPTION', value: 'true' }],
};
async function login(page: Page, username: string, password: string) {
  await page.goto('/');
  await page.getByRole('button', { name: 'Sign in securely' }).click();
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.locator('#kc-login').click();
  await expect(page.getByRole('heading', { name: 'Device inventory', exact: true })).toBeVisible();
  // The production CSP must not leave the stylesheet deferred behind a blocked inline handler.
  await expect(page.locator('.shell')).toHaveCSS('display', 'flex');
  await expect(page.locator('aside')).toHaveCSS('position', 'fixed');
}
async function browserHeaders(page: Page) {
  // Actual PKCE session from the running frontend; never an authentication mock.
  const token = await page.evaluate(() => sessionStorage.getItem('access_token'));
  expect(token).toBeTruthy();
  return { Authorization: 'Bearer ' + token };
}
async function read(request: APIRequestContext, url: string, headers: Record<string, string>) {
  const response = await request.get(url, { headers });
  expect(response.ok(), url + ': ' + response.status()).toBeTruthy();
  return response.json();
}
async function pages(request: APIRequestContext, url: string, headers: Record<string, string>) {
  const items = [];
  let cursor: string | null = null;
  do {
    const page = await read(request, url + '?limit=100' + (cursor ? '&cursor=' + encodeURIComponent(cursor) : ''), headers);
    items.push(...page.items);
    cursor = page.nextCursor;
  } while (cursor);
  return items;
}

test('administrator provisioning, restricted machine reports, visible failure and recovery', async ({ page, request }, testInfo) => {
  await login(page, 'admin', 'local-admin-only');
  const unique = Date.now().toString();
  const name = 'Reviewer Mac ' + unique;
  await page.getByRole('button', { name: '+ Register device' }).click();
  await page.getByLabel('Device name', { exact: true }).fill(name);
  await page.getByLabel('Model', { exact: true }).fill('Synthetic MacBook');
  await page.getByLabel('OS version', { exact: true }).fill('16.0');
  await page.getByLabel('Serial number', { exact: true }).fill('E2E-' + unique);
  const registered = page.waitForResponse(r => r.request().method() === 'POST' && r.url().endsWith('/organizations/demo/devices'));
  await page.getByRole('button', { name: 'Register device', exact: true }).click();
  const response = await registered;
  expect(response.status()).toBe(201);
  const device = await response.json();
  expect(device.status).toBe('REGISTERED');
  expect(device.compliance).toBe('UNKNOWN');
  const headers = await browserHeaders(page);
  const deviceUrl = organization + '/devices/' + device.id;

  await page.getByRole('link', { name: 'Security policies' }).click();
  await page.getByRole('button', { name: '+ Create policy' }).click();
  const policyName = 'Reviewer baseline ' + unique;
  await page.getByLabel('Policy name', { exact: true }).fill(policyName);
  await page.getByRole('button', { name: '+ Add requirement', exact: true }).click();
  const secondRequirement = page.getByRole('combobox', { name: 'Requirement 2', exact: true });
  await secondRequirement.selectOption('MIN_OS');
  await expect(secondRequirement).toHaveValue('MIN_OS');
  await page.getByLabel('Minimum version', { exact: true }).fill('17.0');
  const policySaved = page.waitForResponse(r => r.request().method() === 'POST' && r.url().endsWith('/organizations/demo/policies'));
  await page.getByRole('button', { name: 'Save policy version' }).click();
  expect((await policySaved).status()).toBe(201);
  await page.getByRole('button', { name: 'View history for ' + policyName, exact: true }).click();
  await expect(page.getByText(/Version 1 ·/)).toBeVisible();
  await page.getByRole('button', { name: 'Close history', exact: true }).click();

  const credentials = await request.post(issuer + '/protocol/openid-connect/token', {
    form: { grant_type: 'client_credentials', client_id: 'fleetops-simulator', client_secret: 'local-simulator-secret-not-for-production' },
  });
  expect(credentials.ok()).toBeTruthy();
  const machine = { Authorization: 'Bearer ' + (await credentials.json()).access_token };
  const firstReport = { reportId: 'browser-a-' + unique, sequence: 1, reportedAt: new Date().toISOString(), osVersion: '16.0', encryption: false, screenLock: true, firewall: true };
  const failed = await request.post(deviceUrl + '/reports', { headers: machine, data: firstReport });
  expect(failed.status()).toBe(200);
  expect(await failed.json()).toMatchObject({ status: 'ACTIVE', compliance: 'NONCOMPLIANT' });
  await page.goto('/devices/' + device.id);
  await expect(page.locator('.page-heading .badge')).toHaveText('NONCOMPLIANT');
  await expect(page.getByRole('heading', { name: /^Compliance evidence/ })).toBeVisible();
  await expect(page.locator('dt').filter({ hasText: /^Lifecycle$/ }).locator('+ dd')).toHaveText('ACTIVE');
  await page.locator('details').first().locator('summary').click();
  await expect(page.locator('details').first()).toContainText('MIN_OS');
  await expect(page.locator('details').first()).toContainText('ENCRYPTION');
  await page.screenshot({ path: testInfo.outputPath('compliance-failure.png'), fullPage: true });

  // Manual evaluation must not change ACTIVE lifecycle either.
  await page.getByRole('button', { name: 'Evaluate policies' }).click();
  await expect(page.getByText('Change saved.', { exact: true })).toBeVisible();
  expect((await read(request, deviceUrl, headers)).status).toBe('ACTIVE');
  const corrected = { ...firstReport, reportId: 'browser-b-' + unique, sequence: 2, reportedAt: new Date().toISOString(), osVersion: '17.0', encryption: true };
  const recovered = await request.post(deviceUrl + '/reports', { headers: machine, data: corrected });
  expect(recovered.status()).toBe(200);
  const healthy = await recovered.json();
  expect(healthy).toMatchObject({ status: 'ACTIVE', compliance: 'COMPLIANT' });
  await page.reload();
  await expect(page.locator('.page-heading .badge')).toHaveText('COMPLIANT');
  await expect(page.locator('dt').filter({ hasText: /^Lifecycle$/ }).locator('+ dd')).toHaveText('ACTIVE');
  await page.locator('details').first().locator('summary').click();
  await expect(page.locator('details').first()).toContainText('MIN_OS');
  await expect(page.locator('details').first()).toContainText('COMPLIANT');
  await page.screenshot({ path: testInfo.outputPath('compliance-recovery.png'), fullPage: true });
  const evidence = await pages(request, deviceUrl + '/evaluations', headers);
  const audit = await pages(request, organization + '/audit-events', headers);
  const policies = await pages(request, organization + '/policies', headers);
  const inventory = await pages(request, organization + '/devices', headers);

  // A → B → A uses the original accepted content; a conflicting ID cannot change state.
  const replay = await request.post(deviceUrl + '/reports', { headers: machine, data: firstReport });
  expect(replay.status()).toBe(200);
  expect(await replay.json()).toEqual(healthy);
  expect((await request.post(deviceUrl + '/reports', { headers: machine, data: { ...firstReport, sequence: 3 } })).status()).toBe(409);
  expect((await request.post(deviceUrl + '/reports', { headers: machine, data: { ...firstReport, reportId: 'browser-old-' + unique } })).status()).toBe(409);
  expect((await request.post(organization + '/devices', { headers: machine, data: registration('DENIED-' + unique) })).status()).toBe(403);
  expect((await request.post(organization + '/policies', { headers: machine, data: policyInput })).status()).toBe(403);
  expect((await request.post(organization + '/devices/unknown-device/reports', { headers: machine, data: corrected })).status()).toBe(404);
  expect(await read(request, deviceUrl, headers)).toEqual(healthy);
  expect(await pages(request, deviceUrl + '/evaluations', headers)).toEqual(evidence);
  expect(await pages(request, organization + '/audit-events', headers)).toEqual(audit);
  expect(await pages(request, organization + '/policies', headers)).toEqual(policies);
  expect(await pages(request, organization + '/devices', headers)).toEqual(inventory);

  // An actual existing lab device establishes that denial is tenant authorization, not a missing ID.
  const lab = api + '/api/v1/organizations/lab';
  const labRegistration = await request.post(lab + '/devices', { headers, data: registration('LAB-' + unique) });
  expect(labRegistration.status()).toBe(201);
  const labDevice = await labRegistration.json();
  const labAudit = await pages(request, lab + '/audit-events', headers);
  expect((await request.post(lab + '/devices/' + labDevice.id + '/reports', { headers: machine, data: corrected })).status()).toBe(403);
  expect(await read(request, lab + '/devices/' + labDevice.id, headers)).toEqual(labDevice);
  expect(await pages(request, lab + '/audit-events', headers)).toEqual(labAudit);
  expect(await pages(request, lab + '/devices/' + labDevice.id + '/evaluations', headers)).toEqual([]);

  await page.getByRole('button', { name: 'Retire device', exact: true }).click();
  await page.getByRole('button', { name: 'Confirm retirement', exact: true }).click();
  await expect(page.locator('dt').filter({ hasText: /^Lifecycle$/ }).locator('+ dd')).toHaveText('RETIRED');
  const retired = await read(request, deviceUrl, headers);
  expect(retired).toMatchObject({ status: 'RETIRED', compliance: 'COMPLIANT' });
  expect((await request.post(deviceUrl + '/reports', { headers: machine, data: { ...corrected, reportId: 'browser-c-' + unique, sequence: 3 } })).status()).toBe(400);
  expect((await request.post(deviceUrl + '/evaluations', { headers, data: {} })).status()).toBe(400);
  expect(await read(request, deviceUrl, headers)).toEqual(retired);
  expect(await pages(request, deviceUrl + '/evaluations', headers)).toEqual(evidence);
  const deviceAudit = (await pages(request, organization + '/audit-events', headers)).filter(event => event.targetId === device.id);
  expect(deviceAudit.length).toBeGreaterThanOrEqual(6);
});

test('viewer cannot provision or change policies and cannot cross the tenant boundary', async ({ page, request }, testInfo) => {
  await login(page, 'viewer', 'local-viewer-only');
  await expect(page.getByRole('button', { name: '+ Register device' })).toHaveCount(0);
  const headers = await browserHeaders(page);
  const inventory = await pages(request, organization + '/devices', headers);
  const audit = await pages(request, organization + '/audit-events', headers);
  const policies = await pages(request, organization + '/policies', headers);
  expect((await request.post(organization + '/devices', { headers, data: registration('VIEWER-' + Date.now()) })).status()).toBe(403);
  expect((await request.post(organization + '/policies', { headers, data: policyInput })).status()).toBe(403);
  expect((await request.get(api + '/api/v1/organizations/lab/devices', { headers })).status()).toBe(403);
  expect(await pages(request, organization + '/devices', headers)).toEqual(inventory);
  expect(await pages(request, organization + '/audit-events', headers)).toEqual(audit);
  expect(await pages(request, organization + '/policies', headers)).toEqual(policies);
  const policiesLoaded = page.waitForResponse(r => r.request().method() === 'GET' && new URL(r.url()).pathname === '/api/v1/organizations/demo/policies');
  await page.getByRole('link', { name: 'Security policies' }).click();
  expect((await policiesLoaded).status()).toBe(200);
  await expect(page.getByRole('heading', { name: 'Security policies', exact: true })).toBeVisible();
  await expect(page.getByText('Loading policies…', { exact: true })).toBeHidden();
  await expect(page.getByRole('button', { name: '+ Create policy' })).toHaveCount(0);
  await page.screenshot({ path: testInfo.outputPath('viewer-policies.png'), fullPage: true });
});
