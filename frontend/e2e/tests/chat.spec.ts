import { CHAT_CASES, COMPONENTS, MUSTER, RAW_MARKDOWN } from '../support/demo-clients';
import { expect, test } from '../support/fixtures';
import { goToCaseSection } from '../support/flows';

test.describe('Chat', () => {
  test.describe('conversation', () => {
    test('should show the question and the streamed answer', async ({ dashboard, chat }) => {
      await dashboard.open(MUSTER.id);

      await chat.ask('Welche Unterlagen liegen vor?');

      await expect(chat.userMessages.last()).toContainText('Welche Unterlagen liegen vor?');
      await expect(chat.lastAnswerBody).not.toBeEmpty();
      await expect(chat.input).toHaveValue('');
    });

    test('should send the question with the Enter key', async ({ dashboard, chat }) => {
      await dashboard.open(MUSTER.id);

      await chat.input.fill(CHAT_CASES.segment);
      await chat.input.press('Enter');

      await expect(chat.assistantMessages).toHaveCount(1);
      await chat.waitUntilIdle();
      await expect(chat.lastAnswerBody).toContainText('Privatkunde');
    });

    test('should not send an empty question', async ({ dashboard, chat }) => {
      await dashboard.open(MUSTER.id);

      await chat.input.fill('   ');

      await expect(chat.sendButton).toBeDisabled();
      await expect(chat.messages).toHaveCount(0);
    });

    test('should ask for a client when none is selected', async ({ startPage, chat }) => {
      await startPage.open();

      await chat.ask(CHAT_CASES.segment);

      await expect(chat.lastAnswerBody).toContainText('Bitte zuerst einen Kunden auswählen');
    });
  });

  // docs/specs/agent-testcases.md
  test.describe('scripted agent test cases', () => {
    test('case 1: should report EUR inflows and outflows with their source', async ({
      dashboard,
      chat,
      snap,
    }) => {
      await dashboard.open(COMPONENTS.id);

      await chat.ask(CHAT_CASES.eurFlows);

      await expect(chat.lastAnswerBody).toContainText('85.4');
      await expect(chat.lastAnswerBody).toContainText('33.0');
      await expect(chat.lastAnswer).toContainText('Geschäftsbericht 2025');
      await expect(chat.lastAnswer).toContainText('Segmentbericht, S. 41');
      await expect(chat.lastAnswerBody).not.toContainText(RAW_MARKDOWN);
      await snap('chat-case-1-eur-flows');
    });

    test('case 2: should explain the segment and the policy cap from the rule engine', async ({
      dashboard,
      chat,
      snap,
    }) => {
      await dashboard.open(MUSTER.id);

      await chat.ask(CHAT_CASES.segment);

      await expect(chat.lastAnswerBody).toContainText('Privatkunde');
      await expect(chat.lastAnswerBody).toContainText('1 von 3 Schwellenwerten');
      await expect(chat.lastAnswerBody).toContainText('Art. 4 und 5 FIDLEG');
      await expect(chat.lastAnswerBody).toContainText(/höchstens 80\s?%/);
      await expect(chat.lastAnswerBody).toContainText(/80 \/ 70 \/ 60 \/ 50/);
      await expect(chat.lastAnswer).toContainText('Audit-Trail');
      await expect(chat.lastAnswerBody).not.toContainText(RAW_MARKDOWN);
      await snap('chat-case-2-segment');
    });

    test('case 3: should refuse to approve and offer a draft at the policy cap', async ({
      dashboard,
      chat,
      snap,
    }) => {
      await dashboard.open(MUSTER.id);

      await chat.ask(CHAT_CASES.approval);

      await expect(chat.lastAnswerBody).toContainText('Das kann ich nicht übernehmen');
      await expect(chat.lastAnswerBody).toContainText(/höchstens 80\s?%/);
      await expect(chat.lastAnswerBody).toContainText('nur der Berater');
      await expect(chat.lastAnswerBody).not.toContainText(
        /(habe|ist|wurde)( den Vorschlag| die Absicherung)? (jetzt |nun )?freigegeben/,
      );
      await expect(chat.lastAnswer).toContainText('Entwurf: EUR-Absicherung 80 / 80 / 80 / 80 %');
      await expect(chat.lastAnswerBody).not.toContainText(RAW_MARKDOWN);
      await snap('chat-case-3-refusal');
    });

    test('case 3: should leave the case unapproved after the request', async ({
      page,
      dashboard,
      beratung,
      chat,
    }) => {
      await dashboard.open(MUSTER.id);

      await chat.ask(CHAT_CASES.approval);
      await goToCaseSection(page, 'Beratung');

      await expect(beratung.banner).toContainText('Entwurf, nicht freigegeben.');
      await expect(beratung.approveButton).toBeDisabled();
      await expect(beratung.trancheRatios('EUR')).toHaveText(MUSTER.eurTranches);
    });

    test('case 3: should apply the draft as the advisor without approving it', async ({
      page,
      dashboard,
      beratung,
      chat,
      snap,
    }) => {
      await dashboard.open(MUSTER.id);
      await chat.ask(CHAT_CASES.approval);

      await chat.lastAnswer.getByRole('button', { name: 'Übernehmen' }).click();

      await expect(chat.lastAnswer).toContainText('Übernommen, bitte in der Beratung prüfen');
      await goToCaseSection(page, 'Beratung');
      await expect(beratung.trancheRatios('EUR')).toHaveText(['80 %', '80 %', '80 %', '80 %']);
      expect(Math.max(...(await beratung.ratiosOf('EUR')))).toBeLessThanOrEqual(MUSTER.cap);
      await expect(beratung.banner).toContainText('Entwurf, nicht freigegeben.');
      await snap('chat-case-3-draft-applied');
    });
  });

  test.describe('markdown', () => {
    test('should render the balance sheet answer as a table', async ({ dashboard, chat, snap }) => {
      await dashboard.open(MUSTER.id);

      await chat.ask(CHAT_CASES.balanceSheet);

      const table = chat.lastAnswerBody.getByRole('table');
      await expect(table).toBeVisible();
      await expect(table).toContainText(MUSTER.figures.totalAssets);
      await expect(table).toContainText(MUSTER.figures.equity);
      await expect(table).toContainText('6.4');
      await expect(chat.lastAnswerBody).not.toContainText(RAW_MARKDOWN);
      await snap('chat-markdown-table');
    });
  });

  test.describe('collapse', () => {
    test('should collapse and widen the main content', async ({ dashboard, shell, chat, snap }) => {
      await dashboard.open(MUSTER.id);
      await expect(chat.toggle).toHaveAttribute('aria-expanded', 'true');
      const widthExpanded = await shell.mainWidth();

      await chat.collapse();

      await expect(chat.input).toBeHidden();
      await expect.poll(() => shell.mainWidth()).toBeGreaterThan(widthExpanded);
      await snap('chat-collapsed');
    });

    test('should expand again', async ({ dashboard, shell, chat }) => {
      await dashboard.open(MUSTER.id);
      const widthExpanded = await shell.mainWidth();
      await chat.collapse();

      await chat.expand();

      await expect(chat.input).toBeVisible();
      await expect.poll(() => shell.mainWidth()).toBe(widthExpanded);
    });

    test('should stay collapsed after a reload', async ({ page, dashboard, shell, chat }) => {
      await dashboard.open(MUSTER.id);
      const widthExpanded = await shell.mainWidth();
      await chat.collapse();

      await page.reload();

      await expect(chat.toggle).toHaveAttribute('aria-expanded', 'false');
      await expect(chat.input).toBeHidden();
      await expect.poll(() => shell.mainWidth()).toBeGreaterThan(widthExpanded);
    });

    test('should stay expanded after a reload once expanded again', async ({
      page,
      dashboard,
      chat,
    }) => {
      await dashboard.open(MUSTER.id);
      await chat.collapse();
      await chat.expand();

      await page.reload();

      await expect(chat.toggle).toHaveAttribute('aria-expanded', 'true');
      await expect(chat.input).toBeVisible();
    });
  });
});
