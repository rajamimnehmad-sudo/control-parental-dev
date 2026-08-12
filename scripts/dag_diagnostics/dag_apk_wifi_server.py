#!/usr/bin/env python3
"""Serve the latest local DAG DEV APK over the private LAN.

The server exposes only an unguessable download route. It never lists the
repository and does not copy or publish the APK.
"""

from __future__ import annotations

import argparse
import datetime
import hmac
import html
import json
import os
import pathlib
import plistlib
import re
import secrets
import socket
import subprocess
import sys
import time
import urllib.parse
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import BinaryIO, Optional, Tuple


PROJECT_ROOT = pathlib.Path(__file__).resolve().parents[2]
DEFAULT_APK = (
    PROJECT_ROOT
    / "app-dag-browser/build/outputs/apk/dev/debug/DagBrowser-dev-debug.apk"
)
DEFAULT_PORT = 8787
LABEL = "com.glosh.dag-apk-wifi"
CONFIG_DIR = pathlib.Path.home() / ".config/glosh"
TOKEN_FILE = CONFIG_DIR / "dag-apk-wifi-token"
LAUNCH_AGENT = pathlib.Path.home() / "Library/LaunchAgents" / f"{LABEL}.plist"
LOG_DIR = pathlib.Path.home() / "Library/Logs"
STDOUT_LOG = LOG_DIR / "DagApkWifiServer.log"
STDERR_LOG = LOG_DIR / "DagApkWifiServer.error.log"
TOKEN_PATTERN = re.compile(r"^[A-Za-z0-9_-]{24,80}$")
RANGE_PATTERN = re.compile(r"^bytes=(\d*)-(\d*)$")
CHUNK_SIZE = 1024 * 1024


def read_token(path: pathlib.Path) -> str:
    token = path.read_text(encoding="utf-8").strip()
    if not TOKEN_PATTERN.fullmatch(token):
        raise ValueError(f"invalid token file: {path}")
    return token


def ensure_token(path: pathlib.Path) -> str:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        os.chmod(path, 0o600)
        return read_token(path)
    token = secrets.token_urlsafe(24)
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8") as output:
        output.write(token + "\n")
    return token


def apk_metadata(metadata_path: pathlib.Path) -> Tuple[str, int]:
    try:
        payload = json.loads(metadata_path.read_text(encoding="utf-8"))
        element = payload["elements"][0]
        return str(element["versionName"]), int(element["versionCode"])
    except (FileNotFoundError, KeyError, IndexError, TypeError, ValueError, json.JSONDecodeError):
        return "local", 0


def formatted_update_time(timestamp: float) -> Tuple[str, str]:
    updated_at = datetime.datetime.fromtimestamp(timestamp).astimezone()
    return updated_at.isoformat(timespec="seconds"), updated_at.strftime("%d/%m/%Y %H:%M:%S %Z")


def parse_range(value: Optional[str], size: int) -> Optional[Tuple[int, int]]:
    if value is None:
        return None
    match = RANGE_PATTERN.fullmatch(value.strip())
    if match is None or size <= 0:
        raise ValueError("invalid range")
    start_text, end_text = match.groups()
    if not start_text and not end_text:
        raise ValueError("empty range")
    if not start_text:
        suffix_length = int(end_text)
        if suffix_length <= 0:
            raise ValueError("invalid suffix")
        start = max(0, size - suffix_length)
        return start, size - 1
    start = int(start_text)
    end = int(end_text) if end_text else size - 1
    if start >= size or end < start:
        raise ValueError("unsatisfiable range")
    return start, min(end, size - 1)


def copy_bytes(source: BinaryIO, destination: BinaryIO, count: int) -> None:
    remaining = count
    while remaining > 0:
        chunk = source.read(min(CHUNK_SIZE, remaining))
        if not chunk:
            break
        destination.write(chunk)
        remaining -= len(chunk)


class DagApkServer(ThreadingHTTPServer):
    daemon_threads = True
    allow_reuse_address = True

    def __init__(
        self,
        address: Tuple[str, int],
        token: str,
        apk_path: pathlib.Path,
        metadata_path: pathlib.Path,
    ) -> None:
        super().__init__(address, DagApkRequestHandler)
        self.token = token
        self.apk_path = apk_path
        self.metadata_path = metadata_path


class DagApkRequestHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server_version = "DagApkWifi/1"

    @property
    def dag_server(self) -> DagApkServer:
        return self.server  # type: ignore[return-value]

    def do_GET(self) -> None:
        self._handle(send_body=True)

    def do_HEAD(self) -> None:
        self._handle(send_body=False)

    def _handle(self, send_body: bool) -> None:
        path = urllib.parse.urlsplit(self.path).path
        if path == "/healthz":
            self._send_text(HTTPStatus.OK, b"ok\n", "text/plain; charset=utf-8", send_body)
            return
        route_prefix = f"/t/{self.dag_server.token}"
        if not hmac.compare_digest(path, route_prefix) and not hmac.compare_digest(
            path, route_prefix + "/"
        ) and not hmac.compare_digest(path, route_prefix + "/dag.apk"):
            self.send_error(HTTPStatus.NOT_FOUND)
            return
        if path.endswith("/dag.apk"):
            self._send_apk(send_body)
            return
        self._send_index(send_body)

    def _send_index(self, send_body: bool) -> None:
        apk_path = self.dag_server.apk_path
        if apk_path.is_file():
            stat = apk_path.stat()
            version_name, version_code = apk_metadata(self.dag_server.metadata_path)
            version = html.escape(version_name)
            size_mb = stat.st_size / (1024 * 1024)
            updated_iso, updated_display = formatted_update_time(stat.st_mtime)
            cache_key = f"{stat.st_mtime_ns}-{stat.st_size}"
            content = f"""<!doctype html>
<html lang=\"es\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">
<title>DAG Browser DEV</title><style>body{{font:18px system-ui;margin:2rem;max-width:36rem}}a{{display:inline-block;padding:1rem 1.3rem;background:#087f5b;color:white;border-radius:.7rem;text-decoration:none}}small{{color:#555}}</style></head>
<body><h1>DAG Browser DEV</h1><p>Versión {version} · código {version_code} · {size_mb:.1f} MiB</p>
<p>Última actualización disponible: <time datetime="{updated_iso}">{updated_display}</time></p>
<p><a href=\"dag.apk?v={cache_key}\">Descargar el último APK</a></p>
<small>Servidor privado de desarrollo en tu red local. La instalación requiere confirmación de Android.</small></body></html>"""
            self._send_text(
                HTTPStatus.OK,
                content.encode("utf-8"),
                "text/html; charset=utf-8",
                send_body,
            )
            return
        content = (
            "<!doctype html><html lang=\"es\"><meta name=\"viewport\" "
            "content=\"width=device-width,initial-scale=1\"><title>DAG Browser DEV</title>"
            "<body><h1>APK todavía no disponible</h1><p>Compilá assembleDevDebug y recargá esta página.</p></body></html>"
        )
        self._send_text(
            HTTPStatus.SERVICE_UNAVAILABLE,
            content.encode("utf-8"),
            "text/html; charset=utf-8",
            send_body,
        )

    def _send_apk(self, send_body: bool) -> None:
        apk_path = self.dag_server.apk_path
        if not apk_path.is_file():
            self.send_error(HTTPStatus.NOT_FOUND)
            return
        with apk_path.open("rb") as source:
            stat = os.fstat(source.fileno())
            try:
                byte_range = parse_range(self.headers.get("Range"), stat.st_size)
            except ValueError:
                self.send_response(HTTPStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                self.send_header("Content-Range", f"bytes */{stat.st_size}")
                self.send_header("Content-Length", "0")
                self._end_common_headers()
                return
            version_name, version_code = apk_metadata(self.dag_server.metadata_path)
            safe_version = re.sub(r"[^A-Za-z0-9._-]", "-", version_name)
            filename = f"DAG-Browser-{version_code}-{safe_version}.apk"
            if byte_range is None:
                start, end = 0, stat.st_size - 1
                status = HTTPStatus.OK
            else:
                start, end = byte_range
                status = HTTPStatus.PARTIAL_CONTENT
            length = max(0, end - start + 1)
            self.send_response(status)
            self.send_header("Content-Type", "application/vnd.android.package-archive")
            self.send_header("Content-Disposition", f'attachment; filename="{filename}"')
            self.send_header("Accept-Ranges", "bytes")
            self.send_header("Content-Length", str(length))
            if byte_range is not None:
                self.send_header("Content-Range", f"bytes {start}-{end}/{stat.st_size}")
            self._end_common_headers()
            if send_body and length > 0:
                source.seek(start)
                try:
                    copy_bytes(source, self.wfile, length)
                except (BrokenPipeError, ConnectionResetError):
                    pass

    def _send_text(
        self,
        status: HTTPStatus,
        body: bytes,
        content_type: str,
        send_body: bool,
    ) -> None:
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self._end_common_headers()
        if send_body:
            try:
                self.wfile.write(body)
            except (BrokenPipeError, ConnectionResetError):
                pass

    def _end_common_headers(self) -> None:
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Referrer-Policy", "no-referrer")
        self.send_header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'")
        self.send_header("Connection", "close")
        self.end_headers()

    def log_request(self, code: object = "-", size: object = "-") -> None:
        # Never log the secret route.
        sys.stderr.write(f"{self.client_address[0]} {self.command} {code} {size}\n")

    def log_message(self, format: str, *args: object) -> None:
        return


def local_hostname() -> str:
    try:
        result = subprocess.run(
            ["/usr/sbin/scutil", "--get", "LocalHostName"],
            check=True,
            capture_output=True,
            text=True,
        )
        value = result.stdout.strip()
        if value:
            return value + ".local"
    except (FileNotFoundError, subprocess.CalledProcessError):
        pass
    return socket.gethostname()


def print_urls(token: str, port: int) -> None:
    print(f"http://{local_hostname()}:{port}/t/{token}/")
    seen = set()
    for interface in ("en0", "en1"):
        try:
            result = subprocess.run(
                ["/usr/sbin/ipconfig", "getifaddr", interface],
                check=True,
                capture_output=True,
                text=True,
            )
        except (FileNotFoundError, subprocess.CalledProcessError):
            continue
        address = result.stdout.strip()
        if address and address not in seen:
            seen.add(address)
            print(f"http://{address}:{port}/t/{token}/")


def installed_port() -> int:
    try:
        with LAUNCH_AGENT.open("rb") as source:
            arguments = plistlib.load(source)["ProgramArguments"]
        port_index = arguments.index("--port") + 1
        port = int(arguments[port_index])
        if port in range(1, 65_536):
            return port
    except (
        FileNotFoundError,
        KeyError,
        ValueError,
        IndexError,
        TypeError,
        plistlib.InvalidFileException,
    ):
        pass
    return DEFAULT_PORT


def wait_until_listening(port: int, timeout_seconds: float = 5.0) -> None:
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        try:
            with socket.create_connection(("127.0.0.1", port), timeout=0.25):
                return
        except OSError:
            time.sleep(0.1)
    raise RuntimeError(f"LaunchAgent did not open port {port} within {timeout_seconds:.1f}s")


def install_launch_agent(port: int) -> None:
    token = ensure_token(TOKEN_FILE)
    LAUNCH_AGENT.parent.mkdir(parents=True, exist_ok=True)
    LOG_DIR.mkdir(parents=True, exist_ok=True)
    payload = {
        "Label": LABEL,
        "ProgramArguments": [
            sys.executable,
            str(pathlib.Path(__file__).resolve()),
            "serve",
            "--host",
            "0.0.0.0",
            "--port",
            str(port),
            "--token-file",
            str(TOKEN_FILE),
        ],
        "WorkingDirectory": str(PROJECT_ROOT),
        "RunAtLoad": True,
        "KeepAlive": True,
        "ProcessType": "Background",
        "LowPriorityIO": True,
        "Nice": 10,
        "ThrottleInterval": 10,
        "StandardOutPath": str(STDOUT_LOG),
        "StandardErrorPath": str(STDERR_LOG),
        "EnvironmentVariables": {"PYTHONUNBUFFERED": "1"},
    }
    with LAUNCH_AGENT.open("wb") as output:
        plistlib.dump(payload, output, sort_keys=True)
    os.chmod(LAUNCH_AGENT, 0o600)
    domain = f"gui/{os.getuid()}"
    subprocess.run(
        ["/bin/launchctl", "bootout", domain, str(LAUNCH_AGENT)],
        check=False,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    subprocess.run(["/bin/launchctl", "bootstrap", domain, str(LAUNCH_AGENT)], check=True)
    subprocess.run(["/bin/launchctl", "enable", f"{domain}/{LABEL}"], check=True)
    subprocess.run(["/bin/launchctl", "kickstart", "-k", f"{domain}/{LABEL}"], check=True)
    wait_until_listening(port)
    print("Servidor Wi-Fi de DAG instalado y activo:")
    print_urls(token, port)


def uninstall_launch_agent() -> None:
    domain = f"gui/{os.getuid()}"
    subprocess.run(
        ["/bin/launchctl", "bootout", domain, str(LAUNCH_AGENT)],
        check=False,
    )
    if LAUNCH_AGENT.exists():
        LAUNCH_AGENT.unlink()
    print("Servicio detenido. El token privado se conservó para una reinstalación futura.")


def serve(host: str, port: int, token_file: pathlib.Path, apk_path: pathlib.Path) -> None:
    token = read_token(token_file)
    metadata_path = apk_path.with_name("output-metadata.json")
    server = DagApkServer((host, port), token, apk_path.resolve(), metadata_path.resolve())
    print(f"DAG APK Wi-Fi escuchando en {host}:{port}", flush=True)
    try:
        server.serve_forever(poll_interval=0.5)
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)
    install_parser = subparsers.add_parser("install", help="install and start the user LaunchAgent")
    install_parser.add_argument("--port", type=int, default=DEFAULT_PORT)
    subparsers.add_parser("uninstall", help="stop and remove the user LaunchAgent")
    subparsers.add_parser("urls", help="print the private download URLs")
    serve_parser = subparsers.add_parser("serve", help="run the HTTP server")
    serve_parser.add_argument("--host", default="127.0.0.1")
    serve_parser.add_argument("--port", type=int, default=DEFAULT_PORT)
    serve_parser.add_argument("--token-file", type=pathlib.Path, default=TOKEN_FILE)
    serve_parser.add_argument("--apk", type=pathlib.Path, default=DEFAULT_APK)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.command == "install":
        install_launch_agent(args.port)
    elif args.command == "uninstall":
        uninstall_launch_agent()
    elif args.command == "urls":
        print_urls(read_token(TOKEN_FILE), installed_port())
    elif args.command == "serve":
        serve(args.host, args.port, args.token_file, args.apk)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
