import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";
import vm from "node:vm";

const testRoot = dirname(fileURLToPath(import.meta.url));
const source = join(testRoot, "../../main/assets/dag-protection/video-premium-fullscreen.js");

test("premium fullscreen presents only the exact active smooth video and restores it", async () => {
  const context = { globalThis: null };
  context.globalThis = context;
  vm.runInNewContext(await readFile(source, "utf8"), context, { filename: source });
  const rootAttributes = new Map();
  const videoAttributes = new Map();
  const diagnostics = [];
  let now = 1_000;
  const record = {
    premiumFullscreenActive: false,
    premiumFullscreenTransitionUntil: 0,
    rawFrameOpen: true,
    retiring: false,
    smoothActive: true,
    video: {
      isConnected: true,
      removeAttribute: (name) => videoAttributes.delete(name),
      setAttribute: (name, value) => videoAttributes.set(name, value),
    },
  };
  const controller = context.__gloshDagVideoPremiumFullscreen.create({
    activeRecord: () => record,
    documentElement: {
      removeAttribute: (name) => rootAttributes.delete(name),
      setAttribute: (name, value) => rootAttributes.set(name, value),
    },
    now: () => now,
    postDiagnostic: (stage) => diagnostics.push(stage),
    rootAttribute: "data-root-fullscreen",
    transitionMillis: 1_000,
    videoAttribute: "data-video-fullscreen",
  });

  assert.equal(controller.set(record, true), true);
  assert.equal(videoAttributes.get("data-video-fullscreen"), "true");
  assert.equal(rootAttributes.get("data-root-fullscreen"), "true");
  assert.equal(record.premiumFullscreenActive, true);
  assert.equal(record.premiumFullscreenTransitionUntil, 2_000);
  now = 1_500;
  assert.equal(controller.set(record, false), true);
  assert.equal(videoAttributes.size, 0);
  assert.equal(rootAttributes.size, 0);
  assert.equal(record.premiumFullscreenActive, false);
  assert.equal(record.premiumFullscreenTransitionUntil, 2_500);
  assert.deepEqual(diagnostics, ["fullscreen_enter", "fullscreen_exit"]);
});

test("fullscreen refuses an inactive or not yet smooth authority", async () => {
  const context = { globalThis: null };
  context.globalThis = context;
  vm.runInNewContext(await readFile(source, "utf8"), context, { filename: source });
  const record = {
    rawFrameOpen: false,
    retiring: false,
    smoothActive: false,
    video: { isConnected: true },
  };
  const controller = context.__gloshDagVideoPremiumFullscreen.create({
    activeRecord: () => record,
    documentElement: {},
    now: () => 0,
    postDiagnostic: () => {},
    rootAttribute: "root",
    transitionMillis: 1_000,
    videoAttribute: "video",
  });
  assert.equal(controller.set(record, true), false);
});
