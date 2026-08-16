import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";
import vm from "node:vm";

const testRoot = dirname(fileURLToPath(import.meta.url));
const asset = join(testRoot, "../../main/assets/dag-protection/video-source-bootstrap.js");

const harness = async () => {
  const timers = new Map();
  let nextTimer = 1;
  const context = {
    clearTimeout: (id) => timers.delete(id),
    globalThis: null,
    setTimeout: (callback, delay) => {
      const id = nextTimer++;
      timers.set(id, { callback, delay });
      return id;
    },
  };
  context.globalThis = context;
  vm.runInNewContext(await readFile(asset, "utf8"), context, { filename: asset });
  const events = [];
  const video = {
    backing: false,
    defaultMuted: false,
    isConnected: true,
    muted: false,
    pause: () => events.push("pause"),
    play: () => {
      events.push("play");
      return Promise.resolve();
    },
    preload: "",
    volume: 1,
  };
  const record = {
    covered: false,
    coverPending: false,
    readinessTimer: null,
    retiring: false,
    sourceBootstrapActive: false,
    sourceBootstrapCompleted: false,
    terminal: false,
    video,
  };
  let activeRecord = record;
  const controller = context.__gloshDagVideoSourceBootstrap.create({
    activeRecord: () => activeRecord,
    enforceMediaIsolation: () => events.push("isolate"),
    enforcePresentationCapabilities: () => events.push("capabilities"),
    hasBackingMedia: (item) => item.backing,
    onPlayRejected: () => events.push("rejected"),
    onPlayStarted: () => events.push("started"),
    onReady: () => events.push("ready"),
    onTimeout: () => events.push("timeout"),
    safePause: (item) => item.pause(),
    timeoutMillis: 2500,
  });
  return { controller, events, record, setActive: (value) => { activeRecord = value; }, timers, video };
};

test("starts once under cover and completes when backing appears", async () => {
  const h = await harness();
  assert.equal(h.controller.start(h.record), true);
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(h.record.sourceBootstrapActive, true);
  h.video.backing = true;
  assert.equal(h.controller.backingReady(h.record), true);
  assert.equal(h.record.sourceBootstrapCompleted, true);
  assert.equal(h.timers.size, 0);
  assert.deepEqual(h.events, ["capabilities", "isolate", "play", "started", "pause", "ready"]);
  assert.equal(h.controller.start(h.record), false);
});

test("timeout pauses and fails without retrying the same element", async () => {
  const h = await harness();
  assert.equal(h.controller.start(h.record), true);
  await new Promise((resolve) => setImmediate(resolve));
  const timer = [...h.timers.values()][0];
  assert.equal(timer.delay, 2500);
  timer.callback();
  assert.equal(h.record.sourceBootstrapActive, false);
  assert.equal(h.events.at(-1), "timeout");
  assert.equal(h.controller.canAttempt(h.video), false);
  assert.equal(h.controller.start(h.record), false);
});

test("reject, cancel and stale authority remain fail closed", async () => {
  const rejected = await harness();
  rejected.video.play = () => Promise.reject(new Error("denied"));
  assert.equal(rejected.controller.start(rejected.record), true);
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(rejected.events.includes("rejected"), true);
  assert.equal(rejected.record.sourceBootstrapActive, false);

  const cancelled = await harness();
  assert.equal(cancelled.controller.start(cancelled.record), true);
  cancelled.controller.cancel(cancelled.record);
  assert.equal(cancelled.record.sourceBootstrapActive, false);
  assert.equal(cancelled.timers.size, 0);

  const stale = await harness();
  stale.setActive(null);
  assert.equal(stale.controller.start(stale.record), false);
});
