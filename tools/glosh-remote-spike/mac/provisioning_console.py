"""Artifact-bound Glosh DEV provisioning over the encrypted relay."""
from __future__ import annotations

import base64
import hashlib
import json
import uuid
from pathlib import Path
from typing import Awaitable, Callable

CONTROL_ACTIONS = {
    "maintenance-shell",
    "owner-preflight",
    "artifact-begin",
    "artifact-chunk",
    "artifact-stage",
    "owner-commit",
}
CHUNK_BYTES = 120 * 1024


def result_json(result: dict) -> dict:
    if not result.get("ok"):
        raise RuntimeError(str(result.get("output", "operación rechazada")))
    try:
        value = json.loads(str(result.get("output", "")))
    except json.JSONDecodeError as error:
        raise RuntimeError("El teléfono devolvió una respuesta inválida.") from error
    if not isinstance(value, dict):
        raise RuntimeError("El teléfono devolvió una respuesta inválida.")
    return value


async def owner_preflight(session) -> dict:
    return result_json(await session.command("owner-preflight", timeout=30.0))


async def maintenance_shell(session, command: str, timeout: float = 60.0) -> str:
    value = command.strip()
    if not value or "\n" in value or "\r" in value or "\x00" in value:
        raise ValueError("El comando ADB shell está vacío o contiene saltos de línea.")
    response = result_json(await session.command(
        "maintenance-shell", {"command": value}, timeout=timeout))
    return str(response.get("output", ""))


async def stage_apk(session, apk_path: str, progress: Callable[[int, int], None] | None = None) -> dict:
    path = Path(apk_path).expanduser().resolve()
    if not path.is_file() or path.suffix.lower() != ".apk":
        raise ValueError("Indicá una APK local válida.")
    size = path.stat().st_size
    if size <= 0 or size > 512 * 1024 * 1024:
        raise ValueError("La APK está vacía o excede 512 MiB.")

    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while block := stream.read(1024 * 1024):
            digest.update(block)
    artifact_sha = digest.hexdigest()
    transfer_id = str(uuid.uuid4())
    result_json(await session.command("artifact-begin", {
        "transferId": transfer_id,
        "size": size,
        "sha256": artifact_sha,
    }, timeout=30.0))

    offset = 0
    with path.open("rb") as stream:
        while block := stream.read(CHUNK_BYTES):
            response = result_json(await session.command("artifact-chunk", {
                "transferId": transfer_id,
                "offset": offset,
                "data": base64.urlsafe_b64encode(block).decode("ascii").rstrip("="),
            }, timeout=30.0))
            expected_offset = offset + len(block)
            offset = int(response.get("nextOffset", -1))
            if offset != expected_offset:
                raise RuntimeError("El teléfono devolvió un offset inválido.")
            if progress:
                progress(offset, size)

    staged = result_json(await session.command(
        "artifact-stage", {"transferId": transfer_id}, timeout=60.0))
    if staged.get("artifactSha256") != artifact_sha:
        raise RuntimeError("El SHA staged no coincide con el archivo local.")
    return staged


async def provision_apk(
    session,
    apk_path: str,
    read_input: Callable[[str], Awaitable[str]],
    progress: Callable[[int, int], None] | None = None,
) -> dict:
    preflight = await owner_preflight(session)
    if not preflight.get("eligible"):
        reason = preflight.get("blockReason") or "El teléfono no es elegible."
        raise RuntimeError(str(reason))
    staged = await stage_apk(session, apk_path, progress)
    phrase = "DEVICE OWNER " + str(staged["artifactSha256"])[:12]
    entered = (await read_input(f"Escribí {phrase} para instalar y aprovisionar: ")).strip()
    if entered != phrase:
        raise RuntimeError("Confirmación cancelada; no se instaló nada.")
    return result_json(await session.command("owner-commit", {
        "transferId": staged["transferId"],
        "signerSha256": staged["signerSha256"],
    }, timeout=180.0))
