import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";
import vm from "node:vm";

const testRoot = dirname(fileURLToPath(import.meta.url));
const source = join(
  testRoot,
  "../../main/assets/dag-protection/video-premium-continuity.js",
);

const harness = async () => {
  const context = { globalThis: null };
  context.globalThis = context;
  vm.runInNewContext(await readFile(source, "utf8"), context, { filename: source });
  const attributes = new Map();
  const diagnostics = [];
  const timers = new Map();
  const shown = [];
  let nextTimer = 1;
  const record = {
    rawFrameOpen: true,
    resultTimer: 900,
    retiring: false,
    smoothActive: true,
    terminal: true,
    video: {
      isConnected: true,
      removeAttribute: (name) => attributes.delete(name),
      setAttribute: (name, value) => attributes.set(name, value),
    },
  };
  let activeRecord = record;
  const calls = { hide: 0, rebind: 0, request: 0, reset: 0, schedule: 0 };
  record.framePending = false;
  record.nextCaptureTimer = null;
  record.sourceSignature = "blob:media";
  record.video.currentTime = 10;
  record.video.duration = 60;
  record.video.source = "blob:media";
  const controller = context.__gloshDagVideoPremiumContinuity.create({
    activeRecord: () => activeRecord,
    blurAttribute: "data-blurred",
    clearTimeout: (timer) => timers.delete(timer),
    endMarginSeconds: 0.25,
    maximumSkipAttempts: 5,
    minimumSkipSeconds: 0.5,
    hideSkipControl: () => { calls.hide += 1; },
    postDiagnostic: (stage) => diagnostics.push(stage),
    rebindSkipControl: () => { calls.rebind += 1; },
    requestFrameWhenReady: () => { calls.request += 1; },
    resetFrameState: () => { calls.reset += 1; },
    safeSamplesRequired: 2,
    scheduleNextCapture: () => { calls.schedule += 1; },
    seekSettleMillis: 150,
    setTimeout: (callback, delay) => {
      const id = nextTimer++;
      timers.set(id, { callback, delay });
      return id;
    },
    showSkipControl: (_record, onClick) => shown.push(onClick),
    skipStepSeconds: 2,
    skipControlDelayMillis: 2_000,
    sourceSignature: (video) => video.source,
  });
  return {
    attributes,
    calls,
    controller,
    diagnostics,
    record,
    runDelay(delay) {
      const entry = [...timers.entries()].find(([, timer]) => timer.delay === delay);
      assert.ok(entry, `missing ${delay}ms timer`);
      timers.delete(entry[0]);
      entry[1].callback();
    },
    setActive: (value) => { activeRecord = value; },
    shown,
  };
};

test("a blocked smooth frame enters live blur without pausing or retiring", async () => {
  const h = await harness();
  assert.equal(h.controller.onFrameBlocked(h.record), true);
  assert.equal(h.attributes.get("data-blurred"), "true");
  assert.equal(h.record.terminal, false);
  assert.equal(h.record.smoothActive, true);
  assert.equal(h.record.rawFrameOpen, true);
  assert.equal(h.record.resultTimer, null);
  assert.equal(h.calls.reset, 1);
  assert.equal(h.calls.schedule, 1);
  assert.ok(h.diagnostics.includes("blur_enter"));
});

test("two consecutive allowed samples remove blur", async () => {
  const h = await harness();
  h.controller.onFrameBlocked(h.record);
  assert.equal(h.controller.onFrameAllowed(h.record), true);
  assert.equal(h.attributes.has("data-blurred"), true);
  assert.equal(h.controller.onFrameAllowed(h.record), true);
  assert.equal(h.attributes.has("data-blurred"), false);
  assert.ok(h.diagnostics.includes("blur_exit"));
});

test("a new block resets the safe streak", async () => {
  const h = await harness();
  h.controller.onFrameBlocked(h.record);
  h.controller.onFrameAllowed(h.record);
  h.controller.onFrameBlocked(h.record);
  h.controller.onFrameAllowed(h.record);
  assert.equal(h.attributes.has("data-blurred"), true);
});

test("skip control appears after two seconds and remains user initiated", async () => {
  const h = await harness();
  h.controller.onFrameBlocked(h.record);
  assert.equal(h.shown.length, 0);
  h.runDelay(2_000);
  assert.equal(h.shown.length, 1);
  h.shown[0]();
  assert.equal(h.record.video.currentTime, 12);
  assert.ok(h.diagnostics.includes("skip_button_clicked"));
  assert.equal(h.controller.onSeeking(h.record), true);
  assert.equal(h.controller.onSeeked(h.record), true);
  h.runDelay(150);
  assert.equal(h.calls.request, 1);
  assert.ok(h.diagnostics.includes("skip_analyzing"));
});

test("an inactive or disconnected authority cannot enter blur", async () => {
  const h = await harness();
  h.setActive(null);
  assert.equal(h.controller.onFrameBlocked(h.record), false);
  h.setActive(h.record);
  h.record.video.isConnected = false;
  assert.equal(h.controller.onFrameBlocked(h.record), false);
  assert.equal(h.attributes.size, 0);
});
