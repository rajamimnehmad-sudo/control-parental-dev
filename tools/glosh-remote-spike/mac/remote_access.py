from __future__ import annotations

import base64
import hashlib
import os
import re
import shlex
import uuid
from typing import Tuple

FILE_CHUNK_BYTES = 96 * 1024
MAX_TRANSFER_BYTES = 512 * 1024 * 1024
COMPONENT_PATTERN = re.compile(r"[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+")


def b64u(value: bytes) -> str:
    return base64.urlsafe_b64encode(value).decode("ascii").rstrip("=")


def sha256_file(path: str) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def validate_remote_path(remote_path: str) -> None:
    if (
        not remote_path.startswith("/")
        or len(remote_path) > 512
        or any(value in remote_path for value in ("\0", ",", "\n", "\r"))
    ):
        raise ValueError("invalid remote path")


def require_ok(result: dict, action: str) -> None:
    if not result.get("ok"):
        raise RuntimeError(f"{action}: {result.get('output', 'remote failure')}")


async def push_file(session, local_path: str, remote_path: str) -> dict:
    source = os.path.abspath(os.path.expanduser(local_path))
    if not os.path.isfile(source):
        raise FileNotFoundError(source)
    size = os.path.getsize(source)
    if size <= 0 or size > MAX_TRANSFER_BYTES:
        raise ValueError("file size is outside the supported transfer range")
    validate_remote_path(remote_path)
    transfer_id = b64u(os.urandom(18))
    sha256 = sha256_file(source)
    result = await session.command(
        "push-start",
        {
            "transferId": transfer_id,
            "size": size,
            "sha256": sha256,
            "remotePath": remote_path,
        },
    )
    require_ok(result, "push-start")

    sent = 0
    with open(source, "rb") as file_handle:
        while True:
            chunk = file_handle.read(FILE_CHUNK_BYTES)
            if not chunk:
                break
            result = await session.command(
                "push-chunk",
                {
                    "transferId": transfer_id,
                    "offset": sent,
                    "data": b64u(chunk),
                },
                timeout=30.0,
            )
            require_ok(result, "push-chunk")
            sent += len(chunk)
    return await session.command(
        "push-finish",
        {"transferId": transfer_id},
        timeout=120.0,
    )


async def run_shell(session, command: str, timeout: float = 120.0) -> dict:
    if not command or len(command) > 32 * 1024 or "\0" in command:
        raise ValueError("shell command is empty or outside protocol limits")
    return await session.command("shell", {"command": command}, timeout=timeout)


async def install_apk(session, local_path: str) -> dict:
    remote_path = f"/data/local/tmp/glosh-remote-{uuid.uuid4().hex}.apk"
    transfer = await push_file(session, local_path, remote_path)
    require_ok(transfer, "push")
    try:
        return await run_shell(
            session,
            f"pm install -r --user 0 {shlex.quote(remote_path)}",
            timeout=180.0,
        )
    finally:
        try:
            await run_shell(session, f"rm -f {shlex.quote(remote_path)}")
        except Exception:
            pass


async def provision_device_owner(
    session,
    local_path: str,
    component: str,
) -> Tuple[dict, dict]:
    if not COMPONENT_PATTERN.fullmatch(component):
        raise ValueError("invalid Device Owner component")
    install_result = await install_apk(session, local_path)
    require_ok(install_result, "install")
    owner_result = await run_shell(
        session,
        f"dpm set-device-owner --user 0 {shlex.quote(component)}",
    )
    return install_result, owner_result
