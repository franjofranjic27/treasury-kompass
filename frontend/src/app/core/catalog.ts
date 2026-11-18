import { ChoiceOption, QuestionType } from './models';

export interface CatalogQuestion {
  readonly key: string;
  readonly section: string;
  readonly type: QuestionType;
  readonly text: string;
  readonly hint?: string;
  readonly options?: readonly ChoiceOption[];
}

export interface CatalogSection {
  readonly id: string;
  readonly title: string;
  readonly legalBasis: string;
}

const opts = (pairs: readonly (readonly [string, string])[]): ChoiceOption[] =>
  pairs.map(([value, label]) => ({ value, label }));

const KNOWLEDGE = opts([
  ['NONE', 'Keine'],
  ['KNOWLEDGE', 'Kenntnisse'],
  ['EXPERIENCE', 'Kenntnisse und Erfahrung']
]);

export const SECTIONS: readonly CatalogSection[] = [
  { id: 'FINANCIALS', title: 'Finanzielle Verhältnisse', legalBasis: 'Art. 12 FIDLEG' },
  { id: 'FX_FLOWS', title: 'Fremdwährungsflüsse, nächste 12 Monate', legalBasis: 'Art. 12 FIDLEG' },
  { id: 'SEGMENT', title: 'Kundensegmentierung', legalBasis: 'Art. 4 und 5 FIDLEG' },
  { id: 'GOALS', title: 'Ziele und Bedürfnisse', legalBasis: 'Art. 12 FIDLEG' },
  { id: 'RISK_TOLERANCE', title: 'Risikobereitschaft', legalBasis: 'Art. 12 FIDLEG' },
  { id: 'KNOWLEDGE', title: 'Kenntnisse und Erfahrungen', legalBasis: 'Art. 12 FIDLEG' }
];

const amount = (key: string, section: string, text: string): CatalogQuestion => ({ key, section, type: 'AMOUNT', text });

