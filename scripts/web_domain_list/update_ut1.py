#!/usr/bin/env python3
"""Builds and atomically publishes the signed DEV web-domain database."""

from __future__ import annotations

import argparse
import base64
import email.utils
import hashlib
import json
import os
import pathlib
import struct
import subprocess
import tarfile
import tempfile
import time
import urllib.request
from datetime import datetime, timedelta, timezone

PROJECT_REF = "syeycayasyufedwoprea"
BUCKET = "dev-updates"
BASE_PATH = "web-domain-list/dev"
PUBLIC_BASE = f"https://{PROJECT_REF}.supabase.co/storage/v1/object/public/{BUCKET}/{BASE_PATH}"
PUBLISH_ENDPOINT = f"https://{PROJECT_REF}.supabase.co/functions/v1/update-web-domain-list"
SOURCE_BASE = "https://dsi.ut-capitole.fr/blacklists/download"
ENVIRONMENT = "DEV"
CANARY = "coca.com"
FORMAT_VERSION = 2
HASH_COUNT = 7
BITS_PER_ENTRY = 8
MINIMUM_BITS = 1_024
EXACT_HASH_BYTES = 8
MAGIC = b"CFDL0001"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--action", choices=("refresh", "publish_canary", "remove_canary", "pending"), default="refresh")
    args = parser.parse_args()
    request_time = None
    try:
        action = args.action
        if action == "pending":
            pending = fetch_json(f"{PUBLIC_BASE}/request.json?ts={time.time_ns()}", required=False)
            operational = fetch_json(f"{PUBLIC_BASE}/status.json?ts={time.time_ns()}", required=False) or {}
            if not pending or pending.get("requestedAt") == operational.get("lastProcessedRequestAt"):
                print(json.dumps({"pending": False}))
                return
            action = pending.get("action")
            if action not in {"refresh", "publish_canary", "remove_canary"}:
                raise RuntimeError("pending request action is invalid")
            request_time = pending.get("requestedAt")
        if action == "refresh":
            current = fetch_current_manifest(required=False)
            source = build_from_ut1(bool(current and current.get("canaryIncluded")))
        else:
            current = fetch_current_manifest(required=True)
            source = read_existing_bundle(current)
            source["canary_included"] = action == "publish_canary"
        payload = publish(source)
        publish_status({"state": "active", "activeVersion": payload["version"], "checkedAt": payload["generatedAt"], "lastError": None, "lastProcessedRequestAt": request_time})
        print(json.dumps({"version": payload["version"], "totalCount": payload["totalCount"], "canaryIncluded": payload["canaryIncluded"]}))
    except Exception as error:
        publish_status({"state": "error", "checkedAt": now_iso(), "lastError": f"{type(error).__name__}: {error}", "lastProcessedRequestAt": request_time})
        raise


def build_from_ut1(canary_included: bool) -> dict:
    with tempfile.TemporaryDirectory(prefix="ut1-domain-list-") as temporary:
        root = pathlib.Path(temporary)
        adult_file, adult_date = normalized_category_file("adult", root)
        porn_file, porn_date = normalized_category_file("porn", root)
        adult_count = line_count(adult_file)
        porn_count = line_count(porn_file)
        combined_adult = root / "adult-and-porn.normalized"
        subprocess.run(
            ["sort", "-u", "-o", str(combined_adult), str(adult_file), str(porn_file)],
            check=True,
            env={**os.environ, "LC_ALL": "C"},
        )
        adult, adult_exact, combined_adult_count, adult_bit_count = build_category_file(
            "adult-and-porn",
            combined_adult,
        )
        mixed, mixed_exact, mixed_count, mixed_bit_count, mixed_date = build_category("mixed_adult", root)
        exceptions, _, education_date = exact_category("sexual_education", root)
    return {
        "adult_bits": adult,
        "mixed_bits": mixed,
        "adult_exact": adult_exact,
        "mixed_exact": mixed_exact,
        "adult_bit_count": adult_bit_count,
        "mixed_bit_count": mixed_bit_count,
        "adult_count": combined_adult_count,
        "mixed_count": mixed_count,
        "category_counts": {"adult": adult_count, "porn": porn_count, "mixed_adult": mixed_count},
        "educational_exceptions": exceptions,
        "source_date": max(adult_date, porn_date, mixed_date, education_date),
        "canary_included": canary_included,
    }


