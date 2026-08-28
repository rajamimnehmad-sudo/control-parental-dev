"""Loads the broker operator credential without printing or logging it."""
from __future__ import annotations

import re
import stat
from pathlib import Path

TOKEN_PATTERN = re.compile(r"[A-Za-z0-9_-]{20,100}")
DEFAULT_CREDENTIAL_PATH = Path.home() / "Library/Application Support/Glosh Remote/operator.key"


def load_operator_key(explicit: str = "", path: Path = DEFAULT_CREDENTIAL_PATH) -> str:
    if explicit:
        return _validate(explicit.strip())
    try:
        metadata = path.stat()
    except FileNotFoundError:
        return ""
    if not stat.S_ISREG(metadata.st_mode) or metadata.st_mode & 0o077:
        raise RuntimeError("La credencial del operador debe ser un archivo privado con modo 0600.")
    return _validate(path.read_text(encoding="ascii").strip())


def _validate(value: str) -> str:
    if not TOKEN_PATTERN.fullmatch(value):
        raise RuntimeError("La credencial del operador guardada es inválida.")
    return value
