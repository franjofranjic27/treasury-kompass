package ch.treasurykompass.treasury.rules

/**
 * Rule IDs for audit trail and explanations (architecture.md section 7, OpenAPI `RuleInfo`).
 *
 * @property code ID as stored in `audit_event.rule_id`, e.g. `R-SEG-01`
 * @property regelwerkSection section(s) of `docs/regelwerk.md`
 * @property legalBasis default legal basis; `null` where the catalogue has none («–»)
 */
enum class RuleId(
    val code: String,
    val title: String,
    val description: String,
    val regelwerkSection: String,
    val legalBasis: String?,
) {
    SEG_01(
        "R-SEG-01", "Kundensegment, Opting-in",
        "Grosses Unternehmen (mindestens 2 von 3: Bilanzsumme > CHF 20 Mio., Umsatzerlös > CHF 40 Mio., " +
            "Eigenkapital > CHF 2 Mio.) oder professionelle Tresorerie ergibt einen professionellen Kunden. " +
            "Mit Opting-in gilt er als Privatkunde, sonst Privatkunde.",
        "1", "Art. 4 und 5 FIDLEG",
    ),
    EXP_01(
        "R-EXP-01", "Nettoposition, Sensitivität, Risikostufe, Zinsrisiko",
        "Nettoposition = Zuflüsse − Abflüsse, Sensitivität = |Nettoposition CHF| × 10 %, Risikostufe nach " +
            "Anteil am EBIT (ab 30 % hoch, ab 10 % mittel), Zinsrisiko = Bankkredit × 1 Prozentpunkt pro Jahr.",
        "2", null,
    ),
    PRO_01(
        "R-PRO-01", "Risikofähigkeit",
        "Gerundeter Durchschnitt (half-up) aus Eigenkapitalquote, Überschussliquidität in Monaten Betriebsaufwand, " +
            "EBIT-Marge und ungesichertem Währungsrisiko, je 1 bis 5 Punkte.",
        "3", "Art. 12 FIDLEG",
    ),
    PRO_02(
        "R-PRO-02", "Risikobereitschaft",
        "Gerundeter Durchschnitt (half-up) aus Ziel der Absicherung, tragbarem Wertrückgang und Haltung zum " +
            "entgangenen Kursgewinn.",
        "3", "Art. 12 FIDLEG",
    ),
    PRO_03(
        "R-PRO-03", "Risikoprofil = min(Fähigkeit, Bereitschaft)",
        "Massgebend ist der tiefere Wert: 1 Sicherheit, 2 Konservativ, 3 Ausgewogen, 4 Wachstum, 5 Dynamisch.",
        "3", "Art. 12 FIDLEG",
    ),
    SUI_01(
        "R-SUI-01", "Eignung pro Produkt",
        "Festgeld und Callgeld ohne Prüfung. Professionelle Kunden: geeignet, Kenntnisse und Erfahrung vermutet. " +
            "Privatkunden: mit Erfahrung geeignet, nur mit Kenntnissen nach Aufklärung, ohne Kenntnisse nicht geeignet.",
        "4", "Art. 12 und 14 FIDLEG",
    ),
    SUI_02(
        "R-SUI-02", "Basisinformationsblatt nötig",
        "Bei Privatkunden für Devisentermingeschäft, Zero-Cost-Collar und Geldmarktfonds, nicht für einfache Obligationen.",
        "4", "Art. 58 FIDLEG",
    ),
    HED_01(
        "R-HED-01", "Mindestposition CHF 0.5 Mio.",
        "Abgesichert werden nur Nettopositionen ab CHF 0.5 Mio.; kleinere gelten als natürlich abgesichert.",
        "5", null,
    ),
    HED_02(
        "R-HED-02", "Obergrenze min(100 %, Policy)",
        "Die Absicherungsquote liegt nie über der Treasury-Policy und nie über 100 % der Nettoposition.",
        "5", "Treasury-Policy",
    ),
    HED_03(
        "R-HED-03", "Startquote nach Profil, vier Tranchen, Abstufung",
        "Startquote nach Profil (1 → 90 %, 2 → 80 %, 3 → 70 %, 4 → 60 %, 5 → 50 %), vier Tranchen zu 3, 6, 9 " +
            "und 12 Monaten, je Quartal um die Abstufung (Standard 10 Prozentpunkte) tiefer.",
        "5", null,
    ),
    HED_04(
        "R-HED-04", "Terminkurs und Zinsdifferenz",
        "F = S × (1 + r_CHF × t) / (1 + r_FW × t); Zinsdifferenz = abgesichertes Volumen × (1 − Collar-Anteil) × (F − S).",
        "5", "Art. 26 FIDLEG",
    ),
    HED_05(
        "R-HED-05", "Sperre ohne Eignung Devisentermingeschäft",
        "Ist das Devisentermingeschäft nicht geeignet, wird keine Absicherung empfohlen (effektive Quote 0).",
        "5", "Art. 14 FIDLEG",
    ),
    HED_06(
        "R-HED-06", "Zero-Cost-Collar",
        "Erlaubt, wenn nicht gesperrt und der Collar nicht «Nicht geeignet» ist; standardmässig aktiv, wenn der " +
            "Collar geeignet ist und der entgangene Kursgewinn nicht «akzeptabel» ist. Anteil 30 %.",
        "5", "Art. 12 und 14 FIDLEG",
    ),
    HED_07(
        "R-HED-07", "Szenario",
        "EBIT-Effekt einer Kursänderung gegenüber Kassa ohne und mit Absicherung; wirkt vereinfacht sofort auf alle Fälligkeiten.",
        "5", null,
    ),
    LIQ_01(
        "R-LIQ-01", "Mindestliquidität, Überschuss",
        "Richtwert Mindestliquidität 1.5 × monatlicher Betriebsaufwand; Überschuss = flüssige Mittel − Mindestliquidität (min. 0).",
        "3, 6", null,
    ),
    LIQ_02(
        "R-LIQ-02", "Aufteilung nach Horizont und Profil",
        "Aufteilung auf Festgeld/Callgeld, Geldmarktfonds CHF und Obligationen CHF kurz nach Anlagehorizont und Risikoprofil.",
        "6", "Art. 12 FIDLEG",
    ),
    LIQ_03(
        "R-LIQ-03", "Umschichtung bei fehlender Eignung",
        "Obligationen ohne Eignung → Geldmarktfonds (falls geeignet), sonst Festgeld; Geldmarktfonds ohne Eignung → Festgeld.",
        "6", "Art. 14 FIDLEG",
    ),
    CHK_01(
        "R-CHK-01", "Checkliste",
        "Informationen, Basisinformationsblatt je empfohlenem Produkt (Privatkunde), Risikobroschüre, " +
            "Kostenoffenlegung, Warnhinweis je empfohlenem Produkt mit Status «Nur nach Aufklärung».",
        "7", "Art. 8, 14, 26 und 58 FIDLEG",
    ),
    APP_01(
        "R-APP-01", "Freigabebedingungen",
        "Freigabe nur ohne fehlende Pflichtangaben, unbestätigte Antworten, offene Vorschläge, offene " +
            "Checklistenpunkte und Dokumente in Verarbeitung.",
        "7", "Art. 15 FIDLEG",
    ),
    APP_02(
        "R-APP-02", "Aufhebung bei Änderung",
        "Jede Änderung an Antworten, Absicherungseinstellungen, Checkliste, Begründung, Stammdaten oder Unterlagen " +
            "hebt eine aktive Freigabe auf.",
        "7", null,
    ),
}

/** Read access to the rule catalogue (`GET /api/rules`). */
object RuleCatalog {
    val rules: List<RuleId> = RuleId.entries

    fun byCode(code: String): RuleId? = RuleId.entries.firstOrNull { it.code == code }
}
