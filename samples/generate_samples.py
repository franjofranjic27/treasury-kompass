# /// script
# requires-python = ">=3.12"
# dependencies = ["reportlab==5.1.0"]
# ///
"""Erzeugt die synthetischen Beispiel-PDFs für Treasury Kompass.

Aufruf aus dem Repo-Root:  uv run samples/generate_samples.py
Alle Firmen, Personen und Zahlen sind erfunden (siehe samples/README.md). Die Ausgabe ist
reproduzierbar: Bei gleichem Inhalt entstehen byte-identische PDFs (stabiler SHA-256).
"""

from __future__ import annotations

import argparse
import sys
from collections.abc import Callable
from pathlib import Path

from generator import beispiel_components, injection_test, muster_maschinenbau, treasury_policies
from generator.layout import DocumentSpec, render

SAMPLES_DIR = Path(__file__).resolve().parent

BUILDERS: tuple[Callable[[], DocumentSpec], ...] = (
    muster_maschinenbau.build,
    beispiel_components.build,
    treasury_policies.build_muster,
    treasury_policies.build_components,
    injection_test.build,
)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--out", type=Path, default=SAMPLES_DIR, help="Zielordner (Standard: samples/)")
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    for build in BUILDERS:
        spec = build()
        path = render(spec, args.out)
        print(f"{path.name}: {len(spec.pages)} Seiten")
    return 0


if __name__ == "__main__":
    sys.exit(main())
