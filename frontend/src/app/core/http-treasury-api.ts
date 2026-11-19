import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { FIGURE_KEYS } from './catalog';
import { isEventStreamResponse, isJsonResponse } from './content-type';
import { DataMode } from './data-mode';
import { delay } from './delay';
import { LanguageService } from './i18n/language';
import {
  ANSWER_ORIGINS,
  AnswerStatus,
  Assessment,
  CLIENT_SEGMENTS,
  ChatEvent,
  ClientDetail,
  ClientDocument,
  ClientSummary,
  DocumentStatus,
  EXPOSURE_DIRECTIONS,
  HedgeSettings,
  KNOWLEDGE_LEVELS,
  KeyFigure,
  PRODUCT_CODES,
  ProductSuitability,
  Question,
  QuestionType,
  Questionnaire,
  RISK_PROFILE_CODES,
  SUITABILITY_CODES,
  fxFlowOf,
  toCode,
} from './models';
import { ApiError, BackendUnavailableError, TreasuryApi } from './treasury-api';

type Json = Record<string, unknown>;

const POLL_MS = 2000;
const TERMINAL: readonly DocumentStatus[] = ['EXTRACTED', 'FAILED'];
const ANSWER_STATUSES: readonly AnswerStatus[] = ['PROPOSED', 'CONFIRMED'];
const FX_FLOW_KEY = /^fx\.([a-z]{3})\.(?:inflow|outflow)$/;
const PROPOSAL_OUTCOMES = ['APPLIED', 'OPEN', 'UNCHANGED', 'REJECTED'] as const;
/** Blockers the advisory screen counts itself; all others are listed with the backend's wording. */
const COUNTED_BLOCKERS: readonly string[] = ['UNCONFIRMED_ANSWER', 'OPEN_CHECKLIST_ITEM'];
const ACCEPT_JSON: Readonly<Record<string, string>> = { Accept: 'application/json' };

interface SendInit {
  readonly method: string;
  readonly headers: Readonly<Record<string, string>>;
  readonly body?: BodyInit;
}

const obj = (v: unknown): Json => (v !== null && typeof v === 'object' ? (v as Json) : {});
const arr = (v: unknown): unknown[] => (Array.isArray(v) ? v : []);
const str = (v: unknown, fallback = ''): string =>
  v === null || v === undefined ? fallback : String(v);
const optStr = (v: unknown): string | null =>
  v === null || v === undefined || v === '' ? null : String(v);
const num = (v: unknown, fallback = 0): number =>
  typeof v === 'number' ? v : Number(v ?? fallback) || fallback;
const optNum = (v: unknown): number | null =>
  v === null || v === undefined || v === '' ? null : Number(v);

/** Screen of the agent-service ChatContext, derived from the route the advisor is on. */
type ChatScreen = 'START' | 'QUESTIONNAIRE' | 'DASHBOARD' | 'ADVISORY';

