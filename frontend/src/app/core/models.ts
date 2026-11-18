export type DocumentStatus = 'UPLOADED' | 'TEXT_EXTRACTED' | 'INDEXED' | 'EXTRACTED' | 'FAILED';
export type AnswerStatus = 'PROPOSED' | 'CONFIRMED' | 'MISSING';
export type QuestionType = 'CHOICE' | 'AMOUNT' | 'PERCENT' | 'BOOLEAN' | 'KNOWLEDGE_LEVEL';
export type Suitability = 'SUITABLE' | 'SUITABLE_AFTER_DISCLOSURE' | 'NOT_SUITABLE' | 'NOT_APPLICABLE';
export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH';
export type ApprovalStatus = 'APPROVED' | 'NOT_APPROVED';

export interface ClientSummary {
  readonly id: string;
  readonly name: string;
  readonly shortLabel: string;
  readonly industry: string;
  readonly employees: string;
  readonly segment: string | null;
  readonly riskProfile: string | null;
  readonly documentCount: number;
  readonly openItems: number;
  readonly approvalStatus: ApprovalStatus;
}

export interface KeyFigure {
  readonly key: string;
  readonly label: string;
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
  readonly type: QuestionType;
  readonly options: readonly ChoiceOption[];
  readonly value: string | null;
  readonly status: AnswerStatus;
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
  readonly sensitivityPctEbit: number;
  readonly riskLevel: RiskLevel;
  readonly direction: string;
}

export interface ProductSuitability {
  readonly product: string;
  readonly knowledge: string;
  readonly suitability: Suitability;
  readonly note: string | null;
  readonly kidRequired: boolean;
}

export interface HedgeProposal {
  readonly currency: string;
  readonly action: string;
  readonly cap: number;
  readonly ratios: readonly number[];
  readonly averageRatio: number;
  readonly hedgedAmountFx: number;
  readonly carryChf: number;
  readonly limitedByPolicy: boolean;
  readonly collarShare: number | null;
  readonly scenarioUnhedgedChf: number | null;
  readonly scenarioHedgedChf: number | null;
}

export interface LiquidityPart {
  readonly product: string;
  readonly share: number;
  readonly amount: number;
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
    readonly label: string;
    readonly reason: string;
    readonly legalBasis: string;
    readonly thresholdsExceeded: number;
  };
  readonly capacity: { readonly score: number; readonly factors: readonly ScoredFactor[] };
  readonly willingness: { readonly score: number; readonly factors: readonly ScoredFactor[] };
  readonly profile: { readonly score: number; readonly label: string };
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
