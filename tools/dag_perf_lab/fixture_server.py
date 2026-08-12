#!/usr/bin/env python3
"""Deterministic, no-store HTTPS fixture for local DAG performance runs."""

from __future__ import annotations

import argparse
import base64
import functools
import hashlib
import http.server
import json
import os
import signal
import ssl
import struct
import subprocess
import tempfile
import threading
import time
import urllib.parse
import zlib
from pathlib import Path
from typing import Callable


MAX_EVENT_BYTES = 4_096
MAX_RUN_ID_LENGTH = 80
NO_STORE_HEADERS = {
    "Cache-Control": "no-store, no-cache, must-revalidate, max-age=0",
    "Pragma": "no-cache",
    "Expires": "0",
    "X-Content-Type-Options": "nosniff",
    "Cross-Origin-Resource-Policy": "same-origin",
    "Referrer-Policy": "no-referrer",
    "Permissions-Policy": "camera=(), microphone=(), geolocation=()",
    "Content-Security-Policy": (
        "default-src 'self'; img-src 'self' data: blob:; style-src 'self'; script-src 'self'; "
        "connect-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'"
    ),
}


INDEX_HTML = """<!doctype html>
<html lang="es">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
  <title>DAG controlled performance fixture</title>
  <link rel="stylesheet" href="/fixture/styles.css">
</head>
<body data-fixture-state="booting">
  <header>
    <button class="icon-button" type="button" aria-label="Menú de prueba">
      <img src="/fixture/media/icon.svg" alt="">
    </button>
    <div>
      <strong>DAG Performance Fixture</strong>
      <small>Página local, determinista y sin caché</small>
    </div>
    <svg class="inline-icon" viewBox="0 0 24 24" role="img" aria-label="Fixture listo">
      <path d="M12 2 4 5v6c0 5 3.4 9.7 8 11 4.6-1.3 8-6 8-11V5l-8-3Zm-1 14-4-4 1.4-1.4 2.6 2.6 4.7-4.7 1.4 1.4L11 16Z"/>
    </svg>
  </header>

  <main>
    <section class="intro">
      <p id="fixture-status" aria-live="polite">Preparando recursos controlados…</p>
      <div class="css-safe" aria-label="Fondo CSS seguro"></div>
      <div class="css-sensitive" aria-label="Fondo CSS sensible sintético"></div>
    </section>

    <section id="critical" class="critical" aria-label="Recursos iniciales">
      <article>
        <h2>Raster seguro</h2>
        <img src="/fixture/media/safe-0.png?delay_ms=40" alt="Paisaje geométrico sintético seguro">
      </article>
      <article>
        <h2>Raster de bloqueo controlado</h2>
        <img src="/fixture/media/filter-probe.jpg?delay_ms=80" alt="Sonda sintética filtrada por el modelo vigente">
      </article>
      <article>
        <h2>Vector funcional</h2>
        <img class="vector-card" src="/fixture/media/control.svg" alt="Control vectorial seguro">
      </article>
      <article>
        <h2>Fuente rotativa</h2>
        <img id="rotating-source" src="/fixture/media/safe-1.png?delay_ms=60" alt="Fuente que cambia de forma determinista">
      </article>
    </section>

    <div class="spacer" aria-hidden="true"><span>Zona de desplazamiento controlada</span></div>
    <section aria-label="Cuadrícula lazy">
      <h2>Cuadrícula diferida</h2>
      <div id="lazy-sentinel" class="sentinel">La cuadrícula aparece al acercarse</div>
      <div id="lazy-grid" class="lazy-grid"></div>
    </section>
  </main>
  <script src="/fixture/app.js" defer></script>
</body>
</html>
"""


