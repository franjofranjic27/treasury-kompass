import { Injectable } from '@angular/core';
import { FIGURE_LABELS, QUESTIONS, SECTIONS } from './catalog';
import { ClientSeed, SEEDS, SeedAnswer } from './mock-data';
import { scriptChatAnswer } from './mock-chat';
import {
  Assessment,
  ChatEvent,
  ClientDetail,
  ClientDocument,
  ClientSummary,
  DocumentStatus,
  HedgeProposal,
  HedgeSettings,
  Questionnaire
} from './models';
import { ApiError, TreasuryApi } from './treasury-api';

interface MockClient {
  readonly summary: Omit<ClientSummary, 'documentCount' | 'openItems' | 'approvalStatus'>;
  readonly seed: ClientSeed | null;
  readonly answers: Map<string, SeedAnswer>;
  documents: ClientDocument[];
  readonly checklistDone: Set<string>;
  readonly hedgeSettings: Map<string, HedgeSettings>;
  approved: boolean;
  version: number;
}

const STATUS_FLOW: readonly DocumentStatus[] = ['UPLOADED', 'TEXT_EXTRACTED', 'INDEXED', 'EXTRACTED'];
const STEP_MS = 1200;

const delay = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

/** In-memory implementation; state lives until the page is reloaded. */
@Injectable()
export class MockTreasuryApi extends TreasuryApi {
  private readonly clients = new Map<string, MockClient>(SEEDS.map((seed) => [seed.id, fromSeed(seed)]));

  async listClients(query: string): Promise<readonly ClientSummary[]> {
    const needle = query.trim().toLowerCase();
    return [...this.clients.values()]
      .filter((c) => c.summary.name.toLowerCase().includes(needle))
      .map((c) => this.toSummary(c));
  }

  async createClient(name: string): Promise<ClientSummary> {
    const trimmed = name.trim();
    if (!trimmed) throw new ApiError('Name ist Pflicht.', 400);
    if ([...this.clients.values()].some((c) => c.summary.name.toLowerCase() === trimmed.toLowerCase())) {
      throw new ApiError(`Kunde «${trimmed}» existiert bereits.`, 409);
    }
    const id = `kunde-${Date.now()}`;
    const client: MockClient = {
      summary: { id, name: trimmed, shortLabel: 'Neu', industry: '–', employees: '–', segment: null, riskProfile: null },
      seed: null,
      answers: new Map(),
      documents: [],
      checklistDone: new Set(),
      hedgeSettings: new Map(),
      approved: false,
      version: 1
    };
    this.clients.set(id, client);
    return this.toSummary(client);
  }

  async getClient(id: string): Promise<ClientDetail> {
    const client = this.require(id);
    const number = (key: string) => {
      const answer = client.answers.get(key);
      return answer ? Number(answer.value) : null;
    };
    return {
      ...this.toSummary(client),
      figures: Object.entries(FIGURE_LABELS).map(([key, label]) => ({
        key,
        label,
        value: number(key),
        source: client.answers.get(key)?.source ?? null
      })),
      fxFlows: ['eur', 'usd']
        .filter((ccy) => client.answers.has(`fx.${ccy}.inflow`))
        .map((ccy) => ({
          currency: ccy.toUpperCase(),
          inflow: number(`fx.${ccy}.inflow`) ?? 0,
          outflow: number(`fx.${ccy}.outflow`) ?? 0,
          source: client.answers.get(`fx.${ccy}.inflow`)?.source ?? null
        })),
      documents: client.documents,
      minimumLiquidity: number('goals.minimumLiquidity')
    };
  }

  async uploadDocument(clientId: string, file: File, onUpdate: (document: ClientDocument) => void): Promise<void> {
    const client = this.require(clientId);
    const id = `doc-${Date.now()}`;
    for (const status of STATUS_FLOW) {
      const document: ClientDocument = { id, fileName: file.name, status, pageCount: status === 'UPLOADED' ? null : 12 };
      client.documents = [...client.documents.filter((d) => d.id !== id), document];
      onUpdate(document);
      if (status !== 'EXTRACTED') await delay(STEP_MS);
    }
  }

  async getQuestionnaire(clientId: string): Promise<Questionnaire> {
    const client = this.require(clientId);
    return {
      sections: SECTIONS.map((section) => ({
        ...section,
        questions: QUESTIONS.filter((q) => q.section === section.id).map((q) => {
          const answer = client.answers.get(q.key);
          return {
            key: q.key,
            text: q.text,
            hint: q.hint ?? null,
            type: q.type,
            options: q.options ?? [],
            value: answer?.value ?? null,
            status: answer?.status ?? 'MISSING',
            source: answer?.source ?? null
          };
        })
      }))
    };
  }

