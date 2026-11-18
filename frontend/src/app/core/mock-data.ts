import { AnswerStatus, Assessment, ChecklistItem } from './models';

/**
 * Demo data of the two fictional clients, copied from reference/prototype/treasury-kompass.html.
 * Rule results (segment, profile, hedges, liquidity) are the expected values from docs/specs/use-cases.md,
 * because the mock does not run the rule engine.
 */
export interface SeedAnswer {
  readonly value: string;
  readonly status: AnswerStatus;
  readonly source: string | null;
}

export interface ClientSeed {
  readonly id: string;
  readonly name: string;
  readonly shortLabel: string;
  readonly industry: string;
  readonly employees: string;
  readonly segment: string;
  readonly riskProfile: string;
  readonly document: { readonly fileName: string; readonly pageCount: number; readonly label: string };
  readonly answers: Readonly<Record<string, SeedAnswer>>;
  readonly assessment: Omit<Assessment, 'checklist' | 'unconfirmedAnswers' | 'approvalStatus' | 'version'>;
  readonly checklist: readonly Omit<ChecklistItem, 'done'>[];
}

const doc = (value: string, source: string): SeedAnswer => ({ value, status: 'PROPOSED', source });
const set = (value: string): SeedAnswer => ({ value, status: 'CONFIRMED', source: 'Berater' });

const INFO_TEXT = 'Informationen zu Finanzdienstleister, Dienstleistung, Risiken und Kosten abgegeben';
const RISK_BROCHURE = { id: 'riskBrochure', text: 'Broschüre «Risiken im Handel mit Finanzinstrumenten» abgegeben', legalBasis: 'Art. 8 FIDLEG' };
const COST_DISCLOSURE = {
  id: 'costDisclosure',
  text: 'Kosten, Marge im Terminkurs und allfällige Entschädigungen Dritter offengelegt',
  legalBasis: 'Art. 26 FIDLEG'
};

