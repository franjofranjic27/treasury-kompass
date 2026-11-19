import { test as base, expect } from '@playwright/test';
import { AppShell } from './pages/app-shell';
import { BeratungPage } from './pages/beratung-page';
import { ChatPanel } from './pages/chat-panel';
import { DashboardPage } from './pages/dashboard-page';
import { ProfilierungPage } from './pages/profilierung-page';
import { StartPage } from './pages/start-page';
import { type SnapOptions, snap } from './screenshots';

interface Fixtures {
  shell: AppShell;
  chat: ChatPanel;
  startPage: StartPage;
  profilierung: ProfilierungPage;
  dashboard: DashboardPage;
  beratung: BeratungPage;
  /** Saves screenshots/<project>/<name>.png. */
  snap: (name: string, options?: SnapOptions) => Promise<void>;
  /** Fails the test on uncaught page errors and console errors; see {@link allowConsoleErrors}. */
  pageErrors: void;
}

const ALLOW_CONSOLE_ERRORS = 'allow-console-errors';

/** Opt-out for tests that provoke errors on purpose, e.g. a missing backend. */
export function allowConsoleErrors(): void {
  base.info().annotations.push({ type: ALLOW_CONSOLE_ERRORS });
}

export const test = base.extend<Fixtures>({
  shell: async ({ page }, use) => use(new AppShell(page)),
  chat: async ({ page }, use) => use(new ChatPanel(page)),
  startPage: async ({ page }, use) => use(new StartPage(page)),
  profilierung: async ({ page }, use) => use(new ProfilierungPage(page)),
  dashboard: async ({ page }, use) => use(new DashboardPage(page)),
  beratung: async ({ page }, use) => use(new BeratungPage(page)),
  snap: async ({ page }, use, testInfo) => {
    await use((name, options) => snap(page, testInfo, name, options));
  },
  pageErrors: [
    async ({ page }, use, testInfo) => {
      const errors: string[] = [];
      page.on('pageerror', (error) => errors.push(`pageerror: ${error.message}`));
      page.on('console', (message) => {
        // A failed request is reported by the test that cares about it, not as a console error.
        if (message.type() === 'error' && !message.text().startsWith('Failed to load resource')) {
          errors.push(`console.error: ${message.text()}`);
        }
      });
      await use();
      const allowed = testInfo.annotations.some((a) => a.type === ALLOW_CONSOLE_ERRORS);
      if (!allowed) expect(errors, 'the app must not log errors').toEqual([]);
    },
    { auto: true },
  ],
});

export { expect };