  async setAnswer(clientId: string, key: string, value: string): Promise<void> {
    const client = this.require(clientId);
    const previous = client.answers.get(key);
    client.answers.set(key, { value, status: 'CONFIRMED', source: previous?.source ?? 'Berater' });
    if (previous?.value !== value) this.revoke(client);
  }

  async confirmAnswer(clientId: string, key: string): Promise<void> {
    const client = this.require(clientId);
    const answer = client.answers.get(key);
    if (!answer) throw new ApiError('Antwort fehlt, bitte zuerst einen Wert erfassen.', 409);
    client.answers.set(key, { ...answer, status: 'CONFIRMED' });
  }

  async getAssessment(clientId: string): Promise<Assessment> {
    const client = this.require(clientId);
    if (!client.seed) {
      throw new ApiError('Noch keine Beurteilung: Unterlagen hochladen und Fragebogen ausfüllen.', 404);
    }
    return {
      ...client.seed.assessment,
      version: client.version,
      hedges: client.seed.assessment.hedges.map((h) => applySettings(h, client.hedgeSettings.get(h.currency))),
      checklist: client.seed.checklist.map((item) => ({ ...item, done: client.checklistDone.has(item.id) })),
      unconfirmedAnswers: this.unconfirmed(client),
      approvalStatus: client.approved ? 'APPROVED' : 'NOT_APPROVED'
    };
  }

  async setHedgeSettings(clientId: string, currency: string, settings: HedgeSettings): Promise<void> {
    const client = this.require(clientId);
    if (settings.startRatio < 0 || settings.startRatio > 100) {
      throw new ApiError('Startquote muss zwischen 0 und 100 % liegen.', 400);
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
    if (assessmentVersion !== client.version) throw new ApiError('Beurteilung wurde inzwischen geändert.', 409);
    const open = assessment.checklist.filter((i) => !i.done).length;
    if (assessment.unconfirmedAnswers > 0 || open > 0) {
      throw new ApiError(`Freigabe blockiert: ${assessment.unconfirmedAnswers} unbestätigte Antworten, ${open} offene Punkte.`, 409);
    }
    client.approved = true;
  }

  async chat(clientId: string | null, message: string, onEvent: (event: ChatEvent) => void): Promise<void> {
    const seed = clientId ? (this.clients.get(clientId)?.seed ?? null) : null;
    for (const event of scriptChatAnswer(seed, message)) {
      if (event.type === 'token') {
        for (const word of event.text.split(/(?<= )/)) {
          await delay(18);
          onEvent({ type: 'token', text: word });
        }
      } else {
        await delay(120);
        onEvent(event);
      }
    }
  }

  private require(id: string): MockClient {
    const client = this.clients.get(id);
    if (!client) throw new ApiError(`Kunde ${id} nicht gefunden.`, 404);
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
    const openChecklist = client.seed ? client.seed.checklist.filter((i) => !client.checklistDone.has(i.id)).length : 0;
    return {
      ...client.summary,
      documentCount: client.documents.length,
      openItems: this.unconfirmed(client) + openChecklist,
      approvalStatus: client.approved ? 'APPROVED' : 'NOT_APPROVED'
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
      riskProfile: seed.riskProfile
    },
    seed,
    answers: new Map(Object.entries(seed.answers)),
    documents: [{ id: `${seed.id}-doc-1`, fileName: seed.document.fileName, status: 'EXTRACTED', pageCount: seed.document.pageCount }],
    checklistDone: new Set(),
    hedgeSettings: new Map(),
    approved: false,
    version: 1
  };
}

/** Approximation for the demo: tranche ratios follow the settings, volumes scale with the average ratio. */
function applySettings(base: HedgeProposal, settings: HedgeSettings | undefined): HedgeProposal {
  if (!settings) return base;
  const effectiveStart = Math.min(settings.startRatio, base.cap);
  const ratios = base.ratios.map((_, i) => Math.max(0, effectiveStart - i * settings.stepDown));
  const averageRatio = ratios.reduce((sum, r) => sum + r, 0) / ratios.length;
  const factor = base.averageRatio === 0 ? 0 : averageRatio / base.averageRatio;
  return {
    ...base,
    ratios,
    averageRatio,
    hedgedAmountFx: round(base.hedgedAmountFx * factor, 3),
    carryChf: round(base.carryChf * factor, 4),
    limitedByPolicy: settings.startRatio > base.cap,
    scenarioHedgedChf:
      base.scenarioHedgedChf === null || base.scenarioUnhedgedChf === null
        ? null
        : round(base.scenarioUnhedgedChf - (base.scenarioUnhedgedChf - base.scenarioHedgedChf) * factor, 4)
  };
}

function round(value: number, decimals: number): number {
  const f = 10 ** decimals;
  return Math.round(value * f) / f;
}
