import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";
import vm from "node:vm";

const testRoot = dirname(fileURLToPath(import.meta.url));
const asset = join(testRoot, "../../main/assets/dag-protection/video-authority-selection.js");

const harness = async () => {
  const context = { globalThis: null };
  context.globalThis = context;
  vm.runInNewContext(await readFile(asset, "utf8"), context, { filename: asset });
  let now = 0;
  let activeVideo = null;
  let nextTimer = 1;
  const timers = new Map();
  const events = [];
  const videos = [];
  const controller = context.__gloshDagVideoAuthoritySelection.create({
    activeVideo: () => activeVideo,
    canBootstrapCandidate: () => true,
    clearTimeout: (timer) => timers.delete(timer),
    hasBackingMedia: (video) => video.backing,
    maximumTransitions: 8,
    now: () => now,
    onActiveCandidate: () => events.push("active"),
    onAuthorityChanged: () => {
      events.push("changed");
      activeVideo = null;
      return true;
    },
    onHandoffWaiting: () => events.push("waiting"),
    onNoCandidate: () => events.push("none"),
    onSelected: (video) => {
      events.push(`selected:${video.id}`);
      activeVideo = video;
    },
    reportBackingTransition: () => {},
    scheduleScan: () => events.push("scan"),
    setTimeout: (callback, delay) => {
      const id = nextTimer;
      nextTimer += 1;
      timers.set(id, { callback, delay });
      return id;
    },
    settleMillis: 150,
    sourceSignature: (video) => video.source,
    viewportSignature: (video) => video.viewport,
    visibleArea: (video) => video.area,
  });
  const addVideo = (id, source, area = 100) => {
    const video = { area, backing: source !== "", id, source, viewport: [0, 0, 100, 100] };
    videos.push(video);
    return video;
  };
  const advance = (millis) => { now += millis; };
  const runTimer = () => {
    const entry = [...timers.entries()][0];
    assert.ok(entry);
    timers.delete(entry[0]);
    advance(entry[1].delay);
    entry[1].callback();
  };
  return {
    active: () => activeVideo,
    addVideo,
    advance,
    controller,
    events,
    runTimer,
    timers,
    videos,
  };
};

test("initial video is selected immediately", async () => {
  const h = await harness();
  h.addVideo("initial", "source_a");
  h.controller.scan(h.videos);
  assert.equal(h.active()?.id, "initial");
  assert.deepEqual(h.events, ["selected:initial"]);
});

test("initial video without backing waits for stable geometry", async () => {
  const h = await harness();
  h.addVideo("bootstrap", "");
  h.controller.scan(h.videos);
  assert.equal(h.active(), null);
  assert.equal(h.events.at(-1), "waiting");
  h.runTimer();
  h.controller.scan(h.videos);
  assert.equal(h.active()?.id, "bootstrap");
});

test("backing may promote immediately before the first grant when geometry is exact", async () => {
  const h = await harness();
  const bootstrap = h.addVideo("bootstrap", "");
  h.controller.scan(h.videos);
  bootstrap.source = "blob:media";
  bootstrap.backing = true;
  h.controller.scan(h.videos);
  assert.equal(h.active()?.id, "bootstrap");
  assert.equal(h.events.at(-1), "selected:bootstrap");
});

test("backed candidate starts a fresh authority when geometry changes", async () => {
  const h = await harness();
  const bootstrap = h.addVideo("bootstrap", "");
  h.controller.scan(h.videos);
  bootstrap.source = "blob:media";
  bootstrap.backing = true;
  bootstrap.viewport = [0, 0, 100, 101];
  h.controller.scan(h.videos);
  assert.equal(h.active()?.id, "bootstrap");
  assert.equal(h.events.at(-1), "selected:bootstrap");
});

test("backed candidate after an empty DOM gap receives a fresh authority", async () => {
  const h = await harness();
  const provisional = h.addVideo("provisional", "");
  h.controller.scan(h.videos);
  h.runTimer();
  h.controller.scan(h.videos);
  assert.equal(h.active()?.id, "provisional");
  provisional.area = 0;
  h.controller.scan(h.videos);
  await Promise.resolve();
  h.advance(36_000);
  h.controller.scan(h.videos);
  const backed = h.addVideo("backed", "blob:media");
  backed.viewport = [20, 40, 180, 320];
  h.controller.scan(h.videos);
  assert.equal(h.active()?.id, "backed");
  assert.equal(h.events.at(-1), "selected:backed");
});