def build_category(category: str, root: pathlib.Path) -> tuple[bytearray, bytes, int, int, str]:
    normalized, source_date = normalized_category_file(category, root)
    bits, exact, count, bit_count = build_category_file(category, normalized)
    return bits, exact, count, bit_count, source_date


def build_category_file(category: str, normalized: pathlib.Path) -> tuple[bytearray, bytes, int, int]:
    count = line_count(normalized)
    if count == 0:
        raise RuntimeError(f"UT1 category {category} is empty")
    bit_count = max(MINIMUM_BITS, count * BITS_PER_ENTRY)
    bit_count = ((bit_count + 7) // 8) * 8
    bits = bytearray((bit_count + 7) // 8)
    exact_text = root / f"{category}.hashes"
    with normalized.open("r", encoding="ascii") as domains:
        with exact_text.open("w", encoding="ascii") as hashes:
            for line in domains:
                domain = line.rstrip("\n")
                add_bloom(bits, bit_count, domain)
                hashes.write(hashlib.sha256(domain.encode("ascii")).digest()[:EXACT_HASH_BYTES].hex() + "\n")
    subprocess.run(["sort", "-u", "-o", str(exact_text), str(exact_text)], check=True, env={**os.environ, "LC_ALL": "C"})
    exact = bytearray()
    with exact_text.open("r", encoding="ascii") as hashes:
        for line in hashes:
            exact.extend(bytes.fromhex(line.rstrip("\n")))
    if len(exact) != count * EXACT_HASH_BYTES:
        raise RuntimeError(f"UT1 category {category} has an exact-hash collision")
    return bits, bytes(exact), count, bit_count


def line_count(path: pathlib.Path) -> int:
    with path.open("r", encoding="ascii") as lines:
        return sum(1 for _ in lines)


def exact_category(category: str, root: pathlib.Path) -> tuple[list[str], int, str]:
    normalized, source_date = normalized_category_file(category, root)
    domains = normalized.read_text(encoding="ascii").splitlines()
    return domains, len(domains), source_date


def normalized_category_file(category: str, root: pathlib.Path) -> tuple[pathlib.Path, str]:
    archive = root / f"{category}.tar.gz"
    request = urllib.request.Request(f"{SOURCE_BASE}/{category}.tar.gz", headers={"User-Agent": "ContentFilter-UT1-Updater/1"})
    with urllib.request.urlopen(request, timeout=120) as response, archive.open("wb") as output:
        source_date = http_date_iso(response.headers.get("Last-Modified"))
        while chunk := response.read(1024 * 1024):
            output.write(chunk)
    unsorted = root / f"{category}.normalized"
    found = False
    with tarfile.open(archive, "r:gz") as tar, unsorted.open("w", encoding="ascii") as output:
        for member in tar:
            if not member.isfile() or not (member.name.endswith("/domains") or member.name == "domains"):
                continue
            found = True
            source = tar.extractfile(member)
            if source is None:
                continue
            for raw in source:
                domain = normalize_domain(raw.decode("utf-8", errors="ignore"))
                if domain:
                    output.write(domain + "\n")
    if not found:
        raise RuntimeError(f"UT1 archive {category} has no domains entry")
    subprocess.run(["sort", "-u", "-o", str(unsorted), str(unsorted)], check=True, env={**os.environ, "LC_ALL": "C"})
    return unsorted, source_date


def normalize_domain(raw: str) -> str | None:
    value = raw.strip().lower().strip(".")
    if value.startswith("www."):
        value = value[4:]
    if not value or len(value) > 253 or "." not in value:
        return None
    if any(character not in "abcdefghijklmnopqrstuvwxyz0123456789._-" for character in value):
        return None
    return value


def add_bloom(bits: bytearray, bit_count: int, value: str) -> None:
    encoded = value.encode("ascii")
    first = fnv1a(encoded, 0x811C9DC5)
    second = fnv1a(encoded, 0x01000193) | 1
    for index in range(HASH_COUNT):
        bit = ((first & 0xFFFFFFFF) + index * (second & 0xFFFFFFFF)) % bit_count
        bits[bit >> 3] |= 1 << (bit & 7)


def fnv1a(data: bytes, seed: int) -> int:
    value = seed
    for byte in data:
        value ^= byte
        value = (value * 0x01000193) & 0xFFFFFFFF
    return value


def encode_bundle(version: int, source: dict) -> bytes:
    exceptions = "\n".join(source["educational_exceptions"]).encode("ascii")
    canaries = CANARY.encode("ascii") if source["canary_included"] else b""
    header = MAGIC + struct.pack(
        ">qiiiiiiiiii",
        version,
        FORMAT_VERSION,
        HASH_COUNT,
        source["adult_bit_count"],
        source["mixed_bit_count"],
        source["adult_count"],
        source["mixed_count"],
        len(source["adult_exact"]),
        len(source["mixed_exact"]),
        len(exceptions),
        len(canaries),
    )
    return (
        header + bytes(source["adult_bits"]) + bytes(source["mixed_bits"]) +
        source["adult_exact"] + source["mixed_exact"] + exceptions + canaries
    )


def read_existing_bundle(manifest: dict) -> dict:
    data = urllib.request.urlopen(manifest["dataUrl"], timeout=180).read()
    if hashlib.sha256(data).hexdigest() != manifest["sha256"]:
        raise RuntimeError("active data checksum is invalid")
    if data[:8] != MAGIC:
        raise RuntimeError("active data format is invalid")
    values = struct.unpack(">qiiiiiiii", data[8:48])
    _, fmt, hashes, adult_bit_count, mixed_bit_count, adult_count, mixed_count, exception_length, canary_length = values
    if fmt != FORMAT_VERSION:
        raise RuntimeError("active data must be refreshed before changing the DEV canary")
    values = struct.unpack(">qiiiiiiiiii", data[8:56])
    _, fmt, hashes, adult_bit_count, mixed_bit_count, adult_count, mixed_count, adult_exact_length, mixed_exact_length, exception_length, canary_length = values
    if hashes != HASH_COUNT:
        raise RuntimeError("active data format is unsupported")
    offset = 56
    adult_bytes, mixed_bytes = adult_bit_count // 8, mixed_bit_count // 8
    adult = bytearray(data[offset : offset + adult_bytes]); offset += adult_bytes
    mixed = bytearray(data[offset : offset + mixed_bytes]); offset += mixed_bytes
    adult_exact = data[offset : offset + adult_exact_length]; offset += adult_exact_length
    mixed_exact = data[offset : offset + mixed_exact_length]; offset += mixed_exact_length
    exceptions = data[offset : offset + exception_length].decode("ascii").splitlines(); offset += exception_length + canary_length
    if offset != len(data):
        raise RuntimeError("active data length is invalid")
    return {
        "adult_bits": adult, "mixed_bits": mixed, "adult_exact": adult_exact, "mixed_exact": mixed_exact,
        "adult_bit_count": adult_bit_count, "mixed_bit_count": mixed_bit_count,
        "adult_count": adult_count, "mixed_count": mixed_count,
        "category_counts": manifest.get(
            "countByCategory",
            {"adult": adult_count, "porn": 0, "mixed_adult": mixed_count},
        ),
        "educational_exceptions": exceptions,
        "source_date": manifest["sourceDate"], "canary_included": False,
    }


def publish(source: dict) -> dict:
    version = int(time.time() * 1000)
    generated_at = now_iso()
    data = encode_bundle(version, source)
    sha256 = hashlib.sha256(data).hexdigest()
    with tempfile.TemporaryDirectory(prefix="signed-domain-list-") as temporary:
        root = pathlib.Path(temporary)
        data_file = root / f"{version}.bin"
        data_file.write_bytes(data)
        data_signature = sign_file(data_file, root)
        data_url = f"{PUBLIC_BASE}/versions/{version}.bin"
        payload = {
            "source": "UT1", "version": version, "sourceDate": source["source_date"], "generatedAt": generated_at,
            "categories": ["adult", "porn", "mixed_adult"],
            "countByCategory": source["category_counts"],
            "educationalExceptionCount": len(source["educational_exceptions"]),
            "totalCount": sum(source["category_counts"].values()), "sizeBytes": len(data), "sha256": sha256,
            "formatVersion": FORMAT_VERSION, "signatureStatus": "valid", "lastSuccessfulRun": generated_at,
            "lastError": None, "devCanary": CANARY, "canaryIncluded": source["canary_included"],
            "canarySource": "internal-dev-canary", "environment": ENVIRONMENT, "dataUrl": data_url,
            "dataSignature": data_signature, "nextScheduledAt": next_run_iso(),
        }
        payload_bytes = json.dumps(payload, separators=(",", ":"), ensure_ascii=True).encode("ascii")
        payload_file = root / "payload.json"; payload_file.write_bytes(payload_bytes)
        envelope = {"signedPayload": base64.b64encode(payload_bytes).decode("ascii"), "manifestSignature": sign_file(payload_file, root)}
        manifest_file = root / f"{version}.manifest.json"
        manifest_file.write_text(json.dumps(envelope, separators=(",", ":")), encoding="ascii")
        storage_upload(data_file, f"{BASE_PATH}/versions/{version}.bin", "application/octet-stream", immutable=True)
        storage_upload(manifest_file, f"{BASE_PATH}/versions/{version}.manifest.json", "application/json", immutable=True)
        publish_pointer("current-manifest.json", manifest_file.read_text(encoding="ascii"))
    return payload


def sign_file(path: pathlib.Path, root: pathlib.Path) -> str:
    encoded = os.environ.get("DOMAIN_LIST_SIGNING_PRIVATE_KEY_PKCS8_B64")
    if not encoded:
        raise RuntimeError("DOMAIN_LIST_SIGNING_PRIVATE_KEY_PKCS8_B64 is missing")
    der = root / "signing-key.der"; pem = root / "signing-key.pem"; signature = root / f"{path.name}.sig"
    der.write_bytes(base64.b64decode(encoded))
    subprocess.run(["openssl", "pkey", "-inform", "DER", "-in", str(der), "-out", str(pem)], check=True, capture_output=True)
    subprocess.run(["openssl", "dgst", "-sha256", "-sign", str(pem), "-out", str(signature), str(path)], check=True, capture_output=True)
    return base64.b64encode(signature.read_bytes()).decode("ascii")


def storage_upload(source: pathlib.Path, destination: str, content_type: str, immutable: bool) -> None:
    subprocess.run(["supabase", "storage", "cp", "--experimental", "--linked", "--content-type", content_type, "--cache-control", "max-age=31536000" if immutable else "max-age=60", str(source), f"ss:///{BUCKET}/{destination}"], check=True)


def publish_pointer(path: str, content: str) -> None:
    secret = os.environ.get("DOMAIN_LIST_PUBLISH_SECRET")
    if not secret:
        raise RuntimeError("DOMAIN_LIST_PUBLISH_SECRET is missing")
    body = json.dumps({"path": path, "content": content}, separators=(",", ":")).encode("utf-8")
    request = urllib.request.Request(
        PUBLISH_ENDPOINT,
        data=body,
        headers={"content-type": "application/json", "x-domain-list-publish-secret": secret},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        if response.status != 200:
            raise RuntimeError(f"pointer publication failed with HTTP {response.status}")


def fetch_current_manifest(required: bool) -> dict | None:
    try:
        envelope = json.load(urllib.request.urlopen(f"{PUBLIC_BASE}/current-manifest.json?ts={time.time_ns()}", timeout=30))
        return json.loads(base64.b64decode(envelope["signedPayload"]))
    except Exception:
        if required:
            raise RuntimeError("no valid active domain list is published")
        return None


def fetch_json(url: str, required: bool) -> dict | None:
    try:
        return json.load(urllib.request.urlopen(url, timeout=30))
    except Exception:
        if required:
            raise
        return None


def publish_status(status: dict) -> None:
    try:
        active = fetch_current_manifest(required=False)
        body = {**status, "activeVersion": active.get("version") if active else status.get("activeVersion"), "protectionActive": active is not None}
        with tempfile.TemporaryDirectory(prefix="domain-list-status-") as temporary:
            path = pathlib.Path(temporary) / "status.json"
            path.write_text(json.dumps(body, separators=(",", ":")), encoding="utf-8")
            publish_pointer("status.json", path.read_text(encoding="utf-8"))
    except Exception as error:
        print(f"warning: status publication failed: {error}")


def http_date_iso(value: str | None) -> str:
    if not value:
        return now_iso()
    return email.utils.parsedate_to_datetime(value).astimezone(timezone.utc).isoformat().replace("+00:00", "Z")


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def next_run_iso() -> str:
    next_day = datetime.now(timezone.utc) + timedelta(days=1)
    return next_day.replace(hour=3, minute=17, second=0, microsecond=0).isoformat().replace("+00:00", "Z")


if __name__ == "__main__":
    main()
