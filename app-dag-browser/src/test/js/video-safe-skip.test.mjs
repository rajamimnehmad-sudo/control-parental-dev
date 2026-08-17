import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";
import vm from "node:vm";

const testRoot = dirname(fileURLToPath(import.meta.url));
const asset = (name) => join(testRoot, `../../main/assets/dag-protection/${name}`);

const load = async (name, exportName) => {
  const context = { globalThis: null };
  context.globalThis = context;
  vm.runInNewContext(await readFile(asset(name), "utf8"), context, { filename: name });
  return context[exportName];
};

test("blocked generation stays quarantined while a new element remains eligible", async () => {
  const runtime = await load("video-block-quarantine.js", "__gloshDagVideoBlockQuarantine");
  const first = { source: "blob:first" };
  const second = { source: "blob:second" };
  const quarantine = runtime.create((video) => video.source);
  quarantine.block({ sourceSignature: first.source, video: first });

  assert.equal(quarantine.allows(first), false);
  assert.equal(quarantine.allows(second), true);
  first.source = "blob:replacement";
  assert.equal(quarantine.allows(first), true);
});

test("a new media generation releases a reused video element for covered analysis", async () => {
  const runtime = await load("video-block-quarantine.js", "__gloshDagVideoBlockQuarantine");
  const video = { source: "blob:stable" };
  const quarantine = runtime.create((candidate) => candidate.source);
  quarantine.block({ sourceSignature: video.source, video });
  assert.equal(quarantine.allows(video), false);

  quarantine.noteGeneration(video);
  assert.equal(quarantine.allows(video), true);
});

const safeSkipHarness = async ({ maximumAttempts = 5 } = {}) => {
  const runtime = await load("video-safe-skip.js", "__gloshDagVideoSafeSkip");
  let nextTimer = 1;
  const timers = new Map();
  const diagnostics = [];
  const exhausted = [];
  const recovered = [];
  const requests = [];
  const video = {
    currentTime: 10,
    duration: 60,
    isConnected: true,
    seeking: false,
    source: "blob:media",
    viewport: [0, 0, 640, 360],
  };
  const record = { smoothActive: true, terminal: true, video };
  let activeRecord = record;
  const controller = runtime.create({
    activeRecord: () => activeRecord,
    clearTimeout: (timer) => timers.delete(timer),
    endMarginSeconds: 0.25,
    maximumAttempts,
    minimumAdvanceSeconds: 0.5,
    onExhausted: (_record, reason) => exhausted.push(reason),
    onRecovered: (_record, seconds) => recovered.push(seconds),
    postDiagnostic: (stage) => diagnostics.push(stage),
    requestFrameWhenReady: (candidate) => requests.push(candidate),
    resetFrameState: () => {},
    safePause: () => {},
    setTimeout: (callback, delay) => {
      const timer = nextTimer;
      nextTimer += 1;
      timers.set(timer, { callback, delay });
      return timer;
    },
    settleMillis: 150,
    sourceSignature: (candidate) => candidate.source,
    stepSeconds: 2,
    timeToleranceSeconds: 0.25,
    timeoutMillis: 2_500,
    viewportSignature: (candidate) => candidate.viewport,
  });
  const runTimerWithDelay = (delay) => {
    const entry = [...timers.entries()].find(([, timer]) => timer.delay === delay);
    assert.ok(entry, `missing ${delay}ms timer`);
    timers.delete(entry[0]);
    entry[1].callback();
  };
  return {
    controller,
    diagnostics,
    exhausted,
    record,
    recovered,
    requests,
    runTimerWithDelay,
    setActive: (value) => { activeRecord = value; },
    video,
  };
};

test("blocked frame advances under cover and resumes only after an allowed analysis", async () => {
  const h = await safeSkipHarness();
  assert.equal(h.controller.onFrameBlocked(h.record), true);
  assert.equal(h.video.currentTime, 12);
  assert.equal(h.record.terminal, false);
  assert.equal(h.record.smoothActive, false);
  assert.equal(h.controller.onSeeking(h.record), true);
  h.video.seeking = false;
  assert.equal(h.controller.onSeeked(h.record), true);
  h.runTimerWithDelay(150);
  assert.deepEqual(h.requests, [h.record]);
  assert.equal(h.controller.onFrameAllowed(h.record), true);
  assert.deepEqual(h.recovered, [2]);
  assert.equal(h.exhausted.length, 0);
  assert.ok(h.diagnostics.includes("safe_skip_recovered"));
});

test("repeated filtered points stop at the bounded attempt limit", async () => {
  const h = await safeSkipHarness({ maximumAttempts: 2 });
  assert.equal(h.controller.onFrameBlocked(h.record), true);
  h.controller.onSeeking(h.record);
  h.controller.onSeeked(h.record);
  h.runTimerWithDelay(150);
  assert.equal(h.controller.onFrameBlocked(h.record), true);
  h.controller.onSeeking(h.record);
  h.controller.onSeeked(h.record);
  h.runTimerWithDelay(150);
  assert.equal(h.controller.onFrameBlocked(h.record), false);
  assert.ok(h.diagnostics.includes("safe_skip_limit"));
});

test("unseekable media stays fail closed instead of opening a filtered frame", async () => {
  const h = await safeSkipHarness();
  h.video.duration = Number.NaN;
  assert.equal(h.controller.onFrameBlocked(h.record), false);
  assert.ok(h.diagnostics.includes("safe_skip_time_unavailable"));
  assert.equal(h.requests.length, 0);
  assert.equal(h.recovered.length, 0);
});

test("authority mismatch during skip exhausts locally", async () => {
  const h = await safeSkipHarness();
  assert.equal(h.controller.onFrameBlocked(h.record), true);
  h.controller.onSeeking(h.record);
  h.video.source = "blob:other";
  assert.equal(h.controller.onSeeked(h.record), true);
  assert.deepEqual(h.exhausted, ["safe_skip_destination_invalid"]);
  assert.equal(h.requests.length, 0);
});
