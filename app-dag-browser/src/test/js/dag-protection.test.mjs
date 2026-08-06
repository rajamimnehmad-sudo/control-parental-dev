import assert from "node:assert/strict";
import { webcrypto } from "node:crypto";
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

const waitFor = async (predicate, label) => {
  const deadline = Date.now() + 1_000;
  while (Date.now() < deadline) {
    const value = predicate();
    if (value) return value;
    await new Promise((resolvePromise) => setImmediate(resolvePromise));
  }
  throw new Error(`Timed out waiting for ${label}`);
};

const createHarness = async () => {
  const beforeRequest = eventChannel();
  const headersReceived = eventChannel();
  const nativeMessages = eventChannel();
  const nativeDisconnects = eventChannel();
  const runtimeMessages = eventChannel();
  const postedNative = [];
  const filters = new Map();
  const nativePort = {
    onMessage: nativeMessages,
    onDisconnect: nativeDisconnects,
    postMessage(message) {
      postedNative.push(message);
    },
  };
  const browser = {
    runtime: {
      onMessage: runtimeMessages,
      connectNative() {
        return nativePort;
      },
    },
    webRequest: {
      onBeforeRequest: beforeRequest,
      onHeadersReceived: headersReceived,
      filterResponseData(requestId) {
        const filter = {
          writes: [],
          closed: false,
          ondata: null,
          onerror: null,
          onstop: null,
          write(data) {
            this.writes.push(Uint8Array.from(data));
          },
          close() {
            this.closed = true;
          },
        };
        filters.set(requestId, filter);
        return filter;
      },
    },
  };
  const source = await readAsset("background.js");
  vm.runInNewContext(source, {
    URL,
    Uint32Array,
    Uint8Array,
    ArrayBuffer,
    atob,
    btoa,
    browser,
    clearTimeout,
    crypto: webcrypto,
    Date,
    Promise,
    setTimeout,
  }, { filename: "background.js" });
  return {
    before: beforeRequest.listeners[0],
    headers: headersReceived.listeners[0],
    filters,
    postedNative,
    decideInline(message, sender = { url: "https://search.example.test/?q=shoes" }) {
      return runtimeMessages.listeners[0](message, sender);
    },
    setImagePriority(message) {
      return runtimeMessages.listeners[0](message, { url: "https://shop.example.test/" });
    },
    answer(message) {
      for (const listener of nativeMessages.listeners) listener(message);
    },
    disconnect() {
      for (const listener of nativeDisconnects.listeners) listener();
    },
  };
};

test("visible image hints reach the native queue with priority", async () => {
  const harness = await createHarness();
  const details = imageDetails("priority");
  harness.setImagePriority({
    type: "image-priority",
    version: 1,
    url: details.url,
    priority: "visible",
  });
  deliver(harness, details, Uint8Array.from([0xff, 0xd8, 1, 2, 3, 0xff, 0xd9]));
  const request = await waitFor(() => harness.postedNative.find((message) =>
    message.type === "media-bytes"), "prioritized native request");
  assert.equal(request.priority, "visible");
});

const imageDetails = (requestId, url = `https://cdn.example.test/${requestId}.jpg`) => ({
  requestId,
  type: "image",
  url,
  documentUrl: "https://shop.example.test/",
});

const deliver = (harness, details, bytes, mime = "image/jpeg") => {
  const result = harness.before(details);
  harness.headers({ ...details, responseHeaders: [{ name: "Content-Type", value: mime }] });
  const filter = harness.filters.get(details.requestId);
  assert.ok(filter);
  filter.ondata({ data: Uint8Array.from(bytes).buffer });
  filter.onstop();
  return result;
};

test("allowed raster crosses one native gate and keeps exact bytes", async () => {
  const harness = await createHarness();
  const details = imageDetails("allow");
  const original = Uint8Array.from([0xff, 0xd8, 1, 2, 3, 0xff, 0xd9]);
  assert.equal(Object.keys(deliver(harness, details, original)).length, 0);
  const request = await waitFor(() => harness.postedNative.find((message) =>
    message.type === "media-bytes"), "native request");
  harness.answer({
    type: "media-decision",
    version: 1,
    candidateId: request.candidateId,
    action: "allow",
    reason: "model_allow",
  });
  const filter = harness.filters.get(details.requestId);
  await waitFor(() => filter.closed, "allowed stream close");
  assert.deepEqual([...filter.writes[0]], [...original]);
  assert.equal(filter.writes.length, 1);
});

