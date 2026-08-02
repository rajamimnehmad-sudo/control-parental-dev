#!/usr/bin/env node

import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import process from "node:process";
import * as ort from "onnxruntime-web";
import sharp from "sharp";

const TARGET = 224;
const CHANNELS = 3;
const MEAN = [0.48145466, 0.4578275, 0.40821073];
const STD = [0.26862954, 0.26130258, 0.27577711];
const FILTER_THRESHOLD = 0.4;
const UNCERTAIN_FLOOR = 0.3;
const UNCERTAIN_REGION_THRESHOLD = 0.45;
const REGIONAL_THRESHOLD = 0.5;
const REGIONAL_STRONG_THRESHOLD = 0.7;
const REGIONAL_CONSENSUS = 2;
const PANORAMIC_RATIO = 2.0;
const PANORAMIC_FRACTION = 0.42;
const UNCERTAIN_FRACTION = 0.56;
const REGIONAL_LONG_EDGE = TARGET * 3;

sharp.cache(false);
ort.env.wasm.numThreads = 2;

function parseArguments(argv) {
  const values = {};
  for (let index = 0; index < argv.length; index += 1) {
    const key = argv[index];
    if (!key.startsWith("--")) throw new Error(`unexpected argument: ${key}`);
    if (key === "--include-sealed") {
      values.includeSealed = true;
      continue;
    }
    if (key === "--diagnostic-regions") {
      values.diagnosticRegions = true;
      continue;
    }
    const value = argv[index + 1];
    if (!value || value.startsWith("--")) throw new Error(`missing value for ${key}`);
    values[key.slice(2)] = value;
    index += 1;
  }
  if (!values.model) throw new Error("--model is required");
  if (!values.one && (!values.manifest || !values.output)) {
    throw new Error("use --one, or both --manifest and --output");
  }
  return values;
}

function sha256File(filePath) {
  return crypto.createHash("sha256").update(fs.readFileSync(filePath)).digest("hex");
}

function roundToInt(value) {
  return Math.floor(value + 0.5);
}

function fitPlan(width, height) {
  const scale = Math.min(TARGET / width, TARGET / height);
  const contentWidth = Math.max(1, Math.min(TARGET, roundToInt(width * scale)));
  const contentHeight = Math.max(1, Math.min(TARGET, roundToInt(height * scale)));
  return {
    contentWidth,
    contentHeight,
    offsetX: Math.floor((TARGET - contentWidth) / 2),
    offsetY: Math.floor((TARGET - contentHeight) / 2),
  };
}

async function decodedSource(filePath) {
  const pipeline = sharp(filePath, { animated: false, failOn: "warning" }).rotate();
  const metadata = await pipeline.metadata();
  if (
    !metadata.width ||
    !metadata.height ||
    metadata.width > 4096 ||
    metadata.height > 4096 ||
    metadata.width * metadata.height > 16_777_216
  ) {
    throw new Error("unsafe_dimensions");
  }
  if ((metadata.pages ?? 1) !== 1) throw new Error("animated_image");
  return {
    filePath,
    width: metadata.autoOrient?.width ?? metadata.width,
    height: metadata.autoOrient?.height ?? metadata.height,
  };
}

async function rawResize(input, width, height) {
  const pipeline =
    typeof input === "string"
      ? sharp(input).rotate()
      : input.filePath
        ? sharp(input.filePath).rotate()
        : sharp(Buffer.from(input.data), {
            raw: {
              width: input.width,
              height: input.height,
              channels: CHANNELS,
            },
            limitInputPixels: false,
          });
  const { data, info } = await pipeline
    .resize(width, height, { fit: "fill", kernel: sharp.kernel.linear })
    .flatten({ background: { r: 127, g: 127, b: 127 } })
    .removeAlpha()
    .toColourspace("srgb")
    .raw()
    .toBuffer({ resolveWithObject: true });
  if (info.channels !== CHANNELS) throw new Error("unexpected_channels");
  return { data: new Uint8Array(data), width: info.width, height: info.height };
}

function cropRaw(source, left, top, width, height) {
  const output = new Uint8Array(width * height * CHANNELS);
  for (let row = 0; row < height; row += 1) {
    const sourceStart = ((top + row) * source.width + left) * CHANNELS;
    const targetStart = row * width * CHANNELS;
    output.set(
      source.data.subarray(sourceStart, sourceStart + width * CHANNELS),
      targetStart,
    );
  }
  return { data: output, width, height };
}

async function letterbox(source) {
  const plan = fitPlan(source.width, source.height);
  const resized = await rawResize(source, plan.contentWidth, plan.contentHeight);
  const output = new Uint8Array(TARGET * TARGET * CHANNELS);
  output.fill(127);
  for (let row = 0; row < plan.contentHeight; row += 1) {
    const sourceStart = row * plan.contentWidth * CHANNELS;
    const targetStart =
      ((plan.offsetY + row) * TARGET + plan.offsetX) * CHANNELS;
    output.set(
      resized.data.subarray(sourceStart, sourceStart + plan.contentWidth * CHANNELS),
      targetStart,
    );
  }
  return output;
}

