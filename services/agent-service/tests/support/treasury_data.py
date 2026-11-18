"""Responses of the treasury-service for the two fictional demo clients (architecture.md section 13).

Only the parts the agent reads; values follow docs/regelwerk.md and use-cases.md (UC-05, UC-09).
"""

from __future__ import annotations

from uuid import UUID

MUSTER_ID = UUID("00000000-0000-4000-8000-000000000001")
COMPONENTS_ID = UUID("00000000-0000-4000-8000-000000000002")
TIMESTAMP = "2026-11-18T09:00:00Z"


def client_detail(client_id: UUID, name: str) -> dict[str, object]:
    return {
        "id": str(client_id),
        "name": name,
        "industry": "Maschinenbau",
        "employeeCount": 140,
        "fiscalYear": 2025,
        "shortLabel": "Export-KMU",
        "createdAt": TIMESTAMP,
        "updatedAt": TIMESTAMP,
        "segment": "RETAIL",
        "riskProfile": "CONSERVATIVE",
        "documentCount": 1,
        "openItemCount": 17,
        "approvalStatus": "NOT_APPROVED",
        "assessmentVersion": 3,
        "keyFigures": [],
    }


def _tranches(ratios: list[int]) -> list[dict[str, object]]:
    rates = [0.935323, 0.930679, 0.926094, 0.921569]
    return [
        {
            "index": index + 1,
            "maturityMonths": 3 * (index + 1),
            "tenorYears": 0.25 * (index + 1),
            "forwardRate": rates[index],
            "ratio": ratio,
            "effectiveRatio": ratio,
            "amountFx": 3.55,
            "hedgedAmountFx": round(3.55 * ratio / 100, 4),
            "collarFloor": 0.0,
            "collarCap": 0.0,
            "carryChf": -0.0133,
        }
        for index, ratio in enumerate(ratios)
    ]


def muster_assessment(*, ratios: list[int] | None = None, version: int = 3) -> dict[str, object]:
    eur_ratios = ratios or [80, 70, 60, 50]
    return {
        "clientId": str(MUSTER_ID),
        "version": version,
        "computedAt": TIMESTAMP,
        "status": "COMPLETE",
        "missingInputs": [],
        "marketData": {"source": "DEMO", "asOf": "2026-11-18", "spotRates": {"EUR": 0.94, "USD": 0.8}},
        "segment": {
            "result": "RETAIL",
            "label": "Privatkunde",
            "largeCompany": False,
            "thresholdsExceeded": 1,
            "thresholds": [
                {"metric": "TOTAL_ASSETS", "label": "Bilanzsumme", "value": 19.2, "limit": 20, "exceeded": False},
                {"metric": "REVENUE", "label": "Umsatzerlös", "value": 28.4, "limit": 40, "exceeded": False},
                {"metric": "EQUITY", "label": "Eigenkapital", "value": 7.9, "limit": 2, "exceeded": True},
            ],
            "professionalTreasury": False,
            "professionalBase": False,
            "optingIn": False,
            "reason": "1 von 3 Schwellenwerten überschritten, keine professionelle Tresorerie",
            "legalBasis": "Art. 4 und 5 FIDLEG",
            "decisionSteps": [
                {
                    "question": "Grosses Unternehmen?",
                    "answer": "Nein",
                    "reason": "1 von 3 Schwellenwerten überschritten, mindestens 2 nötig",
                    "legalBasis": "Art. 4 Abs. 5 FIDLEG",
                }
            ],
            "consequences": [],
        },
        "hedges": {
            "cap": 80,
            "proposals": [
                {
                    "currency": "EUR",
                    "direction": "NET_INFLOW",
                    "hedgeAction": "SELL_FORWARD",
                    "actionLabel": "EUR auf Termin verkaufen",
                    "netPosition": 14.2,
                    "defaultStartRatio": 80,
                    "requestedStartRatio": None,
                    "startRatio": eur_ratios[0],
                    "limitedByPolicy": False,
                    "stepDown": 10,
                    "blocked": False,
                    "collarAllowed": False,
                    "collarEnabled": False,
                    "collarShare": 0,
                    "tranches": _tranches(eur_ratios),
                    "averageRatio": sum(eur_ratios) / 4,
                }
            ],
            "naturallyHedged": [],
            "totals": {"hedgedVolumeChf": 8.6762, "carryChf": -0.0987},
        },
        "recommendedProducts": ["FX_FORWARD", "MONEY_MARKET_FUND", "SHORT_TERM_BOND", "DEPOSIT"],
        "checklist": [{"id": "info", "text": "Informationspflichten erfüllt (Art. 8 FIDLEG)", "done": False}],
        "reasons": ["Die Muster Maschinenbau AG ist Privatkunde nach Art. 4 FIDLEG."],
        "approval": {
            "status": "NOT_APPROVED",
            "canApprove": False,
            "blockers": [
                {"type": "UNCONFIRMED_ANSWER", "key": "fin.revenue", "text": "Bestätigen: Umsatzerlös"},
                {"type": "OPEN_CHECKLIST_ITEM", "key": "info", "text": "Checkliste: Informationspflichten"},
            ],
        },
    }


