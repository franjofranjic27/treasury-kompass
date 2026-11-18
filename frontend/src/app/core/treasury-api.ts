import {
  Assessment,
  ChatEvent,
  ClientDetail,
  ClientDocument,
  ClientSummary,
  HedgeSettings,
  Questionnaire
} from './models';

/**
 * Single data access abstraction for all screens. Implemented by an in-memory mock
 * (static Vercel deployment) and an HTTP client for treasury-service and agent-service.
 */
export abstract class TreasuryApi {
  abstract listClients(query: string): Promise<readonly ClientSummary[]>;
  abstract createClient(name: string): Promise<ClientSummary>;
  abstract getClient(id: string): Promise<ClientDetail>;
  abstract uploadDocument(
    clientId: string,
    file: File,
    onUpdate: (document: ClientDocument) => void
  ): Promise<void>;
  abstract getQuestionnaire(clientId: string): Promise<Questionnaire>;
  abstract setAnswer(clientId: string, key: string, value: string): Promise<void>;
  abstract confirmAnswer(clientId: string, key: string): Promise<void>;
  abstract getAssessment(clientId: string): Promise<Assessment>;
  abstract setHedgeSettings(clientId: string, currency: string, settings: HedgeSettings): Promise<void>;
  abstract setChecklistItem(clientId: string, itemId: string, done: boolean): Promise<void>;
  abstract approve(clientId: string, assessmentVersion: number): Promise<void>;
  abstract chat(
    clientId: string | null,
    message: string,
    onEvent: (event: ChatEvent) => void
  ): Promise<void>;
}

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number
  ) {
    super(message);
    this.name = 'ApiError';
  }
}