async function preparedViews(filePath) {
  const source = await decodedSource(filePath);
  const ratio = Math.max(source.width, source.height) / Math.min(source.width, source.height);
  if (ratio < PANORAMIC_RATIO) {
    return {
      width: source.width,
      height: source.height,
      full: await letterbox(source),
      panoramic: [],
    };
  }
  const scale = Math.min(
    1,
    REGIONAL_LONG_EDGE / Math.max(source.width, source.height),
  );
  const decodedWidth = Math.max(1, roundToInt(source.width * scale));
  const decodedHeight = Math.max(1, roundToInt(source.height * scale));
  const decoded = await rawResize(filePath, decodedWidth, decodedHeight);
  const full = await letterbox(decoded);
  const panoramic = [];
  if (decodedWidth >= decodedHeight) {
    const cropWidth = Math.max(
      1,
      Math.min(decodedWidth, roundToInt(decodedWidth * PANORAMIC_FRACTION)),
    );
    const starts = [...new Set([
      0,
      Math.floor((decodedWidth - cropWidth) / 2),
      decodedWidth - cropWidth,
    ])];
    for (const left of starts) {
      panoramic.push(
        await letterbox(cropRaw(decoded, left, 0, cropWidth, decodedHeight)),
      );
    }
  } else {
    const cropHeight = Math.max(
      1,
      Math.min(decodedHeight, roundToInt(decodedHeight * PANORAMIC_FRACTION)),
    );
    const starts = [...new Set([
      0,
      Math.floor((decodedHeight - cropHeight) / 2),
      decodedHeight - cropHeight,
    ])];
    for (const top of starts) {
      panoramic.push(
        await letterbox(cropRaw(decoded, 0, top, decodedWidth, cropHeight)),
      );
    }
  }
  return { width: source.width, height: source.height, full, panoramic };
}

function uncertainQuadrants(full) {
  const cropSize = Math.max(1, Math.min(TARGET, roundToInt(TARGET * UNCERTAIN_FRACTION)));
  const lastStart = TARGET - cropSize;
  const offsets = [[0, 0], [lastStart, 0], [0, lastStart], [lastStart, lastStart]];
  return offsets.map(([left, top]) => {
    const output = new Uint8Array(TARGET * TARGET * CHANNELS);
    let outputIndex = 0;
    for (let y = 0; y < TARGET; y += 1) {
      const sourceY = top + Math.min(cropSize - 1, Math.floor((y * cropSize) / TARGET));
      for (let x = 0; x < TARGET; x += 1) {
        const sourceX =
          left + Math.min(cropSize - 1, Math.floor((x * cropSize) / TARGET));
        const sourceIndex = (sourceY * TARGET + sourceX) * CHANNELS;
        output[outputIndex++] = full[sourceIndex];
        output[outputIndex++] = full[sourceIndex + 1];
        output[outputIndex++] = full[sourceIndex + 2];
      }
    }
    return output;
  });
}

function normalizedTensor(rgb) {
  const pixels = TARGET * TARGET;
  const output = new Float32Array(pixels * CHANNELS);
  for (let pixel = 0; pixel < pixels; pixel += 1) {
    const source = pixel * CHANNELS;
    for (let channel = 0; channel < CHANNELS; channel += 1) {
      const value = rgb[source + channel] / 255;
      output[channel * pixels + pixel] = (value - MEAN[channel]) / STD[channel];
    }
  }
  return new ort.Tensor("float32", output, [1, 3, TARGET, TARGET]);
}

async function score(session, rgb) {
  const result = await session.run({ pixel_values: normalizedTensor(rgb) });
  const probability = Number(result.filter_probability.data[0]);
  if (!Number.isFinite(probability) || probability < 0 || probability > 1) {
    throw new Error("invalid_model_output");
  }
  return probability;
}

