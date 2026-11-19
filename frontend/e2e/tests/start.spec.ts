import { COMPONENTS, MUSTER, SAMPLE_PDF } from '../support/demo-clients';
import { expect, test } from '../support/fixtures';

test.describe('Start page', () => {
  test.beforeEach(async ({ startPage }) => {
    await startPage.open();
  });

  test.describe('client list', () => {
    test('should list both demo clients', async ({ startPage, snap }) => {
      await expect(startPage.rows).toHaveCount(2);
      await expect(startPage.row(MUSTER.name)).toBeVisible();
      await expect(startPage.row(COMPONENTS.name)).toBeVisible();
      await expect(startPage.count).toHaveText('2 Kunden');
      await snap('start');
    });

    test('should show segment and risk profile of each client in its row', async ({
      startPage,
    }) => {
      await expect(startPage.row(MUSTER.name)).toContainText(MUSTER.segment);
      await expect(startPage.row(MUSTER.name)).toContainText('Risikoprofil Konservativ');
      await expect(startPage.row(COMPONENTS.name)).toContainText(COMPONENTS.segment);
      await expect(startPage.row(COMPONENTS.name)).toContainText('Risikoprofil Ausgewogen');
    });

    test('should filter the list by a case-insensitive part of the name', async ({ startPage }) => {
      await startPage.search.fill('compo');

      await expect(startPage.rows).toHaveCount(1);
      await expect(startPage.row(COMPONENTS.name)).toBeVisible();
      await expect(startPage.count).toHaveText('1 Kunde');
    });

    test('should show an empty state when no client matches the search', async ({
      startPage,
      snap,
    }) => {
      await startPage.search.fill('Gibt es nicht AG');

      await expect(startPage.rows).toHaveCount(0);
      await expect(startPage.emptyState).toBeVisible();
      await snap('start-search-empty');
    });

    test('should show all clients again when the search is cleared', async ({ startPage }) => {
      await startPage.search.fill('muster');
      await expect(startPage.rows).toHaveCount(1);

      await startPage.search.clear();

      await expect(startPage.rows).toHaveCount(2);
    });
  });

  test.describe('documents', () => {
    test('should open the documents card of the selected client', async ({ startPage, snap }) => {
      await startPage.select(MUSTER.name);

      await expect(startPage.row(MUSTER.name)).toHaveAttribute('aria-pressed', 'true');
      await expect(startPage.documentsCard(MUSTER.name)).toContainText(MUSTER.document);
      await snap('start-client-selected');
    });

    test('should preselect the client given in the URL', async ({ page, startPage }) => {
      await page.goto(`/?client=${COMPONENTS.id}`);

      await expect(startPage.documentsCard(COMPONENTS.name)).toContainText(COMPONENTS.document);
    });

    // The mock backend advances one processing step every 1.2 s, so the whole flow takes about 3.6 s.
    test('should process an uploaded PDF up to «Kennzahlen extrahiert»', async ({
      startPage,
      snap,
    }) => {
      const fileName = 'Treasury-Policy_Muster_Maschinenbau.pdf';
      await startPage.select(MUSTER.name);

      await startPage.fileInput.setInputFiles(SAMPLE_PDF.policyMuster);

      await expect(startPage.stage(fileName, 'Hochgeladen')).toHaveAttribute('data-state', 'done');
      await expect(startPage.stage(fileName, 'Kennzahlen extrahiert')).not.toHaveAttribute(
        'data-state',
        'done',
      );
      await snap('start-upload-running', { mode: 'viewport' });
      await expect(startPage.stage(fileName, 'Text extrahiert')).toHaveAttribute(
        'data-state',
        'done',
      );
      await expect(startPage.stage(fileName, 'Indexiert')).toHaveAttribute('data-state', 'done');
      await expect(startPage.stage(fileName, 'Kennzahlen extrahiert')).toHaveAttribute(
        'data-state',
        'done',
      );
      await expect(startPage.row(MUSTER.name)).toContainText('2 Dokumente');
      await expect(startPage.error).toHaveCount(0);
      await snap('start-upload-done');
    });

    test('should reject a file that is not a PDF', async ({ startPage }) => {
      await startPage.select(MUSTER.name);

      await startPage.fileInput.setInputFiles({
        name: 'notizen.txt',
        mimeType: 'text/plain',
        buffer: Buffer.from('Keine PDF-Datei'),
      });

      await expect(startPage.error).toContainText('Nur PDF-Dateien werden unterstützt.');
      await expect(startPage.documentsCard(MUSTER.name)).not.toContainText('notizen.txt');
    });
  });

  test.describe('navigation', () => {
    test('should open the questionnaire from «Profilierung starten»', async ({
      page,
      startPage,
    }) => {
      await startPage.select(MUSTER.name);

      await startPage.link('Profilierung starten').click();

      await expect(page).toHaveURL(`/clients/${MUSTER.id}/profilierung`);
      await expect(page.getByRole('heading', { level: 1 })).toHaveText('FIDLEG-Kundenprofilierung');
    });

    test('should open the dashboard from «Zum Dashboard»', async ({ page, startPage }) => {
      await startPage.select(COMPONENTS.name);

      await startPage.link('Zum Dashboard').click();

      await expect(page).toHaveURL(`/clients/${COMPONENTS.id}`);
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(COMPONENTS.name);
    });

    test('should lead back to the client list through the breadcrumb', async ({
      page,
      shell,
      startPage,
    }) => {
      await startPage.select(MUSTER.name);
      await startPage.link('Zum Dashboard').click();
      await expect(page).toHaveURL(`/clients/${MUSTER.id}`);

      await shell.breadcrumb.getByRole('link', { name: 'Kunden' }).click();

      await expect(page).toHaveURL('/');
      await expect(startPage.rows).toHaveCount(2);
    });

    test('should move between the screens of a case with the case navigation', async ({
      page,
      startPage,
    }) => {
      await startPage.select(MUSTER.name);
      await startPage.link('Zum Dashboard').click();
      const caseNav = page.getByRole('navigation', { name: 'Kundenfall' });

      await caseNav.getByRole('link', { name: 'Beratung' }).click();
      await expect(page).toHaveURL(`/clients/${MUSTER.id}/beratung`);
      await expect(caseNav.getByRole('link', { name: 'Beratung' })).toHaveAttribute(
        'aria-current',
        'page',
      );

      await caseNav.getByRole('link', { name: 'Profilierung' }).click();
      await expect(page).toHaveURL(`/clients/${MUSTER.id}/profilierung`);

      await caseNav.getByRole('link', { name: 'Dashboard' }).click();
      await expect(page).toHaveURL(`/clients/${MUSTER.id}`);
    });

    test('should send unknown routes to the client list', async ({ page, startPage }) => {
      await page.goto('/gibt/es/nicht');

      await expect(page).toHaveURL('/');
      await expect(startPage.rows).toHaveCount(2);
    });
  });
});
