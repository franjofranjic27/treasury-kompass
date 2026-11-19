import { MUSTER } from '../support/demo-clients';
import { expect, test } from '../support/fixtures';

/*
 * Muster Maschinenbau AG: 22 questions, 13 prefilled from documents or rules (status «Vorschlag»),
 * the other 9 entered by the advisor. The first section is «Finanzielle Verhältnisse».
 */
const UNCONFIRMED_AT_START = MUSTER.totalQuestions - MUSTER.prefilledToConfirm;

test.describe('Profilierung', () => {
  test.beforeEach(async ({ profilierung }) => {
    await profilierung.open(MUSTER.id);
  });

  test('should show a prefilled answer with tag «Vorschlag» and its Fundstelle', async ({
    profilierung,
    snap,
  }) => {
    const proposal = profilierung.proposalOf('Umsatzerlös');

    await expect(proposal).toContainText('Vorschlag');
    await expect(proposal).toContainText('aus Erfolgsrechnung, S. 5');
    await expect(profilierung.page.getByLabel('Umsatzerlös', { exact: true })).toHaveValue('28.4');
    await snap('profilierung');
  });

  test('should start with the confirmed and prefilled counts of the demo case', async ({
    profilierung,
  }) => {
    await expect(profilierung.questionsCompleted).toContainText(
      `${UNCONFIRMED_AT_START} von ${MUSTER.totalQuestions}`,
    );
    await expect(profilierung.prefilledConfirmed).toContainText(
      `0 von ${MUSTER.prefilledToConfirm}`,
    );
  });

  test('should increase the progress when a proposal is confirmed', async ({ profilierung }) => {
    await profilierung.confirmButton('Umsatzerlös').click();

    await expect(profilierung.questionsCompleted).toContainText(
      `${UNCONFIRMED_AT_START + 1} von ${MUSTER.totalQuestions}`,
    );
    await expect(profilierung.prefilledConfirmed).toContainText(
      `1 von ${MUSTER.prefilledToConfirm}`,
    );
    await expect(profilierung.confirmButton('Umsatzerlös')).toHaveCount(0);
  });

  test('should keep the Fundstelle once a proposal is confirmed', async ({
    profilierung,
    page,
  }) => {
    await profilierung.confirmButton('Umsatzerlös').click();

    await expect(page.getByText('Fundstelle: Erfolgsrechnung, S. 5').first()).toBeVisible();
  });

  test('should mark a section as done in the stepper when all its proposals are confirmed', async ({
    profilierung,
  }) => {
    const financials = profilierung.step('Finanzielle Verhältnisse');
    await expect(financials).toContainText('7 Vorschläge zu bestätigen');

    await profilierung.confirmAllPrefilled();

    await expect(financials).toContainText('Erledigt');
    await expect(profilierung.questionsCompleted).toContainText(
      `${MUSTER.totalQuestions} von ${MUSTER.totalQuestions}`,
    );
  });

  test.describe('stepper', () => {
    test('should switch the section when a step is clicked', async ({
      profilierung,
      page,
      snap,
    }) => {
      await expect(profilierung.step('Finanzielle Verhältnisse')).toHaveAttribute(
        'aria-current',
        'step',
      );

      await profilierung.goToStep('Ziele und Bedürfnisse');

      await expect(
        page.getByRole('heading', { level: 2, name: 'Ziele und Bedürfnisse' }),
      ).toBeVisible();
      await expect(profilierung.step('Finanzielle Verhältnisse')).not.toHaveAttribute(
        'aria-current',
        'step',
      );
      await expect(page.getByText('Schritt 4 von 6')).toBeVisible();
      await snap('profilierung-goals');
    });

    test('should move to the next section with «Weiter» and focus its heading', async ({
      profilierung,
      page,
    }) => {
      await page.getByRole('button', { name: /^Weiter: Fremdwährungsflüsse/ }).click();

      const heading = page.getByRole('heading', { level: 2, name: /^Fremdwährungsflüsse/ });
      await expect(heading).toBeVisible();
      await expect(heading).toBeFocused();
      await expect(profilierung.step('Fremdwährungsflüsse')).toHaveAttribute(
        'aria-current',
        'step',
      );
    });

    test('should show the knowledge section as a matrix with a radio per level', async ({
      profilierung,
      page,
      snap,
    }) => {
      await profilierung.goToStep('Kenntnisse und Erfahrungen');

      const matrix = page.getByRole('table', {
        name: 'Kenntnisse und Erfahrungen je Produktklasse',
      });
      await expect(matrix).toBeVisible();
      await expect(
        matrix.getByRole('radio', { name: 'Devisentermingeschäft: Kenntnisse und Erfahrung' }),
      ).toBeChecked();
      await snap('profilierung-knowledge');
    });

    test('should show the rule result next to the segment questions', async ({
      profilierung,
      page,
    }) => {
      await profilierung.goToStep('Kundensegmentierung');

      await expect(page.getByText(MUSTER.segment, { exact: true })).toBeVisible();
      await expect(
        page.getByText(/keine professionelle Tresorerie · Art\. 4 und 5 FIDLEG/),
      ).toBeVisible();
    });
  });
});
