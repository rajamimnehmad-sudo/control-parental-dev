import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { test } from "node:test";
import vm from "node:vm";
import { webcrypto } from "node:crypto";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const testRoot = dirname(fileURLToPath(import.meta.url));
const assetRoot = join(testRoot, "../../main/assets/dag-protection");
const readAsset = (name) => readFile(join(assetRoot, name), "utf8");

const waitFor = async (probe, label) => {
  for (let attempt = 0; attempt < 40; attempt += 1) {
    const value = probe();
    if (value) return value;
    await new Promise((resolve) => setImmediate(resolve));
  }
  throw new Error(`timed out waiting for ${label}`);
};

test("product smooth mode restores visible audible playback and revokes the original grant on block", async () => {
  const animationFrames = [];
  const posted = [];
  const timers = new Map();
  const windowListeners = new Map();
  const revealMessages = [];
  const concealMessages = [];
  let nextTimer = 1;
  let now = 1_000;

  class HTMLMediaElement {
    constructor() {
      this.defaultMuted = false;
      this.listeners = new Map();
      this.muted = false;
      this.pauseCalls = 0;
      this.volume = 0.7;
    }
    addEventListener(type, listener) {
      this.listeners.set(type, listener);
    }
    pause() {
      this.pauseCalls += 1;
    }
  }

  class HTMLVideoElement extends HTMLMediaElement {
    constructor() {
      super();
      this.attributes = new Map();
      this.currentSrc = "https://media.example.test/movie.mp4";
      this.duration = 30;
      this.frameCallback = null;
      this.isConnected = true;
      this.readyState = 4;
      this.remote = { state: "disconnected", addEventListener() {} };
      this.srcObject = null;
      this.playCalls = 0;
      this.rect = { bottom: 80, height: 80, left: 0, right: 100, top: 0, width: 100 };
    }
    getAttribute(name) {
      return this.attributes.get(name.toLowerCase()) ?? null;
    }
    setAttribute(name, value) {
      this.attributes.set(name.toLowerCase(), String(value));
    }
    removeAttribute(name) {
      this.attributes.delete(name.toLowerCase());
    }
    get disablePictureInPicture() {
      return this.attributes.has("disablepictureinpicture");
    }
    set disablePictureInPicture(value) {
      if (value) this.attributes.set("disablepictureinpicture", "");
      else this.attributes.delete("disablepictureinpicture");
    }
    get disableRemotePlayback() {
      return this.attributes.has("disableremoteplayback");
    }
    set disableRemotePlayback(value) {
      if (value) this.attributes.set("disableremoteplayback", "");
      else this.attributes.delete("disableremoteplayback");
    }
    get playsInline() {
      return this.attributes.has("playsinline");
    }
    set playsInline(value) {
      if (value) this.attributes.set("playsinline", "");
      else this.attributes.delete("playsinline");
    }
    getBoundingClientRect() {
      return this.rect;
    }
    play() {
      this.playCalls += 1;
      return Promise.resolve();
    }
    querySelectorAll() {
      return [];
    }
    requestVideoFrameCallback(callback) {
      this.frameCallback = callback;
      return 1;
    }
    cancelVideoFrameCallback() {
      this.frameCallback = null;
    }
  }

  class MutationObserver {
    observe() {}
  }

  const video = new HTMLVideoElement();
  const document = {
    documentElement: {
      getAttribute: (name) => name === "data-glosh-dag-presentation-guard" ? "1" : null,
      hasAttribute: () => false,
    },
    fullscreenElement: null,
    pictureInPictureElement: null,
    addEventListener() {},
    querySelectorAll(selector) {
      if (selector === "audio, video" || selector === "video") return [video];
      return [];
    },
  };

  const runNextTimer = (accept) => {
    const entry = [...timers.entries()].find(([, timer]) => accept(timer.delay));
    if (!entry) return false;
    timers.delete(entry[0]);
    entry[1].callback();
    return true;
  };

  const runTimersUntil = async (probe, accept, label) => {
    for (let attempt = 0; attempt < 60; attempt += 1) {
      if (probe()) return;
      runNextTimer(accept);
      await new Promise((resolve) => setImmediate(resolve));
    }
    throw new Error(`timed out waiting for ${label}`);
  };

  const context = {
    HTMLMediaElement,
    HTMLSourceElement: class {},
    HTMLVideoElement,
    MutationObserver,
    Uint32Array,
    browser: {
      runtime: {
        sendMessage(message) {
          if (message.type === "video-lab-status") return Promise.resolve({ enabled: true });
          if (message.type === "video-lab-reveal-style") {
            revealMessages.push({ ...message });
            return Promise.resolve({ inserted: true });
          }
          if (message.type === "video-lab-conceal-style") {
            concealMessages.push({ ...message });
            return Promise.resolve({ removed: true });
          }
          return Promise.resolve(null);
        },
      },
    },
    clearTimeout(id) {
      timers.delete(id);
    },
    crypto: webcrypto,
    document,
    getComputedStyle(target) {
      const released = target.getAttribute("data-glosh-dag-video-lab-token") !== null;
      return { display: "block", opacity: released ? "1" : "0", visibility: released ? "visible" : "hidden" };
    },
    innerHeight: 100,
    innerWidth: 100,
    performance: { now: () => now },
    requestAnimationFrame(callback) {
      animationFrames.push(callback);
      return animationFrames.length;
    },
    setTimeout(callback, delay) {
      const id = nextTimer;
      nextTimer += 1;
      timers.set(id, { callback, delay });
      return id;
    },
    addEventListener(type, listener) {
      windowListeners.set(type, listener);
    },
    window: { top: null },
  };
  context.globalThis = context;
  context.window.top = context.window;

  vm.runInNewContext(await readAsset("video-lab-geometry.js"), context, {
    filename: "video-lab-geometry.js",
  });
  vm.runInNewContext(await readAsset("video-lab-presentation.js"), context, {
    filename: "video-lab-presentation.js",
  });
  vm.runInNewContext(await readAsset("video-lab-diagnostics.js"), context, {
    filename: "video-lab-diagnostics.js",
  });
  vm.runInNewContext(await readAsset("video-bootstrap-state.js"), context, {
    filename: "video-bootstrap-state.js",
  });
  vm.runInNewContext(await readAsset("video-lab.js"), context, { filename: "video-lab.js" });
  context.__gloshDagVideoLab.install({
    protocolVersion: 2,
    documentToken: "document_a1",
    postToAndroid(message) {
      posted.push(message);
    },
  });
  context.__gloshDagVideoLab.onNativeMessage({
    type: "video-lab-config",
    version: 2,
    diagnostics: false,
    enabled: true,
  });

  assert.equal(video.muted, true);
  assert.equal(video.defaultMuted, true);
  assert.equal(video.volume, 0);
  animationFrames.shift()();
  const cover = await waitFor(() => posted.find((message) =>
    message.type === "video-lab-cover-request"), "cover request");
  context.__gloshDagVideoLab.onNativeMessage({ ...cover, type: "video-lab-cover-armed", version: 2 });
  await new Promise((resolve) => setImmediate(resolve));

  const allowCoveredFrame = async () => {
    now += 200;
    await runTimersUntil(
      () => video.frameCallback,
      (delay) => delay <= 150,
      "frame callback",
    );
    const callback = video.frameCallback;
    video.frameCallback = null;
    const frameCount = posted.filter((message) => message.type === "video-lab-frame-request").length;
    callback(now, { presentedFrames: 1 });
    const frame = await waitFor(() => {
      const frames = posted.filter((message) => message.type === "video-lab-frame-request");
      return frames.length > frameCount ? frames.at(-1) : null;
    }, "frame request");
    context.__gloshDagVideoLab.onNativeMessage({
      ...frame,
      type: "video-lab-frame-captured",
      version: 2,
    });
    await new Promise((resolve) => setImmediate(resolve));
    context.__gloshDagVideoLab.onNativeMessage({
      ...frame,
      type: "video-lab-frame-result",
      version: 2,
      action: "allow",
      captured: true,
    });
    await new Promise((resolve) => setImmediate(resolve));
  };

  await allowCoveredFrame();
  video.rect = { bottom: 80, height: 79, left: 0, right: 100, top: 1, width: 100 };
  windowListeners.get("resize")({ type: "resize" });
  await allowCoveredFrame();

  const smooth = await waitFor(() => posted.find((message) =>
    message.type === "video-lab-smooth-start"), "smooth start");
  assert.equal(smooth.cadenceMillis, 500);
  assert.equal(video.muted, false);
  assert.equal(video.defaultMuted, false);
  assert.equal(video.volume, 0.7);
  assert.equal(context.getComputedStyle(video).visibility, "visible");
  assert.equal(typeof video.listeners.get("seeking"), "function");

  const persistentGrant = revealMessages.at(-1);
  await runTimersUntil(
    () => video.frameCallback,
    (delay) => delay <= 500,
    "smooth frame callback",
  );
  const smoothCallback = video.frameCallback;
  video.frameCallback = null;
  const smoothFrameCount = posted.filter((message) =>
    message.type === "video-lab-frame-request").length;
  smoothCallback(now, { presentedFrames: 2 });
  const blockedFrame = await waitFor(() => {
    const frames = posted.filter((message) => message.type === "video-lab-frame-request");
    return frames.length > smoothFrameCount ? frames.at(-1) : null;
  }, "smooth frame request");
  context.__gloshDagVideoLab.onNativeMessage({
    ...blockedFrame,
    type: "video-lab-frame-captured",
    version: 2,
  });
  context.__gloshDagVideoLab.onNativeMessage({
    ...blockedFrame,
    type: "video-lab-frame-result",
    version: 2,
    action: "block",
    captured: true,
  });
  await waitFor(() => concealMessages.length > 0, "smooth grant concealment");

  const finalConceal = concealMessages.at(-1);
  assert.equal(finalConceal.frameSequence, persistentGrant.frameSequence);
  assert.notEqual(finalConceal.frameSequence, blockedFrame.frameSequence);
  assert.ok(video.pauseCalls > 0);
  assert.equal(concealMessages.length > 0, true);
});
