import { Localized, l, law } from './i18n/localized';
import { QuestionType } from './models';

/** Questionnaire of the in-memory demo backend; the HTTP backend delivers its own worded catalog. */
export interface CatalogOption {
  readonly value: string;
  readonly label: Localized;
}

export interface CatalogQuestion {
  readonly key: string;
  readonly section: string;
  readonly type: QuestionType;
  readonly text: Localized;
  readonly unit?: Localized;
  readonly hint?: Localized;
  readonly options?: readonly CatalogOption[];
}

export interface CatalogSection {
  readonly id: string;
  readonly title: Localized;
  readonly legalBasis: Localized;
}

const opts = (entries: readonly (readonly [string, string, string])[]): CatalogOption[] =>
  entries.map(([value, de, en]) => ({ value, label: l(de, en) }));

const KNOWLEDGE = opts([
  ['NONE', 'Keine', 'None'],
  ['KNOWLEDGE', 'Kenntnisse', 'Knowledge'],
  ['EXPERIENCE', 'Kenntnisse und Erfahrung', 'Knowledge and experience'],
]);

const ART_12 = law('Art. 12');

export const SECTIONS: readonly CatalogSection[] = [
  {
    id: 'FINANCIALS',
    title: l('Finanzielle Verhältnisse', 'Financial situation'),
    legalBasis: ART_12,
  },
  {
    id: 'FX_FLOWS',
    title: l('Fremdwährungsflüsse, nächste 12 Monate', 'Foreign currency flows, next 12 months'),
    legalBasis: ART_12,
  },
  {
    id: 'SEGMENT',
    title: l('Kundensegmentierung', 'Client segmentation'),
    legalBasis: law('Art. 4 und 5', 'Art. 4 and 5'),
  },
  { id: 'GOALS', title: l('Ziele und Bedürfnisse', 'Objectives and needs'), legalBasis: ART_12 },
  { id: 'RISK_TOLERANCE', title: l('Risikobereitschaft', 'Risk willingness'), legalBasis: ART_12 },
  {
    id: 'KNOWLEDGE',
    title: l('Kenntnisse und Erfahrungen', 'Knowledge and experience'),
    legalBasis: ART_12,
  },
];

const CHF_MIO = l('CHF Mio.', 'CHF m');

const amount = (
  key: string,
  section: string,
  text: Localized,
  unit: Localized = CHF_MIO,
): CatalogQuestion => ({
  key,
  section,
  type: 'AMOUNT',
  text,
  unit,
});

