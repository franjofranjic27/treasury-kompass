import { Language } from './i18n/language';
import { Localizable, Localized, l, resolveLocalized, toLocalized } from './i18n/localized';
import { COMPONENTS, ClientSeed, MUSTER } from './mock-data';
import { ChatEvent, ClientSegment } from './models';

type ScriptedEvent = Localizable<ChatEvent>;

const AUDIT_TRAIL = l('Audit-Trail', 'Audit trail');

const SEGMENT_NAMES: Readonly<Record<ClientSegment, Localized>> = {
  RETAIL: l('Privatkunde', 'a retail client'),
  PROFESSIONAL: l('Professioneller Kunde', 'a professional client'),
  INSTITUTIONAL: l('Institutioneller Kunde', 'an institutional client'),
};

/*
 * Intent patterns match German and English, so a question is understood whatever the UI language is;
 * the answer follows the active language.
 */
const APPROVAL_INTENT = /freigeb|freigabe|approv|100\s*%/;
const SEGMENT_INTENT = /privatkunde|segment|höchstens|maximal|retail|at most|maximum/;
const FX_FLOW_INTENT = /umsatz|einkauf|zuflüss|abflüss|revenue|purchas|inflow|outflow|eur/;

/** Scripted agent answers following docs/specs/agent-testcases.md. The agent never approves. */
export function scriptChatAnswer(
  seed: ClientSeed | null,
  message: string,
  language: Language,
): readonly ChatEvent[] {
  return resolveLocalized<readonly ChatEvent[]>(script(seed, message.toLowerCase()), language);
}

function script(seed: ClientSeed | null, text: string): readonly ScriptedEvent[] {
  if (APPROVAL_INTENT.test(text)) return refuseApproval(seed);
  if (SEGMENT_INTENT.test(text)) return explainSegment(seed);
  if (FX_FLOW_INTENT.test(text)) return eurFlows(seed);
  return [
    {
      type: 'token',
      text: l(
        'Dazu finde ich in den Unterlagen keine belastbare Angabe. Ich beantworte Fragen zu Kennzahlen, ' +
          'Fremdwährungsflüssen, Kundensegment und Absicherungsvorschlag, jeweils mit Fundstelle.',
        'I cannot find any reliable information on this in the documents. I answer questions on key figures, ' +
          'foreign currency flows, client segment and hedging proposal, each with a citation.',
      ),
    },
    { type: 'done' },
  ];
}

function noClient(): readonly ScriptedEvent[] {
  return [
    {
      type: 'token',
      text: l(
        'Bitte zuerst einen Kunden auswählen, damit ich in dessen Unterlagen suchen kann.',
        'Please select a client first so I can search their documents.',
      ),
    },
    { type: 'done' },
  ];
}

function eurFlows(seed: ClientSeed | null): readonly ScriptedEvent[] {
  if (!seed) return noClient();
  const inflow = seed.answers['fx.eur.inflow'];
  const outflow = seed.answers['fx.eur.outflow'];
  const subject =
    seed === COMPONENTS
      ? l('Der Geschäftsbericht', 'The annual report')
      : l('Die Jahresrechnung', 'The annual financial statements');
  return [
    {
      type: 'token',
      text: l(
        `${subject['de-CH']} 2025 weist für die nächsten 12 Monate Zuflüsse von EUR ${inflow.value} Mio. ` +
          `und Abflüsse von EUR ${outflow.value} Mio. aus [S1]. Die Nettoposition berechnet das Regelwerk.`,
        `${subject.en} 2025 show inflows of EUR ${inflow.value}m and outflows of EUR ${outflow.value}m ` +
          'for the next 12 months [S1]. The rule engine calculates the net position.',
      ),
    },
    {
      type: 'citation',
      citation: { document: seed.document.label, location: inflow.source ?? '–' },
    },
    { type: 'done' },
  ];
}

