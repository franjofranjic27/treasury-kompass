import { type Locator, type Page, expect } from '@playwright/test';

/** Client list, search and document upload («/»). */
export class StartPage {
  readonly rows: Locator;
  readonly search: Locator;
  readonly count: Locator;
  readonly emptyState: Locator;
  readonly fileInput: Locator;
  readonly error: Locator;

  constructor(readonly page: Page) {
    this.rows = page.getByTestId('client-row');
    this.search = page.getByRole('searchbox');
    this.count = page.getByRole('status').filter({ hasText: /^\s*\d+ (Kunden?|clients?)\s*$/ });
    this.emptyState = page.getByText('Kein Kunde gefunden');
    this.fileInput = page.getByLabel('Datei wählen');
    this.error = page.getByRole('alert');
  }

  async open(): Promise<void> {
    await this.page.goto('/');
    await expect(this.rows.first()).toBeVisible();
  }

  row(clientName: string): Locator {
    return this.rows.filter({ hasText: clientName });
  }

  async select(clientName: string): Promise<void> {
    await this.row(clientName).click();
    await expect(this.documentsCard(clientName)).toBeVisible();
  }

  /** The «Unterlagen» card of the selected client, labelled by the client name. */
  documentsCard(clientName: string): Locator {
    return this.page.getByRole('region', { name: clientName, exact: true });
  }

  /** Processing steps of one uploaded document; each step carries data-state done|running|open. */
  stages(fileName: string): Locator {
    return this.page.getByRole('list', { name: `Verarbeitung ${fileName}` });
  }

  stage(fileName: string, label: string): Locator {
    return this.stages(fileName).getByRole('listitem').filter({ hasText: label });
  }

  link(name: string): Locator {
    return this.page.getByRole('link', { name, exact: true });
  }
}