test("filtered raster receives a neutral PNG without rejected pixels", async () => {
  const harness = await createHarness();
  const details = imageDetails("block");
  const original = Uint8Array.from([0xff, 0xd8, 9, 8, 7, 0xff, 0xd9]);
  deliver(harness, details, original);
  const request = await waitFor(() => harness.postedNative.find((message) =>
    message.type === "media-bytes"), "native request");
  harness.answer({
    type: "media-decision",
    version: 1,
    candidateId: request.candidateId,
    action: "block",
    reason: "model_filter",
  });
  const filter = harness.filters.get(details.requestId);
  await waitFor(() => filter.closed, "blocked stream close");
  assert.deepEqual([...filter.writes[0].slice(0, 8)], [137, 80, 78, 71, 13, 10, 26, 10]);
  assert.notDeepEqual([...filter.writes[0]], [...original]);
});

test("experimental redaction delivers the validated frosted replacement", async () => {
  const harness = await createHarness();
  const details = imageDetails("redact");
  const original = Uint8Array.from([0xff, 0xd8, 9, 8, 7, 0xff, 0xd9]);
  const replacement = Uint8Array.from([137, 80, 78, 71, 13, 10, 26, 10, 4, 5]);
  deliver(harness, details, original);
  const request = await waitFor(() => harness.postedNative.find((message) =>
    message.type === "media-bytes"), "redaction native request");
  harness.answer({
    type: "media-decision",
    version: 1,
    candidateId: request.candidateId,
    action: "redact",
    reason: "model_partial_redaction",
    replacementBytesBase64: btoa(String.fromCharCode(...replacement)),
  });
  const filter = harness.filters.get(details.requestId);
  await waitFor(() => filter.closed, "redacted stream close");
  assert.deepEqual([...filter.writes[0]], [...replacement]);
});

test("sanitized passive sprite is cached and preserves replacement bytes", async () => {
  const harness = await createHarness();
  const original = Uint8Array.from([137, 80, 78, 71, 13, 10, 26, 10, 1, 2]);
  const sanitized = Uint8Array.from([137, 80, 78, 71, 13, 10, 26, 10, 9, 8]);
  const first = imageDetails("sprite-a", "https://cdn.example.test/ui-strip.png");
  deliver(harness, first, original, "image/png");
  const request = await waitFor(() => harness.postedNative.find((message) =>
    message.type === "media-bytes"), "sprite native request");
  harness.answer({
    type: "media-decision",
    version: 1,
    candidateId: request.candidateId,
    action: "block",
    reason: "safe_ui_sprite",
    replacementBytesBase64: btoa(String.fromCharCode(...sanitized)),
  });
  await waitFor(() => harness.filters.get(first.requestId).closed, "sprite close");
  assert.deepEqual([...harness.filters.get(first.requestId).writes[0]], [...sanitized]);

  const second = imageDetails("sprite-b", "https://cdn.example.test/ui-strip.png?copy=1");
  deliver(harness, second, original, "image/png");
  await waitFor(() => harness.filters.get(second.requestId).closed, "cached sprite close");
  assert.equal(harness.postedNative.filter((message) => message.type === "media-bytes").length, 1);
  assert.deepEqual([...harness.filters.get(second.requestId).writes[0]], [...sanitized]);
});

test("bounded inline raster crosses the same native gate and fails closed", async () => {
  const harness = await createHarness();
  const original = Uint8Array.from([137, 80, 78, 71, 13, 10, 26, 10, 1, 2]);
  const allowed = harness.decideInline({
    type: "inline-raster-decision",
    version: 1,
    dataUrl: `data:image/png;base64,${btoa(String.fromCharCode(...original))}`,
  });
  const request = await waitFor(() => harness.postedNative.find((message) =>
    message.type === "media-bytes"), "inline native request");
  harness.answer({
    type: "media-decision",
    version: 1,
    candidateId: request.candidateId,
    action: "allow",
    reason: "model_allow",
  });
  assert.equal((await allowed).action, "allow");

  assert.equal((await harness.decideInline({
    type: "inline-raster-decision",
    version: 1,
    dataUrl: "data:image/svg+xml;base64,PHN2Zy8+",
  })).action, "block");
  assert.equal((await harness.decideInline({
    type: "inline-raster-decision",
    version: 1,
    dataUrl: `data:image/png;base64,${"A".repeat(66 * 1024)}`,
  })).action, "block");
  assert.equal(harness.postedNative.filter((message) => message.type === "media-bytes").length, 1);
});

test("SVG and icon URLs never enter the response gate", async () => {
  const harness = await createHarness();
  const result = harness.before(imageDetails("icon", "https://cdn.example.test/heart.svg?v=2"));
  assert.equal(Object.keys(result).length, 0);
  assert.equal(harness.filters.size, 0);
  assert.equal(harness.postedNative.length, 0);
});