STYLES_CSS = """:root {
  color-scheme: light;
  font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  background: #f4f7fb;
  color: #172033;
}
* { box-sizing: border-box; }
body { margin: 0; min-height: 100vh; background: #f4f7fb; }
header {
  position: sticky; top: 0; z-index: 5; display: flex; align-items: center; gap: 12px;
  min-height: 64px; padding: 10px 16px; background: rgba(255,255,255,.96);
  border-bottom: 1px solid #dce3ee;
}
header div { flex: 1; display: grid; gap: 2px; }
header small { color: #68758a; }
.icon-button { width: 40px; height: 40px; border: 0; border-radius: 20px; background: #e9eff8; padding: 8px; }
.icon-button img, .inline-icon { width: 24px; height: 24px; fill: #2457a6; }
main { width: min(100%, 860px); margin: auto; padding: 16px; }
.intro, article, .sentinel { background: #fff; border: 1px solid #dce3ee; border-radius: 16px; }
.intro { padding: 14px; display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.intro p { grid-column: 1 / -1; margin: 0; font-weight: 600; }
.css-safe, .css-sensitive { min-height: 108px; border-radius: 12px; background-size: cover; background-position: center; }
.css-safe { background-image: url('/fixture/media/safe-2.png?delay_ms=30'); }
.css-sensitive { background-image: url('/fixture/media/filter-probe.jpg?surface=css&delay_ms=70'); }
.critical, .lazy-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; margin-top: 16px; }
article { overflow: hidden; padding: 10px; }
article h2 { margin: 0 0 8px; font-size: 15px; }
article img { width: 100%; aspect-ratio: 4 / 3; display: block; object-fit: cover; border-radius: 10px; background: #e5eaf1; }
article img.vector-card { object-fit: contain; }
.spacer { height: 105vh; display: grid; place-items: center; color: #748197; }
.sentinel { padding: 18px; text-align: center; }
.lazy-grid { padding-bottom: 80px; }
.lazy-grid article:nth-child(3n) img { aspect-ratio: 3 / 4; }
body[data-fixture-state="stable"] #fixture-status { color: #146c43; }
@media (max-width: 520px) {
  .intro, .critical, .lazy-grid { grid-template-columns: 1fr 1fr; gap: 9px; }
  main { padding: 10px; }
  article { padding: 7px; }
  .spacer { height: 115vh; }
}
@media (prefers-reduced-motion: reduce) { * { scroll-behavior: auto !important; } }
"""


