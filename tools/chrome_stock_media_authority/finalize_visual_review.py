#!/usr/bin/env python3
"""Finalize bounded model/human review and delete H19 contact-sheet pixels."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any

from h19_plan import HarnessError, safe_id


PASS_VERDICT = "NO_UNEXPECTED_IN_SCOPE_RAW_MEDIA"
ALLOWED_PASS_VERDICTS = {
    PASS_VERDICT,
    "SELECTIVE_BEHAVIOR_REVIEWED_NO_ARCHITECTURE_ESCAPE",
    "MANAGED_POLICY_EVIDENCE_REVIEWED",
}
ALLOWED_VERDICTS = ALLOWED_PASS_VERDICTS | {"IN_SCOPE_ESCAPE_OBSERVED", "INCONCLUSIVE"}


def _load(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise HarnessError(f"expected JSON object: {path}")
    return value


def finalize_review(output: Path, review_path: Path) -> dict[str, Any]:
    manifest_path = output / "visual-review-manifest.json"
    summary_path = output / "summary.json"
    manifest = _load(manifest_path)
    review = _load(review_path)
    if manifest.get("schema") != "glosh-h19-visual-review-manifest-v1":
        raise HarnessError("unexpected visual review manifest schema")
    if review.get("schema") != "glosh-h19-visual-review-v1":
        raise HarnessError("unexpected visual review verdict schema")
    reviewer = review.get("reviewer")
    if not isinstance(reviewer, str) or not 1 <= len(reviewer) <= 80:
        raise HarnessError("reviewer must be a bounded non-empty string")
    submitted = review.get("entries")
    if not isinstance(submitted, list):
        raise HarnessError("visual review entries must be a list")
    reviewed: dict[str, dict[str, str]] = {}
    for entry in submitted:
        if not isinstance(entry, dict):
            raise HarnessError("visual review entry must be an object")
        state_id = safe_id(entry.get("stateId"))
        verdict = entry.get("verdict")
        digest = entry.get("contactSheetSha256")
        if state_id in reviewed or verdict not in ALLOWED_VERDICTS or not isinstance(digest, str):
            raise HarnessError(f"invalid or duplicate visual review entry: {state_id}")
        reviewed[state_id] = {"verdict": verdict, "contactSheetSha256": digest}

    expected = {entry["stateId"]: entry for entry in manifest.get("entries", [])}
    if set(reviewed) != set(expected):
        raise HarnessError("visual review state set does not exactly match the manifest")
    results: list[dict[str, str]] = []
    contact_paths: list[Path] = []
    snapshots: list[tuple[Path, dict[str, Any], str]] = []
    for state_id, expected_entry in sorted(expected.items()):
        expected_path = f"states/{state_id}/contact-sheet.png"
        if expected_entry.get("path") != expected_path:
            raise HarnessError(f"unexpected contact sheet path: {state_id}")
        path = output / expected_path
        if not path.is_file():
            raise HarnessError(f"contact sheet missing before review finalization: {state_id}")
        actual_digest = hashlib.sha256(path.read_bytes()).hexdigest()
        submitted_entry = reviewed[state_id]
        if actual_digest != expected_entry["sha256"] or actual_digest != submitted_entry["contactSheetSha256"]:
            raise HarnessError(f"contact sheet digest mismatch: {state_id}")
        results.append(
            {
                "stateId": state_id,
                "contactSheetSha256": actual_digest,
                "verdict": submitted_entry["verdict"],
                "requiredVerdict": expected_entry.get("requiredVerdict", PASS_VERDICT),
            }
        )
        contact_paths.append(path)
        snapshot_path = output / "states" / state_id / "snapshot.json"
        if snapshot_path.is_file():
            snapshot = _load(snapshot_path)
            if snapshot.get("visualReview", {}).get("sha256") != actual_digest:
                raise HarnessError(f"snapshot visual-review digest mismatch: {state_id}")
            snapshots.append((snapshot_path, snapshot, submitted_entry["verdict"]))

    passed = all(entry["verdict"] == entry["requiredVerdict"] for entry in results)
    status = "PASS" if passed else "FAILED_OR_INCONCLUSIVE"
    result = {
        "schema": "glosh-h19-visual-review-result-v1",
        "status": status,
        "reviewer": reviewer,
        "entries": results,
        "rawContactSheetsRetained": False,
    }
    for path in contact_paths:
        path.unlink()
    for snapshot_path, snapshot, verdict in snapshots:
        snapshot["rawArtifactsRetained"] = False
        snapshot["visualReview"]["reviewStatus"] = verdict
        snapshot_path.write_text(json.dumps(snapshot, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    (output / "visual-review-result.json").write_text(
        json.dumps(result, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    manifest["status"] = status
    manifest["automaticPassEligible"] = passed
    manifest["rawContactSheetsRetained"] = False
    manifest_path.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    if summary_path.is_file():
        summary = _load(summary_path)
        summary["visualReviewGate"] = {
            "status": status,
            "automaticPassEligible": passed,
            "manifest": "visual-review-manifest.json",
            "result": "visual-review-result.json",
        }
        summary_path.write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--review", required=True, type=Path)
    args = parser.parse_args()
    result = finalize_review(args.output, args.review)
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0 if result["status"] == "PASS" else 3


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except HarnessError as error:
        print(f"H19 review finalization error: {error}")
        raise SystemExit(2)
