#!/usr/bin/env python3
"""Create a secret-free Markdown summary from downloaded JUnit XML."""

from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path


root = Path(sys.argv[1] if len(sys.argv) > 1 else "build/compatibility/downloaded-results")
rows: list[tuple[str, str, str, str]] = []
for path in root.rglob("*.xml"):
    try:
        suite = ET.parse(path).getroot()
    except ET.ParseError:
        continue
    if suite.tag not in {"testsuite", "testsuites"}:
        continue
    suites = [suite] if suite.tag == "testsuite" else list(suite.findall("testsuite"))
    for item in suites:
        rows.append(
            (
                path.relative_to(root).as_posix(),
                item.attrib.get("tests", "0"),
                item.attrib.get("failures", "0"),
                item.attrib.get("errors", "0"),
            )
        )

print("# Resumen Firebase Test Lab\n")
print("| Archivo | Tests | Fallos | Errores |")
print("| --- | ---: | ---: | ---: |")
for row in rows:
    print(f"| {row[0]} | {row[1]} | {row[2]} | {row[3]} |")
if not rows:
    print("| Sin XML JUnit reconocible | 0 | 0 | 0 |")
