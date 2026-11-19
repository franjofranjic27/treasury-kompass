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
const BALANCE_INTENT = /bilanz|eigenkapital|flüssige mittel|total assets|balance sheet|equity|cash/;
const FX_FLOW_INTENT = /umsatz|einkauf|zuflüss|abflüss|revenue|purchas|inflow|outflow|eur/;

const text = (value: Localized): ScriptedEvent => ({ type: 'token', text: value });
const cite = (id: string, document: Localized, location: Localized): ScriptedEvent => ({
  type: 'citation',
  citation: { id, document, location },
});
const DONE: ScriptedEvent = { type: 'done' };

/**
 * Scripted agent answers following docs/specs/agent-testcases.md. Like the real agent they use markdown
 * and cite sources as «[S1]». The agent never approves.
 */
export function scriptChatAnswer(
  seed: ClientSeed | null,
  message: string,
  language: Language,
): readonly ChatEvent[] {
  return resolveLocalized<readonly ChatEvent[]>(script(seed, message.toLowerCase()), language);
}

function script(seed: ClientSeed | null, question: string): readonly ScriptedEvent[] {
  if (APPROVAL_INTENT.test(question)) return refuseApproval(seed);
  if (SEGMENT_INTENT.test(question)) return explainSegment(seed);
  if (BALANCE_INTENT.test(question)) return balanceSheet(seed);
  if (FX_FLOW_INTENT.test(question)) return eurFlows(seed);
  return [
    text(
      l(
        'Dazu finde ich in den Unterlagen keine belastbare Angabe. Ich beantworte Fragen zu Kennzahlen, ' +
          'Fremdwährungsflüssen, Kundensegment und Absicherungsvorschlag, jeweils mit Fundstelle.',
        'I cannot find any reliable information on this in the documents. I answer questions on key figures, ' +
          'foreign currency flows, client segment and hedging proposal, each with a citation.',
      ),
    ),
    DONE,
  ];
}

function noClient(): readonly ScriptedEvent[] {
  return [
    text(
      l(
        'Bitte zuerst einen Kunden auswählen, damit ich in dessen Unterlagen suchen kann.',
        'Please select a client first so I can search their documents.',
      ),
    ),
    DONE,
  ];
}

function balanceSheet(seed: ClientSeed | null): readonly ScriptedEvent[] {
  if (!seed) return noClient();
  const value = (key: string) => seed.answers[key].value;
  const { label, balanceSheetPage: page } = seed.document;
  return [
    text(
      l(
        `Bilanzwerte laut ${label['de-CH']} [S1]:\n\n` +
          '| Position | CHF Mio. |\n| --- | ---: |\n' +
          `| Bilanzsumme | ${value('fin.totalAssets')} |\n` +
          `| Eigenkapital | ${value('fin.equity')} |\n` +
          `| Flüssige Mittel | ${value('fin.cash')} |\n\n` +
          'Kennzahlen wie die Eigenkapitalquote berechnet das Regelwerk.',
        `Balance sheet figures (${label.en}) [S1]:\n\n` +
          '| Item | CHF m |\n| --- | ---: |\n' +
          `| Total assets | ${value('fin.totalAssets')} |\n` +
          `| Equity | ${value('fin.equity')} |\n` +
          `| Cash and cash equivalents | ${value('fin.cash')} |\n\n` +
          'Ratios such as the equity ratio are calculated by the rule engine.',
      ),
    ),
    cite('S1', label, l(`S. ${page}`, `p. ${page}`)),
    DONE,
  ];
}

function eurFlows(seed: ClientSeed | null): readonly ScriptedEvent[] {
  if (!seed) return noClient();
  const inflow = seed.answers['fx.eur.inflow'];
  const outflow = seed.answers['fx.eur.outflow'];
  const subject =
    seed === COMPONENTS
      ? l('Der Geschäftsbericht 2025 weist', 'The annual report 2025 shows')
      : l('Die Jahresrechnung 2025 weist', 'The annual financial statements 2025 show');
  return [
    text(
      l(
        `${subject['de-CH']} für die nächsten 12 Monate folgende EUR-Flüsse aus [S1]:\n\n` +
          '| Position | EUR Mio. |\n| --- | ---: |\n' +
          `| Zuflüsse (Umsatz) | ${inflow.value} |\n` +
          `| Abflüsse (Einkauf) | ${outflow.value} |\n\n` +
          'Die Nettoposition berechnet das Regelwerk.',
        `${subject.en} the following EUR flows for the next 12 months [S1]:\n\n` +
          '| Item | EUR m |\n| --- | ---: |\n' +
          `| Inflows (revenue) | ${inflow.value} |\n` +
          `| Outflows (purchasing) | ${outflow.value} |\n\n` +
          'The rule engine calculates the net position.',
      ),
    ),
    cite('S1', seed.document.label, inflow.source ?? l('–', '–')),
    DONE,
  ];
}

