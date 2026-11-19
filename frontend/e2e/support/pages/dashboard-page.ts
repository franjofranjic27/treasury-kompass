import { type Locator, type Page, expect } from '@playwright/test';

type Box = NonNullable<Awaited<ReturnType<Locator['boundingBox']>>>;

/** Client dashboard: client info on the left, FIDLEG risk profile on the right («/clients/:id»). */
export class DashboardPage {
  readonly clientInfoTitle: Locator;
  readonly riskProfileTitle: Locator;
  readonly masterData: Locator;
  readonly keyFigures: Locator;
  readonly fx: Locator;
  readonly liquidity: Locator;
  readonly segment: Locator;
  readonly riskProfile: Locator;
  readonly status: Locator;

  constructor(readonly page: Page) {
    this.clientInfoTitle = page.getByRole('heading', { level: 2, name: 'Kundeninformationen' });
    this.riskProfileTitle = page.getByRole('heading', {
      level: 2,
      name: 'FIDLEG-Kundenrisikoprofil',
    });
    this.masterData = page.getByRole('region', { name: 'Stammdaten', exact: true });
    this.keyFigures = page.getByRole('region', { name: 'Kennzahlen aus den Unterlagen' });
    this.fx = page.getByRole('region', { name: 'Fremdwährungen, nächste 12 Monate' });
    this.liquidity = page.getByRole('region', { name: 'Liquidität und Finanzierung' });
    this.segment = page.getByRole('region', { name: 'Kundensegment', exact: true });
    this.riskProfile = page.getByRole('region', { name: 'Risikoprofil', exact: true });
    this.status = page.getByRole('region', { name: 'Profilierungsstatus', exact: true });
  }

  async open(clientId: string): Promise<void> {
    await this.page.goto(`/clients/${clientId}`);
    await expect(this.segment).toBeVisible();
  }

  /** Column below a column heading, i.e. the heading's parent. */
  column(title: Locator): Locator {
    return title.locator('..');
  }

  /** Bounding boxes of the client info column (left) and the risk profile column (right). */
  async columnBoxes(): Promise<{ info: Box; profile: Box }> {
    const info = await this.column(this.clientInfoTitle).boundingBox();
    const profile = await this.column(this.riskProfileTitle).boundingBox();
    if (!info || !profile) throw new Error('dashboard columns are not rendered');
    return { info, profile };
  }

  figureRow(label: string): Locator {
    return this.keyFigures.getByRole('row').filter({
      has: this.page.getByRole('cell', { name: label, exact: true }),
    });
  }
}
