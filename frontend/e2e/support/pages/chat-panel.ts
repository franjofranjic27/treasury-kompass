import { type Locator, type Page, expect } from '@playwright/test';

/** Copilot chat docked on the right of every screen. */
export class ChatPanel {
  readonly panel: Locator;
  readonly toggle: Locator;
  readonly input: Locator;
  readonly sendButton: Locator;
  readonly messages: Locator;
  readonly userMessages: Locator;
  readonly assistantMessages: Locator;
  readonly log: Locator;

  constructor(readonly page: Page) {
    this.panel = page.getByTestId('chat-panel');
    this.toggle = page.getByTestId('chat-toggle');
    this.input = page.getByTestId('chat-input');
    this.sendButton = page.getByTestId('chat-send');
    this.messages = page.getByTestId('chat-message');
    this.userMessages = this.messages.and(page.locator('[data-role="user"]'));
    this.assistantMessages = this.messages.and(page.locator('[data-role="assistant"]'));
    this.log = page.getByRole('log');
  }

  get lastAnswer(): Locator {
    return this.assistantMessages.last();
  }

  get lastAnswerBody(): Locator {
    return this.lastAnswer.getByTestId('chat-message-body');
  }

  /** Sends a question and waits until the streamed answer is complete. */
  async ask(question: string): Promise<void> {
    const answersBefore = await this.assistantMessages.count();
    await this.input.fill(question);
    await this.sendButton.click();
    await expect(this.assistantMessages).toHaveCount(answersBefore + 1);
    await this.waitUntilIdle();
  }

  async waitUntilIdle(timeout = 15_000): Promise<void> {
    await expect(this.log).toHaveAttribute('aria-busy', 'false', { timeout });
  }

  async collapse(): Promise<void> {
    await this.toggle.click();
    await expect(this.toggle).toHaveAttribute('aria-expanded', 'false');
  }

  async expand(): Promise<void> {
    await this.toggle.click();
    await expect(this.toggle).toHaveAttribute('aria-expanded', 'true');
  }
}