APP_JS = r"""(() => {
  'use strict';
  const params = new URLSearchParams(location.search);
  const runId = (params.get('run') || 'manual').slice(0, 80);
  const lazyCount = Math.min(40, Math.max(4, Number(params.get('lazy') || 20)));
  const inlineMode = params.get('inline') === '1';
  const startedAt = performance.now();
  const status = document.getElementById('fixture-status');
  let lazyStarted = false;

  function report(event, detail = {}) {
    performance.mark(`dag-fixture:${event}`);
    const payload = JSON.stringify({
      event,
      run_id: runId,
      elapsed_ms: Math.round(performance.now() - startedAt),
      ...detail,
    });
    fetch('/fixture/event', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: payload,
      cache: 'no-store',
      keepalive: true,
      credentials: 'same-origin',
    }).catch(() => {});
  }

  function waitForImages(images) {
    return Promise.all(images.map((image) => {
      if (image.complete) return Promise.resolve(image.naturalWidth > 0 ? 'load' : 'error');
      return new Promise((resolve) => {
        image.addEventListener('load', () => resolve('load'), {once: true});
        image.addEventListener('error', () => resolve('error'), {once: true});
      });
    }));
  }

  function dataUrl(blob) {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.addEventListener('load', () => resolve(String(reader.result)), {once: true});
      reader.addEventListener('error', reject, {once: true});
      reader.readAsDataURL(blob);
    });
  }

  async function insertInlineFixtures() {
    const specifications = [
      ['safe-data', '/fixture/media/safe-0.bin', 'image/png', 'data'],
      ['safe-blob', '/fixture/media/safe-1.bin', 'image/png', 'blob'],
      ['filter-data', '/fixture/media/filter-probe.bin', 'image/jpeg', 'data'],
      ['filter-blob', '/fixture/media/filter-probe.bin?copy=1', 'image/jpeg', 'blob'],
    ];
    const section = document.createElement('section');
    section.className = 'critical';
    section.setAttribute('aria-label', 'Recursos inline controlados');
    const images = [];
    for (const [label, url, mimeType, transport] of specifications) {
      const response = await fetch(url, {cache: 'no-store'});
      const bytes = await response.arrayBuffer();
      const blob = new Blob([bytes], {type: mimeType});
      const image = document.createElement('img');
      image.alt = label;
      image.dataset.fixtureInline = label;
      image.src = transport === 'blob' ? URL.createObjectURL(blob) : await dataUrl(blob);
      const article = document.createElement('article');
      const heading = document.createElement('h2');
      heading.textContent = label;
      article.append(heading, image);
      section.append(article);
      images.push(image);
    }
    document.querySelector('main').append(section);
    await waitForImages(images);
    window.setTimeout(() => {
      const allowed = images.filter((image) =>
        image.getAttribute('data-glosh-dag-stable') === 'true').length;
      report('inline-settled', {count: images.length, allowed, blocked: images.length - allowed});
    }, 3000);
  }

  function insertLazyGrid() {
    if (lazyStarted) return;
    lazyStarted = true;
    const grid = document.getElementById('lazy-grid');
    const images = [];
    const fragment = document.createDocumentFragment();
    for (let i = 0; i < lazyCount; i += 1) {
      const article = document.createElement('article');
      const heading = document.createElement('h2');
      const image = document.createElement('img');
      const sensitive = i % 4 === 3;
      heading.textContent = sensitive ? 'Sensible sintético' : 'Seguro sintético';
      image.loading = 'lazy';
      image.decoding = 'async';
      image.alt = sensitive ? 'Sonda sensible sintética diferida' : 'Patrón seguro sintético diferido';
      image.src = sensitive
        ? `/fixture/media/filter-probe.jpg?lazy=${i}&delay_ms=${20 + (i % 5) * 25}`
        : `/fixture/media/safe-${i + 3}.png?delay_ms=${20 + (i % 5) * 25}`;
      article.append(heading, image);
      fragment.append(article);
      images.push(image);
    }
    grid.append(fragment);
    report('lazy-inserted', {count: images.length});
    waitForImages(images).then((results) => {
      report('lazy-settled', {
        count: results.length,
        loaded: results.filter((value) => value === 'load').length,
        errors: results.filter((value) => value === 'error').length,
      });
    });
  }

  report('script-ready');
  if (inlineMode) insertInlineFixtures().catch(() => report('inline-error'));
  document.addEventListener('DOMContentLoaded', () => report('dom-content-loaded'), {once: true});

  const criticalImages = Array.from(document.querySelectorAll('#critical img'));
  waitForImages(criticalImages).then((results) => {
    document.body.dataset.fixtureState = 'stable';
    status.textContent = 'Recursos iniciales resueltos; desplazá para activar la cuadrícula.';
    report('critical-settled', {
      count: results.length,
      loaded: results.filter((value) => value === 'load').length,
      errors: results.filter((value) => value === 'error').length,
    });
    requestAnimationFrame(() => requestAnimationFrame(() => report('visual-stable')));
  });

  const sentinel = document.getElementById('lazy-sentinel');
  const observer = new IntersectionObserver((entries) => {
    if (entries.some((entry) => entry.isIntersecting)) {
      observer.disconnect();
      insertLazyGrid();
    }
  }, {rootMargin: '600px 0px'});
  observer.observe(sentinel);

  window.setTimeout(() => {
    const rotating = document.getElementById('rotating-source');
    if (inlineMode) {
      fetch('/fixture/media/filter-probe.bin?surface=rotating', {cache: 'no-store'})
        .then((response) => response.blob())
        .then(dataUrl)
        .then((source) => {
          rotating.src = source;
          report('source-rotated', {transport: 'data'});
        })
        .catch(() => report('source-rotate-error', {transport: 'data'}));
    } else {
      rotating.src = '/fixture/media/filter-probe.jpg?surface=rotating&delay_ms=90';
      report('source-rotated', {transport: 'network'});
    }
  }, 1400);

  window.addEventListener('load', () => report('window-load'), {once: true});
})();
"""


ICON_SVG = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
<path fill="#2457a6" d="M3 5h18v2H3V5Zm0 6h18v2H3v-2Zm0 6h18v2H3v-2Z"/>
</svg>"""


CONTROL_SVG = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 320 220">
<rect width="320" height="220" rx="24" fill="#edf3fb"/>
<circle cx="90" cy="110" r="48" fill="#2d68b2"/>
<path fill="#fff" d="m70 110 14 14 28-32 10 9-38 43-24-24z"/>
<rect x="160" y="76" width="110" height="18" rx="9" fill="#8798b2"/>
<rect x="160" y="112" width="78" height="18" rx="9" fill="#b4c0d2"/>
</svg>"""


