export type DocumentStatus = 'UPLOADED' | 'TEXT_EXTRACTED' | 'INDEXED' | 'EXTRACTED' | 'FAILED';
export type AnswerStatus = 'PROPOSED' | 'CONFIRMED' | 'MISSING';
export type QuestionType = 'CHOICE' | 'AMOUNT' | 'PERCENT' | 'BOOLEAN' | 'KNOWLEDGE_LEVEL';
export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH';
export type ApprovalStatus = 'APPROVED' | 'NOT_APPROVED';

/*
 * Codes the UI translates itself (public/i18n). Free texts such as rule reasons, notes and
 * citations arrive already worded by the backend and are shown as delivered.
 */
export const CLIENT_SEGMENTS = ['RETAIL', 'PROFESSIONAL', 'INSTITUTIONAL'] as const;
export type ClientSegment = (typeof CLIENT_SEGMENTS)[number];

export const RISK_PROFILE_CODES = [
  'SAFETY',
  'CONSERVATIVE',
  'BALANCED',
  'GROWTH',
  'DYNAMIC',
] as const;
export type RiskProfileCode = (typeof RISK_PROFILE_CODES)[number];

export const PRODUCT_CODES = [
  'FX_FORWARD',
  'ZERO_COST_COLLAR',
  'MONEY_MARKET_FUND',
  'SHORT_TERM_BOND',
  'DEPOSIT',
] as const;
export type ProductCode = (typeof PRODUCT_CODES)[number];

export const SUITABILITY_CODES = [
  'SUITABLE',
  'SUITABLE_AFTER_DISCLOSURE',
  'NOT_SUITABLE',
  'NOT_APPLICABLE',
] as const;
export type Suitability = (typeof SUITABILITY_CODES)[number];

export const KNOWLEDGE_LEVELS = ['NONE', 'KNOWLEDGE', 'EXPERIENCE'] as const;
export type KnowledgeLevel = (typeof KNOWLEDGE_LEVELS)[number];

export const EXPOSURE_DIRECTIONS = ['NET_INFLOW', 'NET_OUTFLOW', 'NONE'] as const;
export type ExposureDirection = (typeof EXPOSURE_DIRECTIONS)[number];

export const ANSWER_ORIGINS = ['DOCUMENT', 'RULE', 'AGENT', 'ADVISOR'] as const;
export type AnswerOrigin = (typeof ANSWER_ORIGINS)[number];

export interface ClientSummary {
  readonly id: string;
  readonly name: string;
  readonly shortLabel: string | null;
  readonly industry: string | null;
  readonly employees: number | null;
  readonly segment: ClientSegment | null;
  readonly riskProfile: RiskProfileCode | null;
  readonly documentCount: number;
  readonly openItems: number;
  readonly approvalStatus: ApprovalStatus;
}

/** Labelled in the UI by `key` (translation `figure.<key>`). */
export interface KeyFigure {
  readonly key: string;
  readonly value: number | null;
  readonly source: string | null;
}

export interface FxFlow {
  readonly currency: string;
  readonly inflow: number;
  readonly outflow: number;
  readonly source: string | null;
}

export interface ClientDocument {
  readonly id: string;
  readonly fileName: string;
  readonly status: DocumentStatus;
  readonly pageCount: number | null;
}

export interface ClientDetail extends ClientSummary {
  readonly figures: readonly KeyFigure[];
  readonly fxFlows: readonly FxFlow[];
  readonly documents: readonly ClientDocument[];
  readonly minimumLiquidity: number | null;
}

export interface ChoiceOption {
  readonly value: string;
  readonly label: string;
}

export interface Question {
  readonly key: string;
  readonly text: string;
  readonly hint: string | null;
  readonly unit: string | null;
  readonly type: QuestionType;
  readonly options: readonly ChoiceOption[];
  readonly value: string | null;
  readonly status: AnswerStatus;
  readonly origin: AnswerOrigin | null;
  readonly source: string | null;
}

export interface QuestionnaireSection {
  readonly id: string;
  readonly title: string;
  readonly legalBasis: string;
  readonly questions: readonly Question[];
}

export interface Questionnaire {
  readonly sections: readonly QuestionnaireSection[];
}

export interface ScoredFactor {
  readonly label: string;
  readonly value: string;
  readonly score: number;
}

export interface Exposure {
  readonly currency: string;
  readonly netPosition: number;
  readonly netPositionChf: number;
  /** EBIT impact of a 10 % rate move, CHF Mio., as a positive number. */
  readonly sensitivityChf: number;
  readonly sensitivityPctEbit: number;
  readonly riskLevel: RiskLevel;
  readonly direction: ExposureDirection;
}

export interface ProductSuitability {
  readonly product: ProductCode;
  readonly knowledge: KnowledgeLevel | null;
  readonly knowledgePresumed: boolean;
  readonly suitability: Suitability;
  readonly note: string | null;
  readonly kidRequired: boolean;
}

export interface HedgeProposal {
  readonly currency: string;
  readonly action: string;
  readonly cap: number;
  readonly startRatio: number;
  readonly stepDown: number;
  readonly ratios: readonly number[];
  readonly averageRatio: number;
  readonly hedgedAmountFx: number;
  readonly hedgedAmountChf: number;
  readonly carryChf: number;
  readonly limitedByPolicy: boolean;
  readonly collarShare: number | null;
  readonly scenarioUnhedgedChf: number | null;
  readonly scenarioHedgedChf: number | null;
}

export interface LiquidityPart {
  readonly product: ProductCode;
  readonly share: number;
  readonly amount: number;
  readonly suitability: Suitability;
}

export interface ChecklistItem {
  readonly id: string;
  readonly text: string;
  readonly legalBasis: string;
  readonly done: boolean;
}

export interface Assessment {
  readonly version: number;
  readonly segment: {
    readonly code: ClientSegment | null;
    readonly reason: string;
    readonly legalBasis: string;
    readonly thresholdsExceeded: number;
  };
  readonly capacity: { readonly score: number; readonly factors: readonly ScoredFactor[] };
  readonly willingness: { readonly score: number; readonly factors: readonly ScoredFactor[] };
  readonly profile: { readonly score: number; readonly code: RiskProfileCode | null };
  readonly exposures: readonly Exposure[];
  readonly suitability: readonly ProductSuitability[];
  readonly hedges: readonly HedgeProposal[];
  readonly liquidity: { readonly excess: number; readonly parts: readonly LiquidityPart[] };
  readonly checklist: readonly ChecklistItem[];
  readonly unconfirmedAnswers: number;
  readonly approvalStatus: ApprovalStatus;
}

export interface Citation {
  readonly document: string;
  readonly location: string;
}

export interface ChatProposal {
  readonly title: string;
  readonly detail: string;
  readonly currency: string;
  readonly startRatio: number;
  readonly stepDown: number;
}

export type ChatEvent =
  | { readonly type: 'token'; readonly text: string }
  | { readonly type: 'citation'; readonly citation: Citation }
  | { readonly type: 'proposal'; readonly proposal: ChatProposal }
  | { readonly type: 'done' }
  | { readonly type: 'error'; readonly message: string };

export interface HedgeSettings {
  readonly startRatio: number;
  readonly stepDown: number;
}

/** Narrows an unknown backend value to one of the given codes, or null if it is not one of them. */
export function toCode<T extends string>(value: unknown, codes: readonly T[]): T | null {
  return typeof value === 'string' && (codes as readonly string[]).includes(value)
    ? (value as T)
    : null;
}
