# Regelwerk

Regeln und Formeln, wie sie im Prototyp (`reference/prototype/treasury-kompass.html`, Funktion `compute()`) umgesetzt sind. Grundlage für das Regelwerk-Modul der Services.

Alle Schwellen, Gewichte und Tabellen sind Heuristiken für den Hackathon und müssen mit Fachleuten aus der Bank kalibriert werden. Beträge in Mio., Quoten in Prozent.

## 1. Kundensegment (Art. 4 und 5 FIDLEG)

1. Institutioneller Kunde (Bank, Versicherung, Zentralbank)? Bei Firmenkunden im Scope immer nein.
2. Grosses Unternehmen: mindestens 2 von 3 Grössen überschritten:
   - Bilanzsumme > CHF 20 Mio.
   - Umsatzerlös > CHF 40 Mio.
   - Eigenkapital > CHF 2 Mio.
3. Professionelle Tresorerie: Antwort «Eigene Treasury-Abteilung mit Fachpersonen».
4. Professionell, wenn 2 oder 3 zutrifft. Erklärt ein professioneller Kunde Opting-in, gilt er als Privatkunde.
5. Sonst Privatkunde.

## 2. Exposures

| Grösse | Formel |
| --- | --- |
| Nettoposition je Währung | Zuflüsse − Abflüsse (nächste 12 Monate, in Fremdwährung) |
| Nettoposition in CHF | Nettoposition × Kassakurs |
| Sensitivität | \|Nettoposition CHF\| × 10 % |
| Risikostufe | Sensitivität / EBIT: ≥ 30 % hoch, ≥ 10 % mittel, sonst tief |
| Zinsrisiko | Bankkredit (variabel) × 1 Prozentpunkt pro Jahr |

Eine Zufluss-Position verliert bei fallendem Kurs, eine Abfluss-Position bei steigendem.

## 3. Risikoprofil (Art. 12 FIDLEG)

### Risikofähigkeit (objektiv, aus den Zahlen)

Jeder Faktor ergibt 1 bis 5 Punkte. Risikofähigkeit = gerundeter Durchschnitt.

| Faktor | 1 | 2 | 3 | 4 | 5 |
| --- | --- | --- | --- | --- | --- |
| Eigenkapitalquote (EK / Bilanzsumme) | < 20 % | 20–30 % | 30–40 % | 40–50 % | ≥ 50 % |
| Überschussliquidität in Monaten Betriebsaufwand | < 1 | 1–2 | 2–4 | 4–6 | ≥ 6 |
| EBIT-Marge | < 3 % | 3–6 % | 6–9 % | 9–12 % | ≥ 12 % |
| Ungesichertes Währungsrisiko (Σ Sensitivitäten / EBIT) | ≥ 50 % | 30–50 % | 15–30 % | 5–15 % | < 5 % |

Mindestliquidität, Richtwert: 1.5 × monatlicher Betriebsaufwand. Überschussliquidität = flüssige Mittel − Mindestliquidität (min. 0).

### Risikobereitschaft (subjektiv, aus dem Fragebogen)

Risikobereitschaft = gerundeter Durchschnitt der drei Antworten.

| Frage | Antwort → Punkte |
| --- | --- |
| Ziel der Absicherung | Budgetsicherheit 1 · Margen schützen 3 · Kosten tief halten 4 |
| Tragbarer Wertrückgang der Liquiditätsanlage | keiner 1 · bis 2 % 2 · bis 5 % 3 · mehr als 5 % 5 |
| Entgangener Kursgewinn nach Absicherung | akzeptabel 1 · ärgerlich, aber tragbar 3 · nicht akzeptabel 4 |

### Profil

Risikoprofil = min(Risikofähigkeit, Risikobereitschaft): 1 Sicherheit, 2 Konservativ, 3 Ausgewogen, 4 Wachstum, 5 Dynamisch.

## 4. Eignung pro Produkt (Art. 12 und 14 FIDLEG)

| Fall | Ergebnis |
| --- | --- |
| Festgeld, Callgeld | Keine Prüfung nötig (Einlage, kein Finanzinstrument) |
| Professioneller Kunde | Geeignet, Kenntnisse und Erfahrung vermutet |
| Privatkunde mit Kenntnissen und Erfahrung | Geeignet |
| Privatkunde nur mit Kenntnissen | Nur nach Aufklärung, Warnhinweis dokumentieren |
| Privatkunde ohne Kenntnisse | Nicht geeignet, abraten (Art. 14) |

Basisinformationsblatt nötig bei Privatkunden für Devisentermingeschäft, Zero-Cost-Collar und Geldmarktfonds, nicht für einfache Obligationen.

## 5. Währungsabsicherung

