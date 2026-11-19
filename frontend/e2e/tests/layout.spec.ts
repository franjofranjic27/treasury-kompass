import { MUSTER } from '../support/demo-clients';
import { expect, test } from '../support/fixtures';

/*
 * The shell is a fixed frame: header on top, chat docked right, and only the main area scrolls.
 * Both mock projects run these tests, at 1440×900 and 1280×720.
 */
test.describe('Layout', () => {
  test('should not scroll the document on the start page', async ({ shell, startPage, snap }) => {
    await startPage.open();

    expect(await shell.documentOverflow()).toBeLessThanOrEqual(0);
    await snap('layout-start-viewport', { mode: 'viewport' });
  });

  test('should not scroll the document when a client with documents is selected', async ({
    shell,
    startPage,
  }) => {
    await startPage.open();

    await startPage.select(MUSTER.name);

    expect(await shell.documentOverflow()).toBeLessThanOrEqual(0);
  });

  test('should scroll long content inside the main area', async ({
    page,
    shell,
    beratung,
    chat,
    snap,
  }) => {
    await beratung.open(MUSTER.id);
    const main = await shell.main.evaluate((el) => ({
      scrollHeight: el.scrollHeight,
      clientHeight: el.clientHeight,
    }));
    expect(main.scrollHeight, 'Beratung is longer than the viewport').toBeGreaterThan(
      main.clientHeight,
    );

    await shell.main.hover();
    await page.mouse.wheel(0, 1500);

    await expect.poll(() => shell.main.evaluate((el) => el.scrollTop)).toBeGreaterThan(0);
    expect(await page.evaluate(() => window.scrollY)).toBe(0);
    expect(await shell.documentOverflow()).toBeLessThanOrEqual(0);
    await expect(chat.input).toBeInViewport();
    await snap('layout-beratung-scrolled', { mode: 'viewport' });
  });

  test('should keep header and chat in place while the main area scrolls', async ({
    page,
    shell,
    beratung,
    chat,
  }) => {
    await beratung.open(MUSTER.id);
    const headerBefore = await page.getByRole('banner').boundingBox();
    const chatBefore = await chat.panel.boundingBox();

    await shell.main.evaluate((el) => el.scrollTo({ top: el.scrollHeight }));

    await expect.poll(() => shell.main.evaluate((el) => el.scrollTop)).toBeGreaterThan(0);
    expect(await page.getByRole('banner').boundingBox()).toEqual(headerBefore);
    expect(await chat.panel.boundingBox()).toEqual(chatBefore);
  });

  test('should keep the chat input visible next to the content', async ({ dashboard, chat }) => {
    await dashboard.open(MUSTER.id);

    await expect(chat.panel).toBeInViewport();
    await expect(chat.input).toBeInViewport();
  });

  // Wide tables sit in a horizontally scrollable wrapper; on the dashboard that clips badges and sources.
  test('should fit the dashboard tables without horizontal scrolling', async ({
    shell,
    dashboard,
    snap,
  }) => {
    await dashboard.open(MUSTER.id);

    const overflowing = await shell.main.evaluate((main) =>
      Array.from(main.querySelectorAll('table'))
        .map((table) => table.parentElement as HTMLElement)
        .filter((wrapper) => wrapper.scrollWidth > wrapper.clientWidth + 1)
        .map((wrapper) => wrapper.closest('section')?.querySelector('h3')?.textContent?.trim()),
    );

    await snap('layout-dashboard-tables');
    expect(overflowing, 'tables that need horizontal scrolling').toEqual([]);
  });
});
