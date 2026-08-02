import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";
import vm from "node:vm";

const testRoot = dirname(fileURLToPath(import.meta.url));
const extensionRoot = resolve(testRoot, "../../main/assets/dag-protection");
const readAsset = (name) => readFile(join(extensionRoot, name), "utf8");

const eventChannel = () => ({
  listeners: [],
  addListener(listener) {
    this.listeners.push(listener);
  },
});

const createBackgroundHarness = async () => {
  const beforeRequest = eventChannel();
  const headersReceived = eventChannel();
  const browser = {
    webRequest: {
      onBeforeRequest: beforeRequest,
      onHeadersReceived: headersReceived,
    },
  };
  const source = await readAsset("background.js");
  vm.runInNewContext(source, { browser, URL }, { filename: "background.js" });
  assert.equal(beforeRequest.listeners.length, 1);
  assert.equal(headersReceived.listeners.length, 1);
  return {
    before: beforeRequest.listeners[0],
    headers: headersReceived.listeners[0],
  };
};

test("ordinary image requests stay entirely owned by Gecko", async () => {
  const harness = await createBackgroundHarness();
  const result = harness.before({
    type: "image",
    url: "https://cdn.example.test/header/heart.svg",
    documentUrl: "https://shop.example.test/",
  });

  assert.equal(Object.keys(result).length, 0);
  const source = await readAsset("background.js");
  assert.doesNotMatch(source, /filterResponseData|connectNative|media-presentation/u);
});

test("responsive image sets are not intercepted", async () => {
  const harness = await createBackgroundHarness();
  assert.equal(Object.keys(harness.before({
    type: "imageset",
    url: "https://cdn.example.test/product-2x.webp",
  })).length, 0);
});

test("video and object responses remain blocked", async () => {
  const harness = await createBackgroundHarness();
  assert.equal(harness.before({ type: "media", url: "https://media.example.test/a.mp4" }).cancel, true);
  assert.equal(harness.before({ type: "object", url: "https://media.example.test/a.swf" }).cancel, true);
  assert.equal(harness.headers({
    responseHeaders: [{ name: "Content-Type", value: "video/mp4" }],
  }).cancel, true);
  assert.equal(harness.headers({
    responseHeaders: [{ name: "content-type", value: "image/webp" }],
  }).cancel, undefined);
});

test("known ad subresources are blocked without blocking navigation", async () => {
  const harness = await createBackgroundHarness();
  assert.equal(harness.before({
    type: "script",
    url: "https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js",
    documentUrl: "https://news.example.test/",
  }).cancel, true);
  assert.equal(harness.before({
    type: "main_frame",
    url: "https://doubleclick.net/",
  }).cancel, undefined);
});

test("video sites keep their page resources while media remains blocked", async () => {
  const harness = await createBackgroundHarness();
  assert.equal(harness.before({
    type: "script",
    url: "https://pagead2.googlesyndication.com/pagead/id",
    documentUrl: "https://www.youtube.com/watch?v=test",
  }).cancel, undefined);
  assert.equal(harness.before({
    type: "media",
    url: "https://r1---sn.googlevideo.com/videoplayback",
    documentUrl: "https://www.youtube.com/watch?v=test",
  }).cancel, true);
});

test("page bridge stays minimal and never mutates image state", async () => {
  const barrier = await readAsset("barrier.js");
  new vm.Script(barrier);
  assert.match(barrier, /barrier-ready/u);
  assert.match(barrier, /tab-preview-eligibility/u);
  assert.doesNotMatch(barrier, /MutationObserver|data-glosh-dag-media|querySelectorAll|srcset/u);
});

test("presentation css never targets images svg or page backgrounds", async () => {
  const css = await readAsset("barrier.css");
  assert.match(css, /video,/u);
  assert.match(css, /glosh-dag-page-ad-hidden/u);
  assert.doesNotMatch(css, /\bimg\b|\bimage\b|\bsvg\b|background-image|object-position/u);
});

test("active extension has no store or device exceptions", async () => {
  const source = [
    await readAsset("background.js"),
    await readAsset("barrier.js"),
    await readAsset("barrier.css"),
  ].join("\n");
  assert.doesNotMatch(source, /cheeky|mimo|fravega|sm-a235|sm-s908/iu);
});

test("manifest keeps the bridge at document start in every frame", async () => {
  const manifest = JSON.parse(await readAsset("manifest.json"));
  const content = manifest.content_scripts[0];
  assert.equal(content.run_at, "document_start");
  assert.equal(content.all_frames, true);
  assert.equal(content.match_about_blank, true);
  assert.deepEqual(content.js, ["barrier.js", "ads.js"]);
});
