import { Localizable, Localized, l, law } from './i18n/localized';
import { AnswerOrigin, AnswerStatus, Assessment, ClientSegment, RiskProfileCode } from './models';

/**
 * Demo data of the two fictional clients, copied from reference/prototype/treasury-kompass.html.
 * Rule results (segment, profile, hedges, liquidity) are the expected values from docs/specs/use-cases.md,
 * because the mock does not run the rule engine. Texts exist in German and English, like a backend
 * that honours the requested language.
 */
export interface SeedAnswer {
  readonly value: string;
  readonly status: AnswerStatus;
  readonly origin: AnswerOrigin;
  readonly source: Localized | null;
}

export type AssessmentSeed = Omit<
  Assessment,
  'checklist' | 'unconfirmedAnswers' | 'approvalStatus' | 'version'
>;

export interface ClientSeed {
  readonly id: string;
  readonly name: string;
  readonly shortLabel: Localized;
  readonly industry: Localized;
  readonly employees: number;
  readonly segment: ClientSegment;
  readonly riskProfile: RiskProfileCode;
  readonly document: {
    readonly fileName: string;
    readonly pageCount: number;
    readonly label: Localized;
    readonly balanceSheetPage: number;
  };
  readonly answers: Readonly<Record<string, SeedAnswer>>;
  readonly assessment: Localizable<AssessmentSeed>;
  readonly checklist: readonly ChecklistSeed[];
}

export interface ChecklistSeed {
  readonly id: string;
  readonly text: Localized;
  readonly legalBasis: Localized;
}

const doc = (value: string, source: Localized): SeedAnswer => ({
  value,
  status: 'PROPOSED',
  origin: 'DOCUMENT',
  source,
});
const rule = (value: string, source: Localized): SeedAnswer => ({
  value,
  status: 'PROPOSED',
  origin: 'RULE',
  source,
});
const set = (value: string): SeedAnswer => ({
  value,
  status: 'CONFIRMED',
  origin: 'ADVISOR',
  source: null,
});

const INFO_TEXT = l(
  'Informationen zu Finanzdienstleister, Dienstleistung, Risiken und Kosten abgegeben',
  'Information on the financial service provider, the service, risks and costs provided',
);
const RISK_BROCHURE: ChecklistSeed = {
  id: 'riskBrochure',
  text: l(
    'Broschüre «Risiken im Handel mit Finanzinstrumenten» abgegeben',
    'Brochure “Risks Involved in Trading Financial Instruments” provided',
  ),
  legalBasis: law('Art. 8'),
};
const COST_DISCLOSURE: ChecklistSeed = {
  id: 'costDisclosure',
  text: l(
    'Kosten, Marge im Terminkurs und allfällige Entschädigungen Dritter offengelegt',
    'Costs, margin in the forward rate and any third-party compensation disclosed',
  ),
  legalBasis: law('Art. 26'),
};
const NO_FINANCIAL_INSTRUMENT = l('Kein Finanzinstrument', 'Not a financial instrument');
const TOLERABLE = l('Ärgerlich, aber tragbar', 'Annoying but tolerable');

const MUSTER_INCOME = l('Erfolgsrechnung, S. 5', 'Income statement, p. 5');
const MUSTER_BALANCE = l('Bilanz, S. 4', 'Balance sheet, p. 4');
const MUSTER_MARKETS = l(
  'Anhang Ziff. 3: Umsatz nach Märkten, auf 12 Monate hochgerechnet',
  'Notes, item 3: revenue by market, projected to 12 months',
);
const MUSTER_PAYABLES = l(
  'Kreditorenliste 2025, vom Kunden ergänzt',
  'Accounts payable list 2025, supplied by the client',
);

