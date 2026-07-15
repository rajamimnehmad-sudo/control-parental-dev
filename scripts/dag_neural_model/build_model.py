#!/usr/bin/env python3
"""Build and verify DAG's multilingual MiniLM classifier artifact."""

from __future__ import annotations

import copy
import hashlib
import importlib.util
import sys
import urllib.request
from pathlib import Path

import numpy as np
import onnx
import onnxruntime as ort
from onnx import TensorProto, helper, numpy_helper
from onnxruntime_extensions import gen_processing_models
from sklearn.linear_model import LogisticRegression
from tokenizers import Tokenizer
from transformers import XLMRobertaTokenizer


ROOT = Path(__file__).resolve().parents[2]
OUTPUT_DIR = ROOT / "build" / "dag-neural-model"
MODEL_OUTPUT = OUTPUT_DIR / "dag_text_classifier.onnx"
EXPECTED_SHA256 = "641b05b3775dbb94ba7291c7fe607d0bfa304b844cdf6551271018fb287c3627"
UPSTREAM_BASE = "https://huggingface.co/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2/resolve/main"
UPSTREAM = {
    "model.onnx": ("onnx/model_qint8_arm64.onnx", "783fea82d71a58179b830a4dbd2d58447e640609e98eedf9ffa12622d375a672"),
    "tokenizer.json": ("tokenizer.json", "2c3387be76557bd40970cec13153b3bbf80407865484b209e655e5e4729076b8"),
    "sentencepiece.bpe.model": ("sentencepiece.bpe.model", "cfc8146abe2a0488e9e2a0c56de7952f7c11ab059eca145a0a727afce0db2865"),
}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def acquire_upstream() -> None:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    for filename, (remote, expected) in UPSTREAM.items():
        destination = OUTPUT_DIR / filename
        if not destination.exists() or sha256(destination) != expected:
            temporary = destination.with_suffix(destination.suffix + ".download")
            urllib.request.urlretrieve(f"{UPSTREAM_BASE}/{remote}", temporary)
            if sha256(temporary) != expected:
                temporary.unlink(missing_ok=True)
                raise RuntimeError(f"Upstream digest mismatch: {filename}")
            temporary.replace(destination)


