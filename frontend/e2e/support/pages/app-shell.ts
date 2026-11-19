import { type Locator, type Page, expect } from '@playwright/test';

export type UiLanguage = 'de' | 'en';

/** Header (language switch, «Demo-Daten»), main scroll container and backend error of the app shell. */
export class AppShell {
  readonly main: Locator;
  readonly heading: Locator;
  readonly mockToggle: Locator;
  readonly backendError: Locator;
  readonly switchToMock: Locator;
  readonly breadcrumb: Locator;

  constructor(readonly page: Page) {
    this.main = page.getByTestId('app-main');
    this.heading = page.getByRole('heading', { level: 1 });
    this.mockToggle = page.getByTestId('mock-toggle');
    this.backendError = page.getByTestId('backend-error');
    this.switchToMock = page.getByTestId('switch-to-mock');
    this.breadcrumb = page.getByRole('navigation', { name: /Brotkrumen|Breadcrumb/ });
  }

  languageButton(language: UiLanguage): Locator {
    return this.page.getByTestId(`lang-${language}`);
  }

  /** Opens a route with a full page load. Note: a reload resets the in-memory mock backend. */
  async open(path: string): Promise<void> {
    await this.page.goto(path);
    await expect(this.heading).toBeVisible();
  }

  async switchLanguage(language: UiLanguage): Promise<void> {
    await this.languageButton(language).click();
    await expect(this.languageButton(language)).toHaveAttribute('aria-pressed', 'true');
  }

  /**
   * Switches «Demo-Daten» off with a reachable backend: the app reloads on «/» in HTTP mode.
   * Without a backend there is no reload; use {@link mockToggle} directly for that case.
   */
  async leaveDemoMode(): Promise<void> {
    const reloaded = this.page.waitForEvent('load', { timeout: 20_000 });
    await this.mockToggle.click();
    await reloaded;
    await expect(this.mockToggle).not.toBeChecked();
    await expect(this.heading).toBeVisible();
  }

  /** Starts the app in HTTP mode, as if «Demo-Daten» had been switched off in an earlier session. */
  async startInBackendMode(path = '/'): Promise<void> {
    await this.page.addInitScript(() => {
      if (!sessionStorage.getItem('e2e.modeSet')) {
        localStorage.setItem('treasury-kompass.useMock', 'false');
        sessionStorage.setItem('e2e.modeSet', 'true');
      }
    });
    await this.page.goto(path);
  }

  /** Height of the document beyond the viewport; 0 means the document itself cannot scroll. */
  async documentOverflow(): Promise<number> {
    return this.page.evaluate(() => {
      const scroller = document.scrollingElement ?? document.documentElement;
      return scroller.scrollHeight - window.innerHeight;
    });
  }

  async mainWidth(): Promise<number> {
    const box = await this.main.boundingBox();
    if (!box) throw new Error('app-main is not rendered');
    return box.width;
  }
}