function screenOf(url: string): ChatScreen {
  const path = url.split(/[?#]/)[0];
  if (/^\/clients\/[^/]+\/profilierung/.test(path)) return 'QUESTIONNAIRE';
  if (/^\/clients\/[^/]+\/beratung/.test(path)) return 'ADVISORY';
  if (/^\/clients\/[^/]+/.test(path)) return 'DASHBOARD';
  return 'START';
}

/**
 * HTTP client for treasury-service (/api) and agent-service (/agent). DTOs are mapped loosely
 * because the backend contract is still evolving; unknown fields fall back to neutral defaults.
 * Codes stay codes (the UI translates them), worded texts are passed through as delivered.
 * Anything that is not an answer of the backend becomes a {@link BackendUnavailableError}.
 */
@Injectable()
export class HttpTreasuryApi extends TreasuryApi {
  private readonly language = inject(LanguageService);
  private readonly translate = inject(TranslateService);
  private readonly router = inject(Router);
  private readonly dataMode = inject(DataMode);
  /** Conversation per client from the `meta` event, so follow-up questions keep their context. */
  private readonly conversations = new Map<string, string>();

  async listClients(query: string): Promise<readonly ClientSummary[]> {
    const params = query.trim() ? `?query=${encodeURIComponent(query.trim())}` : '';
    const body = await this.request(`/api/clients${params}`);
    const list = Array.isArray(body) ? body : arr(obj(body)['content'] ?? obj(body)['items']);
    return list.map((c) => mapSummary(obj(c)));
  }

  async createClient(name: string): Promise<ClientSummary> {
    return mapSummary(obj(await this.request('/api/clients', 'POST', { name })));
  }

  async getClient(id: string): Promise<ClientDetail> {
    // The detail's key figures cover financials and FX flows only; the minimum liquidity is a goal.
    const [client, documents, questionnaire] = await Promise.all([
      this.request(`/api/clients/${id}`),
      this.request(`/api/clients/${id}/documents`).catch(() => []),
      this.request(`/api/clients/${id}/questionnaire`).catch(() => null),
    ]);
    const c = obj(client);
    const answers = new Map(
      arr(c['keyFigures']).map((f): [string, Json] => [str(obj(f)['key']), obj(f)]),
    );
    const figure = (key: string) => this.mapFigure(key, answers.get(key) ?? {});
    const currencies = new Set(
      [...answers.keys()].flatMap((key) => FX_FLOW_KEY.exec(key)?.[1] ?? []),
    );
    const minimumLiquidity = findAnswer(questionnaire, 'goals.minimumLiquidity');
    return {
      ...mapSummary(c),
      figures: FIGURE_KEYS.map(figure),
      fxFlows: [...currencies].map((ccy) =>
        fxFlowOf(ccy.toUpperCase(), figure(`fx.${ccy}.inflow`), figure(`fx.${ccy}.outflow`)),
      ),
      documents: arr(documents).map((d) => mapDocument(obj(d))),
      minimumLiquidity: minimumLiquidity
        ? this.mapFigure('goals.minimumLiquidity', minimumLiquidity)
        : null,
    };
  }

  async uploadDocument(
    clientId: string,
    file: File,
    onUpdate: (document: ClientDocument) => void,
  ): Promise<void> {
    const form = new FormData();
    form.append('file', file);
    const response = await this.send(`/api/clients/${clientId}/documents`, {
      method: 'POST',
      headers: ACCEPT_JSON,
      body: form,
    });
    let document = mapDocument(obj(await this.readJson(response)));
    onUpdate(document);
    while (!TERMINAL.includes(document.status)) {
      await delay(POLL_MS);
      const list = arr(await this.request(`/api/clients/${clientId}/documents`));
      const match = list.map((d) => mapDocument(obj(d))).find((d) => d.id === document.id);
      if (!match) throw new ApiError(this.translate.instant('errors.documentGone'), 404);
      document = match;
      onUpdate(document);
    }
  }

  async getQuestionnaire(clientId: string): Promise<Questionnaire> {
    const body = obj(await this.request(`/api/clients/${clientId}/questionnaire`));
    return {
      sections: arr(body['sections']).map((s) => {
        const section = obj(s);
        return {
          id: str(section['id']),
          title: str(section['title'], str(section['id'])),
          legalBasis: str(section['legalBasis']),
          // Hidden questions (e.g. opting-in without a professional base) do not apply to this client.
          questions: arr(section['questions'])
            .map(obj)
            .filter((q) => q['visible'] !== false)
            .map((q) => mapQuestion(q, (answer) => this.sourceOf(answer))),
        };
      }),
    };
  }

  async setAnswer(clientId: string, question: Question, value: string): Promise<void> {
    await this.request(`/api/clients/${clientId}/answers/${question.key}`, 'PUT', {
      value: answerPayload(question.type, value),
    });
  }

  async confirmAnswer(clientId: string, key: string): Promise<void> {
    await this.request(`/api/clients/${clientId}/answers/${key}/confirm`, 'POST');
  }

  async getAssessment(clientId: string): Promise<Assessment> {
    return mapAssessment(obj(await this.request(`/api/clients/${clientId}/assessment`)));
  }

  async setHedgeSettings(
    clientId: string,
    currency: string,
    settings: HedgeSettings,
  ): Promise<void> {
    await this.request(`/api/clients/${clientId}/hedge-settings/${currency}`, 'PUT', settings);
  }

  async setChecklistItem(clientId: string, itemId: string, done: boolean): Promise<void> {
    await this.request(`/api/clients/${clientId}/checklist/${itemId}`, 'PUT', { done });
  }

  async approve(clientId: string, assessmentVersion: number): Promise<void> {
    await this.request(`/api/clients/${clientId}/approval`, 'POST', { assessmentVersion });
  }

  async chat(
    clientId: string | null,
    message: string,
    onEvent: (event: ChatEvent) => void,
  ): Promise<void> {
    // The agent only answers within one client's documents; clientId is required by the contract.
    if (!clientId) throw new ApiError(this.translate.instant('chat.noClient'), 400);
    const response = await this.send('/agent/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
      body: JSON.stringify({
        clientId,
        conversationId: this.conversations.get(clientId) ?? null,
        message,
        context: { screen: screenOf(this.router.url) },
      }),
    });
    if (!isEventStreamResponse(response) || !response.body) {
      throw this.backendUnavailable(response.status);
    }
    this.dataMode.reportAvailable();
    const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
    let buffer = '';
    for (;;) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += value;
      const frames = buffer.split(/\r?\n\r?\n/);
      buffer = frames.pop() ?? '';
      frames.forEach((frame) => this.dispatchSseFrame(clientId, frame, onEvent));
    }
    if (buffer.trim()) this.dispatchSseFrame(clientId, buffer, onEvent);
    onEvent({ type: 'done' });
  }

  private async request(url: string, method = 'GET', body?: unknown): Promise<unknown> {
    const response = await this.send(url, {
      method,
      headers:
        body === undefined ? ACCEPT_JSON : { ...ACCEPT_JSON, 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    return this.readJson(response);
  }

  /**
   * Sends a request with the UI language and returns only successful responses. The backend's
   * problem details become an {@link ApiError}; network failures, 5xx and non-JSON error pages
   * become a {@link BackendUnavailableError}.
   */
  private async send(url: string, init: SendInit): Promise<Response> {
    let response: Response;
    try {
      response = await fetch(url, {
        ...init,
        // Lets the backend word rule texts in the UI language; it may ignore the header.
        headers: { 'Accept-Language': this.language.current(), ...init.headers },
      });
    } catch {
      throw this.backendUnavailable();
    }
    if (response.ok) return response;
    if (response.status >= 500 || !isJsonResponse(response)) {
      throw this.backendUnavailable(response.status);
    }
    const problem = obj(await response.json().catch(() => null));
    const detail = str(problem['detail'] ?? problem['title'], response.statusText);
    throw new ApiError(detail || `HTTP ${response.status}`, response.status);
  }

  /** An empty body is a valid answer; any other body must be JSON, never e.g. an SPA's index.html. */
  private async readJson(response: Response): Promise<unknown> {
    const text = await response.text();
    if (!text.trim()) return null;
    if (!isJsonResponse(response)) throw this.backendUnavailable(response.status);
    let body: unknown;
    try {
      body = JSON.parse(text);
    } catch {
      throw this.backendUnavailable(response.status);
    }
    this.dataMode.reportAvailable();
    return body;
  }

  private mapFigure(key: string, answer: Json): KeyFigure {
    const value = optNum(answer['value']);
    return {
      key,
      value,
      status: answerStatus(answer['status'], value !== null),
      origin: toCode(answer['source'], ANSWER_ORIGINS),
      source: this.sourceOf(answer),
    };
  }

  /**
   * Fundstelle of an answer: the citation's label, else document and page. Rule proposals explain
   * their origin in `note` («Vorschlag aus dem Betriebsaufwand …»), as the demo data does.
   */
  private sourceOf(answer: Json): string | null {
    const note = optStr(answer['note']);
    if (answer['source'] === 'RULE' && note) return note;
    return this.citationLabel(answer['citation']) ?? note;
  }

  private citationLabel(citation: unknown): string | null {
    const c = obj(citation);
    const label = optStr(c['locationLabel']);
    if (label) return label;
    const document = optStr(c['documentName']);
    const page = optNum(c['page']);
    if (!document) return null;
    return page === null
      ? document
      : `${document}, ${this.translate.instant('chat.page', { page })}`;
  }

  /** Reports the outage to the shell, which offers the switch to demo data, and returns the error to throw. */
  private backendUnavailable(status = 0): BackendUnavailableError {
    this.dataMode.reportUnavailable();
    return new BackendUnavailableError(this.translate.instant('errors.backendUnavailable'), status);
  }

  private dispatchSseFrame(
    clientId: string,
    frame: string,
    onEvent: (event: ChatEvent) => void,
  ): void {
    let eventName = 'message';
    const dataLines: string[] = [];
    for (const line of frame.split(/\r?\n/)) {
      if (line.startsWith('event:')) eventName = line.slice(6).trim();
      else if (line.startsWith('data:')) dataLines.push(line.slice(5).replace(/^ /, ''));
    }
    // Frames without data are keep-alive comments (`: ping`).
    if (!dataLines.length) return;
    const raw = dataLines.join('\n');
    let data: Json = {};
    try {
      data = obj(JSON.parse(raw));
    } catch {
      data = { text: raw };
    }
    switch (eventName) {
      case 'meta': {
        const conversationId = optStr(data['conversationId']);
        if (conversationId) this.conversations.set(clientId, conversationId);
        break;
      }
      case 'token':
      case 'message':
        onEvent({ type: 'token', text: str(data['text'] ?? data['delta'] ?? data['content']) });
        break;
      case 'citation':
        onEvent({
          type: 'citation',
          citation: {
            id: optStr(data['sourceId']),
            document: str(
              data['document'] ?? data['documentName'] ?? data['fileName'] ?? data['title'],
              this.translate.instant('chat.fallbackDocument'),
            ),
            location: str(
              data['locationLabel'] ??
                data['location'] ??
                data['label'] ??
                (data['page'] !== undefined
                  ? this.translate.instant('chat.page', { page: str(data['page']) })
                  : undefined),
              '–',
            ),
          },
        });
        break;
      case 'proposal': {
        // The agent proposes answers (propose_answer), already recorded at treasury-service.
        const label = str(data['questionLabel'] ?? data['questionKey']);
        if (!label) break;
        const outcome = toCode(data['outcome'], PROPOSAL_OUTCOMES) ?? 'OPEN';
        onEvent({
          type: 'proposal',
          proposal: {
            kind: 'answer',
            title: `${label}: ${str(data['valueLabel'] ?? data['value'], '–')}`,
            detail: this.translate.instant(`chat.answerOutcome.${outcome}`),
          },
        });
        break;
      }
      case 'error':
        onEvent({
          type: 'error',
          message: str(
            data['detail'] ?? data['message'] ?? data['text'],
            this.translate.instant('errors.agentFailed'),
          ),
        });
        break;
      default:
        break;
    }
  }
}

/** Unconfirmed values without an explicit status count as proposals, so they still need confirming. */
function answerStatus(status: unknown, hasValue: boolean): AnswerStatus {
  return toCode(status, ANSWER_STATUSES) ?? (hasValue ? 'PROPOSED' : 'MISSING');
}

/** The backend expects JSON numbers for amounts and percentages and booleans for yes/no questions. */
function answerPayload(type: QuestionType, value: string): string | number | boolean {
  switch (type) {
    case 'AMOUNT':
    case 'PERCENT':
      return Number(value);
    case 'BOOLEAN':
      return value === 'true';
    default:
      return value;
  }
}

function findAnswer(questionnaire: unknown, key: string): Json | null {
  for (const section of arr(obj(questionnaire)['sections'])) {
    const question = arr(obj(section)['questions'])
      .map(obj)
      .find((q) => q['key'] === key);
    if (question) return question['answer'] ? obj(question['answer']) : null;
  }
  return null;
}

function mapSummary(c: Json): ClientSummary {
  return {
    id: str(c['id']),
    name: str(c['name']),
    shortLabel: optStr(c['shortLabel'] ?? c['short']),
    industry: optStr(c['industry'] ?? c['branche']),
    employees: optNum(c['employeeCount'] ?? c['employees']),
    segment: toCode(obj(c['segment'])['result'] ?? c['segment'], CLIENT_SEGMENTS),
    riskProfile: toCode(obj(c['riskProfile'])['code'] ?? c['riskProfile'], RISK_PROFILE_CODES),
    documentCount: num(c['documentCount']),
    openItems: num(c['openItemCount'] ?? c['openItems'] ?? c['openPoints']),
    approvalStatus: c['approvalStatus'] === 'APPROVED' ? 'APPROVED' : 'NOT_APPROVED',
  };
}

function mapDocument(d: Json): ClientDocument {
  return {
    id: str(d['id']),
    fileName: str(d['filename'] ?? d['fileName'] ?? d['name']),
    status: str(d['status'], 'UPLOADED') as DocumentStatus,
    pageCount: optNum(d['pageCount']),
  };
}

function mapQuestion(q: Json, sourceOf: (answer: Json) => string | null): Question {
  const answer = obj(q['answer']);
  const value = q['value'] ?? answer['value'];
  return {
    key: str(q['key']),
    text: str(q['text'] ?? q['label'], str(q['key'])),
    hint: optStr(q['hint']),
    unit: optStr(q['unit']),
    type: str(q['type'], 'AMOUNT') as QuestionType,
    options: arr(q['options']).map((o) => {
      const option = obj(o);
      return typeof o === 'object'
        ? { value: str(option['value']), label: str(option['label'] ?? option['value']) }
        : { value: str(o), label: str(o) };
    }),
    value: value === undefined || value === null ? null : str(value),
    status: answerStatus(q['status'] ?? answer['status'], value !== undefined && value !== null),
    origin: toCode(answer['source'], ANSWER_ORIGINS),
    source: sourceOf(answer),
  };
}

function mapAssessment(a: Json): Assessment {
  const segment = obj(a['segment']);
  const profile = obj(a['riskProfile'] ?? a['profile']);
  const capacity = obj(profile['capacity'] ?? a['capacity']);
  const willingness = obj(profile['willingness'] ?? a['willingness']);
  const factors = (v: unknown) =>
    arr(v).map((f) => {
      const x = obj(f);
      return {
        label: str(x['label'] ?? x['name']),
        value: str(x['displayValue'] ?? x['answerLabel'] ?? x['value']),
        score: num(x['score']),
      };
    });
  const hedge = obj(a['hedges'] ?? a['hedge']);
  const liquidity = obj(a['liquidity']);
  const approval = obj(a['approval']);
  const blockers = arr(approval['blockers']).map(obj);
  return {
    version: num(a['version'] ?? a['assessmentVersion'], 1),
    segment: {
      code: toCode(segment['result'], CLIENT_SEGMENTS),
      reason: str(segment['reason']),
      legalBasis: str(segment['legalBasis']),
      thresholdsExceeded: num(segment['thresholdsExceeded']),
    },
    capacity: { score: num(capacity['score']), factors: factors(capacity['factors']) },
    willingness: { score: num(willingness['score']), factors: factors(willingness['factors']) },
    profile: { score: num(profile['score']), code: toCode(profile['code'], RISK_PROFILE_CODES) },
    exposures: arr(
      obj(a['exposures'])['fx'] ?? obj(a['exposures'])['currencies'] ?? a['exposures'],
    ).map((e) => {
      const x = obj(e);
      return {
        currency: str(x['currency']),
        netPosition: num(x['netPosition']),
        netPositionChf: num(x['netPositionChf']),
        sensitivityChf: num(x['sensitivityChf']),
        sensitivityPctEbit: num(x['sensitivityPctEbit']),
        riskLevel: str(x['riskLevel'], 'LOW') as 'LOW' | 'MEDIUM' | 'HIGH',
        direction: toCode(x['direction'], EXPOSURE_DIRECTIONS) ?? 'NONE',
      };
    }),
    suitability: arr(a['suitability']).flatMap((s): ProductSuitability[] => {
      const x = obj(s);
      const product = toCode(x['product'], PRODUCT_CODES);
      if (!product) return [];
      return [
        {
          product,
          knowledge: toCode(x['knowledgeLevel'] ?? x['knowledge'], KNOWLEDGE_LEVELS),
          knowledgePresumed: Boolean(x['knowledgePresumed']),
          suitability: toCode(x['status'] ?? x['result'], SUITABILITY_CODES) ?? 'NOT_APPLICABLE',
          note: optStr(x['note']),
          kidRequired: Boolean(x['kidRequired']),
        },
      ];
    }),
    hedges: arr(hedge['proposals'] ?? hedge['currencies'] ?? a['hedges']).map((h) => {
      const x = obj(h);
      const tranches = arr(x['tranches']);
      const ratios = arr(x['ratios'] ?? tranches.map((t) => obj(t)['ratio'])).map((r) => num(r));
      const scenario = scenarioAtMinusTen(x['scenario']);
      return {
        currency: str(x['currency']),
        action: str(x['actionLabel'] ?? x['action'] ?? x['hedgeAction']),
        cap: num(hedge['cap'] ?? x['cap']),
        startRatio: num(x['startRatio'], ratios[0] ?? 0),
        stepDown: num(x['stepDown'], ratios.length > 1 ? ratios[0] - ratios[1] : 0),
        ratios,
        averageRatio: num(x['averageRatio']),
        hedgedAmountFx: num(x['hedgedAmountFx']),
        hedgedAmountChf: num(x['hedgedAmountChf']),
        carryChf: num(x['carryChf']),
        limitedByPolicy: Boolean(x['limitedByPolicy']),
        collarShare: x['collarEnabled'] ? num(x['collarShare']) : null,
        scenarioUnhedgedChf: optNum(scenario['unhedgedChf']),
        scenarioHedgedChf: optNum(scenario['hedgedChf']),
      };
    }),
    liquidity: {
      excess: num(liquidity['excessLiquidity'] ?? liquidity['excess']),
      parts: arr(liquidity['parts'] ?? liquidity['allocation']).flatMap((p) => {
        const x = obj(p);
        const product = toCode(x['product'], PRODUCT_CODES);
        if (!product) return [];
        return [
          {
            product,
            share: num(x['sharePct'] ?? x['share']),
            amount: num(x['amountChf'] ?? x['amount']),
            suitability:
              toCode(x['suitabilityStatus'] ?? x['suitability'], SUITABILITY_CODES) ??
              'NOT_APPLICABLE',
          },
        ];
      }),
    },
    checklist: arr(a['checklist']).map((i) => ({
      id: str(obj(i)['id']),
      text: str(obj(i)['text']),
      legalBasis: str(obj(i)['legalBasis'] ?? obj(i)['ref']),
      done: Boolean(obj(i)['done']),
    })),
    unconfirmedAnswers: blockers.filter((b) => b['type'] === 'UNCONFIRMED_ANSWER').length,
    otherBlockers: blockers
      .filter((b) => !COUNTED_BLOCKERS.includes(str(b['type'])))
      .map((b) => str(b['text']))
      .filter((text) => text !== ''),
    approvalStatus:
      str(approval['status'] ?? a['approvalStatus']) === 'APPROVED' ? 'APPROVED' : 'NOT_APPROVED',
  };
}

/** The contract delivers the scenario as points from −15 % to +15 %; the UI shows the −10 % case. */
function scenarioAtMinusTen(scenario: unknown): Json {
  if (!Array.isArray(scenario)) return obj(scenario);
  return obj(scenario.find((point) => num(obj(point)['shiftPct'], 0) === -10));
}
