import { ClientSummary, DocumentStatus, RiskLevel, Suitability } from '../core/models';
import { BadgeTone } from '../shared/badge';

/*
 * Codes map to badge tones here; their texts are translation keys of the same name,
 * e.g. `suitability.SUITABLE`, `risk.HIGH`, `documentStatus.INDEXED`.
 */
export const DOCUMENT_STEPS: readonly DocumentStatus[] = [
  'UPLOADED',
  'TEXT_EXTRACTED',
  'INDEXED',
  'EXTRACTED',
];

export const DOCUMENT_TONE: Readonly<Record<DocumentStatus, BadgeTone>> = {
  UPLOADED: 'pending',
  TEXT_EXTRACTED: 'pending',
  INDEXED: 'pending',
  EXTRACTED: 'ok',
  FAILED: 'crit',
};

export const SUITABILITY_TONE: Readonly<Record<Suitability, BadgeTone>> = {
  SUITABLE: 'ok',
  SUITABLE_AFTER_DISCLOSURE: 'warn',
  NOT_SUITABLE: 'crit',
  NOT_APPLICABLE: 'neutral',
};

export const RISK_TONE: Readonly<Record<RiskLevel, BadgeTone>> = {
  LOW: 'ok',
  MEDIUM: 'warn',
  HIGH: 'crit',
};

export type ClientStatus = 'APPROVED' | 'IN_PROGRESS' | 'NEW';

export const CLIENT_STATUS_TONE: Readonly<Record<ClientStatus, BadgeTone>> = {
  APPROVED: 'ok',
  IN_PROGRESS: 'pending',
  NEW: 'new',
};

export function clientStatus(client: ClientSummary): ClientStatus {
  if (client.approvalStatus === 'APPROVED') return 'APPROVED';
  return client.documentCount === 0 && client.segment === null ? 'NEW' : 'IN_PROGRESS';
}

/** Translation key for counts, e.g. `start.count` → `start.count.one` or `start.count.other`. */
export function plural(key: string, count: number): string {
  return `${key}.${count === 1 ? 'one' : 'other'}`;
}

export function errorText(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}
