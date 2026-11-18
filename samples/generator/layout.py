"""Deklarative Seitenbausteine und Rendering mit reportlab.

Die Inhaltsmodule beschreiben jede PDF-Seite als `Page` mit Blöcken. `render` setzt jede `Page`
auf genau eine PDF-Seite. Passt der Inhalt nicht, bricht der Lauf ab: Die Seitenzahlen sind
Fundstellen, auf die sich Extraktion und Tests verlassen.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from enum import Enum
from pathlib import Path
from xml.sax.saxutils import escape

from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT, TA_RIGHT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.units import mm
from reportlab.pdfgen.canvas import Canvas
from reportlab.platypus import (
    BaseDocTemplate,
    LayoutError,
    Flowable,
    Frame,
    KeepInFrame,
    PageBreak,
    PageTemplate,
    Paragraph,
    Spacer,
    Table,
    TableStyle,
)

FICTION_MARK = "Fiktives Beispiel – Hackathon"
AUTHOR = "Treasury Kompass, Hackathon (fiktive Beispieldaten)"
CREATOR = "samples/generate_samples.py"

PAGE_WIDTH, PAGE_HEIGHT = A4
MARGIN_X = 20 * mm
MARGIN_TOP = 24 * mm
MARGIN_BOTTOM = 20 * mm
CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN_X
CONTENT_HEIGHT = PAGE_HEIGHT - MARGIN_TOP - MARGIN_BOTTOM

FONT = "Helvetica"
FONT_BOLD = "Helvetica-Bold"
TEXT_COLOR = colors.HexColor("#1D232B")
MUTED = colors.HexColor("#5A6270")
RULE = colors.HexColor("#C3C9D2")
ZEBRA = colors.HexColor("#F3F5F8")


# ---------------------------------------------------------------------------
# Bausteine
# ---------------------------------------------------------------------------


class TextStyle(Enum):
    BODY = "body"
    LEAD = "lead"
    SMALL = "small"
    TITLE = "title"
    SUBTITLE = "subtitle"


class RowKind(Enum):
    HEADING = "heading"
    ITEM = "item"
    SUBITEM = "subitem"
    SUBTOTAL = "subtotal"
    TOTAL = "total"


@dataclass(frozen=True)
class Heading:
    text: str
    level: int = 1


@dataclass(frozen=True)
class Text:
    text: str
    style: TextStyle = TextStyle.BODY


@dataclass(frozen=True)
class Bullets:
    items: tuple[str, ...]


@dataclass(frozen=True)
class Space:
    height_mm: float


@dataclass(frozen=True)
class Hidden:
    """Weisser Text in 1 pt: unsichtbar im Viewer, aber im Textlayer (Prompt-Injection-Test)."""

    text: str


@dataclass(frozen=True)
class Row:
    label: str
    values: tuple[str, ...] = ()
    kind: RowKind = RowKind.ITEM


@dataclass(frozen=True)
class Statement:
    """Abschlusstabelle (Bilanz, Erfolgsrechnung, Geldfluss) mit Zwischen- und Endsummen."""

    columns: tuple[str, ...]
    rows: tuple[Row, ...]
    widths_mm: tuple[float, ...]


@dataclass(frozen=True)
class Grid:
    """Allgemeine Tabelle mit Kopfzeile; Spalten ab `numeric_from` rechtsbündig."""

    header: tuple[str, ...]
    rows: tuple[tuple[str, ...], ...]
    widths_mm: tuple[float, ...]
    numeric_from: int = 1
    total_last: bool = False


@dataclass(frozen=True)
class KeyFigures:
    """Kachelraster für Kennzahlen: (Wert, Bezeichnung, Vorjahr)."""

    tiles: tuple[tuple[str, str, str], ...]
    columns: int = 3


Block = Heading | Text | Bullets | Space | Hidden | Statement | Grid | KeyFigures


@dataclass(frozen=True)
class Page:
    section: str
    blocks: tuple[Block, ...]
    cover: bool = False


@dataclass(frozen=True)
class Theme:
    accent: colors.Color


@dataclass(frozen=True)
class DocumentSpec:
    filename: str
    title: str
    subject: str
    running_header: str
    theme: Theme
    pages: tuple[Page, ...]


class PageOverflowError(RuntimeError):
    """Inhalt einer `Page` passt nicht auf eine PDF-Seite."""


class MisplacedFactError(RuntimeError):
    """Eine Fundstelle steht nicht auf der Seite, die Prototyp und README nennen."""


def page_text(page: Page) -> str:
    """Sichtbarer und versteckter Text einer Seite, für Prüfungen vor dem Rendern."""
    parts: list[str] = [page.section]
    for block in page.blocks:
        match block:
            case Heading() | Text() | Hidden():
                parts.append(block.text)
            case Bullets():
                parts.extend(block.items)
            case Statement():
                parts.extend(block.columns)
                parts.extend(" ".join((row.label, *row.values)) for row in block.rows)
            case Grid():
                parts.append(" ".join(block.header))
                parts.extend(" ".join(row) for row in block.rows)
            case KeyFigures():
                parts.extend(" ".join(tile) for tile in block.tiles)
    return "\n".join(parts)


def require_on_page(spec: DocumentSpec, page_number: int, *snippets: str) -> None:
    """Prüft Textstellen auf einer Seite; Leerraum zählt wie in der Fundstellen-Prüfung nicht."""
    text = _normalize(page_text(spec.pages[page_number - 1]))
    for snippet in snippets:
        if _normalize(snippet) not in text:
            raise MisplacedFactError(f"{spec.filename}: «{snippet}» fehlt auf S. {page_number}")


def _normalize(text: str) -> str:
    return " ".join(text.split())


# ---------------------------------------------------------------------------
# Rendering
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class _Styles:
    body: ParagraphStyle
    lead: ParagraphStyle
    small: ParagraphStyle
    title: ParagraphStyle
    subtitle: ParagraphStyle
    h1: ParagraphStyle
    h2: ParagraphStyle
    h3: ParagraphStyle
    bullet: ParagraphStyle
    hidden: ParagraphStyle
    cell: ParagraphStyle
    cell_right: ParagraphStyle
    cell_head: ParagraphStyle
    cell_head_right: ParagraphStyle
    tile_value: ParagraphStyle
    tile_label: ParagraphStyle


def _styles(theme: Theme) -> _Styles:
    base = ParagraphStyle("base", fontName=FONT, fontSize=9.5, leading=13.4, textColor=TEXT_COLOR)
    cell = ParagraphStyle("cell", parent=base, fontSize=8.6, leading=10.8)
    head = ParagraphStyle("cell_head", parent=cell, fontName=FONT_BOLD, textColor=MUTED)
    return _Styles(
        body=ParagraphStyle("body", parent=base, spaceAfter=5),
        lead=ParagraphStyle("lead", parent=base, fontSize=11, leading=15.5, spaceAfter=7),
        small=ParagraphStyle("small", parent=base, fontSize=8, leading=10.5, textColor=MUTED, spaceAfter=4),
        title=ParagraphStyle(
            "title", parent=base, fontName=FONT_BOLD, fontSize=30, leading=36, textColor=theme.accent, spaceAfter=10
        ),
        subtitle=ParagraphStyle("subtitle", parent=base, fontSize=15, leading=20, spaceAfter=8),
        h1=ParagraphStyle(
            "h1", parent=base, fontName=FONT_BOLD, fontSize=17, leading=21, textColor=theme.accent, spaceAfter=8
        ),
        h2=ParagraphStyle(
            "h2", parent=base, fontName=FONT_BOLD, fontSize=11.2, leading=14.5, spaceBefore=7, spaceAfter=3,
            textColor=theme.accent,
        ),
        h3=ParagraphStyle("h3", parent=base, fontName=FONT_BOLD, fontSize=9.8, leading=13, spaceBefore=4, spaceAfter=2),
        bullet=ParagraphStyle("bullet", parent=base, leftIndent=11, bulletIndent=2, spaceAfter=2.5),
        hidden=ParagraphStyle("hidden", parent=base, fontSize=1, leading=1.2, textColor=colors.white),
        cell=cell,
        cell_right=ParagraphStyle("cell_right", parent=cell, alignment=TA_RIGHT),
        cell_head=ParagraphStyle("cell_head_left", parent=head, alignment=TA_LEFT),
        cell_head_right=ParagraphStyle("cell_head_right", parent=head, alignment=TA_RIGHT),
        tile_value=ParagraphStyle(
            "tile_value", parent=base, fontName=FONT_BOLD, fontSize=17, leading=21, textColor=theme.accent
        ),
        tile_label=ParagraphStyle("tile_label", parent=base, fontSize=8.4, leading=11, textColor=MUTED),
    )


def _para(text: str, style: ParagraphStyle) -> Paragraph:
    return Paragraph(escape(text), style)


def _heading(block: Heading, s: _Styles) -> Flowable:
    style = {1: s.h1, 2: s.h2, 3: s.h3}[block.level]
    return _para(block.text, style)


def _text(block: Text, s: _Styles) -> Flowable:
    style = {
        TextStyle.BODY: s.body,
        TextStyle.LEAD: s.lead,
        TextStyle.SMALL: s.small,
        TextStyle.TITLE: s.title,
        TextStyle.SUBTITLE: s.subtitle,
    }[block.style]
    return _para(block.text, style)


def _bullets(block: Bullets, s: _Styles) -> list[Flowable]:
    return [Paragraph(escape(item), s.bullet, bulletText="•") for item in block.items]


def _widths(widths_mm: Sequence[float], column_count: int) -> list[float]:
    if len(widths_mm) != column_count:
        raise ValueError(f"{column_count} Spalten, aber {len(widths_mm)} Breiten")
    widths = [w * mm for w in widths_mm]
    if sum(widths) > CONTENT_WIDTH + 0.5:
        raise ValueError(f"Tabelle ist breiter als der Satzspiegel ({sum(widths_mm):.0f} mm)")
    return widths


def _statement(block: Statement, s: _Styles, theme: Theme) -> Flowable:
    widths = _widths(block.widths_mm, len(block.columns))
    value_count = len(block.columns) - 1
    data: list[list[Flowable]] = [
        [_para(block.columns[0], s.cell_head)] + [_para(c, s.cell_head_right) for c in block.columns[1:]]
    ]
    commands: list[tuple] = [
        ("LINEBELOW", (0, 0), (-1, 0), 0.8, theme.accent),
        ("VALIGN", (0, 0), (-1, -1), "BOTTOM"),
        ("TOPPADDING", (0, 0), (-1, -1), 1.6),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 1.6),
        ("LEFTPADDING", (0, 0), (-1, -1), 2),
        ("RIGHTPADDING", (0, 0), (-1, -1), 2),
    ]
    for index, row in enumerate(block.rows, start=1):
        if row.kind is not RowKind.HEADING and len(row.values) != value_count:
            raise ValueError(f"Zeile «{row.label}»: {len(row.values)} Werte, erwartet {value_count}")
        bold = row.kind in (RowKind.HEADING, RowKind.SUBTOTAL, RowKind.TOTAL)
        label_style = ParagraphStyle(
            f"label_{row.kind.value}",
            parent=s.cell,
            fontName=FONT_BOLD if bold else FONT,
            leftIndent=10 if row.kind is RowKind.SUBITEM else 0,
            textColor=theme.accent if row.kind is RowKind.HEADING else TEXT_COLOR,
        )
        value_style = ParagraphStyle(
            f"value_{row.kind.value}", parent=s.cell_right, fontName=FONT_BOLD if bold else FONT
        )
        values = row.values if row.kind is not RowKind.HEADING else ("",) * value_count
        data.append([_para(row.label, label_style)] + [_para(v, value_style) for v in values])
        if row.kind is RowKind.HEADING:
            commands.append(("TOPPADDING", (0, index), (-1, index), 7))
        elif row.kind is RowKind.SUBTOTAL:
            commands.append(("LINEABOVE", (1, index), (-1, index), 0.5, RULE))
        elif row.kind is RowKind.TOTAL:
            commands.append(("LINEABOVE", (0, index), (-1, index), 0.9, TEXT_COLOR))
            commands.append(("LINEBELOW", (0, index), (-1, index), 0.9, TEXT_COLOR))
    table = Table(data, colWidths=widths, hAlign="LEFT", repeatRows=0)
    table.setStyle(TableStyle(commands))
    return table


def _grid(block: Grid, s: _Styles, theme: Theme) -> Flowable:
    widths = _widths(block.widths_mm, len(block.header))

    def cell(text: str, column: int, header: bool) -> Paragraph:
        right = column >= block.numeric_from
        if header:
            return _para(text, s.cell_head_right if right else s.cell_head)
        return _para(text, s.cell_right if right else s.cell)

    data = [[cell(h, i, True) for i, h in enumerate(block.header)]]
    for row in block.rows:
        if len(row) != len(block.header):
            raise ValueError(f"Zeile {row!r}: {len(row)} Zellen, erwartet {len(block.header)}")
        data.append([cell(text, i, False) for i, text in enumerate(row)])
    commands: list[tuple] = [
        ("LINEBELOW", (0, 0), (-1, 0), 0.8, theme.accent),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("TOPPADDING", (0, 0), (-1, -1), 2.2),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 2.2),
        ("LEFTPADDING", (0, 0), (-1, -1), 3),
        ("RIGHTPADDING", (0, 0), (-1, -1), 3),
        ("LINEBELOW", (0, 1), (-1, -1), 0.3, RULE),
    ]
    if block.total_last:
        commands.append(("LINEABOVE", (0, -1), (-1, -1), 0.9, TEXT_COLOR))
        last = len(data) - 1
        bold_cell = ParagraphStyle("total_cell", parent=s.cell, fontName=FONT_BOLD)
        bold_cell_right = ParagraphStyle("total_cell_right", parent=s.cell_right, fontName=FONT_BOLD)
        data[last] = [
            _para(text, bold_cell_right if i >= block.numeric_from else bold_cell)
            for i, text in enumerate(block.rows[-1])
        ]
    table = Table(data, colWidths=widths, hAlign="LEFT")
    table.setStyle(TableStyle(commands))
    return table


def _key_figures(block: KeyFigures, s: _Styles) -> Flowable:
    width = CONTENT_WIDTH / block.columns
    cells = [
        [_para(value, s.tile_value), _para(label, s.tile_label), _para(prior, s.tile_label)]
        for value, label, prior in block.tiles
    ]
    rows = [cells[i : i + block.columns] for i in range(0, len(cells), block.columns)]
    if rows and len(rows[-1]) < block.columns:
        rows[-1] = rows[-1] + [[""]] * (block.columns - len(rows[-1]))
    table = Table(rows, colWidths=[width] * block.columns, hAlign="LEFT")
    table.setStyle(
        TableStyle(
            [
                ("VALIGN", (0, 0), (-1, -1), "TOP"),
                ("BACKGROUND", (0, 0), (-1, -1), ZEBRA),
                ("LINEBELOW", (0, 0), (-1, -1), 4, colors.white),
                ("LINEAFTER", (0, 0), (-1, -1), 4, colors.white),
                ("TOPPADDING", (0, 0), (-1, -1), 8),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 8),
                ("LEFTPADDING", (0, 0), (-1, -1), 8),
            ]
        )
    )
    return table


def _flowables(block: Block, s: _Styles, theme: Theme) -> list[Flowable]:
    match block:
        case Heading():
            return [_heading(block, s)]
        case Text():
            return [_text(block, s)]
        case Bullets():
            return _bullets(block, s)
        case Space():
            return [Spacer(1, block.height_mm * mm)]
        case Hidden():
            return [_para(block.text, s.hidden)]
        case Statement():
            return [_statement(block, s, theme), Spacer(1, 3 * mm)]
        case Grid():
            return [_grid(block, s, theme), Spacer(1, 3 * mm)]
        case KeyFigures():
            return [_key_figures(block, s), Spacer(1, 3 * mm)]
    raise TypeError(f"Unbekannter Block: {block!r}")


def _decorator(spec: DocumentSpec):
    def decorate(canvas: Canvas, _doc: BaseDocTemplate) -> None:
        page_number = canvas.getPageNumber()
        page = spec.pages[page_number - 1]
        canvas.saveState()
        if page.cover:
            canvas.setFillColor(spec.theme.accent)
            canvas.rect(0, PAGE_HEIGHT - 18 * mm, PAGE_WIDTH, 18 * mm, stroke=0, fill=1)
        else:
            canvas.setFont(FONT, 7.8)
            canvas.setFillColor(MUTED)
            header_y = PAGE_HEIGHT - 14 * mm
            canvas.drawString(MARGIN_X, header_y, spec.running_header)
            canvas.drawRightString(PAGE_WIDTH - MARGIN_X, header_y, page.section)
            canvas.setStrokeColor(RULE)
            canvas.setLineWidth(0.5)
            canvas.line(MARGIN_X, header_y - 2.5 * mm, PAGE_WIDTH - MARGIN_X, header_y - 2.5 * mm)
            canvas.drawRightString(PAGE_WIDTH - MARGIN_X, 11 * mm, str(page_number))
        canvas.setFont(FONT_BOLD, 7.8)
        canvas.setFillColor(MUTED)
        canvas.drawString(MARGIN_X, 11 * mm, FICTION_MARK)
        canvas.restoreState()

    return decorate


def render(spec: DocumentSpec, out_dir: Path) -> Path:
    """Schreibt das PDF und liefert den Pfad. Byte-identisch bei gleichem Inhalt (invariant)."""
    path = out_dir / spec.filename
    styles = _styles(spec.theme)
    doc = BaseDocTemplate(
        str(path),
        pagesize=A4,
        leftMargin=MARGIN_X,
        rightMargin=MARGIN_X,
        topMargin=MARGIN_TOP,
        bottomMargin=MARGIN_BOTTOM,
        title=spec.title,
        subject=spec.subject,
        author=AUTHOR,
        creator=CREATOR,
        keywords=f"{FICTION_MARK}; Treasury Kompass; Beispieldaten",
        lang="de-CH",
        invariant=1,
    )
    frame = Frame(
        MARGIN_X, MARGIN_BOTTOM, CONTENT_WIDTH, CONTENT_HEIGHT,
        id="content", leftPadding=0, rightPadding=0, topPadding=0, bottomPadding=0,
    )
    doc.addPageTemplates([PageTemplate(id="page", frames=[frame], onPage=_decorator(spec))])

    story: list[Flowable] = []
    for number, page in enumerate(spec.pages, start=1):
        content = [f for block in page.blocks for f in _flowables(block, styles, spec.theme)]
        story.append(
            KeepInFrame(
                CONTENT_WIDTH, CONTENT_HEIGHT - 2, content, mode="error", name=f"{spec.filename}, S. {number}"
            )
        )
        if number < len(spec.pages):
            story.append(PageBreak())
    try:
        doc.build(story)
    except LayoutError as error:
        raise PageOverflowError(f"{spec.filename}: {error}") from error
    if doc.page != len(spec.pages):
        raise PageOverflowError(f"{spec.filename}: {doc.page} PDF-Seiten statt {len(spec.pages)}")
    return path