async function predict(session, filePath, diagnosticRegions = false) {
  const started = performance.now();
  const views = await preparedViews(filePath);
  const fullProbability = await score(session, views.full);
  const regional = [];
  let action = "allow";
  if (fullProbability >= FILTER_THRESHOLD) {
    action = "filter";
  } else if (views.panoramic.length > 0) {
    let votes = 0;
    for (const view of views.panoramic) {
      const probability = await score(session, view);
      regional.push(probability);
      if (probability >= REGIONAL_THRESHOLD) votes += 1;
      if (probability >= REGIONAL_STRONG_THRESHOLD || votes >= REGIONAL_CONSENSUS) {
        action = "filter";
        break;
      }
    }
  } else if (fullProbability >= UNCERTAIN_FLOOR) {
    for (const view of uncertainQuadrants(views.full)) {
      const probability = await score(session, view);
      regional.push(probability);
      if (probability >= UNCERTAIN_REGION_THRESHOLD) {
        action = "filter";
        break;
      }
    }
  }
  if (diagnosticRegions) {
    const diagnosticViews =
      views.panoramic.length > 0 ? views.panoramic : uncertainQuadrants(views.full);
    while (regional.length < diagnosticViews.length) {
      regional.push(await score(session, diagnosticViews[regional.length]));
    }
  }
  const maximum = Math.max(fullProbability, ...regional);
  return {
    schema_version: "gloshia-lab-prediction-v1",
    action,
    reason: action === "filter" ? "model_filter" : "model_allow",
    full_probability: Number(fullProbability.toFixed(6)),
    regional_probabilities: regional.map((value) => Number(value.toFixed(6))),
    maximum_probability: Number(maximum.toFixed(6)),
    inference_count: 1 + regional.length,
    elapsed_ms: Number((performance.now() - started).toFixed(3)),
    source_width: views.width,
    source_height: views.height,
    policy_version: "dag-36",
    diagnostic_region_sweep: diagnosticRegions,
  };
}

function readJsonl(filePath) {
  return fs
    .readFileSync(filePath, "utf8")
    .split(/\r?\n/)
    .filter((line) => line.trim())
    .map((line) => JSON.parse(line));
}

async function main() {
  const args = parseArguments(process.argv.slice(2));
  const modelPath = path.resolve(args.model);
  const session = await ort.InferenceSession.create(modelPath, {
    executionProviders: ["wasm"],
    graphOptimizationLevel: "all",
  });
  const modelSha256 = sha256File(modelPath);
  if (args.one) {
    const prediction = await predict(
      session,
      path.resolve(args.one),
      Boolean(args.diagnosticRegions),
    );
    console.log(JSON.stringify({ ...prediction, model_sha256: modelSha256 }));
    return;
  }

  const manifestPath = path.resolve(args.manifest);
  const outputPath = path.resolve(args.output);
  const corpusRoot = path.dirname(manifestPath);
  const limit = args.limit ? Number.parseInt(args.limit, 10) : Number.MAX_SAFE_INTEGER;
  if (!Number.isInteger(limit) || limit <= 0) throw new Error("--limit must be positive");
  const rows = readJsonl(manifestPath).filter(
    (row) =>
      (args.includeSealed || row.split !== "final_sealed") &&
      row.usage_state !== "excluded",
  );
  const outputRows = [];
  for (const row of rows.slice(0, limit)) {
    try {
      const prediction = await predict(
        session,
        path.resolve(corpusRoot, row.local_path),
        Boolean(args.diagnosticRegions),
      );
      outputRows.push({
        sample_id: row.sample_id,
        split: row.split,
        category: row.category,
        model_sha256: modelSha256,
        ...prediction,
      });
    } catch (error) {
      outputRows.push({
        sample_id: row.sample_id,
        split: row.split,
        category: row.category,
        model_sha256: modelSha256,
        error: error instanceof Error ? error.message : String(error),
      });
    }
    if (outputRows.length % 25 === 0) {
      console.error(`GloshIA: ${outputRows.length}/${Math.min(rows.length, limit)}`);
    }
  }
  fs.mkdirSync(path.dirname(outputPath), { recursive: true });
  const temporary = `${outputPath}.tmp`;
  fs.writeFileSync(
    temporary,
    `${outputRows.map((row) => JSON.stringify(row)).join("\n")}\n`,
    "utf8",
  );
  fs.renameSync(temporary, outputPath);
  const valid = outputRows.filter((row) => !row.error);
  const counts = valid.reduce(
    (result, row) => {
      result[row.action] = (result[row.action] ?? 0) + 1;
      return result;
    },
    {},
  );
  const latencies = valid.map((row) => row.elapsed_ms).sort((a, b) => a - b);
  const percentile = (fraction) =>
    latencies.length
      ? latencies[Math.min(latencies.length - 1, Math.floor(latencies.length * fraction))]
      : null;
  console.log(
    JSON.stringify({
      schema_version: "gloshia-lab-run-summary-v1",
      model_sha256: modelSha256,
      policy_version: "dag-36",
      requested: Math.min(rows.length, limit),
      completed: valid.length,
      errors: outputRows.length - valid.length,
      decisions: counts,
      latency_ms: {
        median: percentile(0.5),
        p95: percentile(0.95),
        maximum: latencies.at(-1) ?? null,
      },
      sealed_split_opened: Boolean(args.includeSealed),
      output: outputPath,
    }),
  );
}

main().catch((error) => {
  console.error(error instanceof Error ? error.stack : String(error));
  process.exitCode = 1;
});
