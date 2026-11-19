import { join } from 'node:path';
import type { Page, TestInfo } from '@playwright/test';

export const SCREENSHOT_DIR = join(__dirname, '..', 'screenshots');

export interface SnapOptions {
  /**
   * `full` (default) lifts the height limits of the main scroll container and its ancestors for the
   * capture, so the whole page content is on the image although the document itself does not scroll.
   * `viewport` captures exactly what the user sees.
   */
  readonly mode?: 'full' | 'viewport';
}

const MARKER = 'data-e2e-snap-style';

/**
 * Saves screenshots/<project>/<name>.png for the main session to look at. Not a visual regression
 * check: nothing is compared, the images only document the screens and key states.
 */
export async function snap(
  page: Page,
  testInfo: TestInfo,
  name: string,
  options: SnapOptions = {},
): Promise<void> {
  const path = join(SCREENSHOT_DIR, testInfo.project.name, `${name}.png`);
  const full = (options.mode ?? 'full') === 'full';
  if (full) await page.evaluate(unclamp, MARKER);
  try {
    await page.screenshot({ path, fullPage: full, animations: 'disabled', caret: 'hide' });
  } finally {
    if (full) await page.evaluate(restore, MARKER);
  }
}

/** Runs in the browser: lets app-main and its ancestors grow to their content height. */
function unclamp(marker: string): void {
  const main =
    document.querySelector<HTMLElement>('[data-testid="app-main"]') ??
    document.querySelector('main');
  for (let el: HTMLElement | null = main; el; el = el.parentElement) {
    el.setAttribute(marker, el.getAttribute('style') ?? '');
    el.style.setProperty('height', 'auto', 'important');
    el.style.setProperty('max-height', 'none', 'important');
    el.style.setProperty('overflow', 'visible', 'important');
  }
}

/** Runs in the browser: puts the original inline styles back. */
function restore(marker: string): void {
  for (const el of Array.from(document.querySelectorAll<HTMLElement>(`[${marker}]`))) {
    const original = el.getAttribute(marker) ?? '';
    if (original) el.setAttribute('style', original);
    else el.removeAttribute('style');
    el.removeAttribute(marker);
  }
}
