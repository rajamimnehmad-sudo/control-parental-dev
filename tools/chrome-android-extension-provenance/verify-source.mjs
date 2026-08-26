import { createHash, createPublicKey } from "node:crypto";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = dirname(fileURLToPath(import.meta.url));
const manifest = JSON.parse(readFileSync(resolve(root, "extension/manifest.json"), "utf8"));
const contentScript = readFileSync(resolve(root, "extension/content-script.js"), "utf8");
const publicKey = createPublicKey({ key: Buffer.from(manifest.key, "base64"), format: "der", type: "spki" });
const digest = createHash("sha256").update(publicKey.export({ format: "der", type: "spki" })).digest("hex").slice(0, 32);
const extensionId = digest.replace(/[0-9a-f]/g, (value) => String.fromCharCode("a".charCodeAt(0) + Number.parseInt(value, 16)));

if (process.argv.includes("--print-id")) {
  process.stdout.write(extensionId);
  process.exit(0);
}

const content = manifest.content_scripts?.[0];
const checks = {
  manifestV3: manifest.manifest_version === 3,
  minimumChrome146: Number(manifest.minimum_chrome_version) >= 146,
  serviceWorker: manifest.background?.service_worker === "service-worker.js",
  documentStart: content?.run_at === "document_start",
  allFrames: content?.all_frames === true,
  originFallback: content?.match_origin_as_fallback === true,
  isolatedWorld: content?.world === "ISOLATED",
  scopedFixtureHost: content?.matches?.length === 1 && content.matches[0] === "https://glosh-photos.test/*",
  noWindowMessages: !contentScript.includes("postMessage") && !contentScript.includes('addEventListener("message"'),
  expectedId: extensionId === "hdjdhkkibdhlmmoemopmbgiklklkpofp",
};
const failures = Object.entries(checks).filter(([, passed]) => !passed).map(([name]) => name);
if (failures.length) throw new Error(`source checks failed: ${failures.join(",")}`);
process.stdout.write(`${JSON.stringify({ extensionId, checks }, null, 2)}\n`);