export const QUESTIONS: readonly CatalogQuestion[] = [
  amount('fin.revenue', 'FINANCIALS', 'Umsatzerlös (CHF Mio.)'),
  amount('fin.totalAssets', 'FINANCIALS', 'Bilanzsumme (CHF Mio.)'),
  amount('fin.equity', 'FINANCIALS', 'Eigenkapital (CHF Mio.)'),
  amount('fin.ebit', 'FINANCIALS', 'Betriebsergebnis (EBIT) (CHF Mio.)'),
  amount('fin.cash', 'FINANCIALS', 'Flüssige Mittel (CHF Mio.)'),
  amount('fin.operatingExpenses', 'FINANCIALS', 'Betriebsaufwand (CHF Mio.)'),
  amount('fin.floatingRateDebt', 'FINANCIALS', 'Bankkredit, SARON-basiert (CHF Mio.)'),
  amount('fx.eur.inflow', 'FX_FLOWS', 'EUR-Zuflüsse, nächste 12 Monate (EUR Mio.)'),
  amount('fx.eur.outflow', 'FX_FLOWS', 'EUR-Abflüsse, nächste 12 Monate (EUR Mio.)'),
  amount('fx.usd.inflow', 'FX_FLOWS', 'USD-Zuflüsse, nächste 12 Monate (USD Mio.)'),
  amount('fx.usd.outflow', 'FX_FLOWS', 'USD-Abflüsse, nächste 12 Monate (USD Mio.)'),
  {
    key: 'segment.treasuryOrganisation',
    section: 'SEGMENT',
    type: 'CHOICE',
    text: 'Wie werden die Finanzen des Unternehmens geführt?',
    hint: 'Professionelle Tresorerie heisst: Mindestens eine fachlich ausgewiesene Person mit Erfahrung im Finanzbereich ist mit der Bewirtschaftung der Finanzmittel betraut.',
    options: opts([
      ['TREASURY_DEPARTMENT', 'Eigene Treasury-Abteilung mit Fachpersonen'],
      ['CFO_OR_ACCOUNTING', 'CFO oder Buchhaltung, neben anderen Aufgaben'],
      ['OUTSOURCED', 'An externen Dienstleister ausgelagert']
    ])
  },
  {
    key: 'goals.hedgeObjective',
    section: 'GOALS',
    type: 'CHOICE',
    text: 'Was ist das Hauptziel der Absicherung?',
    options: opts([
      ['BUDGET_CERTAINTY', 'Budgetsicherheit: Plankurs halten'],
      ['PROTECT_MARGINS', 'Margen schützen, Chancen teilweise offen lassen'],
      ['LOW_HEDGING_COST', 'Absicherungskosten tief halten']
    ])
  },
  {
    key: 'goals.policyMaxHedgeRatio',
    section: 'GOALS',
    type: 'CHOICE',
    text: 'Welche maximale Absicherungsquote erlaubt die Treasury-Policy?',
    options: opts([
      ['60', '60 %'],
      ['80', '80 %'],
      ['90', '90 %'],
      ['100', '100 % oder keine Vorgabe']
    ])
  },
  {
    key: 'goals.minimumLiquidity',
    section: 'GOALS',
    type: 'AMOUNT',
    text: 'Welche Mindestliquidität muss jederzeit verfügbar bleiben? (CHF Mio.)',
    hint: 'Richtwert: 1.5 Monate Betriebsaufwand.'
  },
  {
    key: 'goals.investmentHorizon',
    section: 'GOALS',
    type: 'CHOICE',
    text: 'Wie lange kann die überschüssige Liquidität angelegt werden?',
    options: opts([
      ['UNDER_1_YEAR', 'Unter 1 Jahr'],
      ['ONE_TO_THREE_YEARS', '1 bis 3 Jahre'],
      ['OVER_THREE_YEARS', 'Über 3 Jahre']
    ])
  },
  {
    key: 'risk.tolerableDrawdown',
    section: 'RISK_TOLERANCE',
    type: 'CHOICE',
    text: 'Welcher vorübergehende Wertrückgang der Liquiditätsanlage innert 12 Monaten wäre tragbar?',
    options: opts([
      ['NONE', 'Keiner, Kapitalerhalt'],
      ['UP_TO_2_PERCENT', 'Bis 2 %'],
      ['UP_TO_5_PERCENT', 'Bis 5 %'],
      ['OVER_5_PERCENT', 'Mehr als 5 %']
    ])
  },
  {
    key: 'risk.forgoneGainAttitude',
    section: 'RISK_TOLERANCE',
    type: 'CHOICE',
    text: 'Der Euro steigt nach Abschluss der Absicherung um 8 %. Wie beurteilt die Geschäftsleitung den entgangenen Kursgewinn?',
    options: opts([
      ['ACCEPTABLE', 'Akzeptabel, Planungssicherheit zählt'],
      ['TOLERABLE', 'Ärgerlich, aber tragbar'],
      ['NOT_ACCEPTABLE', 'Nicht akzeptabel']
    ])
  },
  { key: 'knowledge.fxForward', section: 'KNOWLEDGE', type: 'KNOWLEDGE_LEVEL', text: 'Devisentermingeschäft', options: KNOWLEDGE },
  { key: 'knowledge.zeroCostCollar', section: 'KNOWLEDGE', type: 'KNOWLEDGE_LEVEL', text: 'Zero-Cost-Collar (Devisenoptionen)', options: KNOWLEDGE },
  { key: 'knowledge.moneyMarketFund', section: 'KNOWLEDGE', type: 'KNOWLEDGE_LEVEL', text: 'Geldmarktfonds CHF', options: KNOWLEDGE },
  { key: 'knowledge.shortTermBond', section: 'KNOWLEDGE', type: 'KNOWLEDGE_LEVEL', text: 'Obligationen CHF, Investment Grade, kurze Laufzeit', options: KNOWLEDGE }
];

export const FIGURE_LABELS: Readonly<Record<string, string>> = {
  'fin.revenue': 'Umsatzerlös',
  'fin.totalAssets': 'Bilanzsumme',
  'fin.equity': 'Eigenkapital',
  'fin.ebit': 'Betriebsergebnis (EBIT)',
  'fin.cash': 'Flüssige Mittel',
  'fin.operatingExpenses': 'Betriebsaufwand',
  'fin.floatingRateDebt': 'Bankkredit, SARON-basiert'
};
