import { type Locator, type Page, expect } from '@playwright/test';

/** FIDLEG questionnaire with stepper and progress («/clients/:id/profilierung»). */
export class ProfilierungPage {
  readonly progress: Locator;
  readonly questionsCompleted: Locator;
  readonly prefilledConfirmed: Locator;
  readonly steps: Locator;
  readonly currentStep: Locator;
  readonly sectionHeading: Locator;
  readonly confirmButtons: Locator;

  constructor(readonly page: Page) {
    this.progress = page.getByRole('region', { name: 'Fortschritt' });
    // «Fragen abgeschlossen  12 von 23»: the label's parent holds the counter.
    this.questionsCompleted = this.progress.getByText('Fragen abgeschlossen').locator('..');
    this.prefilledConfirmed = this.progress
      .getByText('Vorschläge aus Unterlagen bestätigt')
      .locator('..');
    this.steps = page
      .getByRole('navigation', { name: 'Abschnitte des Fragebogens' })
      .getByRole('button');
    this.currentStep = page
      .getByRole('navigation', { name: 'Abschnitte des Fragebogens' })
      .locator('[aria-current="step"]');
    this.sectionHeading = page.getByRole('heading', { level: 2 }).first();
    this.confirmButtons = page.getByRole('button', { name: /^Bestätigen: / });
  }

  async open(clientId: string): Promise<void> {
    await this.page.goto(`/clients/${clientId}/profilierung`);
    await expect(this.progress).toBeVisible();
  }

  step(title: string): Locator {
    return this.steps.filter({ hasText: title });
  }

  async goToStep(title: string): Promise<void> {
    await this.step(title).click();
    await expect(this.step(title)).toHaveAttribute('aria-current', 'step');
  }

  /** «Bestätigen» of one prefilled question; its accessible name is «Bestätigen: <question>». */
  confirmButton(question: string): Locator {
    return this.page.getByRole('button', { name: `Bestätigen: ${question}`, exact: true });
  }

  /** Proposal box next to the confirm button, holding tag «Vorschlag» and the Fundstelle. */
  proposalOf(question: string): Locator {
    return this.confirmButton(question).locator('..');
  }

  radio(question: string | RegExp, option: string): Locator {
    return this.page.getByRole('group', { name: question }).getByRole('radio', { name: option });
  }

  /** Clicks every «Bestätigen» in every section, so all prefilled answers are confirmed. */
  async confirmAllPrefilled(): Promise<void> {
    const sectionCount = await this.steps.count();
    for (let index = 0; index < sectionCount; index++) {
      await this.steps.nth(index).click();
      await expect(this.steps.nth(index)).toHaveAttribute('aria-current', 'step');
      await this.confirmAllInSection();
    }
  }

  private async confirmAllInSection(): Promise<void> {
    let remaining = await this.confirmButtons.count();
    while (remaining > 0) {
      await this.confirmButtons.first().click();
      remaining -= 1;
      await expect(this.confirmButtons).toHaveCount(remaining);
    }
  }
}
