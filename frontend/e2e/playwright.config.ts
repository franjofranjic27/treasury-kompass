import { existsSync } from 'node:fs';
import { join } from 'node:path';
import { defineConfig, devices } from '@playwright/test';
import { demoModeStorageState } from './support/data-mode';

/**
 * Two kinds of runs:
 *  - `mock-*` (default): the production build served by serve.mjs on port 4400, without a
 *    backend (/api and /agent answer 404). The browser starts with «Demo-Daten» chosen, so the
 *    tests never depend on the startup probe; tests of the startup fallback clear that choice.
 *  - `stack` (opt-in): only when E2E_STACK_URL is set, e.g. http://localhost:8088 (docker-compose gateway).
 *    Runs the tests tagged @stack against the real backend (the default without a stored choice).
 *
 * The web server always builds the app first. The compose gateway serves the same dist folder,
 * so a stack run also tests the current frontend code. E2E_SKIP_BUILD=1 reuses an existing build,
 * E2E_DIST=<folder> serves a different build without building.
 */
const PORT = Number(process.env.E2E_PORT ?? 4400);
const MOCK_URL = `http://127.0.0.1:${PORT}`;
const STACK_URL = process.env.E2E_STACK_URL;
const CI = !!process.env.CI;

const appDir = join(__dirname, '..');
const install = existsSync(join(appDir, 'node_modules')) ? '' : 'npm --prefix .. ci && ';
// E2E_DIST serves another build folder (and skips the build), e.g. a build of an older commit.
const dist = process.env.E2E_DIST;
const build = process.env.E2E_SKIP_BUILD || dist ? '' : `${install}npm --prefix .. run build && `;
const root = dist ? ` --root "${dist}"` : '';

const desktop = devices['Desktop Chrome'];
const mock = { ...desktop, baseURL: MOCK_URL, storageState: demoModeStorageState(MOCK_URL) };

export default defineConfig({
  testDir: './tests',
  outputDir: './test-results',
  fullyParallel: true,
  forbidOnly: CI,
  retries: CI ? 1 : 0,
  workers: CI ? 2 : undefined,
  timeout: 60_000,
  expect: {
    // The mock backend simulates latency: 3 × 1.2 s document processing, chat answers streamed word by word.
    timeout: 10_000,
  },
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  use: {
    locale: 'de-CH',
    timezoneId: 'Europe/Zurich',
    actionTimeout: 15_000,
    navigationTimeout: 30_000,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'off',
  },
  projects: [
    {
      name: 'mock-1440',
      grepInvert: /@stack/,
      use: { ...mock, viewport: { width: 1440, height: 900 } },
    },
    {
      name: 'mock-1280',
      grepInvert: /@stack/,
      use: { ...mock, viewport: { width: 1280, height: 720 } },
    },
    ...(STACK_URL
      ? [
          {
            name: 'stack',
            grep: /@stack/,
            // Real backend and a paid LLM: one worker, generous timeouts, no retries that repeat chat calls.
            workers: 1,
            fullyParallel: false,
            retries: 0,
            timeout: 180_000,
            use: { ...desktop, baseURL: STACK_URL, viewport: { width: 1440, height: 900 } },
          },
        ]
      : []),
  ],
  webServer: {
    command: `${build}node serve.mjs --port ${PORT}${root}`,
    cwd: __dirname,
    url: MOCK_URL,
    reuseExistingServer: !CI,
    // An Angular production build takes about a minute, plus `npm ci` on a fresh checkout.
    timeout: 300_000,
    stdout: 'pipe',
    stderr: 'pipe',
  },
});
