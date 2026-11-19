import { MUSTER, RAW_TRANSLATION_KEY } from '../support/demo-clients';
import { expect, test } from '../support/fixtures';

test.describe('Language switch', () => {
  test('should start in German with de-CH on <html lang>', async ({ page, shell, startPage }) => {
    await startPage.open();

    await expect(page.locator('html')).toHaveAttribute('lang', 'de-CH');
    await expect(shell.languageButton('de')).toHaveAttribute('aria-pressed', 'true');
    await expect(shell.languageButton('en')).toHaveAttribute('aria-pressed', 'false');
    await expect(shell.heading).toHaveText('Kunden');
  });

  test('should switch visible labels to English', async ({ page, shell, startPage, snap }) => {
    await startPage.open();

    await shell.switchLanguage('en');

    await expect(page.locator('html')).toHaveAttribute('lang', 'en');
    await expect(shell.languageButton('de')).toHaveAttribute('aria-pressed', 'false');
    await expect(shell.heading).toHaveText('Clients');
    await expect(page.getByRole('searchbox', { name: 'Search by name' })).toBeVisible();
    await expect(page.getByTestId('mock-toggle')).toBeVisible();
    await expect(page.getByText('Demo data')).toBeVisible();
    await expect(startPage.row(MUSTER.name)).toContainText(MUSTER.segmentEn);
    await snap('i18n-start-en');
  });

  test('should format amounts with de-CH apostrophes and English commas', async ({
    dashboard,
    shell,
    snap,
  }) => {
    await dashboard.open(MUSTER.id);
    await expect(dashboard.liquidity).toContainText(MUSTER.cash.de);
    await expect(dashboard.fx).toContainText('EUR +14.2 Mio.');

    await shell.switchLanguage('en');

    await expect(shell.page.getByRole('region', { name: 'Liquidity and financing' })).toContainText(
      MUSTER.cash.en,
    );
    await expect(
      shell.page.getByRole('region', { name: 'Foreign currencies, next 12 months' }),
    ).toContainText('EUR +14.2m');
    await snap('i18n-dashboard-en');
  });

  test('should translate the mock backend content of the dashboard', async ({
    dashboard,
    shell,
    page,
  }) => {
    await dashboard.open(MUSTER.id);

    await shell.switchLanguage('en');

    const segment = page.getByRole('region', { name: 'Client segment', exact: true });
    await expect(segment).toContainText(MUSTER.segmentEn);
    await expect(segment).toContainText('Legal basis: Art. 4 and 5 FinSA (FIDLEG)');
    await expect(page.getByRole('heading', { level: 2, name: 'Client information' })).toBeVisible();
  });

  test('should keep English after a reload', async ({ page, shell, startPage }) => {
    await startPage.open();
    await shell.switchLanguage('en');

    await page.reload();

    await expect(shell.heading).toHaveText('Clients');
    await expect(page.locator('html')).toHaveAttribute('lang', 'en');
    await expect(shell.languageButton('en')).toHaveAttribute('aria-pressed', 'true');
  });

  test('should switch back to German', async ({ page, shell, startPage }) => {
    await startPage.open();
    await shell.switchLanguage('en');
    await expect(shell.heading).toHaveText('Clients');

    await shell.switchLanguage('de');

    await expect(shell.heading).toHaveText('Kunden');
    await expect(page.locator('html')).toHaveAttribute('lang', 'de-CH');
  });

  test('should translate the page title', async ({ page, shell, dashboard }) => {
    await dashboard.open(MUSTER.id);
    await expect(page).toHaveTitle(/Dashboard/);

    await shell.switchLanguage('en');
    await shell.page
      .getByRole('navigation', { name: 'Client case' })
      .getByRole('link', { name: 'Advisory' })
      .click();

    await expect(page).toHaveTitle(/Advisory/);
  });

  test.describe('untranslated keys', () => {
    const screens = [
      { name: 'start page', path: '/' },
      { name: 'Profilierung', path: `/clients/${MUSTER.id}/profilierung` },
      { name: 'dashboard', path: `/clients/${MUSTER.id}` },
      { name: 'Beratung', path: `/clients/${MUSTER.id}/beratung` },
    ];
    for (const screen of screens) {
      test(`should show no raw translation key on the ${screen.name} in German or English`, async ({
        page,
        shell,
      }) => {
        await shell.open(screen.path);
        await expect(page.locator('body')).not.toContainText(RAW_TRANSLATION_KEY);

        await shell.switchLanguage('en');

        await expect(shell.heading).not.toHaveText(/^$/);
        await expect(page.locator('body')).not.toContainText(RAW_TRANSLATION_KEY);
      });
    }
  });
});