def incomplete_assessment(client_id: UUID) -> dict[str, object]:
    return {
        "clientId": str(client_id),
        "version": 1,
        "computedAt": TIMESTAMP,
        "status": "INCOMPLETE",
        "missingInputs": ["fin.revenue", "goals.policyMaxHedgeRatio"],
        "marketData": {},
        "segment": None,
        "recommendedProducts": [],
        "checklist": [],
        "reasons": [],
        "approval": {"status": "NOT_APPROVED", "canApprove": False, "blockers": []},
    }


def muster_audit_page() -> dict[str, object]:
    return {
        "items": [
            {
                "id": 12,
                "clientId": str(MUSTER_ID),
                "occurredAt": TIMESTAMP,
                "actorType": "RULE",
                "actorName": "Regel",
                "eventType": "RULE_DECISION",
                "message": "Kundensegment Privatkunde: 1 von 3 Schwellenwerten überschritten, keine professionelle "
                "Tresorerie",
                "ruleId": "R-SEG-01",
                "legalBasis": "Art. 4 und 5 FIDLEG",
                "assessmentVersion": 3,
            }
        ],
        "nextBefore": None,
    }


def questionnaire(client_id: UUID) -> dict[str, object]:
    return {
        "clientId": str(client_id),
        "assessmentVersion": 3,
        "sections": [
            {
                "id": "FINANCIALS",
                "title": "Finanzielle Verhältnisse",
                "legalBasis": "Art. 12 FIDLEG",
                "questions": [
                    {
                        "key": "fin.totalAssets",
                        "section": "FINANCIALS",
                        "text": "Wie hoch ist die Bilanzsumme?",
                        "shortLabel": "Bilanzsumme",
                        "type": "AMOUNT",
                        "unit": "CHF Mio.",
                        "options": [],
                        "required": True,
                        "visible": True,
                        "extractable": True,
                        "openProposals": [],
                    }
                ],
            },
            {
                "id": "GOALS",
                "title": "Ziele und Bedürfnisse",
                "legalBasis": "Art. 12 FIDLEG",
                "questions": [
                    {
                        "key": "goals.policyMaxHedgeRatio",
                        "section": "GOALS",
                        "text": "Welche maximale Absicherungsquote erlaubt die Treasury-Policy?",
                        "shortLabel": "Maximale Absicherungsquote",
                        "type": "PERCENT",
                        "unit": "%",
                        "options": [{"value": value, "label": f"{value} %"} for value in (60, 80, 90, 100)],
                        "required": True,
                        "visible": True,
                        "extractable": False,
                        "answer": {
                            "questionKey": "goals.policyMaxHedgeRatio",
                            "value": 80,
                            "status": "CONFIRMED",
                            "source": "ADVISOR",
                            "updatedAt": TIMESTAMP,
                        },
                        "openProposals": [],
                    }
                ],
            },
        ],
        "progress": {
            "total": 23,
            "visible": 22,
            "answered": 22,
            "confirmed": 11,
            "proposed": 11,
            "openProposals": 0,
            "missingRequired": 0,
        },
    }


def rules() -> list[dict[str, object]]:
    return [
        {
            "id": "R-SEG-01",
            "title": "Kundensegment, Opting-in",
            "description": "Grosses Unternehmen: mindestens 2 von 3 Schwellenwerten.",
            "regelwerkSection": "1",
            "legalBasis": "Art. 4 und 5 FIDLEG",
        },
        {
            "id": "R-HED-02",
            "title": "Obergrenze min(100 %, Policy)",
            "description": "Die Absicherungsquote ist durch die Treasury-Policy begrenzt.",
            "regelwerkSection": "5",
            "legalBasis": "Treasury-Policy",
        },
    ]