- Nur Positionen ab CHF 0.5 Mio. Nettoposition. Kleinere gelten als natürlich abgesichert.
- Obergrenze `cap` = min(100 %, max. Quote der Treasury-Policy). Nie mehr als 100 % der Nettoposition.
- Startquote (nächste 3 Monate) nach Profil: 1 → 90 %, 2 → 80 %, 3 → 70 %, 4 → 60 %, 5 → 50 %, begrenzt auf `cap`. Der Berater kann sie ändern.
- Vier Tranchen zu 3, 6, 9 und 12 Monaten, je ¼ der Jahresposition.
- Quote der Tranche i = clamp(Start − (i − 1) × Abstufung, 0, cap). Abstufung Standard 10 Prozentpunkte.
- Terminkurs: `F(t) = S × (1 + r_CHF × t) / (1 + r_FW × t)`, t in Jahren.
- Zinsdifferenz je Tranche = abgesichertes Volumen × (1 − Collar-Anteil) × (F − S). Negativ = Kosten.
- Gesperrt, wenn das Devisentermingeschäft nicht geeignet ist: effektive Quote 0, Vorschlag rät ab.

### Zero-Cost-Collar

- Erlaubt, wenn nicht gesperrt und Collar nicht «nicht geeignet».
- Standardmässig aktiv, wenn Collar geeignet ist und die Antwort zum entgangenen Kursgewinn nicht «akzeptabel» lautet.
- Anteil: 30 % des abgesicherten Volumens.
- Band (vereinfacht, zero cost angenommen): Zufluss-Position Untergrenze 0.985 F, Obergrenze 1.03 F; Abfluss-Position Untergrenze 0.97 F, Obergrenze 1.015 F.

### Szenario

Kursänderung s gegenüber Kassa: `S1 = S × (1 + s)`. Vereinfachung: wirkt sofort auf alle Fälligkeiten.

- Ohne Absicherung: `Netto × (S1 − S)`
- Mit Vorschlag: Summe über die Tranchen von
  `Termin × (F − S) + Collar × (clamp(S1, Untergrenze, Obergrenze) − S) + Offen × (S1 − S)`

## 6. Anlage der Überschussliquidität

Aufteilung in Prozent: Festgeld/Callgeld · Geldmarktfonds CHF · Obligationen CHF kurz.

| Horizont | Profil 1 | Profil 2 | Profil 3 | Profil 4–5 |
| --- | --- | --- | --- | --- |
| Unter 1 Jahr | 70 · 30 · 0 | 45 · 40 · 15 | 35 · 40 · 25 | 30 · 40 · 30 |
| 1 bis 3 Jahre | 50 · 30 · 20 | 30 · 30 · 40 | 20 · 25 · 55 | 15 · 25 · 60 |
| Über 3 Jahre | 40 · 30 · 30 | 20 · 25 · 55 | 10 · 20 · 70 | 10 · 20 · 70 |

Umschichtung bei fehlender Eignung: Obligationen → Geldmarktfonds (falls geeignet), sonst → Festgeld. Geldmarktfonds → Festgeld.

## 7. Checkliste und Freigabe

Vor der Freigabe zu erledigen:

- Informationen zu Finanzdienstleister, Dienstleistung, Risiken und Kosten (Art. 8)
- Basisinformationsblätter, falls Privatkunde und empfohlene Produkte sie verlangen (Art. 58)
- Risikobroschüre «Risiken im Handel mit Finanzinstrumenten»
- Kosten, Marge im Terminkurs und Entschädigungen Dritter offengelegt (Art. 26)
- Warnhinweis je empfohlenem Produkt mit Status «Nur nach Aufklärung» (Art. 14)

Zusätzlich müssen alle aus den Unterlagen vorbefüllten Antworten bestätigt sein. Jede Änderung an Kennzahlen, Antworten oder Vorschlag hebt eine Freigabe auf.

## 8. Demo-Annahmen

| Grösse | Wert |
| --- | --- |
| EUR/CHF Kassa | 0.9400 |
| USD/CHF Kassa | 0.8000 |
| Zins CHF | 0.00 % |
| Zins EUR | 2.00 % |
| Zins USD | 3.75 % |

## 9. Testfälle aus dem Prototyp

| Grösse | Muster Maschinenbau AG | Beispiel Components AG |
| --- | --- | --- |
| Umsatz / Bilanz / EK / EBIT (CHF Mio.) | 28.4 / 19.2 / 7.9 / 2.3 | 146.0 / 112.0 / 46.0 / 12.8 |
| Fremdwährung netto (Mio.) | EUR +14.2, USD −3.1 | EUR +52.4, USD +21.0 |
| Kundensegment | Privatkunde (1 von 3, keine Tresorerie) | Professionell (3 von 3, Treasury-Abteilung) |
| Risikofähigkeit / -bereitschaft / Profil | 3 / 2 / 2 Konservativ | 3 / 3 / 3 Ausgewogen |
| Staffelung EUR | 80 / 70 / 60 / 50 %, Ø 65 % | 70 / 60 / 50 / 40 %, Ø 55 %, mit Collar |
| Szenario EUR −10 % (CHF Mio.) | ohne −1.33, mit −0.57 | – |
| Anlagefähig | CHF 3.1 Mio., 45 · 40 · 15 | CHF 11.3 Mio., 20 · 25 · 55 |

Die Werte der Muster Maschinenbau AG sind im Prototyp geprüft, die der Beispiel Components AG von Hand nachgerechnet.