export const MUSTER: ClientSeed = {
  id: 'muster-maschinenbau',
  name: 'Muster Maschinenbau AG',
  shortLabel: l('Export-KMU', 'Exporting SME'),
  industry: l('Maschinenbau', 'Mechanical engineering'),
  employees: 140,
  segment: 'RETAIL',
  riskProfile: 'CONSERVATIVE',
  document: {
    fileName: 'Jahresrechnung_2025_Muster_Maschinenbau.pdf',
    pageCount: 24,
    label: l('Jahresrechnung 2025', 'Annual financial statements 2025'),
    balanceSheetPage: 4,
  },
  answers: {
    'fin.revenue': doc('28.4', MUSTER_INCOME),
    'fin.totalAssets': doc('19.2', MUSTER_BALANCE),
    'fin.equity': doc('7.9', MUSTER_BALANCE),
    'fin.ebit': doc('2.3', MUSTER_INCOME),
    'fin.cash': doc('6.4', MUSTER_BALANCE),
    'fin.operatingExpenses': doc(
      '26.1',
      l(
        'Erfolgsrechnung, S. 5, Summe Betriebsaufwand',
        'Income statement, p. 5, total operating expenses',
      ),
    ),
    'fin.floatingRateDebt': doc('6.0', l('Anhang Ziff. 5, S. 11', 'Notes, item 5, p. 11')),
    'fx.eur.inflow': doc('18.7', MUSTER_MARKETS),
    'fx.eur.outflow': doc('4.5', MUSTER_MARKETS),
    'fx.usd.inflow': doc('0', MUSTER_PAYABLES),
    'fx.usd.outflow': doc('3.1', MUSTER_PAYABLES),
    'segment.treasuryOrganisation': set('CFO_OR_ACCOUNTING'),
    'goals.hedgeObjective': set('BUDGET_CERTAINTY'),
    'goals.policyMaxHedgeRatio': set('80'),
    'goals.minimumLiquidity': rule(
      '3.3',
      l(
        'Vorschlag aus dem Betriebsaufwand (Erfolgsrechnung, S. 5)',
        'Proposed from operating expenses (income statement, p. 5)',
      ),
    ),
    'goals.investmentHorizon': set('UNDER_1_YEAR'),
    'risk.tolerableDrawdown': set('UP_TO_2_PERCENT'),
    'risk.forgoneGainAttitude': set('TOLERABLE'),
    'knowledge.fxForward': doc(
      'EXPERIENCE',
      l('Anhang Ziff. 7, offene Devisentermingeschäfte', 'Notes, item 7, open FX forwards'),
    ),
    'knowledge.zeroCostCollar': set('NONE'),
    'knowledge.moneyMarketFund': set('KNOWLEDGE'),
    'knowledge.shortTermBond': set('EXPERIENCE'),
  },
  assessment: {
    segment: {
      code: 'RETAIL',
      reason: l(
        '1 von 3 Schwellenwerten überschritten, keine professionelle Tresorerie',
        '1 of 3 thresholds exceeded, no professional treasury',
      ),
      legalBasis: law('Art. 4 und 5', 'Art. 4 and 5'),
      thresholdsExceeded: 1,
    },
    capacity: {
      score: 3,
      factors: [
        { label: l('Eigenkapitalquote', 'Equity ratio'), value: l('41.2 %', '41.2%'), score: 4 },
        {
          label: l('Überschussliquidität', 'Excess liquidity'),
          value: l('1.4 Monate', '1.4 months'),
          score: 2,
        },
        { label: l('EBIT-Marge', 'EBIT margin'), value: l('8.1 %', '8.1%'), score: 3 },
        {
          label: l('Ungesichertes Währungsrisiko', 'Unhedged currency risk'),
          value: l('68.8 % des EBIT', '68.8% of EBIT'),
          score: 1,
        },
      ],
    },
    willingness: {
      score: 2,
      factors: [
        {
          label: l('Ziel der Absicherung', 'Hedging objective'),
          value: l('Budgetsicherheit', 'Budget certainty'),
          score: 1,
        },
        {
          label: l('Tragbarer Wertrückgang', 'Tolerable drawdown'),
          value: l('Bis 2 %', 'Up to 2%'),
          score: 2,
        },
        { label: l('Entgangener Kursgewinn', 'Forgone gain'), value: TOLERABLE, score: 3 },
      ],
    },
    profile: { score: 2, code: 'CONSERVATIVE' },
    exposures: [
      {
        currency: 'EUR',
        netPosition: 14.2,
        netPositionChf: 13.348,
        sensitivityChf: 1.3348,
        sensitivityPctEbit: 0.5803,
        riskLevel: 'HIGH',
        direction: 'NET_INFLOW',
      },
      {
        currency: 'USD',
        netPosition: -3.1,
        netPositionChf: -2.48,
        sensitivityChf: 0.248,
        sensitivityPctEbit: 0.1078,
        riskLevel: 'MEDIUM',
        direction: 'NET_OUTFLOW',
      },
    ],
    suitability: [
      {
        product: 'FX_FORWARD',
        knowledge: 'EXPERIENCE',
        knowledgePresumed: false,
        suitability: 'SUITABLE',
        note: null,
        kidRequired: true,
      },
      {
        product: 'ZERO_COST_COLLAR',
        knowledge: 'NONE',
        knowledgePresumed: false,
        suitability: 'NOT_SUITABLE',
        note: l(
          'Keine Kenntnisse, abraten (Art. 14 FIDLEG)',
          'No knowledge, advise against, Art. 14 FinSA (FIDLEG)',
        ),
        kidRequired: true,
      },
      {
        product: 'MONEY_MARKET_FUND',
        knowledge: 'KNOWLEDGE',
        knowledgePresumed: false,
        suitability: 'SUITABLE_AFTER_DISCLOSURE',
        note: l('Aufklärung und Warnhinweis dokumentieren', 'Document disclosure and warning'),
        kidRequired: true,
      },
      {
        product: 'SHORT_TERM_BOND',
        knowledge: 'EXPERIENCE',
        knowledgePresumed: false,
        suitability: 'SUITABLE',
        note: null,
        kidRequired: false,
      },
      {
        product: 'DEPOSIT',
        knowledge: null,
        knowledgePresumed: false,
        suitability: 'NOT_APPLICABLE',
        note: NO_FINANCIAL_INSTRUMENT,
        kidRequired: false,
      },
    ],
    hedges: [
      {
        currency: 'EUR',
        action: l('EUR auf Termin verkaufen', 'Sell EUR forward'),
        cap: 80,
        startRatio: 80,
        stepDown: 10,
        ratios: [80, 70, 60, 50],
        averageRatio: 65,
        hedgedAmountFx: 9.23,
        hedgedAmountChf: 8.6762,
        carryChf: -0.0987,
        limitedByPolicy: false,
        collarShare: null,
        scenarioUnhedgedChf: -1.3348,
        scenarioHedgedChf: -0.5659,
      },
      {
        currency: 'USD',
        action: l('USD auf Termin kaufen', 'Buy USD forward'),
        cap: 80,
        startRatio: 80,
        stepDown: 10,
        ratios: [80, 70, 60, 50],
        averageRatio: 65,
        hedgedAmountFx: 2.015,
        hedgedAmountChf: 1.612,
        carryChf: 0.034,
        limitedByPolicy: false,
        collarShare: null,
        scenarioUnhedgedChf: null,
        scenarioHedgedChf: null,
      },
    ],
    liquidity: {
      excess: 3.1,
      parts: [
        { product: 'DEPOSIT', share: 45, amount: 1.395, suitability: 'NOT_APPLICABLE' },
        {
          product: 'MONEY_MARKET_FUND',
          share: 40,
          amount: 1.24,
          suitability: 'SUITABLE_AFTER_DISCLOSURE',
        },
        { product: 'SHORT_TERM_BOND', share: 15, amount: 0.465, suitability: 'SUITABLE' },
      ],
    },
  },
  checklist: [
    { id: 'info', text: INFO_TEXT, legalBasis: law('Art. 8') },
    {
      id: 'kid.FX_FORWARD',
      text: l(
        'Basisinformationsblatt ausgehändigt: Devisentermingeschäft',
        'Key information document handed over: FX forward',
      ),
      legalBasis: law('Art. 58'),
    },
    {
      id: 'kid.MONEY_MARKET_FUND',
      text: l(
        'Basisinformationsblatt ausgehändigt: Geldmarktfonds CHF',
        'Key information document handed over: CHF money market fund',
      ),
      legalBasis: law('Art. 58'),
    },
    RISK_BROCHURE,
    COST_DISCLOSURE,
    {
      id: 'warning.MONEY_MARKET_FUND',
      text: l(
        'Aufklärung und Warnhinweis dokumentiert: Geldmarktfonds CHF',
        'Disclosure and warning documented: CHF money market fund',
      ),
      legalBasis: law('Art. 14'),
    },
  ],
};

