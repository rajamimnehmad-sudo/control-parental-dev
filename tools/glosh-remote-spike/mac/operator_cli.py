from __future__ import annotations

import asyncio
import shlex
import sys

from broker_console import accept_request, print_pending_requests
from remote_access import (
    COMPONENT_PATTERN,
    install_apk,
    provision_device_owner,
    push_file,
    run_shell,
)

ACTIONS = {
    "ping": "Round-trip de la sesión, sin ADB",
    "whoami": "Identidad shell (id)",
    "device": "Fabricante, modelo y Android",
    "owners": "Device/Profile Owner",
    "users": "Usuarios Android",
    "battery": "Estado de batería",
    "shell": "Comando ADB shell completo",
    "push": "Transferir un archivo por ADB Sync",
    "install": "Transferir e instalar una APK",
    "owner": "Configurar un Device Owner",
    "provision": "Instalar una APK y configurar Device Owner",
}


async def async_input(prompt: str) -> str:
    """Cancelable stdin reader for the Mac event loop."""
    loop = asyncio.get_running_loop()
    future = loop.create_future()
    fd = sys.stdin.fileno()
    print(prompt, end="", flush=True)

    def on_readable() -> None:
        try:
            line = sys.stdin.readline()
            if not future.done():
                future.set_result(line)
        except Exception as exc:
            if not future.done():
                future.set_exception(exc)
        finally:
            try:
                loop.remove_reader(fd)
            except Exception:
                pass

    loop.add_reader(fd, on_readable)
    try:
        return await future
    finally:
        try:
            loop.remove_reader(fd)
        except Exception:
            pass


async def interactive_cli(session, descriptor: str, broker) -> None:
    print("\nEsperando al Android…")
    print("Comandos: " + ", ".join(ACTIONS) + ", status, requests, accept <request-id>, help, quit")
    while not session.stop_event.is_set():
        try:
            raw = (await async_input("glosh-remote> ")).strip()
        except (EOFError, KeyboardInterrupt):
            raw = "quit"

        command = raw.lower()
        verb = raw.split(maxsplit=1)[0].lower() if raw else ""
        if not raw:
            continue
        if command == "quit":
            session.stop_event.set()
            return
        if command == "help":
            for name, description in ACTIONS.items():
                print(f"  {name:8} {description}")
            print("  shell <comando>         Ejecuta un comando como uid=2000(shell)")
            print("  push <local> <remoto>   Transfiere y verifica tamaño + SHA-256")
            print("  install <apk-local>     Transfiere, instala y limpia el temporal")
            print("  owner <componente>      Ejecuta dpm set-device-owner --user 0")
            print("  provision <apk> <comp>  Instala y configura Device Owner")
            print("  status                  Muestra el agente actual")
            print("  requests                Muestra solicitudes pendientes del broker")
            print("  accept <request-id>     Acepta explícitamente un teléfono")
            print("  quit                    Revoca la sesión")
            continue
        if command == "status":
            print(session.agent_info or "sin agente")
            continue
        if command == "requests":
            if broker is None:
                print("Broker no configurado; esta sesión usa el fallback DEV por descriptor.")
            else:
                await print_pending_requests(broker)
            continue
        if verb == "accept":
            if broker is None:
                print("Broker no configurado.")
                continue
            parts = raw.split(maxsplit=1)
            if len(parts) != 2:
                print("[ERROR] Uso: accept <request-id>")
                continue
            try:
                print(await accept_request(broker, parts[1].strip(), descriptor, session))
            except Exception as exc:
                print(f"[broker] no se pudo aceptar la solicitud: {exc}")
            continue
        try:
            if verb == "shell":
                if len(raw.split(maxsplit=1)) != 2:
                    raise ValueError("Uso: shell <comando>")
                result = await run_shell(session, raw.split(maxsplit=1)[1])
            elif verb == "push":
                parts = shlex.split(raw)
                if len(parts) != 3:
                    raise ValueError("Uso: push <archivo-local> <ruta-remota>")
                result = await push_file(session, parts[1], parts[2])
            elif verb == "install":
                parts = shlex.split(raw)
                if len(parts) != 2:
                    raise ValueError("Uso: install <apk-local>")
                result = await install_apk(session, parts[1])
            elif verb == "owner":
                parts = shlex.split(raw)
                if len(parts) != 2 or not COMPONENT_PATTERN.fullmatch(parts[1]):
                    raise ValueError("Uso: owner <paquete/componente>")
                result = await run_shell(
                    session,
                    f"dpm set-device-owner --user 0 {shlex.quote(parts[1])}",
                )
            elif verb == "provision":
                parts = shlex.split(raw)
                if len(parts) != 3:
                    raise ValueError("Uso: provision <apk-local> <paquete/componente>")
                install_result, result = await provision_device_owner(
                    session, parts[1], parts[2]
                )
                print(f"[PASS] install\n{install_result.get('output', '')}".rstrip())
            elif command in {"ping", "whoami", "device", "owners", "users", "battery"}:
                result = await session.command(command)
            else:
                raise ValueError("Comando desconocido. Usá help.")
            marker = "PASS" if result.get("ok") else "ERROR"
            print(f"[{marker}] {verb}\n{result.get('output', '')}".rstrip())
        except Exception as exc:
            print(f"[ERROR] {exc}")
