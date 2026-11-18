"""Minimal PDF writer for tests: one Helvetica text page per entry, WinAnsi encoding for German umlauts."""

from __future__ import annotations

from collections.abc import Sequence


def _escape(line: str) -> bytes:
    encoded = line.encode("cp1252")
    return encoded.replace(b"\\", b"\\\\").replace(b"(", b"\\(").replace(b")", b"\\)")


def _content_stream(lines: Sequence[str]) -> bytes:
    return b"BT /F1 10 Tf 14 TL 50 800 Td " + b" ".join(b"(" + _escape(line) + b") Tj T*" for line in lines) + b" ET"


def build_pdf(pages: Sequence[Sequence[str]]) -> bytes:
    """`pages[i]` are the text lines of page i + 1. An empty list gives a page without text layer."""
    objects: list[bytes] = []
    page_count = len(pages)
    first_page_object = 4
    kids = " ".join(f"{first_page_object + 2 * index} 0 R" for index in range(page_count))
    objects.append(b"<< /Type /Catalog /Pages 2 0 R >>")
    objects.append(f"<< /Type /Pages /Kids [{kids}] /Count {page_count} >>".encode())
    objects.append(b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>")
    for index, lines in enumerate(pages):
        content_object = first_page_object + 2 * index + 1
        objects.append(
            f"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 3 0 R >> >> "
            f"/Contents {content_object} 0 R >>".encode()
        )
        stream = _content_stream(lines) if lines else b""
        objects.append(b"<< /Length " + str(len(stream)).encode() + b" >>\nstream\n" + stream + b"\nendstream")

    output = bytearray(b"%PDF-1.7\n%\xe2\xe3\xcf\xd3\n")
    offsets: list[int] = []
    for number, body in enumerate(objects, start=1):
        offsets.append(len(output))
        output += f"{number} 0 obj\n".encode() + body + b"\nendobj\n"
    xref_offset = len(output)
    output += f"xref\n0 {len(objects) + 1}\n0000000000 65535 f \n".encode()
    for offset in offsets:
        output += f"{offset:010d} 00000 n \n".encode()
    output += f"trailer\n<< /Size {len(objects) + 1} /Root 1 0 R >>\nstartxref\n{xref_offset}\n%%EOF\n".encode()
    return bytes(output)
