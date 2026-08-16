import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { test } from "node:test";
import vm from "node:vm";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const testRoot = dirname(fileURLToPath(import.meta.url));
const asset = join(testRoot, "../../main/assets/dag-protection/video-seek-state.js");
const controllerAsset = join(testRoot, "../../main/assets/dag-protection/video-lab-seek.js");

const runtime = async () => {
  const context = { globalThis: null };
  context.globalThis = context;
  vm.runInNewContext(await readFile(asset, "utf8"), context, { filename: asset });
  return context.__gloshDagVideoSeekState;
};

const controllerHarness = async () => {
  const context = { globalThis: null };
  context.globalThis = context;
  vm.runInNewContext(await readFile(asset, "utf8"), context, { filename: asset });
  vm.runInNewContext(await readFile(controllerAsset, "utf8"), context, {
    filename: controllerAsset,
  });
  const timers = new Map();
  const retireReasons = [];
  let nextTimer = 1;
  let scans = 0;
  const video = {
    currentTime: 10,
    seeking: true,
  };
  const record = { retiring: false, video };
  const controller = context.__gloshDagVideoLabSeek.create({
    Phase: context.__gloshDagVideoSeekState.Phase,
    activeRecord: () => record,
    clearTimeout: (timer) => timers.delete(timer),
    enforceMediaIsolation: () => {},
    postDiagnostic: () => {},
    retireRecord: async (_record, reason) => { retireReasons.push(reason); },
    scheduleScan: () => { scans += 1; },
    setTimeout: (callback) => {
      const id = nextTimer;
      nextTimer += 1;
      timers.set(id, callback);
      return id;
    },
    settleMillis: 150,
    sourceSignature: () => "source_a",
    stateRuntime: context.__gloshDagVideoSeekState,
    timeoutMillis: 2_500,
    viewportSignature: () => [360, 640, 0, 0],
  });
  const runLatestTimer = () => {
    const entry = [...timers.entries()].at(-1);
    assert.ok(entry);
    timers.delete(entry[0]);
    entry[1]();
  };
  return {
    controller,
    record,
    retireReasons,
    runLatestTimer,
    scans: () => scans,
    video,
  };
};

const snapshot = (currentTime = 10) => ({
  sourceSignature: "source_a",
  viewportSignature: "viewport_a",
  currentTime,
});

test("seek reopens only after exact revoke, seeked and stable destination", async () => {
  const { create, Phase } = await runtime();
  const state = create();

  assert.equal(state.begin(snapshot()), Phase.Closing);
  assert.equal(state.acknowledgeRevocation(true), Phase.WaitingSeeked);
  assert.equal(state.observeSeeked(snapshot(25)), Phase.Stabilizing);
  assert.equal(state.settle(snapshot(25.03)), Phase.Ready);
  assert.deepEqual(
    JSON.parse(JSON.stringify(state.takeRearm())),
    snapshot(25.03),
  );
  assert.equal(state.phase(), Phase.Complete);
});

test("seeked may arrive before native revoke without reopening early", async () => {
  const { create, Phase } = await runtime();
  const state = create();

  assert.equal(state.begin(snapshot()), Phase.Closing);
  assert.equal(state.observeSeeked(snapshot(5)), Phase.Closing);
  assert.equal(state.acknowledgeRevocation(true), Phase.Stabilizing);
  assert.equal(state.phase(), Phase.Stabilizing);
  assert.equal(state.settle(snapshot(5.02)), Phase.Ready);
  assert.deepEqual(
    JSON.parse(JSON.stringify(state.takeRearm())),
    snapshot(5.02),
  );
  assert.equal(state.phase(), Phase.Complete);
});

test("same-authority seeking coalesces while changes, ambiguity and timeout fail closed", async () => {
  const { create, Phase } = await runtime();

  const repeated = create();
  repeated.begin(snapshot());
  assert.equal(repeated.observeSeeking(snapshot(20)), Phase.Closing);
  repeated.acknowledgeRevocation(true);
  assert.equal(repeated.observeSeeking(snapshot(21)), Phase.WaitingSeeked);
  assert.equal(repeated.observeSeeked(snapshot(21)), Phase.Stabilizing);

  const changed = create();
  changed.begin(snapshot());
  changed.acknowledgeRevocation(true);
  assert.equal(
    changed.observeSeeked({ ...snapshot(20), sourceSignature: "source_b" }),
    Phase.Terminal,
  );

  const ambiguous = create();
  ambiguous.begin(snapshot());
  assert.equal(ambiguous.acknowledgeRevocation(false), Phase.Terminal);

  const timedOut = create();
  timedOut.begin(snapshot());
  assert.equal(timedOut.fail("seek_timeout"), Phase.Terminal);
  assert.equal(timedOut.reason(), "seek_timeout");
});

test("seek controller holds scanning through revoke and destination stability", async () => {
  const harness = await controllerHarness();
  assert.equal(harness.controller.onSeeking(harness.record), true);
  assert.deepEqual(harness.retireReasons, ["seek_requested"]);
  assert.equal(harness.controller.holdsScan(), true);

  harness.video.currentTime = 25;
  harness.video.seeking = false;
  assert.equal(harness.controller.onSeeked(harness.record), true);
  assert.equal(harness.scans(), 0);
  assert.equal(harness.controller.onNativeRearm(), true);
  harness.runLatestTimer();

  assert.equal(harness.controller.holdsScan(), false);
  assert.equal(harness.scans(), 1);
});

test("seek controller accepts native revoke before seeked but never reopens early", async () => {
  const harness = await controllerHarness();
  harness.controller.onSeeking(harness.record);
  assert.equal(harness.controller.onNativeRearm(), true);
  assert.equal(harness.scans(), 0);

  harness.video.currentTime = 5;
  harness.video.seeking = false;
  harness.controller.onSeeked(harness.record);
  harness.runLatestTimer();
  assert.equal(harness.scans(), 1);
});

test("seek controller coalesces one repeated gesture under the same closed authority", async () => {
  const harness = await controllerHarness();
  harness.controller.onSeeking(harness.record);
  harness.video.currentTime = 20;
  assert.equal(harness.controller.onSeeking(harness.record), true);
  assert.equal(harness.controller.onNativeRearm(), true);
  harness.video.currentTime = 21;
  harness.video.seeking = false;
  harness.controller.onSeeked(harness.record);
  harness.runLatestTimer();
  assert.equal(harness.controller.holdsScan(), false);
  assert.equal(harness.scans(), 1);
});
