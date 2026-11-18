import { Injectable } from '@angular/core';
import { FIGURE_LABELS } from './catalog';
import {
  AnswerStatus,
  Assessment,
  ChatEvent,
  ClientDetail,
  ClientDocument,
  ClientSummary,
  DocumentStatus,
  HedgeSettings,
  Question,
  Questionnaire,
  QuestionType
} from './models';
import { ApiError, TreasuryApi } from './treasury-api';

type Json = Record<string, unknown>;

const POLL_MS = 2000;
const TERMINAL: readonly DocumentStatus[] = ['EXTRACTED', 'FAILED'];

const obj = (v: unknown): Json => (v !== null && typeof v === 'object' ? (v as Json) : {});
const arr = (v: unknown): unknown[] => (Array.isArray(v) ? v : []);
const str = (v: unknown, fallback = ''): string => (v === null || v === undefined ? fallback : String(v));
const num = (v: unknown, fallback = 0): number => (typeof v === 'number' ? v : Number(v ?? fallback) || fallback);
const optNum = (v: unknown): number | null => (v === null || v === undefined || v === '' ? null : Number(v));
const delay = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

/**
 * HTTP client for treasury-service (/api) and agent-service (/agent). DTOs are mapped loosely
 * because the backend contract is still evolving; unknown fields fall back to neutral defaults.
 */
@Injectable()
export class HttpTreasuryApi extends TreasuryApi {
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
    const [client, documents] = await Promise.all([
      this.request(`/api/clients/${id}`),
      this.request(`/api/clients/${id}/documents`).catch(() => [])
    ]);
    const c = obj(client);
    const fin = obj(c['financials'] ?? c['fin']);
    const fx = obj(c['fxFlows'] ?? c['fx']);
    return {
      ...mapSummary(c),
      figures: Object.entries(FIGURE_LABELS).map(([key, label]) => {
        const shortKey = key.replace('fin.', '');
        const raw = fin[shortKey] ?? c[key];
        const entry = obj(raw);
        return {
          key,
          label,
          value: typeof raw === 'object' ? optNum(entry['value']) : optNum(raw),
          source: typeof raw === 'object' ? str(entry['source'] ?? entry['citation'], '') || null : null
        };
      }),
      fxFlows: Object.entries(fx).map(([ccy, flow]) => ({
        currency: ccy.toUpperCase(),
        inflow: num(obj(flow)['inflow']),
        outflow: num(obj(flow)['outflow']),
        source: str(obj(flow)['source'], '') || null
      })),
      documents: arr(Array.isArray(documents) ? documents : obj(documents)['content']).map((d) => mapDocument(obj(d))),
      minimumLiquidity: optNum(obj(c['goals'])['minimumLiquidity'])
    };
  }

  async uploadDocument(clientId: string, file: File, onUpdate: (document: ClientDocument) => void): Promise<void> {
    const form = new FormData();
    form.append('file', file);
    const response = await fetch(`/api/clients/${clientId}/documents`, { method: 'POST', body: form });
    await ensureOk(response);
    let document = mapDocument(obj(await response.json()));
    onUpdate(document);
    while (!TERMINAL.includes(document.status)) {
      await delay(POLL_MS);
      const list = arr(await this.request(`/api/clients/${clientId}/documents`));
      const match = list.map((d) => mapDocument(obj(d))).find((d) => d.id === document.id);
      if (!match) throw new ApiError('Dokument nicht mehr vorhanden.', 404);
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
          questions: arr(section['questions']).map((q) => mapQuestion(obj(q)))
        };
      })
    };
  }

  async setAnswer(clientId: string, key: string, value: string): Promise<void> {
    await this.request(`/api/clients/${clientId}/answers/${key}`, 'PUT', { value });
  }

  async confirmAnswer(clientId: string, key: string): Promise<void> {
    await this.request(`/api/clients/${clientId}/answers/${key}/confirm`, 'POST');
  }

  async getAssessment(clientId: string): Promise<Assessment> {
    return mapAssessment(obj(await this.request(`/api/clients/${clientId}/assessment`)));
  }

  async setHedgeSettings(clientId: string, currency: string, settings: HedgeSettings): Promise<void> {
    await this.request(`/api/clients/${clientId}/hedge-settings/${currency}`, 'PUT', settings);
  }

  async setChecklistItem(clientId: string, itemId: string, done: boolean): Promise<void> {
    await this.request(`/api/clients/${clientId}/checklist/${itemId}`, 'PUT', { done });
  }

  async approve(clientId: string, assessmentVersion: number): Promise<void> {
    await this.request(`/api/clients/${clientId}/approval`, 'POST', { assessmentVersion });
  }

  async chat(clientId: string | null, message: string, onEvent: (event: ChatEvent) => void): Promise<void> {
    const response = await fetch('/agent/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
      body: JSON.stringify({ clientId, message, context: { route: location.pathname } })
    });
    await ensureOk(response);
    if (!response.body) throw new ApiError('Keine Antwort vom Agenten.', 502);
    const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
    let buffer = '';
    for (;;) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += value;
      const frames = buffer.split(/\r?\n\r?\n/);
      buffer = frames.pop() ?? '';
      frames.forEach((frame) => dispatchSseFrame(frame, onEvent));
    }
    if (buffer.trim()) dispatchSseFrame(buffer, onEvent);
    onEvent({ type: 'done' });
  }

  private async request(url: string, method = 'GET', body?: unknown): Promise<unknown> {
    const response = await fetch(url, {
      method,
      headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body)
    });
    await ensureOk(response);
    const text = await response.text();
    return text ? JSON.parse(text) : null;
  }
}

