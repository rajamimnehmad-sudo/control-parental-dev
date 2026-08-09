#!/usr/bin/env python3
"""Rewrite ConvInteger as integer-valued FP32 Conv for local ORT scoring.

The rewrite casts quantized tensors and zero points to FP32, subtracts zero
points while values are still integers, and then performs the convolution. It
keeps the original post-convolution scale nodes. It is a laboratory
compatibility artifact and never replaces the official R1 ONNX.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def rewrite_conv_integer(model: Any) -> tuple[Any, int]:
    import onnx

    nodes = list(model.graph.node)
    replacements: dict[int, list[Any]] = {}
    count = 0
    for index, node in enumerate(nodes):
        if node.op_type != "ConvInteger":
            continue
        if len(node.input) != 4:
            raise ValueError(f"unsupported ConvInteger inputs: {node.name}")
        input_float = f"{node.name}/input_float"
        input_zero_float = f"{node.name}/input_zero_float"
        input_centered = f"{node.name}/input_centered"
        weight_float = f"{node.name}/weight_float"
        weight_zero_float = f"{node.name}/weight_zero_float"
        weight_centered = f"{node.name}/weight_centered"
        casts_and_subtracts = [
            onnx.helper.make_node("Cast", [node.input[0]], [input_float], name=f"{node.name}/InputCast", to=onnx.TensorProto.FLOAT),
            onnx.helper.make_node("Cast", [node.input[2]], [input_zero_float], name=f"{node.name}/InputZeroPointCast", to=onnx.TensorProto.FLOAT),
            onnx.helper.make_node("Sub", [input_float, input_zero_float], [input_centered], name=f"{node.name}/InputZeroPointSubtract"),
            onnx.helper.make_node("Cast", [node.input[1]], [weight_float], name=f"{node.name}/WeightCast", to=onnx.TensorProto.FLOAT),
            onnx.helper.make_node("Cast", [node.input[3]], [weight_zero_float], name=f"{node.name}/WeightZeroPointCast", to=onnx.TensorProto.FLOAT),
            onnx.helper.make_node("Sub", [weight_float, weight_zero_float], [weight_centered], name=f"{node.name}/WeightZeroPointSubtract"),
        ]
        attributes = {attribute.name: onnx.helper.get_attribute_value(attribute) for attribute in node.attribute}
        float_conv = onnx.helper.make_node(
            "Conv",
            [input_centered, weight_centered],
            list(node.output),
            name=f"{node.name}/IntegerValueCompatConv",
            **attributes,
        )
        replacements[index] = [*casts_and_subtracts, float_conv]
        count += 1

    if not count:
        raise ValueError("model has no ConvInteger nodes")
    rebuilt = []
    for index, node in enumerate(nodes):
        if index in replacements:
            rebuilt.extend(replacements[index])
        else:
            rebuilt.append(node)
    del model.graph.node[:]
    model.graph.node.extend(rebuilt)
    return model, count


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--report", required=True, type=Path)
    args = parser.parse_args()
    import onnx

    model = onnx.load(args.input)
    rewritten, count = rewrite_conv_integer(model)
    onnx.checker.check_model(rewritten)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    onnx.save(rewritten, args.output)
    report = {
        "schema_version": "gloshia-r1-conv-integer-value-compat-v2",
        "status": "laboratory_compatibility_only_not_for_apk",
        "source": {"path": str(args.input), "sha256": _sha256(args.input), "bytes": args.input.stat().st_size},
        "output": {"path": str(args.output), "sha256": _sha256(args.output), "bytes": args.output.stat().st_size},
        "rewritten_conv_integer_nodes": count,
    }
    args.report.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
