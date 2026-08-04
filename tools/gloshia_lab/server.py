"""Loopback-only review server for GloshIA Lab."""

from __future__ import annotations

import hmac
import ipaddress
import json
import mimetypes
import re
import secrets
import socket
import shutil
import threading
from datetime import datetime, timezone
from http import HTTPStatus
from http.cookies import CookieError, SimpleCookie
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any
from urllib.parse import parse_qs, quote, unquote, urlparse

from .metrics import build_review_queue, evaluation_report, joined_rows, read_reviews


SAFE_ID = re.compile(r"^[A-Za-z0-9:._-]{1,180}$")
ACTIONS = {"allow", "filter", "doubt"}
REASONS = {
    "neckline_or_chest",
    "shoulder_or_armpit",
    "elbow_uncovered",
    "abdomen_visible",
    "knee_uncovered",
    "tight_clothing",
    "transparent_clothing",
    "underwear_or_swimwear",
    "explicit_or_nudity",
    "sexualized_pose",
    "other",
    "uncertain_reason",
}

REVIEW_QUEUE_OPTIONS = {
    "possible_filter",
    "disagreement",
    "borderline",
    "doubt",
    "possible_false_filter",
    "random",
    "rest",
}


def review_queue_name(row: dict[str, Any], reviews: dict[str, dict[str, Any]]) -> str:
    """Assign a deterministic, non-overlapping preparation queue."""
    review = reviews.get(row["sample_id"])
    prediction = row.get("model_prediction") or {}
    if review:
        action = review.get("action")
        if action == "doubt":
            return "doubt"
        if action in {"allow", "filter"} and action != prediction.get("action"):
            return "disagreement"
        return "rest"
    if prediction.get("error"):
        return "rest"
    model_action = prediction.get("action")
    score = float(prediction.get("maximum_probability") or 0.0)
    category = str(row.get("category") or "")
    context_category = any(
        marker in category
        for marker in (
            "men",
            "covered",
            "group",
            "family",
            "school",
            "community",
            "small",
            "partial",
            "public",
            "sports",
            "activewear",
        )
    )
    if model_action == "filter" and (context_category or score < 0.55):
        return "possible_false_filter"
    if 0.40 <= score <= 0.60:
        return "borderline"
    if model_action == "filter":
        return "possible_filter"
    # Stable sampling gives a repeatable ten-percent control queue.
    if int.from_bytes(row["sample_id"].encode("utf-8"), "big") % 10 == 0:
        return "random"
    return "rest"


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def write_reviews(path: Path, reviews: dict[str, dict[str, Any]]) -> None:
    payload = {
        "schema_version": "gloshia-lab-human-review-v1",
        "updated_at": utc_now(),
        "reviews": reviews,
    }
    temporary = path.with_suffix(".json.tmp")
    temporary.write_text(
        json.dumps(payload, indent=2, ensure_ascii=False, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    temporary.replace(path)


class ReviewServer(ThreadingHTTPServer):
    def __init__(
        self,
        address: tuple[str, int],
        corpus_dir: Path,
        web_dir: Path,
        include_sealed: bool,
        access_token: str | None = None,
    ) -> None:
        super().__init__(address, ReviewHandler)
        self.corpus_dir = corpus_dir.resolve()
        self.web_dir = web_dir.resolve()
        self.include_sealed = include_sealed
        self.rows = joined_rows(self.corpus_dir, include_sealed=include_sealed)
        self.by_id = {row["sample_id"]: row for row in self.rows}
        self.queue = build_review_queue(self.rows, maximum=max(500, len(self.rows)))
        self.queue_ids = {row["sample_id"] for row in self.queue}
        self.review_lock = threading.Lock()
        self.access_token = access_token


class ReviewHandler(BaseHTTPRequestHandler):
    server: ReviewServer

    def log_message(self, format: str, *args: Any) -> None:
        return

    def _trusted_host(self, require_origin: bool = False) -> bool:
        port = self.server.server_port
        host_header = self.headers.get("Host", "").casefold()
        try:
            parsed_host = urlparse(f"//{host_header}")
            hostname = parsed_host.hostname
            request_port = parsed_host.port
        except ValueError:
            return False
        if not hostname or request_port != port:
            return False
        if self.server.access_token is None:
            if hostname not in {"127.0.0.1", "localhost"}:
                return False
        else:
            try:
                address = ipaddress.ip_address(hostname)
            except ValueError:
                return False
            if (
                not (address.is_private or address.is_loopback)
                or address.is_unspecified
                or address.is_multicast
            ):
                return False
        origin = self.headers.get("Origin")
        if not origin:
            return not require_origin
        return origin.casefold() == f"http://{host_header}"

    def _authenticated(self) -> bool:
        expected = self.server.access_token
        if expected is None:
            return True
        cookie = SimpleCookie()
        try:
            cookie.load(self.headers.get("Cookie", ""))
        except CookieError:
            return False
        supplied = cookie.get("gloshia_session")
        return bool(supplied and hmac.compare_digest(supplied.value, expected))

    def _start_authenticated_session(self, parsed: Any) -> bool:
        expected = self.server.access_token
        if expected is None or parsed.path != "/":
            return False
        supplied = parse_qs(parsed.query).get("token", [""])[0]
        if not hmac.compare_digest(supplied, expected):
            return False
        self.send_response(HTTPStatus.SEE_OTHER)
        self.send_header("Location", "/")
        self.send_header(
            "Set-Cookie",
            f"gloshia_session={expected}; HttpOnly; SameSite=Lax; Path=/; Max-Age=14400",
        )
        self.send_header("Cache-Control", "no-store")
        self.send_header("Referrer-Policy", "no-referrer")
        self.end_headers()
        return True

    def _headers(
        self,
        status: int,
        content_type: str,
        length: int,
        attachment_name: str | None = None,
    ) -> None:
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(length))
        self.send_header("Cache-Control", "no-store")
        self.send_header(
            "Content-Security-Policy",
            "default-src 'self'; img-src 'self'; style-src 'self' 'unsafe-inline'; "
            "script-src 'self'; connect-src 'self'; object-src 'none'; frame-ancestors 'none'",
        )
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Cross-Origin-Resource-Policy", "same-origin")
        self.send_header("Referrer-Policy", "no-referrer")
        if attachment_name is not None:
            self.send_header(
                "Content-Disposition",
                f'attachment; filename="{attachment_name}"',
            )
        self.end_headers()

    def _json(
        self,
        payload: Any,
        status: int = HTTPStatus.OK,
        attachment_name: str | None = None,
    ) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self._headers(
            status,
            "application/json; charset=utf-8",
            len(body),
            attachment_name=attachment_name,
        )
        self.wfile.write(body)
        self.wfile.flush()

    def _file(self, path: Path, content_type: str | None = None) -> None:
        try:
            resolved = path.resolve(strict=True)
        except OSError:
            self.send_error(HTTPStatus.NOT_FOUND)
            return
        allowed_roots = (self.server.web_dir, self.server.corpus_dir / "images")
        if not any(resolved.is_relative_to(root) for root in allowed_roots):
            self.send_error(HTTPStatus.FORBIDDEN)
            return
        body = resolved.read_bytes()
        mime = content_type or mimetypes.guess_type(resolved.name)[0] or "application/octet-stream"
        self._headers(HTTPStatus.OK, mime, len(body))
        self.wfile.write(body)

    def do_GET(self) -> None:
        if not self._trusted_host():
            self.send_error(HTTPStatus.FORBIDDEN)
            return
        parsed = urlparse(self.path)
        if self._start_authenticated_session(parsed):
            return
        if not self._authenticated():
            self.send_error(HTTPStatus.UNAUTHORIZED)
            return
        if parsed.path == "/":
            self._file(self.server.web_dir / "index.html", "text/html; charset=utf-8")
            return
        if parsed.path == "/app.js":
            self._file(self.server.web_dir / "app.js", "text/javascript; charset=utf-8")
            return
        if parsed.path == "/styles.css":
            self._file(self.server.web_dir / "styles.css", "text/css; charset=utf-8")
            return
        if parsed.path.startswith("/image/"):
            sample_id = unquote(parsed.path.removeprefix("/image/"))
            row = self.server.by_id.get(sample_id)
            if row is None:
                self.send_error(HTTPStatus.NOT_FOUND)
                return
            self._file(self.server.corpus_dir / row["local_path"])
            return
        if parsed.path == "/api/status":
            report = evaluation_report(
                self.server.corpus_dir,
                include_sealed=self.server.include_sealed,
            )
            reviews = read_reviews(self.server.corpus_dir / "reviews.json")
            self._json(
                {
                    **report,
                    "queue": len(self.server.queue),
                    "queue_remaining": sum(
                        sample_id not in reviews for sample_id in self.server.queue_ids
                    ),
                    "reviewed_total": len(reviews),
                    "review_target": len(self.server.rows),
                    "categories": sorted({row.get("category") for row in self.server.rows if row.get("category")}),
                    "origins": sorted({row.get("catalog") for row in self.server.rows if row.get("catalog")}),
                    "sealed_unlocked": self.server.include_sealed,
                }
            )
            return
        if parsed.path == "/api/items":
            query = parse_qs(parsed.query)
            scope = query.get("scope", ["queue"])[0]
            rows = self.server.queue if scope == "queue" else self.server.rows
            reviews = read_reviews(self.server.corpus_dir / "reviews.json")
            action = query.get("action", [""])[0]
            category = query.get("category", [""])[0]
            human = query.get("human", [""])[0]
            relation = query.get("relation", [""])[0]
            origin = query.get("origin", [""])[0]
            review_queue = query.get("review_queue", [""])[0]
            if review_queue in REVIEW_QUEUE_OPTIONS:
                rows = [
                    row
                    for row in rows
                    if review_queue_name(row, reviews) == review_queue
                ]
            if action:
                rows = [
                    row
                    for row in rows
                    if row["sample_id"] in reviews
                    if (row.get("model_prediction") or {}).get("action") == action
                ]
            if category:
                rows = [
                    row
                    for row in rows
                    if row["sample_id"] in reviews and row.get("category") == category
                ]
            if human:
                rows = [
                    row
                    for row in rows
                    if (reviews.get(row["sample_id"]) or {}).get("action") == human
                ]
            if origin:
                rows = [row for row in rows if row.get("catalog") == origin]
            if relation:
                def is_relation(row: dict[str, Any]) -> bool:
                    review = reviews.get(row["sample_id"]) or {}
                    prediction = row.get("model_prediction") or {}
                    expected = review.get("action")
                    actual = prediction.get("action")
                    if relation == "disagreement":
                        return expected in {"allow", "filter"} and expected != actual
                    if relation == "false_allow":
                        return expected == "filter" and actual == "allow"
                    if relation == "false_filter":
                        return expected == "allow" and actual == "filter"
                    if relation == "doubt":
                        return expected == "doubt"
                    return True
                rows = [row for row in rows if is_relation(row)]
            try:
                requested_limit = int(query.get("limit", ["200"])[0])
            except ValueError:
                requested_limit = 200
            limit = min(600, max(1, requested_limit))
            payload = []
            for row in rows[:limit]:
                review = reviews.get(row["sample_id"])
                payload.append(
                    {
                        "sample_id": row["sample_id"],
                        "image_url": f"/image/{quote(row['sample_id'], safe='')}",
                        "category": row.get("category") if review else None,
                        "split": row.get("split") if review else None,
                        "model_prediction": row.get("model_prediction") if review else None,
                        "human_decision": review,
                    }
                )
            self._json({"items": payload, "count": len(payload)})
            return
        if parsed.path == "/api/export":
            reviews = read_reviews(self.server.corpus_dir / "reviews.json")
            self._json(
                {
                    "schema_version": "gloshia-lab-review-export-v1",
                    "exported_at": utc_now(),
                    "sealed_unlocked": self.server.include_sealed,
                    "reviews": reviews,
                },
                attachment_name="gloshia-lab-revision.json",
            )
            return
        self.send_error(HTTPStatus.NOT_FOUND)

    def do_POST(self) -> None:
        if not self._trusted_host(require_origin=True) or not self._authenticated():
            self.send_error(HTTPStatus.FORBIDDEN)
            return
        endpoint = urlparse(self.path).path
        if endpoint not in {"/api/review", "/api/restart", "/api/import"}:
            self.send_error(HTTPStatus.NOT_FOUND)
            return
        if self.headers.get("Content-Type", "").split(";")[0] != "application/json":
            self.send_error(HTTPStatus.UNSUPPORTED_MEDIA_TYPE)
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if length <= 0 or length > 512_000:
                raise ValueError("invalid body size")
            payload = json.loads(self.rfile.read(length))
            if endpoint == "/api/restart":
                reviews_path = self.server.corpus_dir / "reviews.json"
                with self.server.review_lock:
                    if reviews_path.exists():
                        backup = reviews_path.with_name(
                            f"reviews.backup-{datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')}.json"
                        )
                        shutil.copy2(reviews_path, backup)
                    write_reviews(reviews_path, {})
                self._json({"ok": True, "reviewed_total": 0})
                return
            if endpoint == "/api/import":
                imported = payload.get("reviews")
                if not isinstance(imported, dict):
                    raise ValueError("invalid review export")
                validated: dict[str, dict[str, Any]] = {}
                for sample_id, review in imported.items():
                    if (
                        not isinstance(sample_id, str)
                        or not SAFE_ID.fullmatch(sample_id)
                        or sample_id not in self.server.by_id
                        or not isinstance(review, dict)
                        or review.get("action") not in ACTIONS
                        or not isinstance(review.get("reasons", []), list)
                        or any(reason not in REASONS for reason in review.get("reasons", []))
                    ):
                        raise ValueError("invalid review export")
                    validated[sample_id] = review
                reviews_path = self.server.corpus_dir / "reviews.json"
                with self.server.review_lock:
                    if reviews_path.exists():
                        backup = reviews_path.with_name(
                            f"reviews.backup-{datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')}.json"
                        )
                        shutil.copy2(reviews_path, backup)
                    write_reviews(reviews_path, validated)
                self._json({"ok": True, "reviewed_total": len(validated)})
                return
            sample_id = payload.get("sample_id")
            action = payload.get("action")
            reasons = payload.get("reasons", [])
            if (
                not isinstance(sample_id, str)
                or not SAFE_ID.fullmatch(sample_id)
                or sample_id not in self.server.by_id
                or action not in ACTIONS
                or not isinstance(reasons, list)
                or len(reasons) > 12
                or any(reason not in REASONS for reason in reasons)
                or (action == "allow" and reasons)
            ):
                raise ValueError("invalid review")
            reviews_path = self.server.corpus_dir / "reviews.json"
            with self.server.review_lock:
                reviews = read_reviews(reviews_path)
                reviews[sample_id] = {
                    "action": action,
                    "reasons": reasons,
                    "reviewed_at": utc_now(),
                    "reviewer_id": "local-owner",
                }
                write_reviews(reviews_path, reviews)
            row = self.server.by_id[sample_id]
            prediction = row.get("model_prediction")
            self._json(
                {
                    "ok": True,
                    "review": reviews[sample_id],
                    "model_prediction": prediction,
                    "category": row.get("category"),
                    "split": row.get("split"),
                    "matched_model": (
                        action == prediction.get("action")
                        if action in {"allow", "filter"} and prediction
                        else None
                    ),
                }
            )
        except (json.JSONDecodeError, OSError, TypeError, ValueError):
            self._json({"error": "invalid review"}, HTTPStatus.BAD_REQUEST)

    def do_DELETE(self) -> None:
        if not self._trusted_host(require_origin=True) or not self._authenticated():
            self.send_error(HTTPStatus.FORBIDDEN)
            return
        if urlparse(self.path).path != "/api/review":
            self.send_error(HTTPStatus.NOT_FOUND)
            return
        if self.headers.get("Content-Type", "").split(";")[0] != "application/json":
            self.send_error(HTTPStatus.UNSUPPORTED_MEDIA_TYPE)
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if length <= 0 or length > 4096:
                raise ValueError("invalid body size")
            payload = json.loads(self.rfile.read(length))
            sample_id = payload.get("sample_id")
            if (
                not isinstance(sample_id, str)
                or not SAFE_ID.fullmatch(sample_id)
                or sample_id not in self.server.by_id
            ):
                raise ValueError("invalid review")
            reviews_path = self.server.corpus_dir / "reviews.json"
            with self.server.review_lock:
                reviews = read_reviews(reviews_path)
                removed = reviews.pop(sample_id, None)
                write_reviews(reviews_path, reviews)
            self._json({"ok": True, "removed": removed is not None})
        except (json.JSONDecodeError, OSError, TypeError, ValueError):
            self._json({"error": "invalid review"}, HTTPStatus.BAD_REQUEST)


def serve(
    corpus_dir: Path,
    web_dir: Path,
    port: int,
    include_sealed: bool = False,
    lan: bool = False,
) -> None:
    access_token = secrets.token_urlsafe(24) if lan else None
    server = ReviewServer(
        ("0.0.0.0" if lan else "127.0.0.1", port),
        corpus_dir=corpus_dir,
        web_dir=web_dir,
        include_sealed=include_sealed,
        access_token=access_token,
    )
    if lan:
        addresses: set[str] = set()
        try:
            for result in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET):
                address = ipaddress.ip_address(result[4][0])
                if address.is_private and not address.is_loopback:
                    addresses.add(str(address))
        except OSError:
            pass
        try:
            with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as probe:
                probe.connect(("8.8.8.8", 80))
                address = ipaddress.ip_address(probe.getsockname()[0])
                if address.is_private and not address.is_loopback:
                    addresses.add(str(address))
        except OSError:
            pass
        for address in sorted(addresses):
            print(f"Laboratorio GloshIA móvil: http://{address}:{port}/?token={access_token}")
        if not addresses:
            print("No se detectó una dirección Wi-Fi privada para mostrar el enlace.")
    else:
        print(f"Laboratorio GloshIA: http://127.0.0.1:{port}")
    print(f"Cola de revisión: {len(server.queue)}")
    print("El examen final está ABIERTO." if include_sealed else "El examen final sigue SELLADO.")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nLaboratorio detenido.")
    finally:
        server.server_close()