async function ensureOk(response: Response): Promise<void> {
  if (response.ok) return;
  let detail = response.statusText;
  try {
    const problem = obj(await response.json());
    detail = str(problem['detail'] ?? problem['title'], detail);
  } catch {
    // Body is not JSON (e.g. proxy error page); keep the status text.
  }
  throw new ApiError(detail || `HTTP ${response.status}`, response.status);
}

function dispatchSseFrame(frame: string, onEvent: (event: ChatEvent) => void): void {
  let eventName = 'message';
  const dataLines: string[] = [];
  for (const line of frame.split(/\r?\n/)) {
    if (line.startsWith('event:')) eventName = line.slice(6).trim();
    else if (line.startsWith('data:')) dataLines.push(line.slice(5).replace(/^ /, ''));
  }
  const raw = dataLines.join('\n');
  let data: Json = {};
  try {
    data = obj(JSON.parse(raw));
  } catch {
    data = { text: raw };
  }
  switch (eventName) {
    case 'token':
    case 'message':
      onEvent({ type: 'token', text: str(data['text'] ?? data['delta'] ?? data['content']) });
      break;
    case 'citation':
      onEvent({
        type: 'citation',
        citation: {
          document: str(data['document'] ?? data['fileName'] ?? data['title'], 'Dokument'),
          location: data['page'] !== undefined ? `S. ${str(data['page'])}` : str(data['location'] ?? data['label'], '–')
        }
      });
      break;
    case 'proposal':
      onEvent({
        type: 'proposal',
        proposal: {
          title: str(data['title'] ?? data['key'], 'Vorschlag'),
          detail: str(data['detail'] ?? data['value'] ?? data['rationale']),
          currency: str(data['currency'], 'EUR'),
          startRatio: num(data['startRatio'], 0),
          stepDown: num(data['stepDown'], 0)
        }
      });
      break;
    case 'error':
      onEvent({ type: 'error', message: str(data['detail'] ?? data['message'] ?? data['text'], 'Fehler im Agenten') });
      break;
    default:
      break;
  }
}

function mapSummary(c: Json): ClientSummary {
  return {
    id: str(c['id']),
    name: str(c['name'], 'Unbenannt'),
    shortLabel: str(c['shortLabel'] ?? c['short'], ''),
    industry: str(c['industry'] ?? c['branche'], '–'),
    employees: c['employees'] !== undefined && c['employees'] !== null ? `${str(c['employees'])} Mitarbeitende` : '–',
    segment: c['segment'] ? segmentLabel(str(obj(c['segment'])['result'] ?? c['segment'])) : null,
    riskProfile: c['riskProfile'] ? profileLabel(str(c['riskProfile'])) : null,
    documentCount: num(c['documentCount']),
    openItems: num(c['openItems'] ?? c['openPoints']),
    approvalStatus: c['approvalStatus'] === 'APPROVED' ? 'APPROVED' : 'NOT_APPROVED'
  };
}

function mapDocument(d: Json): ClientDocument {
  return {
    id: str(d['id']),
    fileName: str(d['fileName'] ?? d['filename'] ?? d['name'], 'Dokument.pdf'),
    status: (str(d['status'], 'UPLOADED') as DocumentStatus),
    pageCount: optNum(d['pageCount'])
  };
}

function mapQuestion(q: Json): Question {
  const answer = obj(q['answer']);
  const value = q['value'] ?? answer['value'];
  const status = str(q['status'] ?? answer['status'], value === undefined || value === null ? 'MISSING' : 'PROPOSED');
  return {
    key: str(q['key']),
    text: str(q['text'] ?? q['label'], str(q['key'])),
    hint: q['hint'] ? str(q['hint']) : null,
    type: (str(q['type'], 'AMOUNT') as QuestionType),
    options: arr(q['options']).map((o) => {
      const option = obj(o);
      return typeof o === 'object' ? { value: str(option['value']), label: str(option['label'] ?? option['value']) } : { value: str(o), label: str(o) };
    }),
    value: value === undefined || value === null ? null : str(value),
    status: (['PROPOSED', 'CONFIRMED'].includes(status) ? status : 'MISSING') as AnswerStatus,
    source: str(q['source'] ?? answer['citation'] ?? answer['source'], '') || null
  };
}

