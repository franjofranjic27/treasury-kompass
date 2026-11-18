"""Question keys of the FIDLEG questionnaire (catalog lives in the treasury-service, architecture.md section 6)."""

from __future__ import annotations

from typing import Final, Literal, get_args

QuestionKey = Literal[
    "fin.revenue",
    "fin.totalAssets",
    "fin.equity",
    "fin.ebit",
    "fin.cash",
    "fin.operatingExpenses",
    "fin.floatingRateDebt",
    "fx.eur.inflow",
    "fx.eur.outflow",
    "fx.usd.inflow",
    "fx.usd.outflow",
    "segment.treasuryOrganisation",
    "segment.optingIn",
    "goals.hedgeObjective",
    "goals.policyMaxHedgeRatio",
    "goals.minimumLiquidity",
    "goals.investmentHorizon",
    "risk.tolerableDrawdown",
    "risk.forgoneGainAttitude",
    "knowledge.fxForward",
    "knowledge.zeroCostCollar",
    "knowledge.moneyMarketFund",
    "knowledge.shortTermBond",
]

# Only these keys may come out of document extraction (`extractable` in the catalog).
ExtractableKey = Literal[
    "fin.revenue",
    "fin.totalAssets",
    "fin.equity",
    "fin.ebit",
    "fin.cash",
    "fin.operatingExpenses",
    "fin.floatingRateDebt",
    "fx.eur.inflow",
    "fx.eur.outflow",
    "fx.usd.inflow",
    "fx.usd.outflow",
    "segment.treasuryOrganisation",
    "knowledge.fxForward",
    "knowledge.zeroCostCollar",
    "knowledge.moneyMarketFund",
    "knowledge.shortTermBond",
]

Confidence = Literal["HIGH", "MEDIUM", "LOW"]
# int stays int on the wire: PERCENT answers must be whole numbers for the treasury-service.
AnswerValue = bool | int | float | str

QUESTION_KEYS: Final[frozenset[str]] = frozenset(get_args(QuestionKey))
EXTRACTABLE_KEYS: Final[frozenset[str]] = frozenset(get_args(ExtractableKey))

AMOUNT_KEYS: Final[frozenset[str]] = frozenset(key for key in EXTRACTABLE_KEYS if key.startswith(("fin.", "fx.")))
FX_FLOW_KEYS: Final[frozenset[str]] = frozenset(key for key in EXTRACTABLE_KEYS if key.startswith("fx."))
NEGATIVE_AMOUNT_KEYS: Final[frozenset[str]] = frozenset({"fin.equity", "fin.ebit"})

TREASURY_ORGANISATION_OPTIONS: Final[frozenset[str]] = frozenset(
    {"TREASURY_DEPARTMENT", "CFO_OR_ACCOUNTING", "OUTSOURCED"}
)
KNOWLEDGE_LEVEL_OPTIONS: Final[frozenset[str]] = frozenset({"NONE", "KNOWLEDGE", "EXPERIENCE"})

CONFIDENCE_ORDER: Final[dict[str, int]] = {"LOW": 0, "MEDIUM": 1, "HIGH": 2}