export const QUESTIONS: readonly CatalogQuestion[] = [
  amount('fin.revenue', 'FINANCIALS', l('Umsatzerlös', 'Revenue')),
  amount('fin.totalAssets', 'FINANCIALS', l('Bilanzsumme', 'Total assets')),
  amount('fin.equity', 'FINANCIALS', l('Eigenkapital', 'Equity')),
  amount('fin.ebit', 'FINANCIALS', l('Betriebsergebnis (EBIT)', 'Operating result (EBIT)')),
  amount('fin.cash', 'FINANCIALS', l('Flüssige Mittel', 'Cash and cash equivalents')),
  amount('fin.operatingExpenses', 'FINANCIALS', l('Betriebsaufwand', 'Operating expenses')),
  amount(
    'fin.floatingRateDebt',
    'FINANCIALS',
    l('Bankkredit, SARON-basiert', 'Bank loan, SARON-based'),
  ),
  amount(
    'fx.eur.inflow',
    'FX_FLOWS',
    l('EUR-Zuflüsse, nächste 12 Monate', 'EUR inflows, next 12 months'),
    l('EUR Mio.', 'EUR m'),
  ),
  amount(
    'fx.eur.outflow',
    'FX_FLOWS',
    l('EUR-Abflüsse, nächste 12 Monate', 'EUR outflows, next 12 months'),
    l('EUR Mio.', 'EUR m'),
  ),
  amount(
    'fx.usd.inflow',
    'FX_FLOWS',
    l('USD-Zuflüsse, nächste 12 Monate', 'USD inflows, next 12 months'),
    l('USD Mio.', 'USD m'),
  ),
  amount(
    'fx.usd.outflow',
    'FX_FLOWS',
    l('USD-Abflüsse, nächste 12 Monate', 'USD outflows, next 12 months'),
    l('USD Mio.', 'USD m'),
  ),
  {
    key: 'segment.treasuryOrganisation',
    section: 'SEGMENT',
    type: 'CHOICE',
    text: l(
      'Wie werden die Finanzen des Unternehmens geführt?',
      'How are the company’s finances managed?',
    ),
    hint: l(
      'Professionelle Tresorerie heisst: Mindestens eine fachlich ausgewiesene Person mit Erfahrung im Finanzbereich ist mit der Bewirtschaftung der Finanzmittel betraut.',
      'Professional treasury means: at least one qualified person with experience in finance is entrusted with managing the financial resources.',
    ),
    options: opts([
      [
        'TREASURY_DEPARTMENT',
        'Eigene Treasury-Abteilung mit Fachpersonen',
        'Own treasury department with specialists',
      ],
      [
        'CFO_OR_ACCOUNTING',
        'CFO oder Buchhaltung, neben anderen Aufgaben',
        'CFO or accounting, alongside other tasks',
      ],
      [
        'OUTSOURCED',
        'An externen Dienstleister ausgelagert',
        'Outsourced to an external service provider',
      ],
    ]),
  },
  {
    key: 'goals.hedgeObjective',
    section: 'GOALS',
    type: 'CHOICE',
    text: l('Was ist das Hauptziel der Absicherung?', 'What is the main objective of hedging?'),
    options: opts([
      [
        'BUDGET_CERTAINTY',
        'Budgetsicherheit: Plankurs halten',
        'Budget certainty: hold the planning rate',
      ],
      [
        'PROTECT_MARGINS',
        'Margen schützen, Chancen teilweise offen lassen',
        'Protect margins, keep some upside open',
      ],
      ['LOW_HEDGING_COST', 'Absicherungskosten tief halten', 'Keep hedging costs low'],
    ]),
  },
  {
    key: 'goals.policyMaxHedgeRatio',
    section: 'GOALS',
    type: 'CHOICE',
    text: l(
      'Welche maximale Absicherungsquote erlaubt die Treasury-Policy?',
      'What maximum hedge ratio does the treasury policy allow?',
    ),
    options: opts([
      ['60', '60 %', '60%'],
      ['80', '80 %', '80%'],
      ['90', '90 %', '90%'],
      ['100', '100 % oder keine Vorgabe', '100% or no limit'],
    ]),
  },
  {
    key: 'goals.minimumLiquidity',
    section: 'GOALS',
    type: 'AMOUNT',
    text: l(
      'Welche Mindestliquidität muss jederzeit verfügbar bleiben?',
      'What minimum liquidity must remain available at all times?',
    ),
    unit: CHF_MIO,
    hint: l(
      'Richtwert: 1.5 Monate Betriebsaufwand.',
      'Guideline: 1.5 months of operating expenses.',
    ),
  },
  {
    key: 'goals.investmentHorizon',
    section: 'GOALS',
    type: 'CHOICE',
    text: l(
      'Wie lange kann die überschüssige Liquidität angelegt werden?',
      'For how long can the excess liquidity be invested?',
    ),
    options: opts([
      ['UNDER_1_YEAR', 'Unter 1 Jahr', 'Under 1 year'],
      ['ONE_TO_THREE_YEARS', '1 bis 3 Jahre', '1 to 3 years'],
      ['OVER_THREE_YEARS', 'Über 3 Jahre', 'Over 3 years'],
    ]),
  },
  {
    key: 'risk.tolerableDrawdown',
    section: 'RISK_TOLERANCE',
    type: 'CHOICE',
    text: l(
      'Welcher vorübergehende Wertrückgang der Liquiditätsanlage innert 12 Monaten wäre tragbar?',
      'What temporary decline in value of the liquidity investment within 12 months would be tolerable?',
    ),
    options: opts([
      ['NONE', 'Keiner, Kapitalerhalt', 'None, capital preservation'],
      ['UP_TO_2_PERCENT', 'Bis 2 %', 'Up to 2%'],
      ['UP_TO_5_PERCENT', 'Bis 5 %', 'Up to 5%'],
      ['OVER_5_PERCENT', 'Mehr als 5 %', 'More than 5%'],
    ]),
  },
  {
    key: 'risk.forgoneGainAttitude',
    section: 'RISK_TOLERANCE',
    type: 'CHOICE',
    text: l(
      'Der Euro steigt nach Abschluss der Absicherung um 8 %. Wie beurteilt die Geschäftsleitung den entgangenen Kursgewinn?',
      'The euro rises by 8% after the hedge is concluded. How does management view the forgone gain?',
    ),
    options: opts([
      [
        'ACCEPTABLE',
        'Akzeptabel, Planungssicherheit zählt',
        'Acceptable, planning certainty matters',
      ],
      ['TOLERABLE', 'Ärgerlich, aber tragbar', 'Annoying but tolerable'],
      ['NOT_ACCEPTABLE', 'Nicht akzeptabel', 'Not acceptable'],
    ]),
  },
  {
    key: 'knowledge.fxForward',
    section: 'KNOWLEDGE',
    type: 'KNOWLEDGE_LEVEL',
    text: l('Devisentermingeschäft', 'FX forward'),
    options: KNOWLEDGE,
  },
  {
    key: 'knowledge.zeroCostCollar',
    section: 'KNOWLEDGE',
    type: 'KNOWLEDGE_LEVEL',
    text: l('Zero-Cost-Collar (Devisenoptionen)', 'Zero-cost collar (FX options)'),
    options: KNOWLEDGE,
  },
  {
    key: 'knowledge.moneyMarketFund',
    section: 'KNOWLEDGE',
    type: 'KNOWLEDGE_LEVEL',
    text: l('Geldmarktfonds CHF', 'CHF money market fund'),
    options: KNOWLEDGE,
  },
  {
    key: 'knowledge.shortTermBond',
    section: 'KNOWLEDGE',
    type: 'KNOWLEDGE_LEVEL',
    text: l(
      'Obligationen CHF, Investment Grade, kurze Laufzeit',
      'CHF bonds, investment grade, short maturity',
    ),
    options: KNOWLEDGE,
  },
];

/** Key figures shown on the dashboard, in catalog order; labels come from the `figure.*` translations. */
export const FIGURE_KEYS: readonly string[] = [
  'fin.revenue',
  'fin.totalAssets',
  'fin.equity',
  'fin.ebit',
  'fin.cash',
  'fin.operatingExpenses',
  'fin.floatingRateDebt',
];
