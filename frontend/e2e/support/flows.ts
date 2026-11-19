import { type Page, expect } from '@playwright/test';
import type { BeratungPage } from './pages/beratung-page';
import type { ProfilierungPage } from './pages/profilierung-page';

/**
 * The mock backend keeps its state only until the next full page load. Multi-screen flows therefore
 * move through the case with in-app links, never with page.goto.
 */
export async function goToCaseSection(
  page: Page,
  section: 'Profilierung' | 'Dashboard' | 'Beratung',
): Promise<void> {
  await page
    .getByRole('navigation', { name: 'Kundenfall' })
    .getByRole('link', { name: section })
    .click();
}

/**
 * Brings a demo case to the point where the advisor may approve: every prefilled answer confirmed
 * and every checklist item ticked. Ends on the Beratung screen.
 */
export async function prepareForApproval(
  page: Page,
  profilierung: ProfilierungPage,
  beratung: BeratungPage,
  clientId: string,
): Promise<void> {
  await profilierung.open(clientId);
  await profilierung.confirmAllPrefilled();
  await goToCaseSection(page, 'Beratung');
  await expect(beratung.hedgeCard('EUR')).toBeVisible();
  await beratung.tickAllChecklistItems();
}

export async function approve(
  page: Page,
  profilierung: ProfilierungPage,
  beratung: BeratungPage,
  clientId: string,
): Promise<void> {
  await prepareForApproval(page, profilierung, beratung, clientId);
  await beratung.approveButton.click();
  await expect(beratung.approvedBadge).toBeVisible();
}
