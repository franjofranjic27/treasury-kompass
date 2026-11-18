"""Zahlenformat (Schweizer Schreibweise) und Konsistenzprüfungen der Beispielabschlüsse."""

from __future__ import annotations

from collections.abc import Iterable, Sequence
from decimal import ROUND_HALF_UP, Decimal

Amount = int | Decimal
Pair = tuple[Amount, Amount]
"""Betrag Berichtsjahr und Vorjahr."""

_ONE_DECIMAL = Decimal("0.1")
_TWO_DECIMALS = Decimal("0.01")
_MILLION = Decimal(1_000_000)


class InconsistentFigureError(ValueError):
    """Eine Summe oder ein Zielwert aus docs/regelwerk.md stimmt nicht."""


def chf(value: int) -> str:
    """Ganze Franken mit Apostroph als Tausendertrennzeichen, z.B. 28'400'540."""
    sign = "-" if value < 0 else ""
    return f"{sign}{abs(value):,}".replace(",", "'")


def mio(value: Decimal) -> str:
    """Millionen mit einer Nachkommastelle, z.B. 146.0."""
    rounded = value.quantize(_ONE_DECIMAL, rounding=ROUND_HALF_UP)
    sign = "-" if rounded < 0 else ""
    return f"{sign}{abs(rounded):,.1f}".replace(",", "'")


def signed_mio(value: Decimal) -> str:
    """Wie `mio`, positive Werte mit Pluszeichen (Nettopositionen)."""
    return f"+{mio(value)}" if value > 0 else mio(value)


def percent(value: Decimal, digits: int = 1) -> str:
    quantum = Decimal(1).scaleb(-digits)
    return f"{value.quantize(quantum, rounding=ROUND_HALF_UP)} %"


def ratio(numerator: Amount, denominator: Amount, digits: int = 1) -> Decimal:
    """Anteil in Prozent, kaufmännisch gerundet."""
    quantum = Decimal(1).scaleb(-digits)
    return (Decimal(numerator) * 100 / Decimal(denominator)).quantize(quantum, rounding=ROUND_HALF_UP)


def growth(current: Amount, prior: Amount, digits: int = 1) -> Decimal:
    return ratio(Decimal(current) - Decimal(prior), prior, digits)


def total(pairs: Iterable[Pair]) -> Pair:
    current, prior = zip(*pairs, strict=True)
    return sum(current), sum(prior)


def require_equal(label: str, actual: Amount, expected: Amount) -> None:
    if actual != expected:
        raise InconsistentFigureError(f"{label}: berechnet {actual}, erwartet {expected}")


def require_millions(label: str, francs: int, expected: str) -> None:
    """Betrag in CHF muss auf 2 Nachkommastellen in Mio. exakt dem Zielwert entsprechen.

    Die Abweichung bleibt unter CHF 1'000, damit auch ungerundet weiterverarbeitete Werte
    die Testfälle aus docs/regelwerk.md Abschnitt 9 treffen.
    """
    target = Decimal(expected)
    value = Decimal(francs) / _MILLION
    if value.quantize(_TWO_DECIMALS, rounding=ROUND_HALF_UP) != target.quantize(_TWO_DECIMALS):
        raise InconsistentFigureError(f"{label}: {value} Mio. gerundet ungleich {expected}")
    if abs(value - target) >= Decimal("0.001"):
        raise InconsistentFigureError(f"{label}: {value} Mio. weicht mehr als CHF 1'000 von {expected} ab")


def shares(values: Sequence[Amount], digits: int = 1) -> list[Decimal]:
    """Prozentanteile, die sich exakt auf 100 summieren (Methode des grössten Rests)."""
    quantum = Decimal(1).scaleb(-digits)
    whole = sum(Decimal(v) for v in values)
    raw = [Decimal(v) * 100 / whole for v in values]
    floored = [r.quantize(quantum, rounding="ROUND_FLOOR") for r in raw]
    missing = int(((Decimal(100) - sum(floored)) / quantum).to_integral_value())
    by_remainder = sorted(range(len(raw)), key=lambda i: raw[i] - floored[i], reverse=True)
    for index in by_remainder[:missing]:
        floored[index] += quantum
    return floored
