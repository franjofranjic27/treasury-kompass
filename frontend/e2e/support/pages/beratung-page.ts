import { type Locator, type Page, expect } from '@playwright/test';

export type Currency = 'EUR' | 'USD';

/** Hedge proposal, liquidity investment, checklist and approval («/clients/:id/beratung»). */
export class BeratungPage {
  readonly banner: Locator;
  readonly approveButton: Locator;
  readonly approvedBadge: Locator;
  readonly checklist: Locator;
  readonly checklistItems: Locator;

  constructor(readonly page: Page) {
    this.banner = page.getByRole('status').filter({ hasText: /freigegeben\./i });
    this.approveButton = page.getByRole('button', { name: 'Vorschlag freigeben', exact: true });
    this.approvedBadge = page.getByText('Vorschlag freigegeben', { exact: true });
    this.checklist = page.getByRole('group', { name: /^Checkliste/ });
    this.checklistItems = this.checklist.getByRole('checkbox');
  }

  async open(clientId: string): Promise<void> {
    await this.page.goto(`/clients/${clientId}/beratung`);
    await expect(this.hedgeCard('EUR')).toBeVisible();
  }

  /** Hedge card of one currency; its name starts with the action, e.g. «EUR auf Termin verkaufen». */
  hedgeCard(currency: Currency): Locator {
    return this.page.getByRole('region', { name: new RegExp(`^${currency} auf Termin`) });
  }

  /** Only the first card is open by default, the others have a disclosure button. */
  async expand(currency: Currency): Promise<void> {
    const toggle = this.hedgeCard(currency).getByRole('button', { expanded: false });
    if (await toggle.count()) await toggle.click();
    await expect(this.trancheTable(currency)).toBeVisible();
  }

  trancheTable(currency: Currency): Locator {
    return this.page.getByRole('table', { name: `Staffelung ${currency} in vier Tranchen` });
  }

  /** Ratio cells of the four tranches (3, 6, 9, 12 months), e.g. «80 %». */
  trancheRatios(currency: Currency): Locator {
    return this.trancheTable(currency).locator('tbody').getByRole('cell').filter({ hasText: '%' });
  }

  startRatio(currency: Currency): Locator {
    return this.hedgeCard(currency).getByRole('slider', { name: 'Quote nächste 3 Monate' });
  }

  stepDown(currency: Currency): Locator {
    return this.hedgeCard(currency).getByRole('combobox', { name: 'Abstufung pro Quartal' });
  }

  /** Tranche ratios as numbers, for invariant checks against the policy cap. */
  async ratiosOf(currency: Currency): Promise<number[]> {
    const texts = await this.trancheRatios(currency).allTextContents();
    return texts.map((text) => Number(text.replace(/[^\d.]/g, '')));
  }

  /** The policy cap is the slider maximum: the UI cannot offer a ratio above it. */
  async capOf(currency: Currency): Promise<number> {
    return Number(await this.startRatio(currency).getAttribute('max'));
  }

  async tickAllChecklistItems(): Promise<void> {
    const count = await this.checklistItems.count();
    for (let index = 0; index < count; index++) {
      await this.checklistItems.nth(index).check();
    }
  }
}