# Original synthetic raster generated for this fixture. With DAG model SHA-256
# 2d52bd9e5eb4cd448cb0d64a784b2ee6f761ad20e890c57b898fd7991d29a9ee it
# scores 0.487243 and follows the filter path. No third-party image is embedded.
_FILTER_PROBE_JPEG_BASE64 = (
    b"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"
)
# Keep the repeated JPEG-table run assembled explicitly and pin the decoded
# bytes, so an editor reflow cannot silently change the controlled model input.
FILTER_PROBE_JPEG = base64.b64decode(
    _FILTER_PROBE_JPEG_BASE64[:182] + b"NjY2" + _FILTER_PROBE_JPEG_BASE64[182:]
)
if hashlib.sha256(FILTER_PROBE_JPEG).hexdigest() != "ad1ed205b7e5c6ef8ade0668b008824d6283061dd066aec23b48c37aebac0076":
    raise RuntimeError("controlled filter probe checksum mismatch")


def _chunk(kind: bytes, payload: bytes) -> bytes:
    checksum = zlib.crc32(kind + payload) & 0xFFFFFFFF
    return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", checksum)


def _png(width: int, height: int, pixel: Callable[[int, int], tuple[int, int, int]]) -> bytes:
    scanlines = bytearray()
    for y in range(height):
        scanlines.append(0)
        for x in range(width):
            scanlines.extend(pixel(x, y))
    header = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + _chunk(b"IHDR", header) + _chunk(
        b"IDAT", zlib.compress(bytes(scanlines), level=6)
    ) + _chunk(b"IEND", b"")