function mapAssessment(a: Json): Assessment {
  const segment = obj(a['segment']);
  const capacity = obj(a['capacity']);
  const willingness = obj(a['willingness']);
  const factors = (v: unknown) =>
    arr(v).map((f) => ({ label: str(obj(f)['label'] ?? obj(f)['name']), value: str(obj(f)['value']), score: num(obj(f)['score']) }));
  const hedge = obj(a['hedge'] ?? a['hedging']);
  const liquidity = obj(a['liquidity']);
  const approval = obj(a['approval']);
  return {
    version: num(a['version'] ?? a['assessmentVersion'], 1),
    segment: {
      label: segmentLabel(str(segment['result'])),
      reason: str(segment['reason']),
      legalBasis: str(segment['legalBasis'], 'Art. 4 und 5 FIDLEG'),
      thresholdsExceeded: num(segment['thresholdsExceeded'])
    },
    capacity: { score: num(capacity['score']), factors: factors(capacity['factors']) },
    willingness: { score: num(willingness['score']), factors: factors(willingness['factors']) },
    profile: { score: num(obj(a['profile'])['score'] ?? a['profile']), label: profileLabel(str(obj(a['profile'])['label'] ?? a['riskProfile'])) },
    exposures: arr(obj(a['exposures'])['currencies'] ?? a['exposures']).map((e) => {
      const x = obj(e);
      return {
        currency: str(x['currency']),
        netPosition: num(x['netPosition']),
        netPositionChf: num(x['netPositionChf']),
        sensitivityPctEbit: num(x['sensitivityPctEbit']),
        riskLevel: (str(x['riskLevel'], 'LOW') as 'LOW' | 'MEDIUM' | 'HIGH'),
        direction: str(x['direction']) === 'NET_OUTFLOW' ? 'Nettoabfluss' : 'Nettozufluss'
      };
    }),
    suitability: arr(a['suitability']).map((s) => {
      const x = obj(s);
      return {
        product: str(x['productLabel'] ?? x['product']),
        knowledge: str(x['knowledge'] ?? (x['knowledgePresumed'] ? 'Vermutet (professionell)' : '–')),
        suitability: (str(x['status'] ?? x['result'], 'NOT_APPLICABLE') as Assessment['suitability'][number]['suitability']),
        note: x['note'] ? str(x['note']) : null,
        kidRequired: Boolean(x['kidRequired'])
      };
    }),
    hedges: arr(hedge['currencies'] ?? a['hedges']).map((h) => {
      const x = obj(h);
      const scenario = obj(x['scenario']);
      return {
        currency: str(x['currency']),
        action: str(x['hedgeAction'] ?? x['action']),
        cap: num(hedge['cap'] ?? x['cap']),
        ratios: arr(x['ratios'] ?? arr(x['tranches']).map((t) => obj(t)['ratio'])).map((r) => num(r)),
        averageRatio: num(x['averageRatio']),
        hedgedAmountFx: num(x['hedgedAmountFx']),
        carryChf: num(x['carryChf']),
        limitedByPolicy: Boolean(x['limitedByPolicy']),
        collarShare: x['collarEnabled'] ? num(x['collarShare']) : null,
        scenarioUnhedgedChf: optNum(scenario['unhedgedChf']),
        scenarioHedgedChf: optNum(scenario['hedgedChf'])
      };
    }),
    liquidity: {
      excess: num(liquidity['excess']),
      parts: arr(liquidity['parts'] ?? liquidity['allocation']).map((p) => ({
        product: str(obj(p)['productLabel'] ?? obj(p)['product']),
        share: num(obj(p)['share']),
        amount: num(obj(p)['amount'])
      }))
    },
    checklist: arr(a['checklist']).map((i) => ({
      id: str(obj(i)['id']),
      text: str(obj(i)['text']),
      legalBasis: str(obj(i)['legalBasis'] ?? obj(i)['ref']),
      done: Boolean(obj(i)['done'])
    })),
    unconfirmedAnswers: num(a['unconfirmedAnswers'] ?? arr(approval['blockers']).filter((b) => obj(b)['type'] === 'UNCONFIRMED_ANSWER').length),
    approvalStatus: str(approval['status'] ?? a['approvalStatus']) === 'APPROVED' ? 'APPROVED' : 'NOT_APPROVED'
  };
}

function segmentLabel(code: string): string {
  return code === 'PROFESSIONAL' ? 'Professioneller Kunde' : code === 'RETAIL' ? 'Privatkunde' : code === 'INSTITUTIONAL' ? 'Institutioneller Kunde' : code || '–';
}

function profileLabel(code: string): string {
  const labels: Record<string, string> = {
    SECURITY: 'Sicherheit',
    CONSERVATIVE: 'Konservativ',
    BALANCED: 'Ausgewogen',
    GROWTH: 'Wachstum',
    DYNAMIC: 'Dynamisch'
  };
  return labels[code] ?? (code || '–');
}
