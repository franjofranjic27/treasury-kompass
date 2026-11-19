import { Injectable, inject } from '@angular/core';
import { FIGURE_KEYS, QUESTIONS, SECTIONS } from './catalog';
import { delay } from './delay';
import { LanguageService } from './i18n/language';
import { Localizable, Localized, l, resolveLocalized } from './i18n/localized';
import { scriptChatAnswer } from './mock-chat';
import { AssessmentSeed, ClientSeed, SEEDS, SeedAnswer } from './mock-data';
import {
  Assessment,
  ChatEvent,
  ClientDetail,
  ClientDocument,
  ClientSummary,
  DOCUMENT_PIPELINE,
  HedgeProposal,
  HedgeSettings,
  KeyFigure,
  Question,
  Questionnaire,
  fxFlowOf,
} from './models';
import { ApiError, TreasuryApi } from './treasury-api';

type SummaryBase = Omit<ClientSummary, 'documentCount' | 'openItems' | 'approvalStatus'>;

interface MockClient {
  readonly summary: Localizable<SummaryBase>;
  readonly seed: ClientSeed | null;
  readonly answers: Map<string, SeedAnswer>;
  documents: ClientDocument[];
  readonly checklistDone: Set<string>;
  readonly hedgeSettings: Map<string, HedgeSettings>;
  approved: boolean;
  version: number;
}

/** Simulated latencies; fixed values keep the demo (and its E2E tests) deterministic. */
const STEP_MS = 1000;
const TOKEN_MS = 18;
const EVENT_MS = 120;

/**
 * In-memory implementation; state lives until the page is reloaded. Like a backend that honours
 * Accept-Language, it words rule texts, citations and errors in the active UI language.
 */
@Injectable()
export class MockTreasuryApi extends TreasuryApi {
  private readonly language = inject(LanguageService);
  private readonly clients = new Map<string, MockClient>(
    SEEDS.map((seed) => [seed.id, fromSeed(seed)]),
  );

  async listClients(query: string): Promise<readonly ClientSummary[]> {
    const needle = query.trim().toLowerCase();
    return [...this.clients.values()]
      .map((c) => this.toSummary(c))
      .filter((c) => c.name.toLowerCase().includes(needle));
  }

  async createClient(name: string): Promise<ClientSummary> {
    const trimmed = name.trim();
    if (!trimmed) this.fail(l('Name ist Pflicht.', 'Name is required.'), 400);
    const exists = [...this.clients.values()].some(
      (c) => this.toSummary(c).name.toLowerCase() === trimmed.toLowerCase(),
    );
    if (exists) {
      this.fail(
        l(`Kunde «${trimmed}» existiert bereits.`, `Client “${trimmed}” already exists.`),
        409,
      );
    }
    const id = `kunde-${Date.now()}`;
    const client: MockClient = {
      summary: {
        id,
        name: trimmed,
        shortLabel: null,
        industry: null,
        employees: null,
        segment: null,
        riskProfile: null,
      },
      seed: null,
      answers: new Map(),
      documents: [],
      checklistDone: new Set(),
      hedgeSettings: new Map(),
      approved: false,
      version: 1,
    };
    this.clients.set(id, client);
    return this.toSummary(client);
  }

  async getClient(id: string): Promise<ClientDetail> {
    const client = this.require(id);
    const figure = (key: string): KeyFigure => {
      const answer = client.answers.get(key);
      return {
        key,
        value: answer ? Number(answer.value) : null,
        status: answer?.status ?? 'MISSING',
        origin: answer?.origin ?? null,
        source: this.textOrNull(answer?.source ?? null),
      };
    };
    return {
      ...this.toSummary(client),
      figures: FIGURE_KEYS.map(figure),
      fxFlows: ['eur', 'usd']
        .filter((ccy) => client.answers.has(`fx.${ccy}.inflow`))
        .map((ccy) =>
          fxFlowOf(ccy.toUpperCase(), figure(`fx.${ccy}.inflow`), figure(`fx.${ccy}.outflow`)),
        ),
      documents: client.documents,
      minimumLiquidity: client.answers.has('goals.minimumLiquidity')
        ? figure('goals.minimumLiquidity')
        : null,
    };
  }

