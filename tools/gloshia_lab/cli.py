#!/usr/bin/env python3
"""Command line entry point for the local GloshIA laboratory."""

from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path
from typing import Sequence

from .corpus import build_corpus, repair_currentness
from .metrics import (
    build_review_queue,
    evaluation_report,
    joined_rows,
    write_contact_sheets,
)
from .server import serve


ROOT = Path(__file__).resolve().parents[2]
TOOL_DIR = Path(__file__).resolve().parent
DEFAULT_MODEL = (
    ROOT
    / "app-dag-browser/src/main/assets/dag-model/"
    "tinyclip-bounded-finetune-r1-int8.onnx"
)
EXPECTED_MODEL_SHA256 = "2d52bd9e5eb4cd448cb0d64a784b2ee6f761ad20e890c57b898fd7991d29a9ee"


def sha256_file(path: Path) -> str:
    import hashlib

    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def find_node() -> str:
    explicit = os.environ.get("GLOSHIA_NODE")
    candidates = [
        explicit,
        shutil.which("node"),
        str(
            Path.home()
            / ".cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node"
        ),
    ]
    for candidate in candidates:
        if candidate and Path(candidate).is_file():
            return candidate
    raise RuntimeError("Node 20 or newer is required")


def score_command(args: argparse.Namespace) -> int:
    corpus_dir = args.corpus.resolve()
    digest = sha256_file(args.model)
    if digest != EXPECTED_MODEL_SHA256:
        raise ValueError(
            "refusing to score with a model that differs from the DAG artefact"
        )
    output = corpus_dir / "predictions.jsonl"
    command = [
        find_node(),
        str(TOOL_DIR / "model-runner.mjs"),
        "--model",
        str(args.model.resolve()),
        "--manifest",
        str(corpus_dir / "manifest.jsonl"),
        "--output",
        str(output),
    ]
    if args.limit:
        command.extend(("--limit", str(args.limit)))
    if args.include_sealed:
        command.append("--include-sealed")
    completed = subprocess.run(command, check=False)
    return completed.returncode


def verify_command(args: argparse.Namespace) -> int:
    errors = []
    digest = sha256_file(args.model)
    if digest != EXPECTED_MODEL_SHA256:
        errors.append("model hash differs from DAG")
    if not (TOOL_DIR / "node_modules/onnxruntime-web").exists():
        errors.append("run pnpm install in tools/gloshia_lab")
    query_plan = json.loads((TOOL_DIR / "queries.json").read_text(encoding="utf-8"))
    target = sum(item["target"] for item in query_plan["categories"].values())
    if target != 1000:
        errors.append(f"query plan target is {target}, expected 1000")
    payload = {
        "ok": not errors,
        "model": str(args.model),
        "model_sha256": digest,
        "policy": "dag-36",
        "query_target": target,
        "node": find_node(),
        "errors": errors,
    }
    print(json.dumps(payload, indent=2, ensure_ascii=False))
    return 0 if not errors else 1


def report_command(args: argparse.Namespace) -> int:
    report = evaluation_report(args.corpus, include_sealed=args.include_sealed)
    output = args.output or args.corpus / "evaluation-report.json"
    output.write_text(
        json.dumps(report, indent=2, ensure_ascii=False, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(report, indent=2, ensure_ascii=False))
    return 0


def sheets_command(args: argparse.Namespace) -> int:
    rows = joined_rows(args.corpus, include_sealed=False)
    queue = build_review_queue(rows, maximum=args.maximum)
    outputs = write_contact_sheets(
        queue,
        corpus_dir=args.corpus,
        output_dir=args.output,
    )
    queue_path = args.output / "queue.json"
    queue_path.write_text(
        json.dumps(
            [
                {
                    "index": index + 1,
                    "sample_id": row["sample_id"],
                    "category": row["category"],
                    "model_prediction": row["model_prediction"],
                }
                for index, row in enumerate(queue)
            ],
            indent=2,
            ensure_ascii=False,
        )
        + "\n",
        encoding="utf-8",
    )
    print(json.dumps({"items": len(queue), "sheets": len(outputs), "queue": str(queue_path)}))
    return 0


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    subcommands = result.add_subparsers(dest="command", required=True)

    verify = subcommands.add_parser("verify")
    verify.add_argument("--model", type=Path, default=DEFAULT_MODEL)
    verify.set_defaults(handler=verify_command)

    corpus = subcommands.add_parser("build-corpus")
    corpus.add_argument("output", type=Path)
    corpus.add_argument("--target", type=int)
    corpus.add_argument("--query-plan", type=Path, default=TOOL_DIR / "queries.json")
    corpus.set_defaults(
        handler=lambda args: (
            print(
                json.dumps(
                    build_corpus(args.query_plan, args.output, args.target),
                    indent=2,
                    ensure_ascii=False,
                )
            )
            or 0
        )
    )

    repair = subcommands.add_parser("repair-currentness")
    repair.add_argument("corpus", type=Path)
    repair.set_defaults(
        handler=lambda args: (
            print(
                json.dumps(
                    repair_currentness(args.corpus),
                    indent=2,
                    ensure_ascii=False,
                )
            )
            or 0
        )
    )

    score = subcommands.add_parser("score")
    score.add_argument("corpus", type=Path)
    score.add_argument("--model", type=Path, default=DEFAULT_MODEL)
    score.add_argument("--limit", type=int)
    score.add_argument("--include-sealed", action="store_true")
    score.set_defaults(handler=score_command)

    report = subcommands.add_parser("report")
    report.add_argument("corpus", type=Path)
    report.add_argument("--output", type=Path)
    report.add_argument("--include-sealed", action="store_true")
    report.set_defaults(handler=report_command)

    sheets = subcommands.add_parser("contact-sheets")
    sheets.add_argument("corpus", type=Path)
    sheets.add_argument("output", type=Path)
    sheets.add_argument("--maximum", type=int, default=200)
    sheets.set_defaults(handler=sheets_command)

    web = subcommands.add_parser("serve")
    web.add_argument("corpus", type=Path)
    web.add_argument("--port", type=int, default=8765)
    web.add_argument("--unlock-sealed", action="store_true")
    web.set_defaults(
        handler=lambda args: (
            serve(
                args.corpus,
                TOOL_DIR / "web",
                args.port,
                include_sealed=args.unlock_sealed,
            )
            or 0
        )
    )
    return result


def main(argv: Sequence[str] | None = None) -> int:
    args = parser().parse_args(argv)
    try:
        return int(args.handler(args))
    except KeyboardInterrupt:
        print(
            "interrumpido; el checkpoint del corpus se conservó para reanudar",
            file=sys.stderr,
        )
        return 130
    except (OSError, RuntimeError, ValueError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