function explainSegment(seed: ClientSeed | null): readonly ScriptedEvent[] {
  if (!seed) return noClient();
  const cap = seed.answers['goals.policyMaxHedgeRatio'].value;
  if (seed !== MUSTER) {
    const reason = toLocalized(seed.assessment.segment.reason);
    const segment = SEGMENT_NAMES[seed.segment];
    return [
      text(
        l(
          `${seed.name} ist **${segment['de-CH']}** (Art. 4 und 5 FIDLEG) [S1]:\n\n` +
            `- ${reason['de-CH']}\n\n` +
            `Die Treasury-Policy erlaubt höchstens **${cap} %**.`,
          `${seed.name} is **${segment.en}** (Art. 4 and 5 FinSA (FIDLEG)) [S1]:\n\n` +
            `- ${reason.en}\n\n` +
            `The treasury policy allows at most **${cap}%**.`,
        ),
      ),
      cite('S1', AUDIT_TRAIL, l('Regelentscheid Kundensegment', 'Rule decision, client segment')),
      DONE,
    ];
  }
  return [
    text(
      l(
        'Die Muster Maschinenbau AG ist **Privatkunde** (Art. 4 und 5 FIDLEG) [S1]:\n\n' +
          '- nur 1 von 3 Schwellenwerten überschritten (Eigenkapital CHF 7.9 Mio.)\n' +
          '- keine professionelle Tresorerie\n\n' +
          'Die EUR-Absicherung darf gemäss Treasury-Policy höchstens **80 %** betragen; ' +
          'vorgeschlagen sind die Tranchen 80 / 70 / 60 / 50 % [S2].\n\n' +
          'Die Werte stammen aus Regelwerk und Audit-Trail, ich rechne nichts selbst.',
        'Muster Maschinenbau AG is a **retail client** (Art. 4 and 5 FinSA (FIDLEG)) [S1]:\n\n' +
          '- only 1 of 3 thresholds exceeded (equity CHF 7.9m)\n' +
          '- no professional treasury\n\n' +
          'Under the treasury policy the EUR hedge may be at most **80%**; ' +
          'the proposed tranches are 80 / 70 / 60 / 50% [S2].\n\n' +
          'The figures come from the rule engine and the audit trail; I do not calculate anything myself.',
      ),
    ),
    cite('S1', seed.document.label, l('Bilanz, S. 4', 'Balance sheet, p. 4')),
    cite(
      'S2',
      AUDIT_TRAIL,
      l('Regelentscheid Absicherungsquote EUR', 'Rule decision, EUR hedge ratio'),
    ),
    DONE,
  ];
}

/** Deliberately plain text: a refusal should read as a clear sentence, not as formatted output. */
function refuseApproval(seed: ClientSeed | null): readonly ScriptedEvent[] {
  if (!seed) return noClient();
  const cap = Number(seed.answers['goals.policyMaxHedgeRatio'].value);
  return [
    text(
      l(
        `Das kann ich nicht übernehmen. Die Treasury-Policy erlaubt höchstens ${cap} %, und freigeben kann nur der Berater, ` +
          'sobald die vorbefüllten Antworten bestätigt sind und die Checkliste erledigt ist. ' +
          `Ich kann einen Entwurf mit allen Tranchen auf ${cap} % vorbereiten, den Sie prüfen und bestätigen.`,
        `I cannot do that. The treasury policy allows at most ${cap}%, and only the advisor can approve, ` +
          'once the prefilled answers are confirmed and the checklist is complete. ' +
          `I can prepare a draft with all tranches at ${cap}% for you to review and confirm.`,
      ),
    ),
    cite(
      'S1',
      AUDIT_TRAIL,
      l('Treasury-Policy, maximale Absicherungsquote', 'Treasury policy, maximum hedge ratio'),
    ),
    {
      type: 'proposal',
      proposal: {
        kind: 'hedge',
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
    DONE,
  ];
}
