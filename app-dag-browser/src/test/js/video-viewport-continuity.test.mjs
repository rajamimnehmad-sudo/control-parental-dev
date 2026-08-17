import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";
import vm from "node:vm";

const testRoot = dirname(fileURLToPath(import.meta.url));
const source = join(testRoot, "../../main/assets/dag-protection/video-lab-viewport.js");

test("ordinary scroll rebinds the exact smooth video without pausing or retiring", async () => {
  const timers = new Map();
  const diagnostics = [];
  const retired = [];
  let nextTimer = 1;
  let now = 1_000;
  let lastChange = now;
  let scheduled = 0;
  let rebound = 0;
  const context = {
    globalThis: null,
    clearTimeout: (id) => timers.delete(id),
    setTimeout: (callback, delay) => {
      const id = nextTimer++;
      timers.set(id, { callback, delay });
      return id;
    },
  };
  context.globalThis = context;
  vm.runInNewContext(await readFile(source, "utf8"), context, { filename: source });
  const video = { isConnected: true, source: "blob:stable", visible: 100 };
  let signature = { rect: "before" };
  const record = {
    nextCaptureTimer: 77,
    pendingViewportSignature: null,
    rawFrameOpen: true,
    readinessTimer: null,
    smoothActive: true,
    sourceSignature: video.source,
    video,
    viewportEpoch: 4,
    viewportSignature: signature,
    viewportSuspended: false,
  };
  const controller = context.__gloshDagVideoLabViewport.create({
    fixtureEnabled: () => false,
    hasDocumentToken: () => true,
    lastViewportChangeAt: () => lastChange,
    now: () => now,
    postDiagnostic: (stage) => diagnostics.push(stage),
    postTimeline: () => {},
    presentationCapabilityFailure: () => null,
    rebindPremiumContinuity: () => { rebound += 1; },
    retireRecord: (_record, reason) => retired.push(reason),
    scheduleNextCapture: () => { scheduled += 1; },
    setLastViewportChangeAt: (value) => { lastChange = value; },
    sourceSignature: (candidate) => candidate.source,
    state: { activeRecord: record },
    unsafePresentationActive: () => false,
    visibleArea: (candidate) => candidate.visible,
    viewportSettleMillis: 150,
    viewportSignature: () => signature,
  });

  signature = { rect: "after" };
  controller.invalidate({ type: "scroll" });
  assert.equal(record.viewportSuspended, true);
  assert.deepEqual(retired, []);
  assert.equal(scheduled, 0);
  now += 150;
  const settle = [...timers.values()].find((timer) => timer.delay === 150);
  assert.ok(settle);
  settle.callback();

  assert.equal(record.viewportSuspended, false);
  assert.equal(record.viewportSignature.rect, "after");
  assert.equal(record.viewportEpoch, 5);
  assert.equal(scheduled, 1);
  assert.equal(rebound, 2);
  assert.deepEqual(retired, []);
  assert.ok(diagnostics.includes("scroll_rebind_start"));
  assert.ok(diagnostics.includes("scroll_rebind_stable"));
});
