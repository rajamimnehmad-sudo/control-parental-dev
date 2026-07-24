#!/usr/bin/env bash
set -euo pipefail

if ! command -v python3 >/dev/null 2>&1; then
    echo "ERROR: python3 is required to verify the DAG v1 archive." >&2
    exit 2
fi

repo_root="$(git rev-parse --show-toplevel 2>/dev/null || true)"
if [[ -z "$repo_root" ]]; then
    echo "ERROR: run this script from inside the content-filter Git repository." >&2
    exit 2
fi

manifest="$repo_root/docs/dag/v1/dag-v1-model-manifest.json"
if [[ ! -f "$manifest" ]]; then
    echo "ERROR: missing manifest: docs/dag/v1/dag-v1-model-manifest.json" >&2
    exit 1
fi

python3 - "$repo_root" "$manifest" <<'PY'
import hashlib
import json
import pathlib
import subprocess
import sys

root = pathlib.Path(sys.argv[1]).resolve()
manifest_path = pathlib.Path(sys.argv[2]).resolve()

try:
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
except (OSError, json.JSONDecodeError) as error:
    print(f"ERROR: invalid DAG v1 manifest: {error}", file=sys.stderr)
    raise SystemExit(1)

source_commit = manifest.get("source_commit")
if not isinstance(source_commit, str) or len(source_commit) != 40:
    print("ERROR: manifest source_commit is missing or invalid.", file=sys.stderr)
    raise SystemExit(1)

commit_check = subprocess.run(
    ["git", "-C", str(root), "cat-file", "-e", f"{source_commit}^{{commit}}"],
    stdout=subprocess.DEVNULL,
    stderr=subprocess.DEVNULL,
    check=False,
)
if commit_check.returncode != 0:
    print(f"ERROR: archived source commit is not available locally: {source_commit}", file=sys.stderr)
    raise SystemExit(1)

entries = []
for section in ("models", "policy_files", "supporting_files"):
    values = manifest.get(section)
    if not isinstance(values, list) or not values:
        print(f"ERROR: manifest section {section!r} is missing or empty.", file=sys.stderr)
        raise SystemExit(1)
    for value in values:
        if not isinstance(value, dict):
            print(f"ERROR: invalid entry in manifest section {section!r}.", file=sys.stderr)
            raise SystemExit(1)
        entries.append((section, value))

failures = []
verified = 0
for section, entry in entries:
    relative_path = entry.get("relative_path")
    expected_hash = entry.get("sha256")
    expected_size = entry.get("size_bytes")
    if (
        not isinstance(relative_path, str)
        or not relative_path
        or not isinstance(expected_hash, str)
        or len(expected_hash) != 64
        or not isinstance(expected_size, int)
        or expected_size < 0
    ):
        failures.append(f"{section}: malformed manifest entry: {entry!r}")
        continue

    candidate = (root / relative_path).resolve()
    try:
        candidate.relative_to(root)
    except ValueError:
        failures.append(f"{relative_path}: path escapes repository root")
        continue
    if not candidate.is_file():
        failures.append(f"{relative_path}: file is missing")
        continue

    digest = hashlib.sha256()
    actual_size = 0
    with candidate.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            actual_size += len(block)
            digest.update(block)
    actual_hash = digest.hexdigest()

    mismatch = False
    if actual_size != expected_size:
        failures.append(
            f"{relative_path}: size mismatch; expected {expected_size}, got {actual_size}"
        )
        mismatch = True
    if actual_hash != expected_hash:
        failures.append(
            f"{relative_path}: SHA-256 mismatch; expected {expected_hash}, got {actual_hash}"
        )
        mismatch = True
    if not mismatch:
        verified += 1

if failures:
    print("DAG v1 archive verification FAILED:", file=sys.stderr)
    for failure in failures:
        print(f" - {failure}", file=sys.stderr)
    raise SystemExit(1)

external_count = len(manifest.get("external_models", []))
print(
    f"DAG v1 archive verified: {verified} repository files match size and SHA-256; "
    f"source commit {source_commit} is available."
)
if external_count:
    print(
        f"Note: {external_count} external runtime model(s) are documented but intentionally "
        "not downloaded or verified by this read-only script."
    )
PY
