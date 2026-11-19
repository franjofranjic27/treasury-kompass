import {
  Assessment,
  ChatEvent,
  ClientDetail,
  ClientDocument,
  ClientSummary,
  HedgeSettings,
  Question,
  Questionnaire,
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
    onUpdate: (document: ClientDocument) => void,
  ): Promise<void>;
  abstract getQuestionnaire(clientId: string): Promise<Questionnaire>;
  /** Sets the answer as the advisor; the question's type decides how the value is sent. */
  abstract setAnswer(clientId: string, question: Question, value: string): Promise<void>;
  abstract confirmAnswer(clientId: string, key: string): Promise<void>;
  abstract getAssessment(clientId: string): Promise<Assessment>;
  abstract setHedgeSettings(
    clientId: string,
    currency: string,
    settings: HedgeSettings,
  ): Promise<void>;
  abstract setChecklistItem(clientId: string, itemId: string, done: boolean): Promise<void>;
  abstract approve(clientId: string, assessmentVersion: number): Promise<void>;
  abstract chat(
    clientId: string | null,
    message: string,
    onEvent: (event: ChatEvent) => void,
  ): Promise<void>;
}

/** A request the backend answered with an error; the message is already worded for the advisor. */
export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

/**
 * Nothing that looks like our backend answered: network failure, gateway error, or an HTML page
 * (e.g. a static host's SPA fallback or 404 page) where JSON was expected. The message is localized.
 */
export class BackendUnavailableError extends ApiError {
  constructor(message: string, status = 0) {
    super(message, status);
    this.name = 'BackendUnavailableError';
  }
}