export const MUSTER: ClientSeed = {
  id: 'muster-maschinenbau',
  name: 'Muster Maschinenbau AG',
  shortLabel: 'Export-KMU',
  industry: 'Maschinenbau',
  employees: '140 Mitarbeitende',
  segment: 'Privatkunde',
  riskProfile: 'Konservativ',
  document: { fileName: 'Jahresrechnung_2025_Muster_Maschinenbau.pdf', pageCount: 24, label: 'Jahresrechnung 2025' },
  answers: {
    'fin.revenue': doc('28.4', 'Erfolgsrechnung, S. 5'),
    'fin.totalAssets': doc('19.2', 'Bilanz, S. 4'),
    'fin.equity': doc('7.9', 'Bilanz, S. 4'),
    'fin.ebit': doc('2.3', 'Erfolgsrechnung, S. 5'),
    'fin.cash': doc('6.4', 'Bilanz, S. 4'),
    'fin.operatingExpenses': doc('26.1', 'Erfolgsrechnung, S. 5, Summe Betriebsaufwand'),
    'fin.floatingRateDebt': doc('6.0', 'Anhang Ziff. 5, S. 11'),
    'fx.eur.inflow': doc('18.7', 'Anhang Ziff. 3: Umsatz nach Märkten, auf 12 Monate hochgerechnet'),
    'fx.eur.outflow': doc('4.5', 'Anhang Ziff. 3: Umsatz nach Märkten, auf 12 Monate hochgerechnet'),
    'fx.usd.inflow': doc('0', 'Kreditorenliste 2025, vom Kunden ergänzt'),
    'fx.usd.outflow': doc('3.1', 'Kreditorenliste 2025, vom Kunden ergänzt'),
    'segment.treasuryOrganisation': set('CFO_OR_ACCOUNTING'),
    'goals.hedgeObjective': set('BUDGET_CERTAINTY'),
    'goals.policyMaxHedgeRatio': set('80'),
    'goals.minimumLiquidity': doc('3.3', 'Vorschlag aus dem Betriebsaufwand (Erfolgsrechnung, S. 5)'),
    'goals.investmentHorizon': set('UNDER_1_YEAR'),
    'risk.tolerableDrawdown': set('UP_TO_2_PERCENT'),
    'risk.forgoneGainAttitude': set('TOLERABLE'),
    'knowledge.fxForward': doc('EXPERIENCE', 'Anhang Ziff. 7, offene Devisentermingeschäfte'),
    'knowledge.zeroCostCollar': set('NONE'),
    'knowledge.moneyMarketFund': set('KNOWLEDGE'),
    'knowledge.shortTermBond': set('EXPERIENCE')
  },
  assessment: {
    segment: {
      label: 'Privatkunde',
      reason: '1 von 3 Schwellenwerten überschritten, keine professionelle Tresorerie',
      legalBasis: 'Art. 4 und 5 FIDLEG',
      thresholdsExceeded: 1
    },
    capacity: {
      score: 3,
      factors: [
        { label: 'Eigenkapitalquote', value: '41.2 %', score: 4 },
        { label: 'Überschussliquidität', value: '1.4 Monate', score: 2 },
        { label: 'EBIT-Marge', value: '8.1 %', score: 3 },
        { label: 'Ungesichertes Währungsrisiko', value: '68.8 % des EBIT', score: 1 }
      ]
    },
    willingness: {
      score: 2,
      factors: [
        { label: 'Ziel der Absicherung', value: 'Budgetsicherheit', score: 1 },
        { label: 'Tragbarer Wertrückgang', value: 'Bis 2 %', score: 2 },
        { label: 'Entgangener Kursgewinn', value: 'Ärgerlich, aber tragbar', score: 3 }
      ]
    },
    profile: { score: 2, label: 'Konservativ' },
    exposures: [
      { currency: 'EUR', netPosition: 14.2, netPositionChf: 13.348, sensitivityPctEbit: 0.5803, riskLevel: 'HIGH', direction: 'Nettozufluss' },
      { currency: 'USD', netPosition: -3.1, netPositionChf: -2.48, sensitivityPctEbit: 0.1078, riskLevel: 'MEDIUM', direction: 'Nettoabfluss' }
    ],
    suitability: [
      { product: 'Devisentermingeschäft', knowledge: 'Kenntnisse und Erfahrung', suitability: 'SUITABLE', note: null, kidRequired: true },
      { product: 'Zero-Cost-Collar', knowledge: 'Keine', suitability: 'NOT_SUITABLE', note: 'Keine Kenntnisse, abraten (Art. 14 FIDLEG)', kidRequired: true },
      { product: 'Geldmarktfonds CHF', knowledge: 'Kenntnisse', suitability: 'SUITABLE_AFTER_DISCLOSURE', note: 'Aufklärung und Warnhinweis dokumentieren', kidRequired: true },
      { product: 'Obligationen CHF kurz', knowledge: 'Kenntnisse und Erfahrung', suitability: 'SUITABLE', note: null, kidRequired: false },
      { product: 'Festgeld / Callgeld', knowledge: '–', suitability: 'NOT_APPLICABLE', note: 'Kein Finanzinstrument', kidRequired: false }
    ],
    hedges: [
      {
        currency: 'EUR', action: 'Termingeschäft: EUR verkaufen', cap: 80, ratios: [80, 70, 60, 50], averageRatio: 65,
        hedgedAmountFx: 9.23, carryChf: -0.0987, limitedByPolicy: false, collarShare: null,
        scenarioUnhedgedChf: -1.3348, scenarioHedgedChf: -0.5659
      },
      {
        currency: 'USD', action: 'Termingeschäft: USD kaufen', cap: 80, ratios: [80, 70, 60, 50], averageRatio: 65,
        hedgedAmountFx: 2.015, carryChf: 0.034, limitedByPolicy: false, collarShare: null,
        scenarioUnhedgedChf: null, scenarioHedgedChf: null
      }
    ],
    liquidity: {
      excess: 3.1,
      parts: [
        { product: 'Festgeld / Callgeld', share: 45, amount: 1.395 },
        { product: 'Geldmarktfonds CHF', share: 40, amount: 1.24 },
        { product: 'Obligationen CHF kurz', share: 15, amount: 0.465 }
      ]
    }
  },
  checklist: [
    { id: 'info', text: INFO_TEXT, legalBasis: 'Art. 8 FIDLEG' },
    { id: 'kid.FX_FORWARD', text: 'Basisinformationsblatt ausgehändigt: Devisentermingeschäft', legalBasis: 'Art. 58 FIDLEG' },
    { id: 'kid.MONEY_MARKET_FUND', text: 'Basisinformationsblatt ausgehändigt: Geldmarktfonds CHF', legalBasis: 'Art. 58 FIDLEG' },
    RISK_BROCHURE,
    COST_DISCLOSURE,
    { id: 'warning.MONEY_MARKET_FUND', text: 'Aufklärung und Warnhinweis dokumentiert: Geldmarktfonds CHF', legalBasis: 'Art. 14 FIDLEG' }
  ]
};