@functools.lru_cache(maxsize=128)
def safe_png(seed: int) -> bytes:
    width, height = 480, 320

    def pixel(x: int, y: int) -> tuple[int, int, int]:
        noise = ((x * 17 + y * 31 + seed * 47) % 13) - 6
        if y < height * 0.58:
            base = (72 + seed % 12, 145 + (x * 35 // width), 204 + (y * 20 // height))
        else:
            base = (46 + (y % 23), 132 + seed % 17, 76 + (x % 19))
        if (x - (80 + seed * 7) % width) ** 2 + (y - 70) ** 2 < 30**2:
            base = (242, 190, 58)
        return tuple(max(0, min(255, value + noise)) for value in base)

    return _png(width, height, pixel)


@functools.lru_cache(maxsize=128)
def sensitive_png(seed: int) -> bytes:
    """A non-explicit synthetic human-like probe; classification is intentionally measured."""

    width, height = 360, 540
    skin_palettes = ((209, 157, 119), (154, 103, 73), (231, 190, 156), (113, 77, 59))
    skin = skin_palettes[seed % len(skin_palettes)]
    center = width // 2 + (seed % 5 - 2) * 5

    def ellipse(x: int, y: int, cx: int, cy: int, rx: int, ry: int) -> bool:
        return ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1

    def pixel(x: int, y: int) -> tuple[int, int, int]:
        shade = ((x * 11 + y * 7 + seed * 29) % 9) - 4
        value = (222 + shade, 226 + shade, 231 + shade)
        is_skin = (
            ellipse(x, y, center, 88, 49, 58)
            or ellipse(x, y, center, 244, 78, 150)
            or ellipse(x, y, center - 88, 245, 25, 145)
            or ellipse(x, y, center + 88, 245, 25, 145)
            or ellipse(x, y, center - 36, 438, 31, 116)
            or ellipse(x, y, center + 36, 438, 31, 116)
        )
        if is_skin:
            value = tuple(max(0, min(255, channel + shade)) for channel in skin)
        if ellipse(x, y, center, 296, 78, 70):
            value = (39 + seed % 20, 65, 100 + seed % 30)
        if ellipse(x, y, center, 73, 51, 45) and y < 59:
            value = (47, 35, 31)
        return value

    return _png(width, height, pixel)


def _bounded_int(query: dict[str, list[str]], key: str, default: int, minimum: int, maximum: int) -> int:
    try:
        value = int(query.get(key, [str(default)])[0])
    except (TypeError, ValueError):
        return default
    return max(minimum, min(maximum, value))


def _safe_event(raw: object) -> dict[str, object] | None:
    if not isinstance(raw, dict):
        return None
    event = raw.get("event")
    run_id = raw.get("run_id")
    elapsed_ms = raw.get("elapsed_ms")
    if not isinstance(event, str) or not event.replace("-", "").isalnum() or len(event) > 48:
        return None
    if not isinstance(run_id, str) or len(run_id) > MAX_RUN_ID_LENGTH:
        return None
    if not isinstance(elapsed_ms, (int, float)) or not 0 <= elapsed_ms <= 300_000:
        return None
    allowed = {"event", "run_id", "elapsed_ms", "count", "loaded", "errors", "allowed", "blocked"}
    return {key: value for key, value in raw.items() if key in allowed and isinstance(value, (str, int, float))}


class FixtureHandler(http.server.BaseHTTPRequestHandler):
    server_version = "DagFixture/1"

    @property
    def event_log(self) -> Path:
        return self.server.event_log  # type: ignore[attr-defined]

    @property
    def event_lock(self) -> threading.Lock:
        return self.server.event_lock  # type: ignore[attr-defined]

    def log_message(self, fmt: str, *args: object) -> None:
        print(f"fixture {self.address_string()} {fmt % args}", flush=True)

    def _send(self, status: int, content_type: str, payload: bytes) -> None:
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(payload)))
        for name, value in NO_STORE_HEADERS.items():
            self.send_header(name, value)
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(payload)

    def do_HEAD(self) -> None:  # noqa: N802
        self.do_GET()

    def do_GET(self) -> None:  # noqa: N802
        parsed = urllib.parse.urlsplit(self.path)
        query = urllib.parse.parse_qs(parsed.query, keep_blank_values=False)
        delay_ms = _bounded_int(query, "delay_ms", 0, 0, 1_000)
        if delay_ms:
            time.sleep(delay_ms / 1_000)

        routes = {
            "/healthz": ("text/plain; charset=utf-8", b"ok\n"),
            "/fixture/": ("text/html; charset=utf-8", INDEX_HTML.encode()),
            "/fixture/index.html": ("text/html; charset=utf-8", INDEX_HTML.encode()),
            "/fixture/styles.css": ("text/css; charset=utf-8", STYLES_CSS.encode()),
            "/fixture/app.js": ("application/javascript; charset=utf-8", APP_JS.encode()),
            "/fixture/media/icon.svg": ("image/svg+xml", ICON_SVG.encode()),
            "/fixture/media/control.svg": ("image/svg+xml", CONTROL_SVG.encode()),
            "/fixture/media/filter-probe.jpg": ("image/jpeg", FILTER_PROBE_JPEG),
            "/fixture/media/filter-probe.bin": ("application/octet-stream", FILTER_PROBE_JPEG),
        }
        if parsed.path in routes:
            content_type, payload = routes[parsed.path]
            self._send(200, content_type, payload)
            return

        media_name = parsed.path.removeprefix("/fixture/media/")
        if media_name.startswith("safe-") and media_name.endswith(".png"):
            seed_text = media_name[5:-4]
            if seed_text.isdigit() and 0 <= int(seed_text) <= 999:
                self._send(200, "image/png", safe_png(int(seed_text) % 4))
                return
        if media_name.startswith("safe-") and media_name.endswith(".bin"):
            seed_text = media_name[5:-4]
            if seed_text.isdigit() and 0 <= int(seed_text) <= 999:
                self._send(200, "application/octet-stream", safe_png(int(seed_text) % 4))
                return
        if media_name.startswith("sensitive-") and media_name.endswith(".png"):
            seed_text = media_name[10:-4]
            if seed_text.isdigit() and 0 <= int(seed_text) <= 999:
                self._send(200, "image/png", sensitive_png(int(seed_text) % 4))
                return
        self._send(404, "text/plain; charset=utf-8", b"not found\n")

    def do_POST(self) -> None:  # noqa: N802
        if urllib.parse.urlsplit(self.path).path != "/fixture/event":
            self._send(404, "text/plain; charset=utf-8", b"not found\n")
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            length = 0
        if not 1 <= length <= MAX_EVENT_BYTES:
            self._send(413, "text/plain; charset=utf-8", b"invalid event\n")
            return
        try:
            event = _safe_event(json.loads(self.rfile.read(length)))
        except (json.JSONDecodeError, UnicodeDecodeError):
            event = None
        if event is None:
            self._send(400, "text/plain; charset=utf-8", b"invalid event\n")
            return
        event["received_at_epoch_ms"] = int(time.time() * 1_000)
        with self.event_lock:
            self.event_log.parent.mkdir(parents=True, exist_ok=True)
            with self.event_log.open("a", encoding="utf-8") as stream:
                stream.write(json.dumps(event, sort_keys=True, separators=(",", ":")) + "\n")
        self._send(204, "text/plain; charset=utf-8", b"")


