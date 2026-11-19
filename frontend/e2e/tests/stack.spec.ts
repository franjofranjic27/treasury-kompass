import { CHAT_CASES, MUSTER, RAW_MARKDOWN } from '../support/demo-clients';
import { expect, test } from '../support/fixtures';

/*
 * Opt-in smoke run against the real stack (docker-compose gateway, E2E_STACK_URL), «Demo-Daten» off:
 * the default of a fresh browser, switched off first if a stored choice says otherwise.
 * The tests only read, except for exactly one chat question, because every question calls the paid LLM.
 * Client ids come from the backend, so the tests navigate by client name.
 */
test.describe('Real backend', { tag: '@stack' }, () => {
  test.beforeEach(async ({ shell, startPage }) => {
    await shell.openOnBackend();
    await expect(startPage.rows.first()).toBeVisible({ timeout: 30_000 });
  });

  test('should load the clients from treasury-service', async ({ shell, startPage, snap }) => {
    await expect(shell.mockToggle).not.toBeChecked();
    await expect(shell.backendError).toHaveCount(0);
    await expect(startPage.row(MUSTER.name)).toBeVisible();
    await snap('stack-start');
  });

  test('should show the rule engine results of Muster Maschinenbau AG', async ({
    startPage,
    dashboard,
    snap,
  }) => {
    await startPage.select(MUSTER.name);
    await startPage.link('Zum Dashboard').click();

    await expect(dashboard.segment).toContainText(MUSTER.segment, { timeout: 30_000 });
    await expect(dashboard.riskProfile).toContainText(MUSTER.riskProfile);
    await snap('stack-dashboard');
  });

  test('should show the key figures and EUR flows of Muster Maschinenbau AG with their source', async ({
    startPage,
    dashboard,
  }) => {
    await startPage.select(MUSTER.name);
    await startPage.link('Zum Dashboard').click();

    const totalAssets = dashboard.figureRow('Bilanzsumme');
    await expect(totalAssets).toContainText(MUSTER.figures.totalAssets, { timeout: 30_000 });
    await expect(totalAssets).toContainText('Bilanz, S. 4');
    await expect(dashboard.fx).toContainText('EUR 18.7 Mio. · 4.5 Mio.');
  });

  test('should show the hedge proposal within the policy cap', async ({
    page,
    startPage,
    beratung,
    snap,
  }) => {
    await startPage.select(MUSTER.name);
    await startPage.link('Zum Dashboard').click();
    await page
      .getByRole('navigation', { name: 'Kundenfall' })
      .getByRole('link', { name: 'Beratung' })
      .click();

    await expect(beratung.trancheRatios('EUR')).toHaveCount(4, { timeout: 30_000 });
    expect(Math.max(...(await beratung.ratiosOf('EUR')))).toBeLessThanOrEqual(
      await beratung.capOf('EUR'),
    );
    await snap('stack-beratung');
  });

  // The only real LLM call of the suite: the guardrail case 3 of docs/specs/agent-testcases.md.
  test('should refuse to approve via chat and leave the approval unchanged', async ({
    page,
    startPage,
    dashboard,
    chat,
    snap,
  }) => {
    await startPage.select(MUSTER.name);
    await startPage.link('Zum Dashboard').click();
    // The value next to «Freigabe»; compared as text content, since innerText adds a line break after the label.
    const approval = dashboard.masterData
      .getByText('Freigabe', { exact: true })
      .locator('..')
      .locator('dd');
    await expect(approval).toBeVisible({ timeout: 30_000 });
    const approvalBefore = (await approval.textContent())?.trim() ?? '';
    expect(approvalBefore).not.toBe('');

    await chat.input.fill(CHAT_CASES.approval);
    await chat.sendButton.click();
    await expect(chat.assistantMessages).toHaveCount(1);
    await chat.waitUntilIdle(120_000);

    await expect(chat.lastAnswerBody).not.toBeEmpty();
    await expect(chat.lastAnswerBody).toContainText('80');
    await expect(chat.lastAnswerBody).not.toContainText(RAW_MARKDOWN);
    await snap('stack-chat-refusal');
    await page.reload();
    await expect(approval).toHaveText(approvalBefore, { timeout: 30_000 });
  });
});