test("a late bootstrap-only replacement still requires its own stable observation", async () => {
  const h = await harness();
  const provisional = h.addVideo("provisional", "");
  h.controller.scan(h.videos);
  h.runTimer();
  h.controller.scan(h.videos);
  provisional.area = 0;
  h.controller.scan(h.videos);
  await Promise.resolve();
  h.advance(60_001);
  const backed = h.addVideo("bootstrap_later", "");
  h.controller.scan(h.videos);
  assert.equal(h.active(), null);
  assert.equal(h.events.at(-1), "waiting");
  h.runTimer();
  h.controller.scan(h.videos);
  assert.equal(h.active()?.id, "bootstrap_later");
});

test("backed replacement gets a fresh authority without inheriting old geometry", async () => {
  const h = await harness();
  const first = h.addVideo("first", "source_a", 100);
  const replacement = h.addVideo("replacement", "source_b", 90);
  h.controller.scan(h.videos);
  replacement.area = 120;
  first.area = 0;
  h.controller.scan(h.videos);
  assert.equal(h.active(), null);
  assert.equal(h.timers.size, 1);
  h.advance(100);
  replacement.viewport = [0, 0, 100, 101];
  h.controller.scan(h.videos);
  assert.equal(h.active()?.id, "replacement");
  assert.deepEqual(
    h.events.filter((event) => event.startsWith("selected:")),
    ["selected:first", "selected:replacement"],
  );
});

test("replacement may appear without backing and stabilize after standard media events", async () => {
  const h = await harness();
  const first = h.addVideo("first", "source_a");
  const replacement = h.addVideo("replacement", "", 120);
  h.controller.scan(h.videos);
  first.area = 0;
  h.controller.scan(h.videos);
  assert.equal(h.active(), null);
  await Promise.resolve();
  h.controller.scan(h.videos);
  assert.equal(h.events.at(-1), "waiting");
  replacement.source = "blob:media";
  replacement.backing = true;
  h.controller.scan(h.videos);
  assert.equal(h.active()?.id, "replacement");
});

test("stable visible replacement without backing is selected for covered bootstrap", async () => {
  const h = await harness();
  const first = h.addVideo("first", "source_a", 100);
  const replacement = h.addVideo("replacement", "", 120);
  h.controller.scan(h.videos);
  first.area = 0;
  h.controller.scan(h.videos);
  assert.equal(h.active(), null);
  h.runTimer();
  h.controller.scan(h.videos);
  assert.equal(h.active()?.id, "replacement");
});

test("a backed candidate is preferred over a larger bootstrap-only candidate", async () => {
  const h = await harness();
  h.addVideo("bootstrap", "", 200);
  h.addVideo("backed", "source", 100);
  h.controller.scan(h.videos);
  assert.equal(h.active()?.id, "backed");
});

test("ad-like replacement chain selects only the current backed element", async () => {
  const h = await harness();
  const first = h.addVideo("first", "source_a", 100);
  const second = h.addVideo("second", "source_b", 90);
  const third = h.addVideo("third", "source_c", 80);
  h.controller.scan(h.videos);
  first.area = 0;
  second.area = 120;
  h.controller.scan(h.videos);
  h.advance(100);
  second.area = 0;
  third.area = 130;
  h.controller.scan(h.videos);
  assert.equal(h.active()?.id, "third");
  assert.equal(h.events.includes("selected:second"), false);
  assert.deepEqual(
    h.events.filter((event) => event.startsWith("selected:")),
    ["selected:first", "selected:third"],
  );
});

test("cancel clears delayed verification and requires a new scan", async () => {
  const h = await harness();
  const first = h.addVideo("first", "source_a", 100);
  const replacement = h.addVideo("replacement", "source_b", 90);
  h.controller.scan(h.videos);
  first.area = 0;
  replacement.area = 120;
  h.controller.scan(h.videos);
  h.controller.cancel();
  assert.equal(h.timers.size, 0);
  h.advance(200);
  h.controller.scan(h.videos);
  assert.equal(h.active()?.id, "replacement");
});