def ensure_certificate(cert_dir: Path) -> tuple[Path, Path]:
    cert_dir.mkdir(parents=True, exist_ok=True)
    cert_path = cert_dir / "localhost.crt"
    key_path = cert_dir / "localhost.key"
    openssl = os.environ.get("OPENSSL", "openssl")
    valid = cert_path.is_file() and key_path.is_file()
    if valid:
        valid = subprocess.run(
            [openssl, "x509", "-checkend", "86400", "-noout", "-in", str(cert_path)],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            check=False,
        ).returncode == 0
    if valid:
        inspection = subprocess.run(
            [openssl, "x509", "-noout", "-text", "-in", str(cert_path)],
            capture_output=True,
            text=True,
            check=False,
        )
        valid = (
            inspection.returncode == 0
            and "CA:FALSE" in inspection.stdout
            and "DNS:localhost" in inspection.stdout
            and "IP Address:127.0.0.1" in inspection.stdout
        )
    if valid:
        return cert_path, key_path

    config = """[req]
prompt = no
distinguished_name = subject
x509_extensions = extensions
[subject]
CN = localhost
[extensions]
basicConstraints = critical,CA:FALSE
keyUsage = critical,digitalSignature,keyEncipherment
extendedKeyUsage = serverAuth
subjectAltName = @alt_names
[alt_names]
DNS.1 = localhost
IP.1 = 127.0.0.1
"""
    with tempfile.NamedTemporaryFile("w", encoding="utf-8", delete=False) as stream:
        stream.write(config)
        config_path = Path(stream.name)
    try:
        subprocess.run(
            [
                openssl,
                "req",
                "-x509",
                "-newkey",
                "rsa:2048",
                "-sha256",
                "-nodes",
                "-days",
                "30",
                "-keyout",
                str(key_path),
                "-out",
                str(cert_path),
                "-config",
                str(config_path),
            ],
            check=True,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
    finally:
        config_path.unlink(missing_ok=True)
    key_path.chmod(0o600)
    cert_path.chmod(0o644)
    return cert_path, key_path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--cert-dir", type=Path, required=True)
    parser.add_argument("--event-log", type=Path, required=True)
    parser.add_argument(
        "--http",
        action="store_true",
        help="Serve plain HTTP for the isolated DAG lab flavor only.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.host != "127.0.0.1":
        raise SystemExit("The fixture may only bind to 127.0.0.1")
    if not 1_024 <= args.port <= 65_535:
        raise SystemExit("Port must be between 1024 and 65535")
    # Do not make the first browser requests pay deterministic image generation.
    for seed in range(4):
        safe_png(seed)
    server = http.server.ThreadingHTTPServer((args.host, args.port), FixtureHandler)
    server.daemon_threads = True
    server.event_log = args.event_log  # type: ignore[attr-defined]
    server.event_lock = threading.Lock()  # type: ignore[attr-defined]
    cert_path = None
    if not args.http:
        cert_path, key_path = ensure_certificate(args.cert_dir)
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.minimum_version = ssl.TLSVersion.TLSv1_2
        context.load_cert_chain(certfile=cert_path, keyfile=key_path)
        server.socket = context.wrap_socket(server.socket, server_side=True)

    def stop(_signum: int, _frame: object) -> None:
        threading.Thread(target=server.shutdown, daemon=True).start()

    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)
    scheme = "http" if args.http else "https"
    fingerprint = "not-used" if cert_path is None else hashlib.sha256(cert_path.read_bytes()).hexdigest()
    print(
        f"READY url={scheme}://localhost:{args.port}/fixture/ cert_file_sha256={fingerprint}",
        flush=True,
    )
    try:
        server.serve_forever(poll_interval=0.2)
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