test("vector MIME discovered after request start passes exact bytes without inference", async () => {
  const harness = await createHarness();
  const details = imageDetails("vector", "https://cdn.example.test/asset?id=2");
  const original = new TextEncoder().encode("<svg xmlns='http://www.w3.org/2000/svg'/>");
  deliver(harness, details, original, "image/svg+xml");
  const filter = harness.filters.get(details.requestId);
  await waitFor(() => filter.closed, "vector stream close");
  assert.deepEqual([...filter.writes[0]], [...original]);
  assert.equal(harness.postedNative.filter((message) => message.type === "media-bytes").length, 0);
});

test("raster opened as a top-level page still crosses the same native gate", async () => {
  const harness = await createHarness();
  const details = {
    requestId: "top-level-raster",
    type: "main_frame",
    url: "https://cdn.example.test/photo.jpg",
  };
  const original = Uint8Array.from([0xff, 0xd8, 5, 6, 7, 0xff, 0xd9]);
  assert.equal(Object.keys(harness.before(details)).length, 0);
  assert.equal(harness.filters.size, 0);
  const headerResult = harness.headers({
    ...details,
    responseHeaders: [{ name: "Content-Type", value: "image/jpeg" }],
  });
  assert.equal(Object.keys(headerResult).length, 0);
  const filter = harness.filters.get(details.requestId);
  assert.ok(filter);
  filter.ondata({ data: original.buffer });
  filter.onstop();
  const request = await waitFor(() => harness.postedNative.find((message) =>
    message.type === "media-bytes"), "top-level native request");
  harness.answer({
    type: "media-decision",
    version: 1,
    candidateId: request.candidateId,
    action: "allow",
    reason: "model_allow",
  });
  await waitFor(() => filter.closed, "top-level raster close");
  assert.deepEqual([...filter.writes[0]], [...original]);
});

test("raster fetched as data crosses the same native gate before page code sees bytes", async () => {
  const harness = await createHarness();
  const details = {
    requestId: "fetched-raster",
    type: "xmlhttprequest",
    url: "https://cdn.example.test/preview?id=42",
    documentUrl: "https://images.example.test/",
  };
  const original = Uint8Array.from([0xff, 0xd8, 9, 8, 7, 0xff, 0xd9]);
  assert.equal(Object.keys(harness.before(details)).length, 0);
  const headerResult = harness.headers({
    ...details,
    responseHeaders: [{ name: "Content-Type", value: "image/jpeg" }],
  });
  assert.equal(Object.keys(headerResult).length, 0);
  const filter = harness.filters.get(details.requestId);
  assert.ok(filter);
  filter.ondata({ data: original.buffer });
  filter.onstop();
  const request = await waitFor(() => harness.postedNative.find((message) =>
    message.type === "media-bytes"), "fetched raster native request");
  harness.answer({
    type: "media-decision",
    version: 1,
    candidateId: request.candidateId,
    action: "block",
    reason: "model_filter",
  });
  await waitFor(() => filter.closed, "fetched raster close");
  assert.deepEqual([...filter.writes[0].slice(0, 8)], [137, 80, 78, 71, 13, 10, 26, 10]);
});

test("trusted content decision cache avoids a second inference", async () => {
  const harness = await createHarness();
  const original = Uint8Array.from([0xff, 0xd8, 3, 3, 3, 0xff, 0xd9]);
  const first = imageDetails("cache-a");
  deliver(harness, first, original);
  const request = await waitFor(() => harness.postedNative.find((message) =>
    message.type === "media-bytes"), "first native request");
  harness.answer({
    type: "media-decision",
    version: 1,
    candidateId: request.candidateId,
    action: "allow",
    reason: "model_allow",
  });
  await waitFor(() => harness.filters.get(first.requestId).closed, "first close");

  const second = imageDetails("cache-b");
  deliver(harness, second, original);
  await waitFor(() => harness.filters.get(second.requestId).closed, "cached close");
  assert.equal(harness.postedNative.filter((message) => message.type === "media-bytes").length, 1);
  assert.deepEqual([...harness.filters.get(second.requestId).writes[0]], [...original]);
});

test("identical in-flight raster shares one native inference", async () => {
  const harness = await createHarness();
  const original = Uint8Array.from([0xff, 0xd8, 6, 6, 6, 0xff, 0xd9]);
  const first = imageDetails("inflight-a");
  const second = imageDetails("inflight-b", first.url);
  deliver(harness, first, original);
  const request = await waitFor(() => harness.postedNative.find((message) =>
    message.type === "media-bytes"), "in-flight native request");
  deliver(harness, second, original);
  harness.answer({
    type: "media-decision",
    version: 1,
    candidateId: request.candidateId,
    action: "allow",
    reason: "model_allow",
  });
  await waitFor(() => harness.filters.get(first.requestId).closed, "first in-flight close");
  await waitFor(() => harness.filters.get(second.requestId).closed, "second in-flight close");
  assert.equal(harness.postedNative.filter((message) => message.type === "media-bytes").length, 1);
  assert.deepEqual([...harness.filters.get(second.requestId).writes[0]], [...original]);
});