def load_training_module():
    path = ROOT / "scripts" / "dag_text_model" / "train_model.py"
    spec = importlib.util.spec_from_file_location("dag_text_training", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def embedding_runner():
    tokenizer = Tokenizer.from_file(str(OUTPUT_DIR / "tokenizer.json"))
    tokenizer.enable_padding(length=128)
    tokenizer.enable_truncation(max_length=128)
    session = ort.InferenceSession(str(OUTPUT_DIR / "model.onnx"), providers=["CPUExecutionProvider"])

    def embed(texts: list[str]) -> np.ndarray:
        encodings = tokenizer.encode_batch(texts)
        ids = np.asarray([item.ids for item in encodings], dtype=np.int64)
        masks = np.asarray([item.attention_mask for item in encodings], dtype=np.int64)
        types = np.asarray([item.type_ids for item in encodings], dtype=np.int64)
        hidden = session.run(None, {"input_ids": ids, "attention_mask": masks, "token_type_ids": types})[0]
        mask = masks[..., None].astype(np.float32)
        pooled = (hidden * mask).sum(axis=1) / np.maximum(mask.sum(axis=1), 1e-9)
        return pooled / np.maximum(np.linalg.norm(pooled, axis=1, keepdims=True), 1e-9)

    return embed


def build_model() -> tuple[object, object]:
    training = load_training_module()
    embed = embedding_runner()
    samples = training.examples()
    vectors = embed([text for _, text in samples])
    head = LogisticRegression(max_iter=2000, class_weight="balanced", random_state=training.SEED)
    head.fit(vectors, [label for label, _ in samples])

    tokenizer = XLMRobertaTokenizer(str(OUTPUT_DIR / "sentencepiece.bpe.model"))
    preprocessing, _ = gen_processing_models(
        tokenizer,
        pre_kwargs={"WITH_DEFAULT_INPUTS": True, "CAST_TOKEN_ID": True},
    )
    base = onnx.load(OUTPUT_DIR / "model.onnx")
    tokenizer_node = copy.deepcopy(preprocessing.graph.node[0])
    tokenizer_node.input[0] = "text"
    tokenizer_node.output[0] = "token_ids_flat"
    tokenizer_node.output[1] = "sentence_offsets"
    tokenizer_node.output[2] = "token_offsets"
    nodes = [tokenizer_node]
    nodes.extend(
        [
            helper.make_node("Cast", ["token_ids_flat"], ["token_ids_i64"], to=TensorProto.INT64),
            helper.make_node("Unsqueeze", ["token_ids_i64", "axes_zero"], ["input_ids"]),
            helper.make_node("Shape", ["input_ids"], ["input_shape"]),
            helper.make_node(
                "ConstantOfShape",
                ["input_shape"],
                ["attention_mask"],
                value=numpy_helper.from_array(np.array([1], dtype=np.int64)),
            ),
            helper.make_node(
                "ConstantOfShape",
                ["input_shape"],
                ["token_type_ids"],
                value=numpy_helper.from_array(np.array([0], dtype=np.int64)),
            ),
        ]
    )
    nodes.extend(copy.deepcopy(base.graph.node))
    nodes.extend(
        [
            helper.make_node("ReduceMean", ["last_hidden_state"], ["embedding_raw"], axes=[1], keepdims=0),
            helper.make_node("ReduceL2", ["embedding_raw"], ["embedding_norm"], axes=[1], keepdims=1),
            helper.make_node("Div", ["embedding_raw", "embedding_norm"], ["embedding"]),
            helper.make_node("Gemm", ["embedding", "head_weights", "head_bias"], ["logits"]),
            helper.make_node("Softmax", ["logits"], ["probabilities"], axis=1),
        ]
    )
    initializers = list(copy.deepcopy(preprocessing.graph.initializer)) + list(copy.deepcopy(base.graph.initializer))
    initializers.extend(
        [
            numpy_helper.from_array(np.array([0], dtype=np.int64), "axes_zero"),
            numpy_helper.from_array(head.coef_.astype(np.float32).T, "head_weights"),
            numpy_helper.from_array(head.intercept_.astype(np.float32), "head_bias"),
        ]
    )
    graph = helper.make_graph(
        nodes,
        "dag_multilingual_text_classifier",
        [helper.make_tensor_value_info("text", TensorProto.STRING, [1])],
        [helper.make_tensor_value_info("probabilities", TensorProto.FLOAT, [1, len(training.CLASSES)])],
        initializer=initializers,
    )
    model = helper.make_model(
        graph,
        opset_imports=[helper.make_opsetid("", 14), helper.make_opsetid("ai.onnx.contrib", 1)],
        producer_name="content-filter-dag",
    )
    model.ir_version = min(model.ir_version, 10)
    onnx.checker.check_model(model)
    onnx.save(model, MODEL_OUTPUT)
    return training, (head, embed)


def verify(training, classifier) -> None:
    head, embed = classifier
    correct = 0
    for expected, text in training.EXPECTED:
        predicted = training.CLASSES[int(head.predict(embed([text]))[0])]
        correct += predicted == expected
    if correct != len(training.EXPECTED):
        raise RuntimeError(f"Holdout failed: {correct}/{len(training.EXPECTED)}")
    actual = sha256(MODEL_OUTPUT)
    if actual != EXPECTED_SHA256:
        raise RuntimeError(f"Artifact digest changed: {actual}")
    print(f"verified={correct}/{len(training.EXPECTED)} bytes={MODEL_OUTPUT.stat().st_size} sha256={actual}")


def main() -> int:
    acquire_upstream()
    training, classifier = build_model()
    verify(training, classifier)
    return 0


if __name__ == "__main__":
    sys.exit(main())
