import { DocumentStatus, RiskLevel, Suitability } from '../core/models';

export const DOCUMENT_STATUS_LABEL: Readonly<Record<DocumentStatus, string>> = {
  UPLOADED: 'Hochgeladen',
  TEXT_EXTRACTED: 'Text extrahiert',
  INDEXED: 'Indexiert',
  EXTRACTED: 'Kennzahlen extrahiert',
  FAILED: 'Fehlgeschlagen'
};

export const DOCUMENT_STEPS: readonly DocumentStatus[] = ['UPLOADED', 'TEXT_EXTRACTED', 'INDEXED', 'EXTRACTED'];

export const SUITABILITY_LABEL: Readonly<Record<Suitability, { readonly text: string; readonly css: string }>> = {
  SUITABLE: { text: 'Geeignet', css: 'ok' },
  SUITABLE_AFTER_DISCLOSURE: { text: 'Geeignet nach Aufklärung', css: 'warn' },
  NOT_SUITABLE: { text: 'Nicht geeignet', css: 'crit' },
  NOT_APPLICABLE: { text: 'Keine Prüfung', css: 'na' }
};

export const RISK_LABEL: Readonly<Record<RiskLevel, { readonly text: string; readonly css: string }>> = {
  LOW: { text: 'Risiko tief', css: 'ok' },
  MEDIUM: { text: 'Risiko mittel', css: 'warn' },
  HIGH: { text: 'Risiko hoch', css: 'crit' }
};

export function errorText(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}