  async uploadDocument(
    clientId: string,
    file: File,
    onUpdate: (document: ClientDocument) => void,
  ): Promise<void> {
    const client = this.require(clientId);
    const id = `doc-${Date.now()}`;
    for (const status of DOCUMENT_PIPELINE) {
      const document: ClientDocument = {
        id,
        fileName: file.name,
        status,
        pageCount: status === 'UPLOADED' ? null : 12,
      };
      client.documents = [...client.documents.filter((d) => d.id !== id), document];
      onUpdate(document);
      if (status !== 'EXTRACTED') await delay(STEP_MS);
    }
  }

  async getQuestionnaire(clientId: string): Promise<Questionnaire> {
    const client = this.require(clientId);
    return {
      sections: SECTIONS.map((section) => ({
        id: section.id,
        title: this.text(section.title),
        legalBasis: this.text(section.legalBasis),
        questions: QUESTIONS.filter((q) => q.section === section.id).map((q) => {
          const answer = client.answers.get(q.key);
          return {
            key: q.key,
            text: this.text(q.text),
            hint: this.textOrNull(q.hint ?? null),
            unit: this.textOrNull(q.unit ?? null),
            type: q.type,
            options: (q.options ?? []).map((o) => ({ value: o.value, label: this.text(o.label) })),
            value: answer?.value ?? null,
            status: answer?.status ?? 'MISSING',
            origin: answer?.origin ?? null,
            source: this.textOrNull(answer?.source ?? null),
          };
        }),
      })),
    };
  }

  async setAnswer(clientId: string, question: Question, value: string): Promise<void> {
    const client = this.require(clientId);
    const key = question.key;
    const previous = client.answers.get(key);
    const changed = previous?.value !== value;
    client.answers.set(key, {
      value,
      status: 'CONFIRMED',
      origin: changed ? 'ADVISOR' : (previous?.origin ?? 'ADVISOR'),
      source: previous?.source ?? null,
    });
    if (changed) this.revoke(client);
  }

  async confirmAnswer(clientId: string, key: string): Promise<void> {
    const client = this.require(clientId);
    const answer = client.answers.get(key);
    if (!answer) {
      this.fail(
        l(
          'Antwort fehlt, bitte zuerst einen Wert erfassen.',
          'Answer missing, please enter a value first.',
        ),
        409,
      );
    }
    client.answers.set(key, { ...answer, status: 'CONFIRMED' });
  }

  async getAssessment(clientId: string): Promise<Assessment> {
    const client = this.require(clientId);
    if (!client.seed) {
      this.fail(
        l(
          'Noch keine Beurteilung: Unterlagen hochladen und Fragebogen ausfüllen.',
          'No assessment yet: upload documents and complete the questionnaire.',
        ),
        404,
      );
    }
    const assessment = resolveLocalized<AssessmentSeed>(
      client.seed.assessment,
      this.language.current(),
    );
    return {
      ...assessment,
      version: client.version,
      hedges: assessment.hedges.map((h) => applySettings(h, client.hedgeSettings.get(h.currency))),
      checklist: client.seed.checklist.map((item) => ({
        id: item.id,
        text: this.text(item.text),
        legalBasis: this.text(item.legalBasis),
        done: client.checklistDone.has(item.id),
      })),
      unconfirmedAnswers: this.unconfirmed(client),
      otherBlockers: [],
      approvalStatus: client.approved ? 'APPROVED' : 'NOT_APPROVED',
    };
  }

  async setHedgeSettings(
    clientId: string,
    currency: string,
    settings: HedgeSettings,
  ): Promise<void> {
    const client = this.require(clientId);
    if (settings.startRatio < 0 || settings.startRatio > 100) {
      this.fail(
        l(
          'Startquote muss zwischen 0 und 100 % liegen.',
          'Start ratio must be between 0 and 100%.',
        ),
        400,
      );
    }
    client.hedgeSettings.set(currency, settings);
    this.revoke(client);
  }

  async setChecklistItem(clientId: string, itemId: string, done: boolean): Promise<void> {
    const client = this.require(clientId);
    if (done) {
      client.checklistDone.add(itemId);
    } else {
      client.checklistDone.delete(itemId);
      this.revoke(client);
    }
  }

