import AxeBuilder from '@axe-core/playwright';
import type { Page, TestInfo } from '@playwright/test';
import { CHAT_CASES, MUSTER } from '../support/demo-clients';
import { expect, test } from '../support/fixtures';

/*
 * Smoke check with axe-core (WCAG 2.2 A/AA rules). The run fails only on `critical` violations;
 * `serious` ones are reported as annotation and attachment so they show up in the report.
 */
type Violation = Awaited<ReturnType<AxeBuilder['analyze']>>['violations'][number];

function summary(violations: readonly Violation[]): string[] {
  return violations.map(
    (v) =>
      `${v.id}: ${v.help} (${v.nodes.length}×: ${v.nodes
        .map((n) => n.target.join(' '))
        .slice(0, 3)
        .join(', ')})`,
  );
}

async function audit(page: Page, testInfo: TestInfo, screen: string): Promise<Violation[]> {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'])
    .analyze();
  await testInfo.attach(`axe-${screen}.json`, {
    body: JSON.stringify(results.violations, null, 2),
    contentType: 'application/json',
  });
  const serious = results.violations.filter((v) => v.impact === 'serious');
  for (const line of summary(serious)) {
    testInfo.annotations.push({ type: 'axe serious', description: `${screen}: ${line}` });
    console.log(`[axe serious] ${testInfo.project.name} ${screen}: ${line}`);
  }
  return results.violations.filter((v) => v.impact === 'critical');
}

test.describe('Accessibility smoke', () => {
  test('start page has no critical violations', async ({ page, startPage }, testInfo) => {
    await startPage.open();
    await startPage.select(MUSTER.name);

    expect(summary(await audit(page, testInfo, 'start'))).toEqual([]);
  });

  test('Profilierung has no critical violations', async ({ page, profilierung }, testInfo) => {
    await profilierung.open(MUSTER.id);

    expect(summary(await audit(page, testInfo, 'profilierung'))).toEqual([]);
  });

  test('knowledge matrix has no critical violations', async ({ page, profilierung }, testInfo) => {
    await profilierung.open(MUSTER.id);
    await profilierung.goToStep('Kenntnisse und Erfahrungen');

    expect(summary(await audit(page, testInfo, 'profilierung-knowledge'))).toEqual([]);
  });

  test('dashboard has no critical violations', async ({ page, dashboard }, testInfo) => {
    await dashboard.open(MUSTER.id);

    expect(summary(await audit(page, testInfo, 'dashboard'))).toEqual([]);
  });

  test('Beratung has no critical violations', async ({ page, beratung }, testInfo) => {
    await beratung.open(MUSTER.id);
    await beratung.expand('USD');

    expect(summary(await audit(page, testInfo, 'beratung'))).toEqual([]);
  });

  test('chat with an answer has no critical violations', async ({
    page,
    dashboard,
    chat,
  }, testInfo) => {
    await dashboard.open(MUSTER.id);
    await chat.ask(CHAT_CASES.approval);

    expect(summary(await audit(page, testInfo, 'chat'))).toEqual([]);
  });

  test('English UI has no critical violations', async ({ page, shell, dashboard }, testInfo) => {
    await dashboard.open(MUSTER.id);
    await shell.switchLanguage('en');

    expect(summary(await audit(page, testInfo, 'dashboard-en'))).toEqual([]);
  });
});