test("disconnect and malformed decisions fail closed", async () => {
  const harness = await createHarness();
  const details = imageDetails("disconnect");
  deliver(harness, details, [0xff, 0xd8, 4, 0xff, 0xd9]);
  await waitFor(() => harness.postedNative.some((message) => message.type === "media-bytes"),
    "native request");
  harness.disconnect();
  const filter = harness.filters.get(details.requestId);
  await waitFor(() => filter.closed, "failed stream close");
  assert.deepEqual([...filter.writes[0].slice(0, 8)], [137, 80, 78, 71, 13, 10, 26, 10]);
});

test("oversized image closes with the safe placeholder before native work", async () => {
  const harness = await createHarness();
  const details = imageDetails("oversized");
  harness.before(details);
  const filter = harness.filters.get(details.requestId);
  filter.ondata({ data: new Uint8Array(2 * 1024 * 1024 + 1).buffer });
  await waitFor(() => filter.closed, "oversized close");
  assert.deepEqual([...filter.writes[0].slice(0, 8)], [137, 80, 78, 71, 13, 10, 26, 10]);
  assert.equal(harness.postedNative.filter((message) => message.type === "media-bytes").length, 0);
});

test("video and advertisement policy remains isolated", async () => {
  const harness = await createHarness();
  assert.equal(harness.before({ type: "media", url: "https://media.example.test/a.mp4" }).cancel, true);
  assert.equal(harness.before({
    type: "script",
    url: "https://pagead2.googlesyndication.com/pagead/id",
    documentUrl: "https://news.example.test/",
  }).cancel, true);
  assert.equal(harness.headers({
    type: "xmlhttprequest",
    requestId: "video-header",
    url: "https://media.example.test/a",
    responseHeaders: [{ name: "content-type", value: "video/mp4" }],
  }).cancel, true);
});

test("first paint closes inline and changing image sources before stable reveal", async () => {
  const barrier = await readAsset("barrier.js");
  const css = await readAsset("barrier.css");
  new vm.Script(barrier);
  assert.match(barrier, /barrier-ready/u);
  assert.match(barrier, /IMAGE_STABILITY_MS = 0/u);
  assert.match(barrier, /MutationObserver/u);
  assert.match(barrier, /attributeFilter: \["src", "srcset", "sizes"\]/u);
  assert.match(barrier, /imageSource\(image\) === source/u);
  assert.match(barrier, /image\.hasAttribute\(STABLE_IMAGE_ATTRIBUTE\)/u);
  assert.match(barrier, /hasInlineImageSource\(record\.target\)/u);
  assert.match(barrier, /MAX_INLINE_IMAGES_PER_DOCUMENT = 16/u);
  assert.match(barrier, /inlineImageIsBounded/u);
  assert.match(barrier, /inline-raster-decision/u);
  assert.match(barrier, /pendingImages\.get\(image\) !== request/u);
  assert.doesNotMatch(barrier, /\.src\s*=|\.srcset\s*=|cheeky|google\.com/iu);
  assert.match(css, /img\[src\^="data:" i\]/u);
  assert.match(css, /img\[src\^="blob:" i\]/u);
  assert.match(css, /img\[srcset\*="data:image" i\]/u);
  assert.match(css, /picture:has\(source\[srcset\*="data:image" i\]\) img/u);
  assert.match(css, /svg image\[href\^="data:" i\]/u);
  assert.match(css, /background-image: none !important/u);
  assert.match(css, /img:not\(\[data-glosh-dag-stable="true"\]\)/u);
  assert.doesNotMatch(css, /\[src\^="https?:"|object-position/u);
});

test("active extension has bounded work and no site or device exceptions", async () => {
  const background = await readAsset("background.js");
  const ads = await readAsset("ads.js");
  assert.match(background, /MAX_IMAGE_BYTES = 2 \* 1024 \* 1024/u);
  assert.match(background, /MAX_CAPTURED_BYTES = 8 \* 1024 \* 1024/u);
  assert.match(background, /MAX_NATIVE_IN_FLIGHT = 2/u);
  assert.match(background, /MAX_QUEUED_ANALYSES = 24/u);
  assert.match(background, /MAX_INLINE_IMAGE_BYTES = 48 \* 1024/u);
  assert.match(background, /decodeInlineRaster/u);
  assert.match(ads, /NodeFilter\.SHOW_TEXT/u);
  assert.match(ads, /SEARCH_QUERY_KEYS/u);
  assert.match(ads, /isSearchResultsDocument/u);
  assert.doesNotMatch(ads, /MutationObserver/u);
  assert.doesNotMatch(ads, /querySelectorAll\?\.\("span,div"\)/u);
  assert.doesNotMatch(background, /cheeky|mimo|fravega|sm-a235|sm-s908/iu);
});
