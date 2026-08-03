#!/usr/bin/env python3
"""Serve a private mobile review UI for the R3 focused relabel queue."""

from __future__ import annotations

import argparse
import json
import mimetypes
import secrets
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any
from urllib.parse import parse_qs, urlparse


MAX_REQUEST_BYTES = 64 * 1024
VALID_STATES = {"positive", "negative", "unknown"}


def normalize_review(item: dict[str, Any], payload: dict[str, Any]) -> dict[str, Any]:
    if payload.get("sample_id") != item["sample_id"]:
        raise ValueError("sample_id does not match queue item")
    labels = payload.get("labels")
    expected = set(item["labels"])
    if not isinstance(labels, dict) or set(labels) != expected:
        raise ValueError("labels must contain exactly the queue signals")
    if any(value not in VALID_STATES for value in labels.values()):
        raise ValueError("invalid label state")
    status = payload.get("status")
    if status not in {"complete", "doubt"}:
        raise ValueError("invalid review status")
    if status == "complete" and "unknown" in labels.values():
        raise ValueError("complete review cannot contain unknown labels")
    return {
        "sample_id": item["sample_id"],
        "status": status,
        "labels": {name: labels[name] for name in item["labels"]},
        "reviewer_id": "local-owner",
    }


def read_reviews(path: Path) -> dict[str, Any]:
    if not path.exists():
        return {"schema_version": "gloshia-r3-focused-owner-reviews-v1", "reviews": {}}
    payload = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(payload.get("reviews"), dict):
        raise ValueError("reviews file is invalid")
    return payload