export const COMPONENTS: ClientSeed = {
  id: 'beispiel-components',
  name: 'Beispiel Components AG',
  shortLabel: 'Mid-Cap',
  industry: 'Industriekomponenten',
  employees: '610 Mitarbeitende',
  segment: 'Professioneller Kunde',
  riskProfile: 'Ausgewogen',
  document: { fileName: 'Geschaeftsbericht_2025_Beispiel_Components.pdf', pageCount: 86, label: 'Geschäftsbericht 2025' },
  answers: {
    'fin.revenue': doc('146.0', 'Konzernerfolgsrechnung, S. 52'),
    'fin.totalAssets': doc('112.0', 'Konzernbilanz, S. 51'),
    'fin.equity': doc('46.0', 'Konzernbilanz, S. 51'),
    'fin.ebit': doc('12.8', 'Konzernerfolgsrechnung, S. 52'),
    'fin.cash': doc('28.0', 'Konzernbilanz, S. 51'),
    'fin.operatingExpenses': doc('133.2', 'Konzernerfolgsrechnung, S. 52'),
    'fin.floatingRateDebt': doc('25.0', 'Anhang Ziff. 14, S. 66'),
    'fx.eur.inflow': doc('85.4', 'Segmentbericht, S. 41: Umsatz und Einkauf nach Währungen'),
    'fx.eur.outflow': doc('33.0', 'Segmentbericht, S. 41: Umsatz und Einkauf nach Währungen'),
    'fx.usd.inflow': doc('33.0', 'Segmentbericht, S. 41'),
    'fx.usd.outflow': doc('12.0', 'Segmentbericht, S. 41'),
    'segment.treasuryOrganisation': doc('TREASURY_DEPARTMENT', 'Geschäftsbericht, S. 34: Group Treasury mit 3 Vollzeitstellen'),
    'goals.hedgeObjective': set('PROTECT_MARGINS'),
    'goals.policyMaxHedgeRatio': set('90'),
    'goals.minimumLiquidity': doc('16.7', 'Vorschlag aus dem Betriebsaufwand (Konzernerfolgsrechnung, S. 52)'),
    'goals.investmentHorizon': set('ONE_TO_THREE_YEARS'),
    'risk.tolerableDrawdown': set('UP_TO_5_PERCENT'),
    'risk.forgoneGainAttitude': set('TOLERABLE'),
    'knowledge.fxForward': doc('EXPERIENCE', 'Anhang Ziff. 9, Devisentermingeschäfte'),
    'knowledge.zeroCostCollar': doc('EXPERIENCE', 'Anhang Ziff. 9, Devisenoptionen'),
    'knowledge.moneyMarketFund': set('EXPERIENCE'),
    'knowledge.shortTermBond': set('EXPERIENCE')
  },
  assessment: {
    segment: {
      label: 'Professioneller Kunde',
      reason: 'grosses Unternehmen, 3 von 3 Schwellenwerten überschritten, professionelle Tresorerie',
      legalBasis: 'Art. 4 und 5 FIDLEG',
      thresholdsExceeded: 3
    },
    capacity: {
      score: 3,
      factors: [
        { label: 'Eigenkapitalquote', value: '41.1 %', score: 4 },
        { label: 'Überschussliquidität', value: '1.0 Monate', score: 2 },
        { label: 'EBIT-Marge', value: '8.8 %', score: 3 },
        { label: 'Ungesichertes Währungsrisiko', value: '51.6 % des EBIT', score: 1 }
      ]
    },
    willingness: {
      score: 3,
      factors: [
        { label: 'Ziel der Absicherung', value: 'Margen schützen', score: 3 },
        { label: 'Tragbarer Wertrückgang', value: 'Bis 5 %', score: 3 },
        { label: 'Entgangener Kursgewinn', value: 'Ärgerlich, aber tragbar', score: 3 }
      ]
    },
    profile: { score: 3, label: 'Ausgewogen' },
    exposures: [
      { currency: 'EUR', netPosition: 52.4, netPositionChf: 49.256, sensitivityPctEbit: 0.3848, riskLevel: 'HIGH', direction: 'Nettozufluss' },
      { currency: 'USD', netPosition: 21.0, netPositionChf: 16.8, sensitivityPctEbit: 0.1313, riskLevel: 'MEDIUM', direction: 'Nettozufluss' }
    ],
    suitability: [
      { product: 'Devisentermingeschäft', knowledge: 'Vermutet (professionell)', suitability: 'SUITABLE', note: null, kidRequired: false },
      { product: 'Zero-Cost-Collar', knowledge: 'Vermutet (professionell)', suitability: 'SUITABLE', note: null, kidRequired: false },
      { product: 'Geldmarktfonds CHF', knowledge: 'Vermutet (professionell)', suitability: 'SUITABLE', note: null, kidRequired: false },
      { product: 'Obligationen CHF kurz', knowledge: 'Vermutet (professionell)', suitability: 'SUITABLE', note: null, kidRequired: false },
      { product: 'Festgeld / Callgeld', knowledge: '–', suitability: 'NOT_APPLICABLE', note: 'Kein Finanzinstrument', kidRequired: false }
    ],
    hedges: [
      {
        currency: 'EUR', action: 'Termingeschäft: EUR verkaufen, 30 % als Zero-Cost-Collar', cap: 90, ratios: [70, 60, 50, 40], averageRatio: 55,
        hedgedAmountFx: 28.82, carryChf: -0.2125, limitedByPolicy: false, collarShare: 0.3,
        scenarioUnhedgedChf: -4.9256, scenarioHedgedChf: -2.6407
      },
      {
        currency: 'USD', action: 'Termingeschäft: USD verkaufen, 30 % als Zero-Cost-Collar', cap: 90, ratios: [70, 60, 50, 40], averageRatio: 55,
        hedgedAmountFx: 11.55, carryChf: -0.1343, limitedByPolicy: false, collarShare: 0.3,
        scenarioUnhedgedChf: null, scenarioHedgedChf: null
      }
    ],
    liquidity: {
      excess: 11.3,
      parts: [
        { product: 'Festgeld / Callgeld', share: 20, amount: 2.26 },
        { product: 'Geldmarktfonds CHF', share: 25, amount: 2.825 },
        { product: 'Obligationen CHF kurz', share: 55, amount: 6.215 }
      ]
    }
  },
  checklist: [
    { id: 'info', text: `${INFO_TEXT} (Verzicht möglich)`, legalBasis: 'Art. 8 FIDLEG' },
    RISK_BROCHURE,
    COST_DISCLOSURE
  ]
};

export const SEEDS: readonly ClientSeed[] = [MUSTER, COMPONENTS];
