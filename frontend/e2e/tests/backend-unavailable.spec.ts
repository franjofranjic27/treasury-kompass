import { RAW_TRANSLATION_KEY } from '../support/demo-clients';
import { allowConsoleErrors, expect, test } from '../support/fixtures';

/*
 * The static deployment has no backend: serve.mjs answers /api/* and /agent/* with 404 and a
 * plain-text body, like Vercel. Two ways to run into that must both fail gracefully:
 *  - the advisor switches «Demo-Daten» off: the app checks for a backend first and stays on demo data,
 *  - the app starts in backend mode (switched off in an earlier session) and every request fails:
 *    `switch-to-mock` brings the demo clients back.
 * Either way `backend-error` explains it in plain words, without technical details.
 */
const TECHNICAL_ERROR = /Unexpected token|JSON|SyntaxError|NOT_FOUND|<!doctype|undefined/i;

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

  test.describe('when the app starts in backend mode', () => {
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
