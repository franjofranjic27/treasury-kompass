import { ChatEvent } from './models';
import { COMPONENTS, ClientSeed, MUSTER } from './mock-data';

/** Scripted agent answers following docs/specs/agent-testcases.md. The agent never approves. */
export function scriptChatAnswer(seed: ClientSeed | null, message: string): readonly ChatEvent[] {
  const text = message.toLowerCase();
  if (/freigeb|freigabe|100\s*%/.test(text)) return refuseApproval(seed);
  if (/privatkunde|segment|höchstens|maximal/.test(text)) return explainSegment(seed);
  if (/umsatz|einkauf|zuflüss|abflüss|eur/.test(text)) return eurFlows(seed);
  return [
    {
      type: 'token',
      text:
        'Dazu finde ich in den Unterlagen keine belastbare Angabe. Ich beantworte Fragen zu Kennzahlen, ' +
        'Fremdwährungsflüssen, Kundensegment und Absicherungsvorschlag, jeweils mit Fundstelle.'
    },
    { type: 'done' }
  ];
}

function noClient(): readonly ChatEvent[] {
  return [{ type: 'token', text: 'Bitte zuerst einen Kunden auswählen, damit ich in dessen Unterlagen suchen kann.' }, { type: 'done' }];
}

function eurFlows(seed: ClientSeed | null): readonly ChatEvent[] {
  if (!seed) return noClient();
  const inflow = seed.answers['fx.eur.inflow'];
  const outflow = seed.answers['fx.eur.outflow'];
  const label = seed === COMPONENTS ? 'Geschäftsbericht' : 'Die Jahresrechnung';
  return [
    {
      type: 'token',
      text:
        `${label} 2025 weist für die nächsten 12 Monate Zuflüsse von EUR ${inflow.value} Mio. ` +
        `und Abflüsse von EUR ${outflow.value} Mio. aus [S1]. Die Nettoposition berechnet das Regelwerk.`
    },
    { type: 'citation', citation: { document: seed.document.label, location: inflow.source ?? '–' } },
    { type: 'done' }
  ];
}

function explainSegment(seed: ClientSeed | null): readonly ChatEvent[] {
  if (!seed) return noClient();
  if (seed !== MUSTER) {
    return [
      { type: 'token', text: `${seed.name} ist ${seed.assessment.segment.label}: ${seed.assessment.segment.reason} (Art. 4 und 5 FIDLEG). Die Treasury-Policy erlaubt höchstens ${seed.answers['goals.policyMaxHedgeRatio'].value} %.` },
      { type: 'citation', citation: { document: 'Audit-Trail', location: 'Regelentscheid Kundensegment' } },
      { type: 'done' }
    ];
  }
  return [
    {
      type: 'token',
      text:
        'Die Muster Maschinenbau AG ist Privatkunde, weil nur 1 von 3 Schwellenwerten überschritten ist ' +
        '(Eigenkapital CHF 7.9 Mio.) und keine professionelle Tresorerie besteht (Art. 4 und 5 FIDLEG) [S1]. ' +
        'Die EUR-Absicherung darf gemäss Treasury-Policy höchstens 80 % betragen; vorgeschlagen sind die Tranchen ' +
        '80 / 70 / 60 / 50 % [S2]. Die Werte stammen aus Regelwerk und Audit-Trail, ich rechne nichts selbst.'
    },
    { type: 'citation', citation: { document: 'Jahresrechnung 2025', location: 'Bilanz, S. 4' } },
    { type: 'citation', citation: { document: 'Audit-Trail', location: 'Regelentscheid Absicherungsquote EUR' } },
    { type: 'done' }
  ];
}

function refuseApproval(seed: ClientSeed | null): readonly ChatEvent[] {
  if (!seed) return noClient();
  const cap = Number(seed.answers['goals.policyMaxHedgeRatio'].value);
  return [
    {
      type: 'token',
      text:
        `Das kann ich nicht übernehmen. Die Treasury-Policy erlaubt höchstens ${cap} %, und freigeben kann nur der Berater, ` +
        'sobald die vorbefüllten Antworten bestätigt sind und die Checkliste erledigt ist. ' +
        `Ich kann einen Entwurf mit allen Tranchen auf ${cap} % vorbereiten, den Sie prüfen und bestätigen.`
    },
    { type: 'citation', citation: { document: 'Audit-Trail', location: 'Treasury-Policy, maximale Absicherungsquote' } },
    {
      type: 'proposal',
      proposal: {
        title: `Entwurf: EUR-Absicherung ${cap} / ${cap} / ${cap} / ${cap} %`,
        detail: 'Alle Tranchen auf die Policy-Obergrenze. Die Freigabe bleibt beim Berater.',
        currency: 'EUR',
        startRatio: cap,
        stepDown: 0
      }
    },
    { type: 'done' }
  ];
}
