import { NO_STORED_CHOICE } from '../support/data-mode';
import { RAW_TRANSLATION_KEY } from '../support/demo-clients';
import { allowConsoleErrors, expect, test } from '../support/fixtures';

/*
 * serve.mjs has no backend: it answers /api/* and /agent/* with 404 and a plain-text body. Three
 * ways to run into that must all end gracefully:
 *  - the app starts without a stored choice (default: backend): the startup probe finds none and
 *    the app falls back to demo data with a short notice,
 *  - the advisor switches «Demo-Daten» off: the app checks for a backend first and stays on demo data,
 *  - the app starts in backend mode (switched off in an earlier session) and every request fails:
 *    `switch-to-mock` brings the demo clients back.
 * Each way `backend-error` explains it in plain words, without technical details.
 */
const TECHNICAL_ERROR = /Unexpected token|JSON|SyntaxError|NOT_FOUND|<!doctype|undefined/i;

// No allowConsoleErrors here: the fallback must not log anything beyond the browser's own 404 line.
test.describe('Startup without a backend', () => {
  test.use({ storageState: NO_STORED_CHOICE });

  test.beforeEach(async ({ startPage }) => {
    await startPage.open();
  });

  test('should fall back to demo data with a short notice', async ({
    page,
    shell,
    startPage,
    snap,
  }) => {
    await expect(startPage.rows).toHaveCount(2);
    await expect(shell.mockToggle).toBeChecked();
    await expect(shell.backendError).toContainText('Kein Backend erreichbar.');
    await expect(shell.backendError).toContainText('Die App startet mit den Demo-Daten.');
    await expect(shell.backendError).not.toContainText(TECHNICAL_ERROR);
    await expect(shell.backendError).not.toContainText(RAW_TRANSLATION_KEY);
    await expect(shell.switchToMock).toHaveCount(0);
    await expect(page.locator('body')).not.toContainText(/Unexpected token|SyntaxError/);
    await snap('backend-fallback-start');
  });

  test('should not store the fallback, so the next start tries the backend again', async ({
    page,
    shell,
  }) => {
    await expect(shell.backendError).toBeVisible();
    expect(await shell.storedDataMode()).toBeNull();

    await page.reload();

    await expect(shell.backendError).toContainText('Die App startet mit den Demo-Daten.');
  });

  test('should hide the notice on dismiss and keep the demo data', async ({
    page,
    shell,
    startPage,
  }) => {
    await page.getByRole('button', { name: 'Hinweis schliessen' }).click();

    await expect(shell.backendError).toHaveCount(0);
    await expect(startPage.rows).toHaveCount(2);
    await expect(shell.mockToggle).toBeChecked();
  });
});

test.describe('Backend unavailable', () => {
  test.beforeEach(() => {
    allowConsoleErrors(); // failed /api requests are the point of these tests
  });

  test.describe('when «Demo-Daten» is switched off', () => {
    test.beforeEach(async ({ shell, startPage }) => {
      await startPage.open();
      await expect(shell.mockToggle).toBeChecked();
    });

    test('should show a friendly error', async ({ shell, snap }) => {
      await shell.mockToggle.click();

      await expect(shell.backendError).toBeVisible();
      await expect(shell.backendError).not.toBeEmpty();
      await expect(shell.backendError).not.toContainText(TECHNICAL_ERROR);
      await expect(shell.backendError).not.toContainText(RAW_TRANSLATION_KEY);
      await snap('backend-error-toggle');
    });

    test('should not show technical error details anywhere on the page', async ({
      page,
      shell,
    }) => {
      await shell.mockToggle.click();
      await expect(shell.backendError).toBeVisible();

      await expect(page.locator('body')).not.toContainText(/Unexpected token|SyntaxError/);
      await expect(page.locator('body')).not.toContainText(/is not valid JSON/);
    });

    test('should stay on demo data with the toggle still on', async ({ shell, startPage }) => {
      await shell.mockToggle.click();
      await expect(shell.backendError).toBeVisible();

      await expect(shell.mockToggle).toBeChecked();
      await expect(startPage.rows).toHaveCount(2);
    });
  });

  test.describe('when the app starts in backend mode chosen earlier', () => {
    test.beforeEach(async ({ shell }) => {
      await shell.startInBackendMode('/');
    });

    test('should show a friendly error instead of the client list', async ({
      shell,
      startPage,
      snap,
    }) => {
      await expect(shell.backendError).toBeVisible();
      await expect(shell.backendError).not.toContainText(TECHNICAL_ERROR);
      await expect(shell.backendError).not.toContainText(RAW_TRANSLATION_KEY);
      await expect(shell.mockToggle).not.toBeChecked();
      await expect(startPage.rows).toHaveCount(0);
      await snap('backend-error-start');
    });

    test('should not show technical error details anywhere on the page', async ({
      page,
      shell,
    }) => {
      await expect(shell.backendError).toBeVisible();

      await expect(page.locator('body')).not.toContainText(/Unexpected token|SyntaxError/);
      await expect(page.locator('body')).not.toContainText(/is not valid JSON/);
    });

    test('should bring the demo clients back with «switch to mock»', async ({
      shell,
      startPage,
      snap,
    }) => {
      await expect(shell.backendError).toBeVisible();

      await shell.switchToMock.click();

      await expect(startPage.rows).toHaveCount(2);
      await expect(shell.backendError).toBeHidden();
      await expect(shell.mockToggle).toBeChecked();
      await snap('backend-error-back-to-mock');
    });

    test('should show a friendly error on a client screen too', async ({ page, shell, snap }) => {
      await page.goto('/clients/muster-maschinenbau');

      await expect(shell.backendError).toBeVisible();
      await expect(page.locator('body')).not.toContainText(/Unexpected token|SyntaxError/);
      await snap('backend-error-dashboard');
    });
  });
});
