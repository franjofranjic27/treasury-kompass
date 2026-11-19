import { DEMO_CLIENTS } from '../support/demo-clients';
import { expect, test } from '../support/fixtures';

// Expected values: docs/regelwerk.md section 9 («Testfälle aus dem Prototyp»).
for (const client of DEMO_CLIENTS) {
  test.describe(`Dashboard ${client.name}`, () => {
    test.beforeEach(async ({ dashboard }) => {
      await dashboard.open(client.id);
    });

    test('should show the client name and master data', async ({ dashboard, page, snap }) => {
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(client.name);
      await expect(dashboard.masterData).toContainText(client.industry);
      await expect(dashboard.masterData).toContainText(client.employees);
      await expect(dashboard.masterData).toContainText('Nicht freigegeben');
      await snap(`dashboard-${client.id}`);
    });

    test('should show the key figures from the documents', async ({ dashboard }) => {
      await expect(dashboard.figureRow('Umsatzerlös')).toContainText(client.figures.revenue);
      await expect(dashboard.figureRow('Bilanzsumme')).toContainText(client.figures.totalAssets);
      await expect(dashboard.figureRow('Eigenkapital')).toContainText(client.figures.equity);
      await expect(dashboard.figureRow('Betriebsergebnis (EBIT)')).toContainText(
        client.figures.ebit,
      );
    });

    test('should show the net foreign currency positions', async ({ dashboard }) => {
      await expect(dashboard.fx).toContainText(client.netFx.EUR);
      await expect(dashboard.fx).toContainText(client.netFx.USD);
    });

    test('should derive the client segment with its legal basis', async ({ dashboard }) => {
      await expect(dashboard.segment).toContainText(client.segment);
      await expect(dashboard.segment).toContainText(client.legalBasis);
      await expect(dashboard.segment).toContainText(client.thresholds);
    });

    test('should derive the risk profile from capacity and willingness', async ({ dashboard }) => {
      await expect(dashboard.riskProfile).toContainText(client.riskProfile);
      await expect(
        dashboard.riskProfile.getByText('Risikofähigkeit', { exact: true }).locator('..'),
      ).toContainText(client.capacity);
      await expect(
        dashboard.riskProfile.getByText('Risikobereitschaft', { exact: true }).locator('..'),
      ).toContainText(client.willingness);
      await expect(dashboard.riskProfile).toContainText('Art. 12 FIDLEG');
    });

    test('should show the open profiling work', async ({ dashboard }) => {
      await expect(dashboard.status).toContainText(`${client.prefilledToConfirm} zu bestätigen`);
      await expect(dashboard.status).toContainText(`0 von ${client.checklistItems}`);
    });

    test('should place client info on the left and the risk profile on the right', async ({
      dashboard,
    }) => {
      const { info, profile } = await dashboard.columnBoxes();

      expect(profile.x, 'risk profile starts right of the client info').toBeGreaterThanOrEqual(
        info.x + info.width,
      );
      expect(Math.abs(profile.y - info.y), 'both columns start at the same height').toBeLessThan(2);
    });

    test('should place the columns side by side with the chat collapsed', async ({
      dashboard,
      chat,
    }) => {
      await chat.collapse();

      await expect
        .poll(async () => {
          const { info, profile } = await dashboard.columnBoxes();
          return profile.x >= info.x + info.width && Math.abs(profile.y - info.y) < 2;
        })
        .toBe(true);
    });
  });
}
