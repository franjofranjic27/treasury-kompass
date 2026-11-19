import { COMPONENTS, MUSTER } from '../support/demo-clients';
import { expect, test } from '../support/fixtures';
import { approve, goToCaseSection, prepareForApproval } from '../support/flows';

test.describe('Beratung', () => {
  test.describe('hedge proposal', () => {
    test('should show the EUR tranches of Muster Maschinenbau AG', async ({ beratung, snap }) => {
      await beratung.open(MUSTER.id);

      await expect(beratung.trancheRatios('EUR')).toHaveText(MUSTER.eurTranches);
      await expect(beratung.trancheTable('EUR')).toContainText(MUSTER.eurAverage);
      await snap(`beratung-${MUSTER.id}`);
    });

    test('should show the EUR tranches of Beispiel Components AG with collar', async ({
      beratung,
      snap,
    }) => {
      await beratung.open(COMPONENTS.id);

      await expect(beratung.trancheRatios('EUR')).toHaveText(COMPONENTS.eurTranches);
      await expect(beratung.trancheTable('EUR')).toContainText(COMPONENTS.eurAverage);
      await expect(beratung.hedgeCard('EUR')).toContainText('Anteil Zero-Cost-Collar');
      await snap(`beratung-${COMPONENTS.id}`);
    });

    test('should show the USD tranches after expanding the collapsed card', async ({
      beratung,
    }) => {
      await beratung.open(MUSTER.id);

      await beratung.expand('USD');

      await expect(beratung.trancheRatios('USD')).toHaveText(MUSTER.usdTranches);
    });

    // Rule: cap = min(100 %, treasury policy); a ratio above the net position would be speculation.
    test('should keep every tranche within the policy cap', async ({ beratung }) => {
      await beratung.open(MUSTER.id);
      await beratung.expand('USD');

      expect(await beratung.capOf('EUR')).toBe(MUSTER.cap);
      expect(Math.max(...(await beratung.ratiosOf('EUR')))).toBeLessThanOrEqual(MUSTER.cap);
      expect(Math.max(...(await beratung.ratiosOf('USD')))).toBeLessThanOrEqual(MUSTER.cap);
      await expect(beratung.startRatio('EUR')).toHaveAccessibleDescription(
        /Höchstens 80 % gemäss Treasury-Policy/,
      );
    });

    test('should not let the advisor raise the start ratio above the policy cap', async ({
      beratung,
      snap,
    }) => {
      await beratung.open(COMPONENTS.id);
      const slider = beratung.startRatio('EUR');

      await slider.focus();
      await slider.press('End');

      await expect(slider).toHaveValue(String(COMPONENTS.cap));
      await expect(beratung.trancheRatios('EUR')).toHaveText(['90 %', '80 %', '70 %', '60 %']);
      expect(Math.max(...(await beratung.ratiosOf('EUR')))).toBeLessThanOrEqual(COMPONENTS.cap);
      await snap('beratung-start-ratio-at-cap');
    });

    test('should recalculate the tranches when the step-down changes', async ({ beratung }) => {
      await beratung.open(MUSTER.id);

      await beratung.stepDown('EUR').selectOption({ label: 'Keine Abstufung' });

      await expect(beratung.trancheRatios('EUR')).toHaveText(['80 %', '80 %', '80 %', '80 %']);
    });
  });

  test.describe('approval', () => {
    test('should block the approval and name the reason while work is open', async ({
      beratung,
      snap,
    }) => {
      await beratung.open(MUSTER.id);

      await expect(beratung.approveButton).toBeDisabled();
      await expect(beratung.approveButton).toHaveAccessibleDescription(
        `${MUSTER.prefilledToConfirm} vorbefüllte Antworten im Fragebogen bestätigen · ` +
          `${MUSTER.checklistItems} Punkte der Checkliste erledigen`,
      );
      await expect(beratung.banner).toContainText('Entwurf, nicht freigegeben.');
      await snap('beratung-approval-blocked');
    });

    test('should still block the approval when only the checklist is done', async ({
      beratung,
    }) => {
      await beratung.open(MUSTER.id);

      await beratung.tickAllChecklistItems();

      await expect(beratung.approveButton).toBeDisabled();
      await expect(beratung.approveButton).toHaveAccessibleDescription(
        `${MUSTER.prefilledToConfirm} vorbefüllte Antworten im Fragebogen bestätigen`,
      );
    });

    test('should still block the approval when only the answers are confirmed', async ({
      page,
      profilierung,
      beratung,
    }) => {
      await profilierung.open(MUSTER.id);
      await profilierung.confirmAllPrefilled();

      await goToCaseSection(page, 'Beratung');

      await expect(beratung.approveButton).toBeDisabled();
      await expect(beratung.approveButton).toHaveAccessibleDescription(
        `${MUSTER.checklistItems} Punkte der Checkliste erledigen`,
      );
      await expect(page.getByText('Alle Antworten bestätigt')).toBeVisible();
    });

    test('should enable the approval once answers are confirmed and the checklist is done', async ({
      page,
      profilierung,
      beratung,
      snap,
    }) => {
      await prepareForApproval(page, profilierung, beratung, MUSTER.id);

      await expect(beratung.approveButton).toBeEnabled();
      await expect(beratung.approveButton).toHaveAccessibleDescription(
        'Alle Voraussetzungen erfüllt, der Vorschlag kann freigegeben werden.',
      );
      await snap('beratung-ready-to-approve');
    });

    test('should approve the proposal', async ({
      page,
      profilierung,
      beratung,
      dashboard,
      snap,
    }) => {
      await prepareForApproval(page, profilierung, beratung, COMPONENTS.id);

      await beratung.approveButton.click();

      await expect(beratung.approvedBadge).toBeVisible();
      await expect(beratung.banner).toContainText('Freigegeben.');
      await snap('beratung-approved');
      await goToCaseSection(page, 'Dashboard');
      await expect(dashboard.status).toContainText('Freigegeben');
    });

    test.describe('revocation', () => {
      test.beforeEach(async ({ page, profilierung, beratung }) => {
        await approve(page, profilierung, beratung, MUSTER.id);
      });

      test('should revoke the approval when a checklist item is unticked', async ({
        beratung,
        snap,
      }) => {
        await beratung.checklistItems.first().uncheck();

        await expect(beratung.approvedBadge).toBeHidden();
        await expect(beratung.approveButton).toBeDisabled();
        await expect(beratung.approveButton).toHaveAccessibleDescription(
          '1 Punkt der Checkliste erledigen',
        );
        await expect(beratung.banner).toContainText('Entwurf, nicht freigegeben.');
        await snap('beratung-revoked');
      });

      test('should revoke the approval when an answer is changed', async ({
        page,
        profilierung,
        beratung,
      }) => {
        await goToCaseSection(page, 'Profilierung');
        await profilierung.goToStep('Risikobereitschaft');
        const noDrawdown = profilierung.radio(
          /Wertrückgang der Liquiditätsanlage/,
          'Keiner, Kapitalerhalt',
        );
        await noDrawdown.check();
        await expect(noDrawdown).toBeChecked();

        await goToCaseSection(page, 'Beratung');

        await expect(beratung.approvedBadge).toBeHidden();
        await expect(beratung.banner).toContainText('Entwurf, nicht freigegeben.');
      });

      test('should revoke the approval when the hedge proposal is changed', async ({
        beratung,
      }) => {
        await beratung.stepDown('EUR').selectOption({ label: '5 Prozentpunkte' });

        await expect(beratung.trancheRatios('EUR')).toHaveText(['80 %', '75 %', '70 %', '65 %']);
        await expect(beratung.approvedBadge).toBeHidden();
        await expect(beratung.banner).toContainText('Entwurf, nicht freigegeben.');
      });
    });
  });
});