  async approve(clientId: string, assessmentVersion: number): Promise<void> {
    const client = this.require(clientId);
    const assessment = await this.getAssessment(clientId);
    if (assessmentVersion !== client.version) {
      this.fail(
        l('Beurteilung wurde inzwischen geändert.', 'The assessment has changed in the meantime.'),
        409,
      );
    }
    const open = assessment.checklist.filter((i) => !i.done).length;
    const unconfirmed = assessment.unconfirmedAnswers;
    if (unconfirmed > 0 || open > 0) {
      this.fail(
        l(
          `Freigabe blockiert: ${unconfirmed} unbestätigte Antworten, ${open} offene Punkte.`,
          `Approval blocked: ${unconfirmed} unconfirmed answers, ${open} open items.`,
        ),
        409,
      );
    }
    client.approved = true;
  }

  async chat(
    clientId: string | null,
    message: string,
    onEvent: (event: ChatEvent) => void,
  ): Promise<void> {
    const seed = clientId ? (this.clients.get(clientId)?.seed ?? null) : null;
    for (const event of scriptChatAnswer(seed, message, this.language.current())) {
      if (event.type === 'token') {
        for (const word of event.text.split(/(?<= )/)) {
          await delay(TOKEN_MS);
          onEvent({ type: 'token', text: word });
        }
      } else {
        await delay(EVENT_MS);
        onEvent(event);
      }
    }
  }

  private text(value: Localized): string {
    return value[this.language.current()];
  }

  private textOrNull(value: Localized | null): string | null {
    return value ? this.text(value) : null;
  }

  private fail(message: Localized, status: number): never {
    throw new ApiError(this.text(message), status);
  }

  private require(id: string): MockClient {
    const client = this.clients.get(id);
    if (!client) this.fail(l(`Kunde ${id} nicht gefunden.`, `Client ${id} not found.`), 404);
    return client;
  }

  private revoke(client: MockClient): void {
    client.version += 1;
    client.approved = false;
  }

  private unconfirmed(client: MockClient): number {
    return QUESTIONS.filter((q) => client.answers.get(q.key)?.status !== 'CONFIRMED').length;
  }

  private toSummary(client: MockClient): ClientSummary {
    const openChecklist = client.seed
      ? client.seed.checklist.filter((i) => !client.checklistDone.has(i.id)).length
      : 0;
    return {
      ...resolveLocalized<SummaryBase>(client.summary, this.language.current()),
      documentCount: client.documents.length,
      openItems: this.unconfirmed(client) + openChecklist,
      approvalStatus: client.approved ? 'APPROVED' : 'NOT_APPROVED',
    };
  }
}

function fromSeed(seed: ClientSeed): MockClient {
  return {
    summary: {
      id: seed.id,
      name: seed.name,
      shortLabel: seed.shortLabel,
      industry: seed.industry,
      employees: seed.employees,
      segment: seed.segment,
      riskProfile: seed.riskProfile,
    },
    seed,
    answers: new Map(Object.entries(seed.answers)),
    documents: [
      {
        id: `${seed.id}-doc-1`,
        fileName: seed.document.fileName,
        status: 'EXTRACTED',
        pageCount: seed.document.pageCount,
      },
    ],
    checklistDone: new Set(),
    hedgeSettings: new Map(),
    approved: false,
    version: 1,
  };
}

/** Approximation for the demo: tranche ratios follow the settings, volumes scale with the average ratio. */
function applySettings(base: HedgeProposal, settings: HedgeSettings | undefined): HedgeProposal {
  if (!settings) return base;
  const startRatio = Math.min(settings.startRatio, base.cap);
  const ratios = base.ratios.map((_, i) => Math.max(0, startRatio - i * settings.stepDown));
  const averageRatio = ratios.reduce((sum, r) => sum + r, 0) / ratios.length;
  const factor = base.averageRatio === 0 ? 0 : averageRatio / base.averageRatio;
  return {
    ...base,
    startRatio,
    stepDown: settings.stepDown,
    ratios,
    averageRatio,
    hedgedAmountFx: round(base.hedgedAmountFx * factor, 3),
    hedgedAmountChf: round(base.hedgedAmountChf * factor, 4),
    carryChf: round(base.carryChf * factor, 4),
    limitedByPolicy: settings.startRatio > base.cap,
    scenarioHedgedChf:
      base.scenarioHedgedChf === null || base.scenarioUnhedgedChf === null
        ? null
        : round(
            base.scenarioUnhedgedChf - (base.scenarioUnhedgedChf - base.scenarioHedgedChf) * factor,
            4,
          ),
  };
}

function round(value: number, decimals: number): number {
  const f = 10 ** decimals;
  return Math.round(value * f) / f;
}