function explainSegment(seed: ClientSeed | null): readonly ScriptedEvent[] {
  if (!seed) return noClient();
  const cap = seed.answers['goals.policyMaxHedgeRatio'].value;
  if (seed !== MUSTER) {
    const reason = toLocalized(seed.assessment.segment.reason);
    const segment = SEGMENT_NAMES[seed.segment];
    return [
      {
        type: 'token',
        text: l(
          `${seed.name} ist ${segment['de-CH']}: ${reason['de-CH']} (Art. 4 und 5 FIDLEG). ` +
            `Die Treasury-Policy erlaubt höchstens ${cap} %.`,
          `${seed.name} is ${segment.en}: ${reason.en} (Art. 4 and 5 FinSA (FIDLEG)). ` +
            `The treasury policy allows at most ${cap}%.`,
        ),
      },
      {
        type: 'citation',
        citation: {
          document: AUDIT_TRAIL,
          location: l('Regelentscheid Kundensegment', 'Rule decision, client segment'),
        },
      },
      { type: 'done' },
    ];
  }
  return [
    {
      type: 'token',
      text: l(
        'Die Muster Maschinenbau AG ist Privatkunde, weil nur 1 von 3 Schwellenwerten überschritten ist ' +
          '(Eigenkapital CHF 7.9 Mio.) und keine professionelle Tresorerie besteht (Art. 4 und 5 FIDLEG) [S1]. ' +
          'Die EUR-Absicherung darf gemäss Treasury-Policy höchstens 80 % betragen; vorgeschlagen sind die Tranchen ' +
          '80 / 70 / 60 / 50 % [S2]. Die Werte stammen aus Regelwerk und Audit-Trail, ich rechne nichts selbst.',
        'Muster Maschinenbau AG is a retail client because only 1 of 3 thresholds is exceeded ' +
          '(equity CHF 7.9m) and there is no professional treasury (Art. 4 and 5 FinSA (FIDLEG)) [S1]. ' +
          'Under the treasury policy the EUR hedge may be at most 80%; the proposed tranches are ' +
          '80 / 70 / 60 / 50% [S2]. The figures come from the rule engine and the audit trail; I do not calculate anything myself.',
      ),
    },
    {
      type: 'citation',
      citation: {
        document: seed.document.label,
        location: l('Bilanz, S. 4', 'Balance sheet, p. 4'),
      },
    },
    {
      type: 'citation',
      citation: {
        document: AUDIT_TRAIL,
        location: l('Regelentscheid Absicherungsquote EUR', 'Rule decision, EUR hedge ratio'),
      },
    },
    { type: 'done' },
  ];
}

function refuseApproval(seed: ClientSeed | null): readonly ScriptedEvent[] {
  if (!seed) return noClient();
  const cap = Number(seed.answers['goals.policyMaxHedgeRatio'].value);
  return [
    {
      type: 'token',
      text: l(
        `Das kann ich nicht übernehmen. Die Treasury-Policy erlaubt höchstens ${cap} %, und freigeben kann nur der Berater, ` +
          'sobald die vorbefüllten Antworten bestätigt sind und die Checkliste erledigt ist. ' +
          `Ich kann einen Entwurf mit allen Tranchen auf ${cap} % vorbereiten, den Sie prüfen und bestätigen.`,
        `I cannot do that. The treasury policy allows at most ${cap}%, and only the advisor can approve, ` +
          'once the prefilled answers are confirmed and the checklist is complete. ' +
          `I can prepare a draft with all tranches at ${cap}% for you to review and confirm.`,
      ),
    },
    {
      type: 'citation',
      citation: {
        document: AUDIT_TRAIL,
        location: l(
          'Treasury-Policy, maximale Absicherungsquote',
          'Treasury policy, maximum hedge ratio',
        ),
      },
    },
    {
      type: 'proposal',
      proposal: {
        title: l(
          `Entwurf: EUR-Absicherung ${cap} / ${cap} / ${cap} / ${cap} %`,
          `Draft: EUR hedge ${cap} / ${cap} / ${cap} / ${cap}%`,
        ),
        detail: l(
          'Alle Tranchen auf die Policy-Obergrenze. Die Freigabe bleibt beim Berater.',
          'All tranches at the policy cap. Approval remains with the advisor.',
        ),
        currency: 'EUR',
        startRatio: cap,
        stepDown: 0,
      },
    },
    { type: 'done' },
  ];
}
