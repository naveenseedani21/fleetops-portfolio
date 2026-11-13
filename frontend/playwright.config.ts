import { defineConfig } from '@playwright/test';
export default defineConfig({
 testDir: './e2e', fullyParallel: false, workers: 1, timeout: 60000,
 use: {baseURL: 'http://localhost:4200', headless: true, viewport: {width:1440,height:1000}, trace:'retain-on-failure', channel: process.env.PLAYWRIGHT_CHANNEL || undefined},
 reporter: [['list'],['html',{open:'never'}]],
});