const COMPONENTS_INCOME = l(
  'Konzernerfolgsrechnung, S. 52',
  'Consolidated income statement, p. 52',
);
const COMPONENTS_BALANCE = l('Konzernbilanz, S. 51', 'Consolidated balance sheet, p. 51');
const COMPONENTS_SEGMENTS = l('Segmentbericht, S. 41', 'Segment report, p. 41');
const COMPONENTS_CURRENCIES = l(
  'Segmentbericht, S. 41: Umsatz und Einkauf nach Währungen',
  'Segment report, p. 41: revenue and purchasing by currency',
);
const PRESUMED = {
  knowledge: null,
  knowledgePresumed: true,
  note: null,
  kidRequired: false,
} as const;

export const COMPONENTS: ClientSeed = {
  id: 'beispiel-components',
  name: 'Beispiel Components AG',
  shortLabel: l('Mid-Cap', 'Mid-cap'),
  industry: l('Industriekomponenten', 'Industrial components'),
  employees: 610,
  segment: 'PROFESSIONAL',
  riskProfile: 'BALANCED',
  document: {
    fileName: 'Geschaeftsbericht_2025_Beispiel_Components.pdf',
    pageCount: 86,
    label: l('Geschäftsbericht 2025', 'Annual report 2025'),
    balanceSheetPage: 51,
  },
  answers: {
    'fin.revenue': doc('146.0', COMPONENTS_INCOME),
    'fin.totalAssets': doc('112.0', COMPONENTS_BALANCE),
    'fin.equity': doc('46.0', COMPONENTS_BALANCE),
    'fin.ebit': doc('12.8', COMPONENTS_INCOME),
    'fin.cash': doc('28.0', COMPONENTS_BALANCE),
    'fin.operatingExpenses': doc('133.2', COMPONENTS_INCOME),
    'fin.floatingRateDebt': doc('25.0', l('Anhang Ziff. 14, S. 66', 'Notes, item 14, p. 66')),
    'fx.eur.inflow': doc('85.4', COMPONENTS_CURRENCIES),
    'fx.eur.outflow': doc('33.0', COMPONENTS_CURRENCIES),
    'fx.usd.inflow': doc('33.0', COMPONENTS_SEGMENTS),
    'fx.usd.outflow': doc('12.0', COMPONENTS_SEGMENTS),
    'segment.treasuryOrganisation': doc(
      'TREASURY_DEPARTMENT',
      l(
        'Geschäftsbericht, S. 34: Group Treasury mit 3 Vollzeitstellen',
        'Annual report, p. 34: group treasury with 3 full-time positions',
      ),
    ),
    'goals.hedgeObjective': set('PROTECT_MARGINS'),
    'goals.policyMaxHedgeRatio': set('90'),
    'goals.minimumLiquidity': rule(
      '16.7',
      l(
        'Vorschlag aus dem Betriebsaufwand (Konzernerfolgsrechnung, S. 52)',
        'Proposed from operating expenses (consolidated income statement, p. 52)',
      ),
    ),
    'goals.investmentHorizon': set('ONE_TO_THREE_YEARS'),
    'risk.tolerableDrawdown': set('UP_TO_5_PERCENT'),
    'risk.forgoneGainAttitude': set('TOLERABLE'),
    'knowledge.fxForward': doc(
      'EXPERIENCE',
      l('Anhang Ziff. 9, Devisentermingeschäfte', 'Notes, item 9, FX forwards'),
    ),
    'knowledge.zeroCostCollar': doc(
      'EXPERIENCE',
      l('Anhang Ziff. 9, Devisenoptionen', 'Notes, item 9, FX options'),
    ),
    'knowledge.moneyMarketFund': set('EXPERIENCE'),
    'knowledge.shortTermBond': set('EXPERIENCE'),
  },
  assessment: {
    segment: {
      code: 'PROFESSIONAL',
      reason: l(
        'grosses Unternehmen, 3 von 3 Schwellenwerten überschritten, professionelle Tresorerie',
        'large company, 3 of 3 thresholds exceeded, professional treasury',
      ),
      legalBasis: law('Art. 4 und 5', 'Art. 4 and 5'),
      thresholdsExceeded: 3,
    },
    capacity: {
      score: 3,
      factors: [
        { label: l('Eigenkapitalquote', 'Equity ratio'), value: l('41.1 %', '41.1%'), score: 4 },
        {
          label: l('Überschussliquidität', 'Excess liquidity'),
          value: l('1.0 Monate', '1.0 months'),
          score: 2,
        },
        { label: l('EBIT-Marge', 'EBIT margin'), value: l('8.8 %', '8.8%'), score: 3 },
        {
          label: l('Ungesichertes Währungsrisiko', 'Unhedged currency risk'),
          value: l('51.6 % des EBIT', '51.6% of EBIT'),
          score: 1,
        },
      ],
    },
    willingness: {
      score: 3,
      factors: [
        {
          label: l('Ziel der Absicherung', 'Hedging objective'),
          value: l('Margen schützen', 'Protect margins'),
          score: 3,
        },
        {
          label: l('Tragbarer Wertrückgang', 'Tolerable drawdown'),
          value: l('Bis 5 %', 'Up to 5%'),
          score: 3,
        },
        { label: l('Entgangener Kursgewinn', 'Forgone gain'), value: TOLERABLE, score: 3 },
      ],
    },
    profile: { score: 3, code: 'BALANCED' },
    exposures: [
      {
        currency: 'EUR',
        netPosition: 52.4,
        netPositionChf: 49.256,
        sensitivityChf: 4.9256,
        sensitivityPctEbit: 0.3848,
        riskLevel: 'HIGH',
        direction: 'NET_INFLOW',
      },
      {
        currency: 'USD',
        netPosition: 21.0,
        netPositionChf: 16.8,
        sensitivityChf: 1.68,
        sensitivityPctEbit: 0.1313,
        riskLevel: 'MEDIUM',
        direction: 'NET_INFLOW',
      },
    ],
    suitability: [
      { product: 'FX_FORWARD', suitability: 'SUITABLE', ...PRESUMED },
      { product: 'ZERO_COST_COLLAR', suitability: 'SUITABLE', ...PRESUMED },
      { product: 'MONEY_MARKET_FUND', suitability: 'SUITABLE', ...PRESUMED },
      { product: 'SHORT_TERM_BOND', suitability: 'SUITABLE', ...PRESUMED },
      {
        product: 'DEPOSIT',
        knowledge: null,
        knowledgePresumed: false,
        suitability: 'NOT_APPLICABLE',
        note: NO_FINANCIAL_INSTRUMENT,
        kidRequired: false,
      },
    ],
    hedges: [
      {
        currency: 'EUR',
        action: l(
          'EUR auf Termin verkaufen, 30 % als Zero-Cost-Collar',
          'Sell EUR forward, 30% as zero-cost collar',
        ),
        cap: 90,
        startRatio: 70,
        stepDown: 10,
        ratios: [70, 60, 50, 40],
        averageRatio: 55,
        hedgedAmountFx: 28.82,
        hedgedAmountChf: 27.0908,
        carryChf: -0.2125,
        limitedByPolicy: false,
        collarShare: 0.3,
        scenarioUnhedgedChf: -4.9256,
        scenarioHedgedChf: -2.6407,
      },
      {
        currency: 'USD',
        action: l(
          'USD auf Termin verkaufen, 30 % als Zero-Cost-Collar',
          'Sell USD forward, 30% as zero-cost collar',
        ),
        cap: 90,
        startRatio: 70,
        stepDown: 10,
        ratios: [70, 60, 50, 40],
        averageRatio: 55,
        hedgedAmountFx: 11.55,
        hedgedAmountChf: 9.24,
        carryChf: -0.1343,
        limitedByPolicy: false,
        collarShare: 0.3,
        scenarioUnhedgedChf: null,
        scenarioHedgedChf: null,
      },
    ],
    liquidity: {
      excess: 11.3,
      parts: [
        { product: 'DEPOSIT', share: 20, amount: 2.26, suitability: 'NOT_APPLICABLE' },
        { product: 'MONEY_MARKET_FUND', share: 25, amount: 2.825, suitability: 'SUITABLE' },
        { product: 'SHORT_TERM_BOND', share: 55, amount: 6.215, suitability: 'SUITABLE' },
      ],
    },
  },
  checklist: [
    {
      id: 'info',
      text: l(`${INFO_TEXT['de-CH']} (Verzicht möglich)`, `${INFO_TEXT.en} (waiver possible)`),
      legalBasis: law('Art. 8'),
    },
    RISK_BROCHURE,
    COST_DISCLOSURE,
  ],
};

export const SEEDS: readonly ClientSeed[] = [MUSTER, COMPONENTS];