def write_reviews(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    temporary.replace(path)


HTML = r"""<!doctype html>
<html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>GloshIA R3</title><style>
:root{font-family:system-ui,-apple-system,sans-serif;color:#172033;background:#f4f6fa}*{box-sizing:border-box}
body{margin:0}.app{max-width:720px;margin:auto;min-height:100vh;background:white;padding:14px 14px 28px}
header{display:flex;justify-content:space-between;align-items:center;gap:12px}.title{font-weight:800;font-size:20px}
.progress{font-variant-numeric:tabular-nums;color:#526078}.bar{height:5px;background:#e7ebf2;border-radius:5px;margin:10px 0 14px;overflow:hidden}.fill{height:100%;background:#3567e8;width:0}
.photo{width:100%;height:min(52vh,520px);object-fit:contain;background:#eef1f6;border-radius:16px}.sample{font-size:12px;color:#667085;margin:8px 2px;word-break:break-all}
.hint{font-size:14px;margin:12px 0 8px}.chips{display:flex;flex-wrap:wrap;gap:8px}.chip{border:1px solid #cdd5e1;background:white;border-radius:999px;padding:9px 12px;font-size:13px}.chip.on{background:#172e70;color:white;border-color:#172e70}
.actions{position:sticky;bottom:0;display:grid;grid-template-columns:1fr 1.4fr;gap:10px;margin-top:18px;padding:10px 0;background:white}button{min-height:48px;font-weight:700}.secondary{border:1px solid #cdd5e1;background:white;border-radius:12px}.primary{border:0;background:#3567e8;color:white;border-radius:12px}.nav{display:flex;justify-content:flex-start;margin-top:6px}.nav button{border:0;background:transparent;color:#3567e8;padding:6px}.done{text-align:center;padding:80px 20px;font-size:20px;font-weight:700}.error{color:#b42318;margin:10px 0}
</style></head><body><main class="app" id="app"><div>Cargando…</div></main><script>
const token=new URLSearchParams(location.search).get('token')||'';let state,current=0,selected=new Set();
const names={explicit_or_nudity:'Explícita/desnudez',underwear_or_swimwear:'Ropa interior/baño',transparent_clothing:'Transparencia',neckline_or_chest:'Escote/pecho',abdomen_visible:'Abdomen',shoulder_or_armpit:'Hombro/axila',elbow_uncovered:'Codo',knee_uncovered:'Rodilla',tight_clothing:'Ropa ajustada',sexualized_pose:'Pose sugerente'};
async function load(){const r=await fetch('/api/state?token='+encodeURIComponent(token));if(!r.ok)throw Error('No autorizado');state=await r.json();const pending=state.items.findIndex(x=>!x.review);current=pending<0?state.items.length:pending;render()}
function render(){const app=document.getElementById('app');const reviewed=state.items.filter(x=>x.review).length;if(current>=state.items.length){app.innerHTML=`<div class="done">Listo: ${reviewed}/${state.items.length} revisadas ✅</div>`;return}const x=state.items[current];const labels=x.review?x.review.labels:x.labels;selected=new Set(Object.keys(labels).filter(k=>labels[k]==='positive'));app.innerHTML=`<header><div class="title">GloshIA R3</div><div class="progress">${reviewed}/${state.items.length}</div></header><div class="bar"><div class="fill" style="width:${100*reviewed/state.items.length}%"></div></div><img class="photo" src="${x.image_url}" alt="Foto a revisar"><div class="sample">${current+1}. ${x.sample_id}</div><div class="hint">Marcá todos los motivos visibles:</div><div class="chips">${Object.keys(labels).map(k=>`<button class="chip ${selected.has(k)?'on':''}" data-key="${k}">${names[k]||k}</button>`).join('')}</div><div class="error" id="error"></div><div class="actions"><button class="secondary" id="doubt">Dudosa</button><button class="primary" id="save">Guardar y seguir</button></div><div class="nav"><button id="prev">← Anterior</button></div>`;document.querySelectorAll('.chip').forEach(b=>b.onclick=()=>{selected.has(b.dataset.key)?selected.delete(b.dataset.key):selected.add(b.dataset.key);b.classList.toggle('on')});document.getElementById('save').onclick=()=>save(false);document.getElementById('doubt').onclick=()=>save(true);document.getElementById('prev').onclick=()=>{if(current>0){current--;render()}}}
async function save(doubt){const x=state.items[current];const labels={};const button=document.getElementById(doubt?'doubt':'save');button.disabled=true;button.textContent='Guardando…';Object.keys(x.labels).forEach(k=>labels[k]=selected.has(k)?'positive':(doubt?'unknown':'negative'));try{const r=await fetch('/api/review?token='+encodeURIComponent(token),{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({sample_id:x.sample_id,status:doubt?'doubt':'complete',labels})});if(!r.ok)throw Error();x.review=(await r.json()).review;current++;render()}catch(e){button.disabled=false;button.textContent=doubt?'Dudosa':'Guardar y seguir';document.getElementById('error').textContent='No se pudo guardar. Revisá la conexión.'}}
load().catch(e=>document.getElementById('app').innerHTML='<div class="error">'+e.message+'</div>');
</script></body></html>"""


def make_handler(queue_path: Path, reviews_path: Path, token: str) -> type[BaseHTTPRequestHandler]:
    queue_payload = json.loads(queue_path.read_text(encoding="utf-8"))
    items = queue_payload["queue"]
    by_id = {item["sample_id"]: item for item in items}

    class Handler(BaseHTTPRequestHandler):
        def _authorized(self) -> bool:
            query = parse_qs(urlparse(self.path).query)
            return secrets.compare_digest(query.get("token", [""])[0], token)

        def _json(self, payload: Any, status: HTTPStatus = HTTPStatus.OK) -> None:
            body = json.dumps(payload, ensure_ascii=False).encode()
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(body)

        def do_GET(self) -> None:  # noqa: N802
            parsed = urlparse(self.path)
            if not self._authorized():
                self._json({"error": "unauthorized"}, HTTPStatus.UNAUTHORIZED)
                return
            if parsed.path == "/":
                body = HTML.encode()
                self.send_response(HTTPStatus.OK)
                self.send_header("Content-Type", "text/html; charset=utf-8")
                self.send_header("Content-Length", str(len(body)))
                self.send_header("Cache-Control", "no-store")
                self.end_headers()
                self.wfile.write(body)
                return
            if parsed.path == "/api/state":
                reviews = read_reviews(reviews_path)["reviews"]
                public_items = []
                for index, item in enumerate(items):
                    public_items.append(
                        {
                            "sample_id": item["sample_id"],
                            "labels": item["labels"],
                            "review": reviews.get(item["sample_id"]),
                            "image_url": f"/image/{index}?token={token}",
                        }
                    )
                self._json({"items": public_items})
                return
            if parsed.path.startswith("/image/"):
                try:
                    index = int(parsed.path.removeprefix("/image/"))
                    item = items[index]
                    path = Path(item["image_path"]).resolve(strict=True)
                except (ValueError, IndexError, OSError):
                    self._json({"error": "not_found"}, HTTPStatus.NOT_FOUND)
                    return
                body = path.read_bytes()
                self.send_response(HTTPStatus.OK)
                self.send_header("Content-Type", mimetypes.guess_type(path.name)[0] or "image/jpeg")
                self.send_header("Content-Length", str(len(body)))
                self.send_header("Cache-Control", "private, max-age=3600")
                self.end_headers()
                self.wfile.write(body)
                return
            self._json({"error": "not_found"}, HTTPStatus.NOT_FOUND)

        def do_POST(self) -> None:  # noqa: N802
            if not self._authorized() or urlparse(self.path).path != "/api/review":
                self._json({"error": "unauthorized"}, HTTPStatus.UNAUTHORIZED)
                return
            try:
                length = int(self.headers.get("Content-Length", "0"))
                if length < 1 or length > MAX_REQUEST_BYTES:
                    raise ValueError("invalid body size")
                payload = json.loads(self.rfile.read(length))
                sample_id = payload.get("sample_id")
                item = by_id.get(sample_id)
                if item is None:
                    raise ValueError("sample not found")
                review = normalize_review(item, payload)
                stored = read_reviews(reviews_path)
                stored["reviews"][sample_id] = review
                write_reviews(reviews_path, stored)
            except (ValueError, json.JSONDecodeError) as error:
                self._json({"error": str(error)}, HTTPStatus.BAD_REQUEST)
                return
            self._json({"saved": True, "review": review})

        def log_message(self, format: str, *args: object) -> None:
            return

    return Handler


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--queue", required=True, type=Path)
    parser.add_argument("--reviews", required=True, type=Path)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", default=8768, type=int)
    parser.add_argument("--token", default=None)
    args = parser.parse_args()
    token = args.token or secrets.token_urlsafe(18)
    server = ThreadingHTTPServer((args.host, args.port), make_handler(args.queue, args.reviews, token))
    print(json.dumps({"host": args.host, "port": args.port, "token": token}), flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
