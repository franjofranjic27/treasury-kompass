# Referenz-Artefakte

Artefakte aus der Konzeptphase. Sie dienen als Referenz für die Entwicklung und werden nicht weiterentwickelt.

| Artefakt | Datei | Original |
| --- | --- | --- |
| Klickbarer Prototyp (6 Schritte, 2 Demo-Kunden) | [`prototype/treasury-kompass.html`](prototype/treasury-kompass.html) | Claude Artifact (privat) |
| One-Pager für den Pitch | [`../docs/one-pager.md`](../docs/one-pager.md) | Claude Doc (privat) |
| Geschärfter Use-Case | [`../docs/use-case.md`](../docs/use-case.md) | Chat-Verlauf |

## Prototyp

- Eine HTML-Datei, Vanilla JS, keine Abhängigkeiten ausser Google Fonts. Öffnen mit `open prototype/treasury-kompass.html`.
- Hell- und Dunkelmodus, auch auf dem Handy nutzbar.
- Die PDF-Extraktion ist simuliert: Eine hochgeladene Datei lädt die Beispieldaten des gewählten Demo-Kunden.
- Kurse, Zinsen und Collar-Bandbreiten sind Demo-Annahmen.
- PDF-Export fehlt; das Protokoll lässt sich als Text kopieren.
- Die Regeln aus `compute()` sind in [`../docs/regelwerk.md`](../docs/regelwerk.md) beschrieben.

## Was der Prototyp live zeigt

- Bilanzsumme der Muster Maschinenbau AG auf 21 setzen: Das Unternehmen wird professioneller Kunde.
- Kenntnisse für Devisentermingeschäfte auf «Keine»: Die Absicherung wird gesperrt und der Vorschlag rät ab.
- Freigabe erst nach bestätigten Antworten und erledigter Checkliste; jede Änderung hebt sie auf.
