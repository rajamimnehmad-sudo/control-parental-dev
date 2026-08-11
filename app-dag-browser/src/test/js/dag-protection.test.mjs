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
  let handlerBehaviorChanges = 0;
  const handlerBehaviorChangeListenerCounts = [];
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
      handlerBehaviorChanged() {
        handlerBehaviorChanges += 1;
        handlerBehaviorChangeListenerCounts.push([
          beforeRequest.listeners.length,
          headersReceived.listeners.length,
        ]);
        return Promise.resolve();
      },
      filterResponseData(requestId) {
        const filter = {
          writes: [],
          closed: false,
          ondata: null,
          onerror: null,
          onstop: null,
          write(data) {
            // Retain the view to simulate a consumer that reads it after write() returns.
            this.writes.push(data instanceof Uint8Array ? data : new Uint8Array(data));
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
  const sendRuntime = (message, sender) => runtimeMessages.listeners[0](message, sender);
  const startDocument = (
    tabId = 1,
    documentToken = "document_a1",
    url = "https://shop.example.test/",
  ) => {
    beforeRequest.listeners[0]({
      requestId: `document-${tabId}-${documentToken}`,
      type: "main_frame",
      tabId,
      frameId: 0,
      url,
    });
    sendRuntime(
      { type: "document-started", version: 2, documentToken },
      { url, tab: { id: tabId }, frameId: 0 },
    );
  };
  const loadDocument = (
    tabId = 1,
    documentToken = "document_a1",
    url = "https://shop.example.test/",
  ) => sendRuntime(
    { type: "document-loaded", version: 2, documentToken },
    { url, tab: { id: tabId }, frameId: 0 },
  );
  startDocument();
  loadDocument();
  postedNative.length = 0;
  return {
    before: beforeRequest.listeners[0],
    headers: headersReceived.listeners[0],
    filters,
    postedNative,
    get handlerBehaviorChanges() {
      return handlerBehaviorChanges;
    },
    handlerBehaviorChangeListenerCounts,
    startDocument,
    loadDocument,
    startFrame(
      frameId,
      documentToken,
      tabId = 1,
      url = "https://frame.example.test/",
    ) {
      return sendRuntime(
        { type: "document-started", version: 2, documentToken },
        { url, tab: { id: tabId }, frameId },
      );
    },
    retireFrame(
      frameId,
      documentToken,
      tabId = 1,
      url = "https://frame.example.test/",
    ) {
      return sendRuntime(
        { type: "document-retired", version: 2, documentToken },
        { url, tab: { id: tabId }, frameId },
      );
    },
    retireDocument(
      tabId = 1,
      documentToken = "document_a1",
      url = "https://shop.example.test/",
    ) {
      return sendRuntime(
        { type: "document-retired", version: 2, documentToken },
        { url, tab: { id: tabId }, frameId: 0 },
      );
    },
    decideInline(
      message,
      sender = {
        url: "https://search.example.test/?q=shoes",
        tab: { id: 1 },
        frameId: 0,
      },
    ) {
      return runtimeMessages.listeners[0](
        { documentToken: "document_a1", ...message },
        sender,
      );
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

test("background flushes Gecko memory cache after installing response filters", async () => {
  const harness = await createHarness();
  assert.equal(harness.handlerBehaviorChanges, 1);
  assert.deepEqual(harness.handlerBehaviorChangeListenerCounts, [[1, 1]]);
});

test("visible image hints reach the native queue with priority", async () => {
  const harness = await createHarness();
  const details = imageDetails("priority");
  harness.setImagePriority({
    type: "image-priority",
    version: 2,
    url: details.url,
    priority: "visible",
  });
  deliver(harness, details, Uint8Array.from([0xff, 0xd8, 1, 2, 3, 0xff, 0xd9]));
  const request = await waitFor(() => harness.postedNative.find((message) =>
    message.type === "media-bytes"), "prioritized native request");
  assert.equal(request.priority, "visible");
  assert.equal(request.tabId, 1);
  assert.match(request.documentKey, /^document_[a-f0-9]{1,16}$/u);
});

test("navigation retires old media work and isolates the next document", async () => {
  const harness = await createHarness();
  const oldDetails = imageDetails("old-document");
  const oldBytes = Uint8Array.from([0xff, 0xd8, 1, 2, 0xff, 0xd9]);
  deliver(harness, oldDetails, oldBytes);
  const oldRequest = await waitFor(() => harness.postedNative.find((message) =>
    message.type === "media-bytes"), "old document native request");

  harness.startDocument(1, "document_b2", "https://shop.example.test/next");
  harness.loadDocument(1, "document_b2", "https://shop.example.test/next");
  const oldFilter = harness.filters.get(oldDetails.requestId);
  await waitFor(() => oldFilter.closed, "retired document stream close");
  assert.notDeepEqual([...oldFilter.writes[0]], [...oldBytes]);

  const newDetails = imageDetails("new-document");
  const newBytes = Uint8Array.from([0xff, 0xd8, 3, 4, 0xff, 0xd9]);
  deliver(harness, newDetails, newBytes);
  const newRequest = await waitFor(() => harness.postedNative.find((message) =>
    message.type === "media-bytes" && message.candidateId !== oldRequest.candidateId),
  "new document native request");
  assert.notEqual(newRequest.documentKey, oldRequest.documentKey);
  harness.answer({
    type: "media-decision",
    version: 2,
    candidateId: newRequest.candidateId,
    action: "allow",
    reason: "model_allow",
  });
  const newFilter = harness.filters.get(newDetails.requestId);
  await waitFor(() => newFilter.closed, "new document stream close");
  assert.deepEqual([...newFilter.writes[0]], [...newBytes]);
});

test("viewport readiness belongs to the exact loaded document", async () => {
  const harness = await createHarness();
  harness.startDocument(1, "document_c3", "https://shop.example.test/current");
  harness.loadDocument(1, "document_c3", "https://shop.example.test/current");
  const ready = await waitFor(() => harness.postedNative.find((message) =>
    message.type === "viewport-images-ready" && message.documentToken === "document_c3"),
  "document viewport readiness");
  assert.equal(ready.tabId, 1);
  assert.match(ready.documentKey, /^document_[a-f0-9]{1,16}$/u);
});

test("queued visible raster overtakes background work", async () => {
  const harness = await createHarness();
  const first = imageDetails("priority-first");
  const second = imageDetails("priority-second");
  deliver(harness, first, Uint8Array.from([0xff, 0xd8, 1, 0xff, 0xd9]));
  deliver(harness, second, Uint8Array.from([0xff, 0xd8, 2, 0xff, 0xd9]));
  await waitFor(() => harness.postedNative.filter((message) =>
    message.type === "media-bytes").length === 2, "two occupied native slots");

  const background = imageDetails("priority-background");
  const promoted = imageDetails("priority-promoted");
  deliver(harness, background, Uint8Array.from([0xff, 0xd8, 3, 0xff, 0xd9]));
  deliver(harness, promoted, Uint8Array.from([0xff, 0xd8, 4, 0xff, 0xd9]));
  harness.setImagePriority({
    type: "image-priority",
    version: 2,
    url: promoted.url,
    priority: "visible",
  });
  await new Promise((resolvePromise) => setTimeout(resolvePromise, 20));

  const occupied = harness.postedNative.filter((message) => message.type === "media-bytes");
  harness.answer({
    type: "media-decision",
    version: 2,
    candidateId: occupied[0].candidateId,
    action: "allow",
    reason: "model_allow",
  });
  const promotedRequest = await waitFor(() => harness.postedNative.find((message, index) =>
    index >= 2 && message.sourceUrl === promoted.url), "promoted native request");
  assert.equal(promotedRequest.priority, "visible");
  assert.equal(harness.postedNative.some((message, index) =>
    index >= 2 && message.sourceUrl === background.url), false);
});

test("full bounded response burst reaches the native gate without placeholder overflow", async () => {
  const harness = await createHarness();
  const details = Array.from({ length: 128 }, (_, index) => imageDetails(`burst-${index}`));
  for (let index = 0; index < details.length; index += 1) {
    deliver(harness, details[index], Uint8Array.from([0xff, 0xd8, index, 0xff, 0xd9]));
  }

  for (let answered = 0; answered < details.length; answered += 1) {
    const request = await waitFor(() => harness.postedNative.filter((message) =>
      message.type === "media-bytes")[answered], `burst native request ${answered}`);
    harness.answer({
      type: "media-decision",
      version: 2,
      candidateId: request.candidateId,
      action: "allow",
      reason: "model_allow",
    });
  }
  await waitFor(() => details.every(({ requestId }) => harness.filters.get(requestId).closed),
    "bounded burst close");
  assert.equal(harness.postedNative.filter((message) => message.type === "media-bytes").length, 128);
  assert.equal(details.every(({ requestId }) =>
    harness.filters.get(requestId).writes[0][0] === 0xff), true);
});

const imageDetails = (requestId, url = `https://cdn.example.test/${requestId}.jpg`) => ({
  requestId,
  type: "image",
  tabId: 1,
  frameId: 0,
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
    version: 2,
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
    version: 2,
    candidateId: request.candidateId,
    action: "block",
    reason: "model_filter",
  });
  const filter = harness.filters.get(details.requestId);
  await waitFor(() => filter.closed, "blocked stream close");
  assert.deepEqual([...filter.writes[0].slice(0, 8)], [137, 80, 78, 71, 13, 10, 26, 10]);
  assert.notDeepEqual([...filter.writes[0]], [...original]);
});

test("unrecognized native raster bypass cannot release replacement pixels", async () => {
  const harness = await createHarness();
  const original = Uint8Array.from([137, 80, 78, 71, 13, 10, 26, 10, 1, 2]);
  const sanitized = Uint8Array.from([137, 80, 78, 71, 13, 10, 26, 10, 9, 8]);
  const first = imageDetails("sprite-a", "https://cdn.example.test/ui-strip.png");
  deliver(harness, first, original, "image/png");
  const request = await waitFor(() => harness.postedNative.find((message) =>
    message.type === "media-bytes"), "sprite native request");
  harness.answer({
    type: "media-decision",
    version: 2,
    candidateId: request.candidateId,
    action: "block",
    reason: "safe_ui_sprite",
    replacementBytesBase64: btoa(String.fromCharCode(...sanitized)),
  });
  await waitFor(() => harness.filters.get(first.requestId).closed, "sprite close");
  assert.notDeepEqual([...harness.filters.get(first.requestId).writes[0]], [...sanitized]);

  const second = imageDetails("sprite-b", "https://cdn.example.test/ui-strip.png?copy=1");
  deliver(harness, second, original, "image/png");
  const secondRequest = await waitFor(() => {
    const requests = harness.postedNative.filter((message) => message.type === "media-bytes");
    return requests.length === 2 ? requests.at(-1) : null;
  }, "second sprite native request");
  assert.notEqual(secondRequest.candidateId, request.candidateId);
  harness.answer({
    type: "media-decision",
    version: 2,
    candidateId: secondRequest.candidateId,
    action: "block",
    reason: "model_filter",
  });
  await waitFor(() => harness.filters.get(second.requestId).closed, "second sprite close");
  assert.equal(harness.postedNative.filter((message) => message.type === "media-bytes").length, 2);
});

test("bounded inline raster crosses the same native gate and fails closed", async () => {
  const harness = await createHarness();
  const original = Uint8Array.from([137, 80, 78, 71, 13, 10, 26, 10, 1, 2]);
  const allowed = harness.decideInline({
    type: "inline-raster-decision",
    version: 2,
    dataUrl: `data:image/png;base64,${btoa(String.fromCharCode(...original))}`,
  });
  const request = await waitFor(() => harness.postedNative.find((message) =>
    message.type === "media-bytes"), "inline native request");
  harness.answer({
    type: "media-decision",
    version: 2,
    candidateId: request.candidateId,
    action: "allow",
    reason: "model_allow",
  });
  assert.equal((await allowed).action, "allow");

  const vector = harness.decideInline({
    type: "inline-raster-decision",
    version: 2,
    dataUrl: "data:image/svg+xml;base64,PHN2Zy8+",
  });
  const vectorRequest = await waitFor(() => {
    const requests = harness.postedNative.filter((message) => message.type === "media-bytes");
    return requests.length === 2 ? requests.at(-1) : null;
  }, "inline vector native request");
  harness.answer({
    type: "media-decision",
    version: 2,
    candidateId: vectorRequest.candidateId,
    action: "allow",
    reason: "safe_ui_vector",
  });
  assert.equal((await vector).action, "allow");
  assert.equal((await harness.decideInline({
    type: "inline-raster-decision",
    version: 2,
    dataUrl: `data:image/png;base64,${"A".repeat(2_800_001)}`,
  })).action, "block");
  assert.equal(harness.postedNative.filter((message) => message.type === "media-bytes").length, 2);
});

test("bounded inline raster in a registered subframe crosses the same native gate", async () => {
  const harness = await createHarness();
  const token = "document_f6";
  const sender = {
    url: "https://frame.example.test/catalog",
    tab: { id: 1 },
    frameId: 6,
  };
  harness.startFrame(6, token);
  const original = Uint8Array.from([137, 80, 78, 71, 13, 10, 26, 10, 7]);
  const decision = harness.decideInline({
    type: "inline-raster-decision",
    version: 2,
    documentToken: token,
    dataUrl: `data:image/png;base64,${btoa(String.fromCharCode(...original))}`,
  }, sender);
  const request = await waitFor(() => harness.postedNative.find((message) =>
    message.type === "media-bytes"), "subframe native request");
  harness.answer({
    type: "media-decision",
    version: 2,
    candidateId: request.candidateId,
    action: "allow",
    reason: "model_allow",
  });
  assert.equal((await decision).action, "allow");

  harness.retireFrame(6, token);
  const retired = await harness.decideInline({
    type: "inline-raster-decision",
    version: 2,
    documentToken: token,
    dataUrl: `data:image/png;base64,${btoa(String.fromCharCode(...original))}`,
  }, sender);
  assert.equal(retired.action, "block");
  assert.equal(harness.postedNative.filter((message) => message.type === "media-bytes").length, 1);
});

test("diagnostic mode reports bounded aggregate drop reasons without URLs", async () => {
  const harness = await createHarness();
  harness.answer({
    type: "media-diagnostics-config",
    version: 2,
    enabled: true,
  });
  const result = await harness.decideInline({
    type: "inline-raster-decision",
    version: 2,
    dataUrl: `data:image/png;base64,${"A".repeat(2_800_001)}`,
  });
  assert.equal(result.action, "block");
  const summary = await waitFor(() => harness.postedNative.find((message) =>
    message.type === "media-diagnostic-summary"), "diagnostic summary");
  assert.equal(JSON.stringify(summary.events), JSON.stringify([{
    carrier: "inline",
    reason: "invalid_or_oversize",
    count: 1,
  }]));
  assert.equal(JSON.stringify(summary).includes("search.example.test"), false);
});

test("SVG URLs enter the native gate and only a validated passive vector is released", async () => {
  const harness = await createHarness();
  const details = imageDetails("icon", "https://cdn.example.test/heart.svg?v=2");
  const original = new TextEncoder().encode("<svg xmlns='http://www.w3.org/2000/svg'/>");
  deliver(harness, details, original, "image/svg+xml");
  const request = await waitFor(() => harness.postedNative.find((message) =>
    message.type === "media-bytes"), "SVG native request");
  harness.answer({
    type: "media-decision",
    version: 2,
    candidateId: request.candidateId,
    action: "allow",
    reason: "safe_ui_vector",
  });
  const filter = harness.filters.get(details.requestId);
  await waitFor(() => filter.closed, "SVG stream close");
  assert.deepEqual([...filter.writes[0]], [...original]);
});

test("vector MIME discovered on a data request still crosses the native gate", async () => {
  const harness = await createHarness();
  const details = {
    ...imageDetails("vector", "https://cdn.example.test/asset?id=2"),
    type: "xmlhttprequest",
  };
  const original = new TextEncoder().encode("<svg xmlns='http://www.w3.org/2000/svg'/>");
  deliver(harness, details, original, "image/svg+xml");
  const request = await waitFor(() => harness.postedNative.find((message) =>
    message.type === "media-bytes"), "vector MIME native request");
  harness.answer({
    type: "media-decision",
    version: 2,
    candidateId: request.candidateId,
    action: "allow",
    reason: "safe_ui_vector",
  });
  const filter = harness.filters.get(details.requestId);
  await waitFor(() => filter.closed, "vector stream close");
  assert.deepEqual([...filter.writes[0]], [...original]);
  assert.equal(harness.postedNative.filter((message) => message.type === "media-bytes").length, 1);
});

test("raster opened as a top-level page still crosses the same native gate", async () => {
  const harness = await createHarness();
  const details = {
    requestId: "top-level-raster",
    type: "main_frame",
    tabId: 1,
    frameId: 0,
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
    version: 2,
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
    tabId: 1,
    frameId: 0,
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
    version: 2,
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
    version: 2,
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
    version: 2,
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
  const transparentPlaceholder = Uint8Array.from(atob(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNgYAAAAAMAASsJTYQAAAAASUVORK5CYII="
  ), (character) => character.charCodeAt(0));
  assert.deepEqual([...filter.writes[0]], [...transparentPlaceholder]);
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
  assert.match(
    barrier,
    /IMAGE_RECONCILIATION_DELAYS_MS = \[100, 400, 1000, 2000, 4000, 6000, 8000, 12000\]/u,
  );
  assert.match(barrier, /const unsettledImages = new Set\(\)/u);
  assert.match(barrier, /for \(const image of unsettledImages\)/u);
  assert.match(barrier, /priorityObserver\?\.unobserve\(image\)/u);
  assert.match(barrier, /reconcileCompleteImages\(index === IMAGE_RECONCILIATION_DELAYS_MS\.length - 1\)/u);
  assert.doesNotMatch(barrier, /const reconcileCompleteImages = \(\) => \{\s*for \(const image of document\.images\)/u);
  assert.match(barrier, /!\(image\.naturalWidth === 1 && image\.naturalHeight === 1\)/u);
  assert.match(barrier, /attributeFilter: \["src", "srcset", "sizes"\]/u);
  assert.match(
    barrier,
    /priorityObserver\?\.unobserve\(record\.target\);\s*resetImage\(record\.target\);\s*observeImage\(record\.target\)/u,
  );
  assert.match(barrier, /imageSource\(image\) === source/u);
  assert.match(barrier, /image\.hasAttribute\(STABLE_IMAGE_ATTRIBUTE\)/u);
  assert.match(barrier, /hasInlineImageSource\(record\.target\)/u);
  assert.match(barrier, /MAX_INLINE_DECISIONS = 64/u);
  assert.doesNotMatch(barrier, /MAX_INLINE_IMAGES_PER_DOCUMENT/u);
  assert.match(barrier, /inlineImageIsBounded/u);
  assert.match(barrier, /inline-raster-decision/u);
  assert.match(barrier, /documentToken,/u);
  assert.match(barrier, /priority: immediateImagePriority\(image\)/u);
  assert.match(barrier, /releaseRemovedImages/u);
  assert.match(barrier, /pendingImages\.get\(image\) !== request/u);
  assert.doesNotMatch(barrier, /MAX_INLINE_(?:NATURAL|RENDERED)_EDGE/u);
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
  assert.match(background, /MAX_ACTIVE_STREAMS = 128/u);
  assert.match(background, /MAX_QUEUED_ANALYSES = 144/u);
  assert.match(background, /MAX_CACHED_REPLACEMENT_BYTES = 2 \* 1024 \* 1024/u);
  assert.match(background, /cachedReplacementBytes/u);
  assert.match(background, /const cachedDecision/u);
  assert.match(background, /takeNextAnalysis/u);
  assert.match(background, /MAX_INLINE_IMAGE_BYTES = MAX_IMAGE_BYTES/u);
  assert.match(background, /capturedBytes \+ bytes\.byteLength > MAX_CAPTURED_BYTES/u);
  assert.match(background, /decodeInlineRaster/u);
  assert.match(ads, /NodeFilter\.SHOW_TEXT/u);
  assert.match(ads, /SEARCH_QUERY_KEYS/u);
  assert.match(ads, /isSearchResultsDocument/u);
  assert.match(ads, /observeDynamicSponsoredResults/u);
  assert.match(ads, /new MutationObserver/u);
  assert.match(ads, /childList: true/u);
  assert.match(ads, /characterData: true/u);
  assert.doesNotMatch(ads, /attributes: true/u);
  assert.doesNotMatch(ads, /querySelectorAll\?\.\("span,div"\)/u);
  assert.doesNotMatch(background, /cheeky|mimo|fravega|sm-a235|sm-s908/iu);
});

test("late sponsored search result is hidden before the next paint without an attribute observer", async () => {
  let mutationCallback = null;
  let observerOptions = null;
  class Element {}
  class HTMLElement extends Element {
    constructor() {
      super();
      this.attributes = new Map();
    }
    setAttribute(name, value) {
      this.attributes.set(name, value);
    }
  }
  class MutationObserver {
    constructor(callback) {
      mutationCallback = callback;
    }
    observe(_target, options) {
      observerOptions = options;
    }
  }
  const result = new HTMLElement();
  const label = new HTMLElement();
  label.closest = () => result;
  const sponsoredText = { nodeValue: "Patrocinado", parentElement: label };
  const document = {
    readyState: "loading",
    documentElement: null,
    addEventListener() {},
  };
  vm.runInNewContext(await readAsset("ads.js"), {
    CustomEvent,
    Element,
    HTMLElement,
    MutationObserver,
    NodeFilter: { SHOW_TEXT: 4 },
    URLSearchParams,
    dispatchEvent() {},
    document,
    location: {
      hostname: "search.example.test",
      pathname: "/search",
      search: "?q=zapatos",
    },
  }, { filename: "ads.js" });

  assert.ok(mutationCallback);
  assert.equal(observerOptions.subtree, true);
  assert.equal(observerOptions.childList, true);
  assert.equal(observerOptions.characterData, true);
  assert.equal("attributes" in observerOptions, false);
  mutationCallback([{ type: "childList", addedNodes: [sponsoredText] }]);
  assert.equal(result.attributes.get("data-glosh-dag-sponsored-result"), "true");
});

test("scheduler guard yields sustained signals without changing normal messages", async () => {
  let now = 0;
  let loadListener = null;
  let timerSequence = 0;
  const timers = [];
  const received = [];
  class MessagePort {
    postMessage(message, transfer) {
      received.push({ message, transfer });
    }
  }
  const context = {
    addEventListener(type, listener) {
      if (type === "load") loadListener = listener;
    },
    document: { readyState: "loading" },
    MessagePort,
    Number,
    performance: { now: () => now },
    Reflect,
    clearTimeout(timerId) {
      const index = timers.findIndex(({ id }) => id === timerId);
      if (index >= 0) timers.splice(index, 1);
    },
    setTimeout(callback, delay) {
      timerSequence += 1;
      timers.push({ callback, dueAt: now + delay, id: timerSequence, sequence: timerSequence });
      return timerSequence;
    },
    WeakMap,
  };
  vm.runInNewContext(await readAsset("runaway-scheduler-guard.js"), context, {
    filename: "runaway-scheduler-guard.js",
  });
  const port = new MessagePort();

  port.postMessage({ type: "normal" });
  port.postMessage(7, ["transfer-token"]);
  for (let signal = 0; signal < 20; signal += 1) {
    now = signal * 100;
    port.postMessage(100 + signal);
  }
  assert.equal(timers.length, 0);
  assert.ok(loadListener);
  loadListener();

  for (let signal = 0; signal < 11; signal += 1) {
    now = 2_000 + signal * 100;
    port.postMessage(signal);
  }
  assert.equal(timers.length, 0);

  now = 3_100;
  port.postMessage(11);
  now = 3_200;
  port.postMessage(12);
  port.postMessage({ type: "still-normal" });
  assert.ok(timers.length > 0);
  assert.deepEqual(received.slice(-1), [{ message: { type: "still-normal" }, transfer: undefined }]);

  while (timers.length > 0) {
    timers.sort((left, right) => left.dueAt - right.dueAt || left.sequence - right.sequence);
    const timer = timers.shift();
    now = timer.dueAt;
    timer.callback();
  }
  assert.deepEqual(received.map(({ message }) => message), [
    { type: "normal" },
    7,
    100,
    101,
    102,
    103,
    104,
    105,
    106,
    107,
    108,
    109,
    110,
    111,
    112,
    113,
    114,
    115,
    116,
    117,
    118,
    119,
    0,
    1,
    2,
    3,
    4,
    5,
    6,
    7,
    8,
    9,
    10,
    { type: "still-normal" },
    11,
    12,
  ]);

  for (let signal = 100; signal <= 164; signal += 1) port.postMessage(signal);
  assert.deepEqual(received.slice(-65).map(({ message }) => message),
    Array.from({ length: 65 }, (_, index) => 100 + index));
  assert.equal(timers.length, 0);
});

test("extension manifest installs the bounded scheduler guard in the main world", async () => {
  const manifest = JSON.parse(await readAsset("manifest.json"));
  const schedulerScript = manifest.content_scripts.find((script) =>
    script.js?.includes("runaway-scheduler-guard.js"));
  assert.ok(schedulerScript);
  assert.equal(schedulerScript.world, "MAIN");
  assert.equal(schedulerScript.all_frames, false);

  const protectionScript = manifest.content_scripts.find((script) =>
    script.js?.includes("barrier.js"));
  assert.ok(protectionScript);
  assert.ok(protectionScript.js.indexOf("barrier.js") < protectionScript.js.indexOf("ads.js"));
});
