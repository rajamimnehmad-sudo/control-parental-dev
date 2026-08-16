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
const defaultVideoGrantIdentity = Object.freeze({
  videoId: "video_0123456789abcdef",
  revision: 1,
  viewportEpoch: 1,
  frameSequence: 1,
  token: "abcdef0123456789abcdef0123456789",
});

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

const createHarness = async ({
  captureIdleTimeoutMs = null,
  insertCss = null,
  removeCss = null,
  queryTabs = null,
  storageState = {},
} = {}) => {
  const beforeRequest = eventChannel();
  const headersReceived = eventChannel();
  const nativeMessages = eventChannel();
  const nativeDisconnects = eventChannel();
  const runtimeMessages = eventChannel();
  const postedNative = [];
  const insertedCss = [];
  const removedCss = [];
  const filters = new Map();
  let handlerBehaviorChanges = 0;
  const handlerBehaviorChangeListenerCounts = [];
  const nativePort = {
    onMessage: nativeMessages,
    onDisconnect: nativeDisconnects,
    postMessage(message) {
      postedNative.push(message);
      if (message.type === "video-lab-grant-active") {
        queueMicrotask(() => {
          for (const listener of nativeMessages.listeners) {
            listener({ ...message, type: "video-lab-grant-active-ack" });
          }
        });
      }
    },
  };
  const browser = {
    storage: {
      local: {
        get(key) {
          return Promise.resolve({ [key]: storageState[key] });
        },
        set(values) {
          Object.assign(storageState, structuredClone(values));
          return Promise.resolve();
        },
      },
    },
    tabs: {
      query(details) {
        return queryTabs === null
          ? Promise.resolve([{ id: 1 }])
          : Promise.resolve(queryTabs(details));
      },
      insertCSS(tabId, details) {
        insertedCss.push({ tabId, details });
        return insertCss === null ? Promise.resolve() : Promise.resolve(insertCss(tabId, details));
      },
      removeCSS(tabId, details) {
        removedCss.push({ tabId, details });
        return removeCss === null ? Promise.resolve() : Promise.resolve(removeCss(tabId, details));
      },
    },
    runtime: {
      onMessage: runtimeMessages,
      connectNative() {
        return nativePort;
      },
      getURL(path) {
        return `moz-extension://dag-test/${path}`;
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
          onstart: null,
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
  const contextSetTimeout = (callback, delay, ...args) => {
    const effectiveDelay = delay === 5_000 && captureIdleTimeoutMs !== null
        ? captureIdleTimeoutMs
        : delay;
    return setTimeout(callback, effectiveDelay, ...args);
  };
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
    queueMicrotask,
    setTimeout: contextSetTimeout,
  }, { filename: "background.js" });
  const sendRuntime = (message, sender) => runtimeMessages.listeners[0]({
    ...defaultVideoGrantIdentity,
    ...message,
  }, sender);
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
    insertedCss,
    removedCss,
    storageState,
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
    setElementState(
      message,
      sender = { url: "https://shop.example.test/", tab: { id: 1 }, frameId: 0 },
    ) {
      return runtimeMessages.listeners[0]({
        type: "media-element-states",
        version: 2,
        events: [message],
      }, sender);
    },
    sendRuntime,
    answer(message) {
      for (const listener of nativeMessages.listeners) {
        listener({ ...defaultVideoGrantIdentity, ...message });
      }
    },
    acknowledgeVideoGrantActive() {
      const active = postedNative.findLast((message) => message.type === "video-lab-grant-active");
      if (active === undefined) return false;
      for (const listener of nativeMessages.listeners) {
        listener({ ...active, type: "video-lab-grant-active-ack" });
      }
      return true;
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
  assert.equal(oldFilter.writes.length, 0);

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

test("rapid reload retires every unopened stream without poisoning the next document", async () => {
  const harness = await createHarness();
  const oldDetails = Array.from({ length: 96 }, (_, index) =>
    imageDetails(`lazy-old-${index}`));
  for (const details of oldDetails) harness.before(details);

  await new Promise((resolvePromise) => setTimeout(resolvePromise, 25));
  assert.equal(oldDetails.every(({ requestId }) => !harness.filters.get(requestId).closed), true);

  harness.startDocument(1, "document_b3", "https://shop.example.test/reloaded");
  harness.loadDocument(1, "document_b3", "https://shop.example.test/reloaded");
  assert.equal(oldDetails.every(({ requestId }) => harness.filters.get(requestId).closed), true);
  assert.equal(oldDetails.every(({ requestId }) =>
    harness.filters.get(requestId).writes.length === 0), true);

  const fresh = imageDetails("lazy-fresh");
  const original = Uint8Array.from([0xff, 0xd8, 5, 6, 0xff, 0xd9]);
  deliver(harness, fresh, original);
  const request = await waitFor(() => harness.postedNative.find((message) =>
    message.type === "media-bytes" && message.requestId === fresh.requestId),
  "fresh document native request");
  harness.answer({
    type: "media-decision",
    version: 2,
    candidateId: request.candidateId,
    action: "allow",
    reason: "model_allow",
  });
  await waitFor(() => harness.filters.get(fresh.requestId).closed, "fresh stream close");
  assert.deepEqual([...harness.filters.get(fresh.requestId).writes[0]], [...original]);
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

test("capture timeout measures network inactivity instead of total transfer time", async () => {
  const harness = await createHarness({ captureIdleTimeoutMs: 40 });
  const details = imageDetails("progressive-transfer");
  harness.before(details);
  const filter = harness.filters.get(details.requestId);
  assert.ok(filter);
  filter.onstart();
  filter.ondata({ data: Uint8Array.from([0xff, 0xd8, 1]).buffer });
  await new Promise((resolvePromise) => setTimeout(resolvePromise, 25));
  filter.ondata({ data: Uint8Array.from([2, 3, 0xff, 0xd9]).buffer });
  await new Promise((resolvePromise) => setTimeout(resolvePromise, 25));
  assert.equal(filter.closed, false);
  filter.onstop();
  const request = await waitFor(() => harness.postedNative.find((message) =>
    message.type === "media-bytes"), "progressive native request");
  harness.answer({
    type: "media-decision",
    version: 2,
    candidateId: request.candidateId,
    action: "allow",
    reason: "model_allow",
  });
  await waitFor(() => filter.closed, "progressive stream close");
  assert.deepEqual([...filter.writes[0]], [0xff, 0xd8, 1, 2, 3, 0xff, 0xd9]);
});

test("lazy response may remain unopened until Gecko starts its bounded stream", async () => {
  const harness = await createHarness({ captureIdleTimeoutMs: 20 });
  const details = imageDetails("lazy-unopened");
  harness.before(details);
  const filter = harness.filters.get(details.requestId);
  await new Promise((resolvePromise) => setTimeout(resolvePromise, 45));
  assert.equal(filter.closed, false);
  harness.startDocument(1, "document_b4", "https://shop.example.test/after-lazy");
  await waitFor(() => filter.closed, "unopened stream retirement");
  assert.equal(filter.writes.length, 0);
});

test("cancelled empty response closes without manufacturing a placeholder and clean retry succeeds", async () => {
  const harness = await createHarness();
  harness.answer({ type: "media-diagnostics-config", version: 2, enabled: true });
  const sourceUrl = "https://cdn.example.test/retry.jpg";
  const cancelled = imageDetails("cancelled-empty", sourceUrl);
  harness.before(cancelled);
  const cancelledFilter = harness.filters.get(cancelled.requestId);
  cancelledFilter.onstop();
  assert.equal(cancelledFilter.closed, true);
  assert.equal(cancelledFilter.writes.length, 0);

  const summary = await waitFor(() => harness.postedNative.find((message) =>
    message.type === "media-diagnostic-summary"), "cancelled response diagnostic");
  assert.equal(summary.events[0].reason, "cancelled_before_headers");

  const retry = imageDetails("clean-retry", sourceUrl);
  const original = Uint8Array.from([0xff, 0xd8, 7, 8, 0xff, 0xd9]);
  deliver(harness, retry, original);
  const request = await waitFor(() => harness.postedNative.find((message) =>
    message.type === "media-bytes" && message.requestId === "clean-retry"), "retry native request");
  harness.answer({
    type: "media-decision",
    version: 2,
    candidateId: request.candidateId,
    action: "allow",
    reason: "model_allow",
  });
  const retryFilter = harness.filters.get(retry.requestId);
  await waitFor(() => retryFilter.closed, "retry close");
  assert.deepEqual([...retryFilter.writes[0]], [...original]);
});

test("HTTP 204 image response closes empty and is diagnosed as no content", async () => {
  const harness = await createHarness();
  harness.answer({ type: "media-diagnostics-config", version: 2, enabled: true });
  const details = imageDetails("no-content", "https://tracking.example.test/pixel");
  harness.before(details);
  harness.headers({
    ...details,
    statusCode: 204,
    fromCache: false,
    responseHeaders: [{ name: "Content-Type", value: "image/gif" }],
  });
  const filter = harness.filters.get(details.requestId);
  filter.onstop();
  assert.equal(filter.closed, true);
  assert.equal(filter.writes.length, 0);
  const summary = await waitFor(() => harness.postedNative.find((message) =>
    message.type === "media-diagnostic-summary"), "no-content diagnostic");
  assert.equal(summary.events[0].reason, "no_content_response");
  assert.equal(summary.events[0].statusCode, 204);
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
  harness.headers({
    ...details,
    statusCode: 200,
    fromCache: false,
    responseHeaders: [{ name: "Content-Type", value: mime }],
  });
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
  assert.equal(request.statusCode, 200);
  assert.equal(request.mimeType, "image/jpeg");
  assert.equal(request.fromCache, false);
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

test("diagnostic mode reports bounded drops with exact resource correlation", async () => {
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
  assert.equal(summary.events.length, 1);
  assert.equal(summary.events[0].carrier, "inline");
  assert.equal(summary.events[0].reason, "invalid_or_oversize");
  assert.equal(summary.events[0].count, 1);
  assert.equal(summary.events[0].pageUrl, "https://search.example.test/?q=shoes");
  assert.equal(summary.events[0].resourceType, "image");
  assert.equal(summary.events[0].activeStreams, 0);
});

test("diagnostic mode correlates HTTP response and terminal DOM image state", async () => {
  const harness = await createHarness();
  harness.answer({ type: "media-diagnostics-config", version: 2, enabled: true });
  const details = imageDetails("correlated", "https://cdn.example.test/photo.jpg?w=320&token=secret");
  harness.before(details);
  harness.headers({
    ...details,
    statusCode: 200,
    fromCache: false,
    responseHeaders: [{ name: "Content-Type", value: "image/webp" }],
  });
  const filter = harness.filters.get(details.requestId);
  filter.ondata({ data: Uint8Array.from([0x52, 0x49, 0x46, 0x46]).buffer });
  filter.onstop();
  const request = await waitFor(() => harness.postedNative.find((message) =>
    message.type === "media-bytes"), "correlated native request");
  harness.answer({
    type: "media-decision",
    version: 2,
    candidateId: request.candidateId,
    action: "allow",
    reason: "model_allow",
  });
  harness.setElementState({
    url: details.url,
    visualState: "shown",
    naturalWidth: 320,
    naturalHeight: 240,
    renderedWidth: 160,
    renderedHeight: 120,
  });
  const summary = await waitFor(() => harness.postedNative.find((message) =>
    message.type === "media-diagnostic-summary"), "correlated diagnostic summary");
  assert.equal(summary.resources[0].requestId, "correlated");
  assert.equal(summary.resources[0].statusCode, 200);
  assert.equal(summary.resources[0].mimeType, "image/webp");
  assert.equal(summary.elements[0].sourceUrl, details.url);
  assert.equal(summary.elements[0].visualState, "shown");
  assert.equal(summary.elements[0].naturalWidth, 320);
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
  harness.answer({ type: "media-diagnostics-config", version: 2, enabled: true });
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
    imageWidth: 320,
    imageHeight: 240,
  });
  await waitFor(() => harness.filters.get(first.requestId).closed, "first close");

  const second = imageDetails("cache-b");
  deliver(harness, second, original);
  await waitFor(() => harness.filters.get(second.requestId).closed, "cached close");
  assert.equal(harness.postedNative.filter((message) => message.type === "media-bytes").length, 1);
  assert.deepEqual([...harness.filters.get(second.requestId).writes[0]], [...original]);
  const summary = await waitFor(() => harness.postedNative.find((message) =>
    message.type === "media-diagnostic-summary" && message.decisions?.length > 0),
  "cached decision diagnostic");
  assert.equal(summary.decisions[0].requestId, second.requestId);
  assert.equal(summary.decisions[0].action, "allow");
  assert.equal(summary.decisions[0].reason, "model_allow");
  assert.equal(summary.decisions[0].decisionCacheHit, true);
  assert.equal(summary.decisions[0].width, 320);
  assert.equal(summary.decisions[0].height, 240);
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

test("video laboratory opens only covered video bytes for the exact diagnostic document", async () => {
  const harness = await createHarness();
  const sender = {
    url: "https://shop.example.test/",
    tab: { id: 1 },
    frameId: 0,
  };
  const token = "0123456789abcdef0123456789abcdef";
  const reveal = {
    type: "video-lab-reveal-style",
    version: 2,
    documentToken: "document_a1",
    token,
  };
  const media = {
    type: "media",
    tabId: 1,
    frameId: 0,
    documentUrl: sender.url,
    url: "https://media.example.test/a.mp4",
  };
  const videoHeaders = {
    type: "xmlhttprequest",
    tabId: 1,
    frameId: 0,
    documentUrl: sender.url,
    requestId: "video-lab-header",
    url: "https://media.example.test/a",
    responseHeaders: [{ name: "content-type", value: "video/mp4" }],
  };
  const audioHeaders = {
    ...videoHeaders,
    requestId: "audio-lab-header",
    responseHeaders: [{ name: "content-type", value: "audio/mp4" }],
  };

  assert.equal(harness.before(media).cancel, true);
  harness.answer({ type: "video-lab-config", version: 2, enabled: true });
  assert.equal(harness.before(media).cancel, true);
  assert.equal((await harness.sendRuntime(reveal, sender)).inserted, true);
  assert.equal(harness.insertedCss.length, 1);
  assert.equal(harness.insertedCss[0].details.cssOrigin, "user");
  assert.match(harness.insertedCss[0].details.code, new RegExp(token, "u"));
  assert.equal(Object.keys(harness.before(media)).length, 0);
  assert.equal(harness.before({ ...media, frameId: 2 }).cancel, true);
  assert.equal(Object.keys(harness.headers(videoHeaders)).length, 0);
  assert.equal(harness.headers({ ...videoHeaders, frameId: 2 }).cancel, true);
  assert.equal(harness.headers(audioHeaders).cancel, true);
  assert.equal(harness.before({ type: "object", url: "https://media.example.test/a" }).cancel, true);
  assert.equal((await harness.sendRuntime({
    type: "video-lab-conceal-style",
    version: 2,
    documentToken: "document_a1",
    token,
  }, sender)).removed, true);
  assert.equal(harness.removedCss.length, 1);
  assert.equal(harness.before(media).cancel, true);
  harness.answer({ type: "video-lab-config", version: 2, enabled: false });
  assert.equal(harness.before(media).cancel, true);
});

test("video laboratory reveal is exact, HTTPS-only and fail-closed", async () => {
  const harness = await createHarness();
  const sender = {
    url: "https://shop.example.test/",
    tab: { id: 1 },
    frameId: 0,
  };
  const token = "0123456789abcdef0123456789abcdef";
  const reveal = {
    type: "video-lab-reveal-style",
    version: 2,
    documentToken: "document_a1",
    token,
  };

  assert.equal((await harness.sendRuntime(reveal, sender)).inserted, false);
  harness.answer({ type: "video-lab-config", version: 2, enabled: true });
  assert.equal((await harness.sendRuntime(reveal, sender)).inserted, true);

  assert.equal((await harness.sendRuntime({
    type: "video-lab-conceal-style",
    version: 2,
    documentToken: "document_a1",
    token,
  }, sender)).removed, true);

  assert.equal((await harness.sendRuntime({ ...reveal, token: "too_short" }, sender)).inserted, false);
  assert.equal((await harness.sendRuntime(reveal, { ...sender, frameId: 2 })).inserted, false);
  assert.equal((await harness.sendRuntime(reveal, { ...sender, url: "http://shop.example.test/" })).inserted, false);
  assert.equal((await harness.sendRuntime({
    ...reveal,
    documentToken: "document_stale",
  }, sender)).inserted, false);
  assert.equal((await harness.sendRuntime(reveal, {
    ...sender,
    url: "https://other.example.test/",
  })).inserted, false);

  harness.startDocument(1, "document_c3", "https://fixture.example.test/");
  const fixtureSender = {
    ...sender,
    url: "https://fixture.example.test/",
  };
  assert.equal((await harness.sendRuntime({
    ...reveal,
    documentToken: "document_c3",
  }, fixtureSender)).inserted, true);
  assert.equal(harness.insertedCss.length, 2, "internal fixture uses the same user-origin grant");
  assert.equal(harness.insertedCss.at(-1).details.cssOrigin, "user");
  assert.equal((await harness.sendRuntime({
    type: "video-lab-conceal-style",
    version: 2,
    documentToken: "document_c3",
    token,
  }, fixtureSender)).removed, true);
  assert.equal(harness.removedCss.length, 2, "fixture revocation removes the privileged CSS");
});

test("video laboratory navigation revokes its user-origin grant before new media", async () => {
  const harness = await createHarness();
  const sender = {
    url: "https://shop.example.test/",
    tab: { id: 1 },
    frameId: 0,
  };
  const token = "abcdef0123456789abcdef0123456789";
  harness.answer({ type: "video-lab-config", version: 2, enabled: true });
  assert.equal((await harness.sendRuntime({
    type: "video-lab-reveal-style",
    version: 2,
    documentToken: "document_a1",
    token,
  }, sender)).inserted, true);

  harness.startDocument(1, "document_b2", "https://shop.example.test/next");
  await waitFor(() => harness.removedCss.length === 1, "video grant removal");
  assert.equal(harness.before({
    type: "media",
    tabId: 1,
    frameId: 0,
    documentUrl: "https://shop.example.test/next",
    url: "https://media.example.test/next.mp4",
  }).cancel, true);
});

test("native video close denies media until its exact CSS revocation is acknowledged", async () => {
  let resolveRemoval = null;
  const harness = await createHarness({
    removeCss() {
      return new Promise((resolve) => {
        resolveRemoval = resolve;
      });
    },
  });
  const sender = {
    url: "https://shop.example.test/",
    tab: { id: 1 },
    frameId: 0,
  };
  const token = "abcdef0123456789abcdef0123456789";
  const closeNonce = "0123456789abcdef0123456789abcdef";
  const media = {
    type: "media",
    tabId: 1,
    frameId: 0,
    documentUrl: sender.url,
    url: "https://media.example.test/a.mp4",
  };
  harness.answer({ type: "video-lab-config", version: 2, enabled: true });
  assert.equal((await harness.sendRuntime({
    type: "video-lab-reveal-style",
    version: 2,
    documentToken: "document_a1",
    token,
  }, sender)).inserted, true);
  assert.equal(Object.keys(harness.before(media)).length, 0);

  harness.answer({
    type: "video-lab-close",
    version: 2,
    tabId: 1,
    documentToken: "document_a1",
    closeNonce,
  });
  await waitFor(() => harness.removedCss.length === 1, "closing CSS removal starts");
  assert.equal(harness.before(media).cancel, true, "closing synchronously revokes media authority");
  assert.equal((await harness.sendRuntime({
    type: "video-lab-reveal-style",
    version: 2,
    documentToken: "document_a1",
    token: "fedcba9876543210fedcba9876543210",
  }, sender)).inserted, false, "a closing grant cannot be replaced");
  assert.equal(harness.postedNative.some((message) => message.type === "video-lab-revoked"), false);

  resolveRemoval();
  const acknowledgement = await waitFor(() => harness.postedNative.find((message) =>
    message.type === "video-lab-revoked"), "exact native close acknowledgement");
  assert.equal(acknowledgement.version, 2);
  assert.equal(acknowledgement.tabId, 1);
  assert.equal(acknowledgement.documentToken, "document_a1");
  assert.equal(acknowledgement.closeNonce, closeNonce);
});

test("unknown native video close never acknowledges after background state loss", async () => {
  const harness = await createHarness();
  const closeNonce = "0123456789abcdef0123456789abcdef";
  harness.answer({
    type: "video-lab-close",
    version: 2,
    tabId: 1,
    documentToken: "document_a1",
    closeNonce,
  });
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(harness.postedNative.some((message) =>
    message.type === "video-lab-revoked" && message.closeNonce === closeNonce), false);
  assert.equal(harness.before({
    type: "media",
    tabId: 1,
    frameId: 0,
    documentUrl: "https://shop.example.test/",
    url: "https://media.example.test/a.mp4",
  }).cancel, true, "unknown state remains fail-closed");
});

test("background restart durably revokes and acknowledges only the exact video frame grant", async () => {
  const storageState = {};
  const sender = {
    url: "https://shop.example.test/",
    tab: { id: 1 },
    frameId: 0,
  };
  const first = await createHarness({ storageState });
  first.answer({ type: "video-lab-config", version: 2, enabled: true });
  assert.equal((await first.sendRuntime({
    type: "video-lab-reveal-style",
    version: 2,
    documentToken: "document_a1",
    ...defaultVideoGrantIdentity,
  }, sender)).inserted, true);

  const restarted = await createHarness({ storageState });
  await waitFor(() => restarted.removedCss.length === 1, "durable CSS recovery removal");
  const closeNonce = "0123456789abcdef0123456789abcdef";
  restarted.answer({
    type: "video-lab-close",
    version: 2,
    tabId: 1,
    documentToken: "document_a1",
    closeNonce,
    ...defaultVideoGrantIdentity,
    viewportEpoch: 2,
  });
  await new Promise((resolve) => setTimeout(resolve, 120));
  assert.equal(restarted.postedNative.some((message) =>
    message.type === "video-lab-revoked" && message.closeNonce === closeNonce), false,
  "a mismatched epoch cannot consume durable proof");

  restarted.answer({
    type: "video-lab-close",
    version: 2,
    tabId: 1,
    documentToken: "document_a1",
    closeNonce,
    ...defaultVideoGrantIdentity,
  });
  const acknowledgement = await waitFor(() => restarted.postedNative.find((message) =>
    message.type === "video-lab-revoked" && message.closeNonce === closeNonce),
  "exact durable close acknowledgement");
  assert.equal(acknowledgement.videoId, defaultVideoGrantIdentity.videoId);
  assert.equal(acknowledgement.revision, defaultVideoGrantIdentity.revision);
  assert.equal(acknowledgement.viewportEpoch, defaultVideoGrantIdentity.viewportEpoch);
  assert.equal(acknowledgement.frameSequence, defaultVideoGrantIdentity.frameSequence);
  assert.equal(acknowledgement.token, defaultVideoGrantIdentity.token);
});

test("a confirmed closed tab retires its durable grant after CSS removal becomes impossible", async () => {
  const storedGrant = {
    tabId: 7,
    documentToken: "document_a1",
    ...defaultVideoGrantIdentity,
    state: "active",
  };
  const storageState = { videoLabRevocationJournalV1: [storedGrant] };
  const harness = await createHarness({
    storageState,
    removeCss() {
      return Promise.reject(new Error("Invalid tab ID"));
    },
    queryTabs() {
      return [];
    },
  });
  await waitFor(
    () => storageState.videoLabRevocationJournalV1?.[0]?.state === "retired",
    "closed-tab durable retirement",
  );

  const closeNonce = "0123456789abcdef0123456789abcdef";
  harness.answer({
    type: "video-lab-close",
    version: 2,
    tabId: 7,
    documentToken: "document_a1",
    closeNonce,
    ...defaultVideoGrantIdentity,
  });
  await waitFor(() => harness.postedNative.find((message) =>
    message.type === "video-lab-revoked" && message.closeNonce === closeNonce),
  "close acknowledgement from confirmed tab retirement");
});

test("an ambiguous tab lookup never retires a durable video grant", async () => {
  const storedGrant = {
    tabId: 7,
    documentToken: "document_a1",
    ...defaultVideoGrantIdentity,
    state: "active",
  };
  const storageState = { videoLabRevocationJournalV1: [storedGrant] };
  const harness = await createHarness({
    storageState,
    removeCss() {
      return Promise.reject(new Error("removeCSS unavailable"));
    },
    queryTabs() {
      return Promise.reject(new Error("tabs unavailable"));
    },
  });
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(storageState.videoLabRevocationJournalV1[0].state, "active");

  harness.answer({
    type: "video-lab-close",
    version: 2,
    tabId: 7,
    documentToken: "document_a1",
    closeNonce: "0123456789abcdef0123456789abcdef",
    ...defaultVideoGrantIdentity,
  });
  await new Promise((resolve) => setTimeout(resolve, 120));
  assert.equal(harness.postedNative.some((message) => message.type === "video-lab-revoked"), false);
});

test("an authenticated replacement document retires the prior document grant on a reused tab id", async () => {
  const storedGrant = {
    tabId: 1,
    documentToken: "document_b2",
    ...defaultVideoGrantIdentity,
    state: "active",
  };
  const storageState = { videoLabRevocationJournalV1: [storedGrant] };
  const harness = await createHarness({
    storageState,
    removeCss() {
      return Promise.reject(new Error("old document unavailable"));
    },
    queryTabs() {
      return [{ id: 1 }];
    },
  });
  harness.answer({ type: "video-lab-config", version: 2, enabled: true });
  const sender = { url: "https://shop.example.test/", tab: { id: 1 }, frameId: 0 };
  const result = await harness.sendRuntime({
    type: "video-lab-reveal-style",
    version: 2,
    documentToken: "document_a1",
    token: "fedcba9876543210fedcba9876543210",
  }, sender);
  assert.equal(result.inserted, true);
  assert.equal(storageState.videoLabRevocationJournalV1[0].documentToken, "document_a1");
  assert.equal(storageState.videoLabRevocationJournalV1[0].state, "active");
});

test("the same current document cannot retire its ambiguous durable grant", async () => {
  const storedGrant = {
    tabId: 1,
    documentToken: "document_a1",
    ...defaultVideoGrantIdentity,
    state: "active",
  };
  const storageState = { videoLabRevocationJournalV1: [storedGrant] };
  const harness = await createHarness({
    storageState,
    removeCss() {
      return Promise.reject(new Error("removeCSS unavailable"));
    },
    queryTabs() {
      return [{ id: 1 }];
    },
  });
  harness.answer({ type: "video-lab-config", version: 2, enabled: true });
  const sender = { url: "https://shop.example.test/", tab: { id: 1 }, frameId: 0 };
  const result = await harness.sendRuntime({
    type: "video-lab-reveal-style",
    version: 2,
    documentToken: "document_a1",
    token: "fedcba9876543210fedcba9876543210",
  }, sender);
  assert.equal(result.inserted, false);
  assert.equal(result.reason, "journal_unavailable");
  assert.equal(storageState.videoLabRevocationJournalV1[0].state, "active");
});

test("a stale background context refreshes durable proof written by a newer context", async () => {
  const storageState = {};
  const stale = await createHarness({ storageState });
  const writer = await createHarness({ storageState });
  const sender = {
    url: "https://shop.example.test/",
    tab: { id: 1 },
    frameId: 0,
  };
  writer.answer({ type: "video-lab-config", version: 2, enabled: true });
  assert.equal((await writer.sendRuntime({
    type: "video-lab-reveal-style",
    version: 2,
    documentToken: "document_a1",
    ...defaultVideoGrantIdentity,
  }, sender)).inserted, true);
  assert.equal((await writer.sendRuntime({
    type: "video-lab-conceal-style",
    version: 2,
    documentToken: "document_a1",
    ...defaultVideoGrantIdentity,
  }, sender)).removed, true);

  const closeNonce = "0123456789abcdef0123456789abcdef";
  stale.answer({
    type: "video-lab-close",
    version: 2,
    tabId: 1,
    documentToken: "document_a1",
    closeNonce,
    ...defaultVideoGrantIdentity,
  });
  await waitFor(() => stale.postedNative.find((message) =>
    message.type === "video-lab-revoked" && message.closeNonce === closeNonce),
  "durable proof refreshed by stale context");
});

test("successful prior video CSS revocation proves a later exact native close", async () => {
  const harness = await createHarness();
  const sender = {
    url: "https://shop.example.test/",
    tab: { id: 1 },
    frameId: 0,
  };
  const token = "abcdef0123456789abcdef0123456789";
  const closeNonce = "0123456789abcdef0123456789abcdef";
  harness.answer({ type: "video-lab-config", version: 2, enabled: true });
  assert.equal((await harness.sendRuntime({
    type: "video-lab-reveal-style",
    version: 2,
    documentToken: "document_a1",
    token,
  }, sender)).inserted, true);
  assert.equal((await harness.sendRuntime({
    type: "video-lab-conceal-style",
    version: 2,
    documentToken: "document_a1",
    token,
  }, sender)).removed, true);

  harness.answer({
    type: "video-lab-close",
    version: 2,
    tabId: 1,
    documentToken: "document_a1",
    closeNonce,
  });
  const acknowledgement = await waitFor(() => harness.postedNative.find((message) =>
    message.type === "video-lab-revoked" && message.closeNonce === closeNonce),
  "close acknowledgement from exact revocation proof");
  assert.equal(acknowledgement.documentToken, "document_a1");
  assert.equal(harness.removedCss.length, 1, "the proof avoids a duplicate CSS removal");
});

test("failed video CSS insertion proves no grant was created for a later exact close", async () => {
  const harness = await createHarness({
    insertCss() {
      return Promise.reject(new Error("insertCSS unavailable"));
    },
  });
  const sender = {
    url: "https://shop.example.test/",
    tab: { id: 1 },
    frameId: 0,
  };
  const closeNonce = "0123456789abcdef0123456789abcdef";
  harness.answer({ type: "video-lab-config", version: 2, enabled: true });
  assert.equal((await harness.sendRuntime({
    type: "video-lab-reveal-style",
    version: 2,
    documentToken: "document_a1",
    token: "abcdef0123456789abcdef0123456789",
  }, sender)).inserted, false);

  harness.answer({
    type: "video-lab-close",
    version: 2,
    tabId: 1,
    documentToken: "document_a1",
    closeNonce,
  });
  const acknowledgement = await waitFor(() => harness.postedNative.find((message) =>
    message.type === "video-lab-revoked" && message.closeNonce === closeNonce),
  "close acknowledgement from failed insertion proof");
  assert.equal(acknowledgement.documentToken, "document_a1");
  assert.equal(harness.removedCss.length, 0);
});

test("native video close waits for a registered pending CSS insertion before acknowledging", async () => {
  let resolveInsertion = null;
  const harness = await createHarness({
    insertCss() {
      return new Promise((resolve) => {
        resolveInsertion = resolve;
      });
    },
  });
  const sender = {
    url: "https://shop.example.test/",
    tab: { id: 1 },
    frameId: 0,
  };
  const token = "abcdef0123456789abcdef0123456789";
  const closeNonce = "0123456789abcdef0123456789abcdef";
  harness.answer({ type: "video-lab-config", version: 2, enabled: true });
  const reveal = harness.sendRuntime({
    type: "video-lab-reveal-style",
    version: 2,
    documentToken: "document_a1",
    token,
  }, sender);
  await waitFor(() => harness.insertedCss.length === 1, "pending CSS insertion registered");

  harness.answer({
    type: "video-lab-close",
    version: 2,
    tabId: 1,
    documentToken: "document_a1",
    closeNonce,
  });
  assert.equal(harness.before({
    type: "media",
    tabId: 1,
    frameId: 0,
    documentUrl: sender.url,
    url: "https://media.example.test/a.mp4",
  }).cancel, true, "opening state never authorizes media");
  assert.equal(harness.removedCss.length, 0, "cannot remove before insertion settles");
  assert.equal(harness.postedNative.some((message) => message.type === "video-lab-revoked"), false);

  resolveInsertion();
  assert.equal((await reveal).inserted, false, "a close invalidates the pending reveal");
  await waitFor(() => harness.removedCss.length === 1, "CSS removed after pending insertion");
  const acknowledgement = await waitFor(() => harness.postedNative.find((message) =>
    message.type === "video-lab-revoked" && message.closeNonce === closeNonce),
  "pending insertion close acknowledgement");
  assert.equal(acknowledgement.documentToken, "document_a1");
});

test("fixture video uses the same close acknowledgement and user-origin CSS revocation", async () => {
  const harness = await createHarness();
  const fixtureUrl = "https://fixture.example.test/";
  const sender = { url: fixtureUrl, tab: { id: 1 }, frameId: 0 };
  const token = "abcdef0123456789abcdef0123456789";
  const closeNonce = "0123456789abcdef0123456789abcdef";
  harness.startDocument(1, "document_c3", fixtureUrl);
  harness.answer({ type: "video-lab-config", version: 2, enabled: true });
  assert.equal((await harness.sendRuntime({
    type: "video-lab-reveal-style",
    version: 2,
    documentToken: "document_c3",
    token,
  }, sender)).inserted, true);
  assert.equal(harness.insertedCss.length, 1);
  assert.equal(harness.insertedCss[0].details.cssOrigin, "user");

  harness.answer({
    type: "video-lab-close",
    version: 2,
    tabId: 1,
    documentToken: "document_c3",
    closeNonce,
  });
  await waitFor(() => harness.removedCss.length === 1, "fixture CSS revocation");
  const acknowledgement = await waitFor(() => harness.postedNative.find((message) =>
    message.type === "video-lab-revoked" && message.closeNonce === closeNonce),
  "fixture close acknowledgement");
  assert.equal(acknowledgement.documentToken, "document_c3");
});

test("failed video CSS revocation leaves the diagnostic grant fail-closed without an acknowledgement", async () => {
  const harness = await createHarness({
    removeCss() {
      return Promise.reject(new Error("removeCSS unavailable"));
    },
  });
  const sender = {
    url: "https://shop.example.test/",
    tab: { id: 1 },
    frameId: 0,
  };
  const token = "abcdef0123456789abcdef0123456789";
  harness.answer({ type: "video-lab-config", version: 2, enabled: true });
  assert.equal((await harness.sendRuntime({
    type: "video-lab-reveal-style",
    version: 2,
    documentToken: "document_a1",
    token,
  }, sender)).inserted, true);

  harness.answer({
    type: "video-lab-close",
    version: 2,
    tabId: 1,
    documentToken: "document_a1",
    closeNonce: "0123456789abcdef0123456789abcdef",
  });
  await waitFor(() => harness.removedCss.length === 1, "failed CSS removal attempted");
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(harness.postedNative.some((message) => message.type === "video-lab-revoked"), false);

  harness.answer({ type: "video-lab-config", version: 2, enabled: true });
  assert.equal((await harness.sendRuntime({
    type: "video-lab-reveal-style",
    version: 2,
    documentToken: "document_a1",
    token: "fedcba9876543210fedcba9876543210",
  }, sender)).inserted, false, "failed grant stays closing after a later enable");
  assert.equal(harness.before({
    type: "media",
    tabId: 1,
    frameId: 0,
    documentUrl: sender.url,
    url: "https://media.example.test/a.mp4",
  }).cancel, true);
});

test("an old-document native close acknowledges only after navigation removed its grant", async () => {
  const harness = await createHarness();
  const oldSender = {
    url: "https://shop.example.test/",
    tab: { id: 1 },
    frameId: 0,
  };
  const token = "abcdef0123456789abcdef0123456789";
  const closeNonce = "0123456789abcdef0123456789abcdef";
  harness.answer({ type: "video-lab-config", version: 2, enabled: true });
  assert.equal((await harness.sendRuntime({
    type: "video-lab-reveal-style",
    version: 2,
    documentToken: "document_a1",
    token,
  }, oldSender)).inserted, true);

  harness.startDocument(1, "document_b2", "https://shop.example.test/next");
  await waitFor(() => harness.removedCss.length === 1, "old grant removed by navigation");
  harness.answer({
    type: "video-lab-close",
    version: 2,
    tabId: 1,
    documentToken: "document_a1",
    closeNonce,
  });
  const acknowledgement = await waitFor(() => harness.postedNative.find((message) =>
    message.type === "video-lab-revoked" && message.closeNonce === closeNonce),
  "old document close acknowledgement after navigation");
  assert.equal(acknowledgement.documentToken, "document_a1");
  assert.equal(acknowledgement.tabId, 1);
  assert.equal(harness.before({
    type: "media",
    tabId: 1,
    frameId: 0,
    documentUrl: "https://shop.example.test/next",
    url: "https://media.example.test/next.mp4",
  }).cancel, true, "old close leaves the next document denied");
});

test("a stale native close removes a newer grant but never acknowledges the stale document", async () => {
  const harness = await createHarness();
  const oldSender = {
    url: "https://shop.example.test/",
    tab: { id: 1 },
    frameId: 0,
  };
  const newUrl = "https://shop.example.test/next";
  const newSender = { ...oldSender, url: newUrl };
  const staleNonce = "0123456789abcdef0123456789abcdef";
  harness.answer({ type: "video-lab-config", version: 2, enabled: true });
  harness.startDocument(1, "document_b2", newUrl);
  assert.equal((await harness.sendRuntime({
    type: "video-lab-reveal-style",
    version: 2,
    documentToken: "document_b2",
    token: "abcdef0123456789abcdef0123456789",
  }, newSender)).inserted, true);

  harness.answer({
    type: "video-lab-close",
    version: 2,
    tabId: 1,
    documentToken: "document_a1",
    closeNonce: staleNonce,
  });
  await waitFor(() => harness.removedCss.length === 1, "newer grant removed for stale close");
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(harness.postedNative.some((message) =>
    message.type === "video-lab-revoked" && message.closeNonce === staleNonce), false);
  assert.equal(harness.before({
    type: "media",
    tabId: 1,
    frameId: 0,
    documentUrl: newUrl,
    url: "https://media.example.test/next.mp4",
  }).cancel, true);
});

test("video protection protocol matches only the exact active frame authority", async () => {
  const context = { Uint32Array };
  vm.runInNewContext(await readAsset("video-protection-protocol.js"), context, {
    filename: "video-protection-protocol.js",
  });
  const protocol = context.__gloshDagVideoProtectionProtocol;
  const record = {
    framePending: true,
    frameSequence: 7,
    frameViewportEpoch: 3,
    revealToken: "token_a",
    revision: 5,
    videoId: 11,
  };
  const exact = { frameSequence: 7, revision: 5, videoId: 11, viewportEpoch: 3 };
  assert.equal(protocol.recordMatchesMessage(record, exact, record), true);
  assert.equal(protocol.frameMatchesMessage(record, exact, record), true);
  assert.equal(protocol.frameMatchesMessage(record, { ...exact, frameSequence: 8 }, record), false);
  assert.equal(protocol.frameMatchesMessage(record, exact, { ...record }), false);
  record.framePending = false;
  assert.equal(protocol.frameMatchesMessage(record, exact, record), false);
});

test("video laboratory transport is bounded and contains no provider exceptions", async () => {
  const background = await readAsset("background.js");
  const videoProtocol = await readAsset("video-protection-protocol.js");
  const videoGeometry = await readAsset("video-lab-geometry.js");
  const videoPresentation = await readAsset("video-lab-presentation.js");
  const videoRecord = await readAsset("video-lab-record.js");
  const videoMutations = await readAsset("video-lab-mutations.js");
  const videoIsolation = await readAsset("video-lab-isolation.js");
  const videoLifecycle = await readAsset("video-lab-lifecycle.js");
  const videoPlayback = await readAsset("video-lab-playback.js");
  const videoCapture = await readAsset("video-lab-capture.js");
  const videoViewport = await readAsset("video-lab-viewport.js");
  const videoBootstrap = await readAsset("video-lab-bootstrap.js");
  const videoSeek = await readAsset("video-lab-seek.js");
  const videoSeekState = await readAsset("video-seek-state.js");
  const videoSourceBootstrap = await readAsset("video-source-bootstrap.js");
  const videoAuthoritySelection = await readAsset("video-authority-selection.js");
  const videoDiagnostics = await readAsset("video-lab-diagnostics.js");
  const videoLab = await readAsset("video-lab.js");
  const presentationGuard = await readAsset("presentation-guard.js");
  const css = await readAsset("barrier.css");
  const fixture = await readAsset("video-lab-fixture.html");
  const fixtureScript = await readAsset("video-lab-fixture.js");
  const manifest = await readAsset("manifest.json");
  new vm.Script(videoProtocol);
  new vm.Script(videoGeometry);
  new vm.Script(videoPresentation);
  new vm.Script(videoRecord);
  new vm.Script(videoMutations);
  new vm.Script(videoIsolation);
  new vm.Script(videoLifecycle);
  new vm.Script(videoPlayback);
  new vm.Script(videoCapture);
  new vm.Script(videoViewport);
  new vm.Script(videoBootstrap);
  new vm.Script(videoSeek);
  new vm.Script(videoSeekState);
  new vm.Script(videoSourceBootstrap);
  new vm.Script(videoAuthoritySelection);
  new vm.Script(videoDiagnostics);
  new vm.Script(videoLab);
  assert.match(videoLab, /INITIAL_COVERED_CAPTURE_COUNT = 2/u);
  assert.match(videoLab, /MAX_CAPTURE_COUNT = 7_200/u);
  assert.match(videoLab, /SMOOTH_CAPTURE_DELAY_MS = 500/u);
  assert.match(videoPlayback, /state\.enabled = false;\s*void dependencies\.retireRecord\(record, "capture_limit"\)/u);
  assert.match(videoLab, /MAX_STATUS_RETRIES = 20/u);
  assert.match(videoProtocol, /video-lab-cover-request/u);
  assert.match(videoProtocol, /video-lab-frame-request/u);
  assert.match(videoProtocol, /video-lab-frame-captured/u);
  assert.match(videoProtocol, /video-lab-frame-concealed/u);
  assert.match(videoProtocol, /video-lab-smooth-start/u);
  assert.match(videoRecord, /smoothGrantIdentity: null/u);
  assert.match(videoPlayback, /smoothGrantIdentity = Object\.freeze\(dependencies\.grantIdentity\(record\)\)/u);
  assert.match(videoLifecycle, /smoothGrantIdentity \?\? dependencies\.grantIdentity\(record\)/u);
  assert.match(videoProtocol, /video-lab-retire/u);
  assert.match(videoSeek, /retireRecord\(record, "seek_requested"\)/u);
  assert.match(videoLab, /video\.addEventListener\("seeking", \(\) => seekController\?\.onSeeking\(record\)\)/u);
  assert.match(videoLab, /video\.addEventListener\("seeked", \(\) => seekController\?\.onSeeked\(record\)\)/u);
  assert.match(videoPlayback, /record\.video\.muted = true/u);
  assert.match(videoIsolation, /video\.pause\(\)/u);
  assert.match(videoIsolation, /document\.querySelectorAll\("audio, video"\)/u);
  assert.match(videoLab, /const mediaIsolationActive/u);
  assert.match(videoLab, /const isAuthorizedRawPlayback/u);
  assert.match(videoIsolation, /const originalAudioStates = new WeakMap\(\)/u);
  assert.match(videoIsolation, /originalAudioState\(media\);[\s\S]*media\.muted = true/u);
  assert.match(videoRecord, /originalMuted: audioState\.muted/u);
  assert.match(videoPlayback, /smooth_visibility_ready/u);
  assert.match(videoPlayback, /smooth_audio_restored/u);
  assert.match(background, /:root \[\$\{VIDEO_LAB_TOKEN_ATTRIBUTE\}/u);
  assert.match(background, /opacity: 1 !important/u);
  assert.match(videoIsolation, /if \(isAuthorizedRawPlayback\(media\)\) return;/u);
  assert.match(videoLab, /document\.addEventListener\("play", stopUnauthorizedPlayback, true\)/u);
  assert.match(videoLab, /document\.addEventListener\("volumechange", stopUnauthorizedPlayback, true\)/u);
  assert.match(videoLab, /const mutationObserver = new MutationObserver\(\(recordsList\) => \{\s*enforceMediaIsolation\(\)/u);
  assert.match(videoLab, /video\.remote/u);
  assert.match(videoLab, /remote\.addEventListener\("connecting", closeUnsafePresentation\)/u);
  assert.match(videoLab, /remote\.addEventListener\("connect", closeUnsafePresentation\)/u);
  assert.match(videoLab, /remote\.addEventListener\("disconnect", closeUnsafePresentation\)/u);
  assert.match(videoLab, /webkitcurrentplaybacktargetiswirelesschanged/u);
  assert.match(videoLab, /const enforcePresentationCapabilities/u);
  assert.match(videoPresentation, /video\.disablePictureInPicture = true/u);
  assert.match(videoPresentation, /video\.disableRemotePlayback = true/u);
  assert.match(videoPresentation, /video\.playsInline = true/u);
  assert.doesNotMatch(videoLab, /controlslist/iu);
  assert.match(videoLab, /attributeOldValue: true/u);
  assert.match(videoLab, /const mutationRequiresTerminalClose/u);
  assert.match(videoPresentation, /"disablepictureinpicture"/u);
  assert.match(videoPresentation, /"disableremoteplayback"/u);
  assert.match(videoPresentation, /"playsinline"/u);
  assert.match(videoCapture, /dependencies\.document\.documentElement\.hasAttribute\(dependencies\.fixtureAttribute\)/u);
  assert.match(videoPlayback, /requestVideoFrameCallback/u);
  assert.match(videoProtocol, /frameSequence/u);
  assert.match(videoProtocol, /viewportEpoch/u);
  assert.match(videoCapture, /record\.frameConcealed/u);
  assert.match(videoCapture, /message\.action === "allow"/u);
  assert.match(videoProtocol, /data-glosh-dag-video-lab-token/u);
  assert.match(videoLab, /FRAME_RESULT_TIMEOUT_MS = 2_500/u);
  assert.match(videoLab, /MAX_COVERED_VIEWPORT_TRANSITION_MS = 1_000/u);
  assert.match(videoLab, /MAX_COVERED_VIEWPORT_TRANSITIONS = 8/u);
  assert.match(videoPresentation, /return "guard_unverified"/u);
  assert.match(presentationGuard, /requestPictureInPicture/u);
  assert.match(presentationGuard, /documentPictureInPicture/u);
  assert.match(presentationGuard, /webkitSetPresentationMode/u);
  assert.match(presentationGuard, /RemotePlayback/u);
  assert.match(presentationGuard, /configurable: false/u);
  assert.match(presentationGuard, /writable: false/u);
  assert.match(videoViewport, /const coveredBrowserTransition =\s*event\?\.type === "resize"/u);
  assert.doesNotMatch(videoViewport, /const coveredBrowserTransition =\s*diagnosticsEnabled &&/u);
  assert.match(videoViewport, /activeRecord\.resultTimer === null &&\s*!activeRecord\.frameCaptured/u);
  assert.match(videoLifecycle, /concealRecord\(record\)\.then\(\(concealed\)/u);
  assert.match(videoViewport, /postDiagnostic\("viewport_transition_unstable"\)/u);
  assert.match(videoCapture, /postDiagnostic\("viewport_settle_mismatch"\)/u);
  assert.match(videoCapture, /postDiagnostic\("play_aborted_for_viewport"\)/u);
  assert.match(videoBootstrap, /bootstrapBackingGeneration === 1/u);
  assert.match(videoLab, /record\.bootstrapLoadStarted = true/u);
  assert.match(videoLab, /record\.coverAcknowledged\) armBootstrapGeneration\(record\)/u);
  assert.match(videoCapture, /if \(record\.bootstrapLoadStarted\) dependencies\.armBootstrapGeneration\(record\)/u);
  assert.match(videoLab, /record\.bootstrapLoadStarted \|\| record\.bootstrapBackingGeneration !== 0/u);
  assert.match(videoBootstrap, /record\.bootstrapLoadSourceSignature !== record\.sourceSignature/u);
  assert.match(videoLab, /record\.sourceSignature !== sourceSignature\(record\.video\)/u);
  assert.match(videoBootstrap, /record\.rawFrameOpen \|\|\s*record\.frameCaptured/u);
  assert.match(videoViewport, /\(activeRecord\.covered \|\| activeRecord\.coverAcknowledged\) &&/u);
  assert.match(videoCapture, /if \(!dependencies\.recordMatchesMessage\(record, message\) \|\| !record\.coverPending\) return;/u);
  assert.match(videoCapture, /record\.coverAcknowledged = true;/u);
  assert.match(videoCapture, /if \(!await dependencies\.backgroundReady\(\)\)/u);
  assert.match(videoCapture, /if \(record === dependencies\.state\.activeRecord\) record\.coverAcknowledged = false/u);
  assert.match(videoCapture, /!dependencies\.recordMatchesMessage\(record, message\) \|\|\s*!record\.coverPending \|\|\s*!record\.coverAcknowledged/u);
  assert.match(videoLab, /cover_message_received/u);
  assert.match(videoCapture, /cover_arm_entered/u);
  assert.match(videoCapture, /background_wait_started/u);
  assert.match(videoCapture, /background_wait_completed/u);
  assert.match(videoCapture, /background_wait_failed/u);
  assert.match(background, /background_status_received/u);
  assert.match(background, /background_status_enabled/u);
  assert.match(background, /background_status_rejected/u);
  assert.match(videoViewport, /record\.bootstrapTransitionUsed/u);
  assert.match(videoBootstrap, /record\.bootstrapTransitionUsed = true/u);
  assert.match(videoLab, /const beginBootstrapViewportTransition/u);
  assert.match(videoLab, /const completeBootstrapViewportTransition/u);
  assert.match(videoCapture, /dependencies\.beginBootstrapViewportTransition\(record, settledSignature\)/u);
  assert.match(videoCapture, /record\.pendingViewportSignature !== null/u);
  assert.match(videoViewport, /dependencies\.beginBootstrapViewportTransition\(activeRecord, nextSignature\)/u);
  assert.match(videoBootstrap, /record\.bootstrapTransitionUsed \|\|/u);
  assert.match(videoBootstrap, /record\.rawFrameOpen \|\|\s*record\.frameCaptured/u);
  assert.match(videoBootstrap, /!dependencies\.sameViewportBounds\(record\.viewportSignature, nextSignature\)/u);
  assert.match(videoLab, /retireRecord\(record, "bootstrap_generation_repeated"\)/u);
  assert.match(videoLab, /record\.sourceSignature !== sourceSignature\(record\.video\)/u);
  assert.match(videoDiagnostics, /play_ready_nothing/u);
  assert.match(videoDiagnostics, /play_generation_second/u);
  assert.match(videoDiagnostics, /play_state_paused/u);
  assert.match(videoDiagnostics, /play_state_ended/u);
  assert.match(videoDiagnostics, /play_network_loading/u);
  assert.match(videoDiagnostics, /play_source_stable/u);
  assert.match(videoCapture, /play_reject_source_stable/u);
  assert.match(videoCapture, /play_reject_network_/u);
  assert.match(videoDiagnostics, /backing_src_attribute_absent/u);
  assert.match(videoDiagnostics, /backing_current_src_absent/u);
  assert.match(videoDiagnostics, /backing_src_object_present/u);
  assert.match(videoDiagnostics, /backing_object_tracks_present/u);
  assert.match(videoDiagnostics, /backing_source_children_none/u);
  assert.match(videoDiagnostics, /backing_scheme_blob_media_source_like/u);
  assert.match(videoGeometry, /const hasBackingMedia/u);
  assert.match(videoAuthoritySelection, /backed \|\| dependencies\.canBootstrapCandidate\(video\)/u);
  assert.match(videoLab, /document\.addEventListener\(type, scheduleScan, true\)/u);
  for (const event of ["loadstart", "durationchange", "loadedmetadata", "canplay"]) {
    assert.match(videoLab, new RegExp(`\"${event}\"`, "u"));
  }
  for (const event of ["play", "playing", "pause", "abort", "emptied", "waiting", "stalled"]) {
    assert.match(videoLab, new RegExp(`play_event_\\$\\{type\\}|\"${event}\"`, "u"));
  }
  assert.match(videoDiagnostics, /play_video_tracks_none/u);
  assert.match(videoDiagnostics, /play_track_live/u);
  assert.match(videoDiagnostics, /play_error_not_allowed/u);
  assert.match(videoDiagnostics, /play_error_not_supported/u);
  assert.match(videoCapture, /!record\.covered \|\|\s*record\.framePending \|\|/u);
  assert.match(css, /video,\s*audio,/u);
  assert.doesNotMatch(css, /data-glosh-dag-video-lab/u);
  assert.doesNotMatch(fixture, /video\[data-glosh-dag-video-lab-token\]/u);
  assert.match(fixtureScript, /__gloshDagInstallVideoFixture/u);
  assert.match(fixtureScript, /position:fixed;left:6vw;top:280px;width:88vw;height:66vw/u);
  assert.match(videoViewport, /dependencies\.fixtureEnabled\(\)[\s\S]*event\?\.type === "resize"[\s\S]*dependencies\.sameVideoRect/u);
  assert.match(background, /awaitVideoLabCloseProof/u);
  assert.match(background, /revoke_waiting_for_proof/u);
  assert.match(manifest, /"video-protection-protocol.js",\s*"video-lab-geometry.js",\s*"video-lab-presentation.js",\s*"video-lab-record.js",\s*"video-lab-mutations.js",\s*"video-lab-isolation.js",\s*"video-lab-lifecycle.js",\s*"video-lab-playback.js",\s*"video-lab-capture.js",\s*"video-lab-viewport.js",\s*"video-lab-diagnostics.js",\s*"video-bootstrap-state.js",\s*"video-lab-bootstrap.js",\s*"video-seek-state.js",\s*"video-lab-seek.js",\s*"video-source-bootstrap.js",\s*"video-authority-selection.js",\s*"video-lab.js",\s*"video-lab-fixture.js",\s*"barrier.js"/u);
  assert.doesNotMatch(videoLab, /youtube|instagram|tiktok|mimo|fravega|cheeky/iu);
  assert.doesNotMatch(videoAuthoritySelection, /youtube|instagram|tiktok|mimo|fravega|cheeky/iu);
  assert.doesNotMatch(videoSourceBootstrap, /youtube|instagram|tiktok|mimo|fravega|cheeky/iu);
});

test("video laboratory bootstraps a visible source only behind the user-origin barrier", async () => {
  const videoLab = await readAsset("video-lab.js");
  const videoCapture = await readAsset("video-lab-capture.js");
  const videoSourceBootstrap = await readAsset("video-source-bootstrap.js");
  assert.match(videoCapture, /!dependencies\.hasBackingMedia\(record\.video\)/u);
  assert.match(videoSourceBootstrap, /record\.sourceBootstrapActive = true/u);
  assert.match(videoSourceBootstrap, /record\.covered \|\|\s*record\.coverPending/u);
  assert.match(videoSourceBootstrap, /record\.video\.muted = true/u);
  assert.match(videoSourceBootstrap, /dependencies\.safePause\(record\.video\)/u);
  assert.match(videoLab, /if \(hasBackingMedia\(video\)\) requestCover\(record\)/u);
  assert.match(videoLab, /"loadstart", "durationchange", "loadedmetadata", "canplay"/u);
  assert.doesNotMatch(videoLab, /\.load\(\).*hasBackingMedia/su);
});

test("video laboratory silences dynamic unselected media in capture phase", async () => {
  const documentListeners = new Map();
  let mutationCallback = null;
  const media = [];
  class HTMLMediaElement {
    constructor() {
      this.defaultMuted = false;
      this.muted = false;
      this.pauseCalls = 0;
      this.volume = 1;
    }
    addEventListener() {}
    pause() {
      this.pauseCalls += 1;
    }
  }
  class MutationObserver {
    constructor(callback) {
      mutationCallback = callback;
    }
    observe() {}
  }
  const document = {
    documentElement: {
      getAttribute: (name) => name === "data-glosh-dag-presentation-guard" ? "1" : null,
      hasAttribute: () => false,
    },
    fullscreenElement: null,
    pictureInPictureElement: null,
    addEventListener(type, listener, capture) {
      documentListeners.set(type, { capture, listener });
    },
    querySelectorAll(selector) {
      assert.equal(selector, "audio, video");
      return media;
    },
  };
  const context = {
    HTMLMediaElement,
    HTMLSourceElement: class {},
    MutationObserver,
    Uint32Array,
    browser: { runtime: { sendMessage: () => Promise.resolve({ enabled: true }) } },
    clearTimeout() {},
    crypto: webcrypto,
    document,
    performance: { now: () => 0 },
    requestAnimationFrame: () => 1,
    setTimeout: () => 1,
    addEventListener() {},
    window: { top: null },
  };
  context.window.top = context.window;

  const initialAudio = new HTMLMediaElement();
  const initialVideo = new HTMLMediaElement();
  media.push(initialAudio, initialVideo);
  vm.runInNewContext(await readAsset("video-protection-protocol.js"), context, { filename: "video-protection-protocol.js" });
  vm.runInNewContext(await readAsset("video-lab-geometry.js"), context, { filename: "video-lab-geometry.js" });
  vm.runInNewContext(await readAsset("video-lab-presentation.js"), context, { filename: "video-lab-presentation.js" });
  vm.runInNewContext(await readAsset("video-lab-record.js"), context, { filename: "video-lab-record.js" });
  vm.runInNewContext(await readAsset("video-lab-mutations.js"), context, { filename: "video-lab-mutations.js" });
  vm.runInNewContext(await readAsset("video-lab-isolation.js"), context, { filename: "video-lab-isolation.js" });
  vm.runInNewContext(await readAsset("video-lab-lifecycle.js"), context, { filename: "video-lab-lifecycle.js" });
  vm.runInNewContext(await readAsset("video-lab-playback.js"), context, { filename: "video-lab-playback.js" });
  vm.runInNewContext(await readAsset("video-lab-capture.js"), context, { filename: "video-lab-capture.js" });
  vm.runInNewContext(await readAsset("video-lab-viewport.js"), context, { filename: "video-lab-viewport.js" });
  vm.runInNewContext(await readAsset("video-lab-diagnostics.js"), context, { filename: "video-lab-diagnostics.js" });
  vm.runInNewContext(await readAsset("video-bootstrap-state.js"), context, { filename: "video-bootstrap-state.js" });
  vm.runInNewContext(await readAsset("video-lab-bootstrap.js"), context, { filename: "video-lab-bootstrap.js" });
  vm.runInNewContext(await readAsset("video-seek-state.js"), context, { filename: "video-seek-state.js" });
  vm.runInNewContext(await readAsset("video-lab-seek.js"), context, { filename: "video-lab-seek.js" });
  vm.runInNewContext(await readAsset("video-source-bootstrap.js"), context, { filename: "video-source-bootstrap.js" });
  vm.runInNewContext(await readAsset("video-authority-selection.js"), context, { filename: "video-authority-selection.js" });
  vm.runInNewContext(await readAsset("video-lab.js"), context, { filename: "video-lab.js" });
  context.__gloshDagVideoLab.install({
    protocolVersion: 2,
    documentToken: "document_a1",
    postToAndroid() {},
  });
  context.__gloshDagVideoLab.onNativeMessage({
    type: "video-lab-config",
    version: 2,
    enabled: true,
  });

  for (const element of [initialAudio, initialVideo]) {
    assert.equal(element.muted, true);
    assert.equal(element.defaultMuted, true);
    assert.equal(element.volume, 0);
    assert.ok(element.pauseCalls >= 1);
  }

  const dynamicAudio = new HTMLMediaElement();
  media.push(dynamicAudio);
  mutationCallback([{ type: "childList", addedNodes: [dynamicAudio], removedNodes: [] }]);
  assert.equal(dynamicAudio.muted, true);
  assert.equal(dynamicAudio.defaultMuted, true);
  assert.equal(dynamicAudio.volume, 0);
  assert.ok(dynamicAudio.pauseCalls >= 1);

  dynamicAudio.muted = false;
  dynamicAudio.defaultMuted = false;
  dynamicAudio.volume = 1;
  const pausesBeforePlay = dynamicAudio.pauseCalls;
  const playListener = documentListeners.get("play");
  assert.equal(playListener.capture, true);
  playListener.listener({ target: dynamicAudio });
  assert.equal(dynamicAudio.muted, true);
  assert.equal(dynamicAudio.defaultMuted, true);
  assert.equal(dynamicAudio.volume, 0);
  assert.equal(dynamicAudio.pauseCalls, pausesBeforePlay + 1);
});

test("video scan waits for one stable viewport after a burst of changes", async () => {
  const context = {};
  vm.runInNewContext(await readAsset("video-lab-viewport.js"), context, {
    filename: "video-lab-viewport.js",
  });
  let now = 10;
  let lastChangeAt = 0;
  let scans = 0;
  let stabilityRequired = true;
  const timers = [];
  const gate = context.__gloshDagVideoLabViewport.createScanGate({
    lastChangeAt: () => lastChangeAt,
    markStable: () => { stabilityRequired = false; },
    now: () => now,
    required: () => stabilityRequired,
    scheduleNow: () => { scans += 1; },
    setTimeout: (callback, delay) => {
      timers.push({ callback, delay });
      return timers.length;
    },
    settleMillis: 150,
  });

  gate.schedule();
  gate.schedule();
  assert.equal(timers.length, 1, "a scroll burst owns one stability timer");
  assert.equal(scans, 0);
  lastChangeAt = 100;
  now = 150;
  timers.shift().callback();
  assert.equal(timers.length, 1, "an extended burst waits only for its remaining quiet time");
  assert.equal(scans, 0);
  now = 250;
  timers.shift().callback();
  assert.equal(scans, 1, "the candidate is scanned once after the viewport settles");
  assert.equal(stabilityRequired, false);
});

test("covered video retirement waits for fresh native configuration before rescanning", async () => {
  const context = {};
  vm.runInNewContext(await readAsset("video-lab-lifecycle.js"), context, {
    filename: "video-lab-lifecycle.js",
  });
  let scans = 0;
  const retired = [];
  const video = { removeAttribute() {} };
  const record = {
    bootstrapLoadSourceSignature: "source",
    bootstrapLoadStarted: true,
    bootstrapState: { terminate() {} },
    concealFailed: false,
    concealPromise: null,
    coverAcknowledged: true,
    coverPending: true,
    covered: true,
    rawFrameOpen: false,
    retirePromise: null,
    retiring: false,
    revision: 1,
    smoothActive: true,
    terminal: false,
    video,
    videoId: "video_1",
  };
  const state = {
    activeRecord: record,
    closingRecord: null,
    enabled: true,
    isolationLocked: false,
    isolationLockedRecord: null,
    isolationRetryPromise: null,
  };
  const lifecycle = context.__gloshDagVideoLabLifecycle.create({
    browser: { runtime: { sendMessage: () => Promise.resolve({ removed: true }) } },
    cancelSourceBootstrap() {},
    clearRecordTimers() {},
    concealMessage: "video-lab-conceal-style",
    documentToken: () => "document_1",
    enforceMediaIsolation() {},
    grantIdentity: () => ({}),
    postDiagnostic() {},
    postToAndroid: () => (message) => retired.push(message),
    protocolVersion: () => 2,
    resetFrameState() {},
    retireMessage: "video-lab-retire",
    safePause() {},
    scheduleScan: () => { scans += 1; },
    state,
    tokenAttribute: "data-glosh-token",
  });

  assert.equal(await lifecycle.retireRecord(record, "viewport_changed"), true);
  assert.equal(scans, 0, "the old enabled epoch cannot select a replacement candidate");
  assert.equal(retired.length, 1);
  assert.equal(retired[0].reason, "viewport_changed");
  assert.equal(state.enabled, false, "retirement closes the old configuration epoch immediately");
  assert.equal(state.activeRecord, null);
  assert.equal(state.closingRecord, null);
});

test("pre-cover video retirement may rescan inside the same native configuration", async () => {
  const context = {};
  vm.runInNewContext(await readAsset("video-lab-lifecycle.js"), context, {
    filename: "video-lab-lifecycle.js",
  });
  let scans = 0;
  const video = { removeAttribute() {} };
  const record = {
    bootstrapLoadSourceSignature: "source",
    bootstrapLoadStarted: false,
    bootstrapState: { terminate() {} },
    captures: 0,
    concealFailed: false,
    concealPromise: null,
    coverAcknowledged: false,
    coverPending: false,
    covered: false,
    rawFrameOpen: false,
    retirePromise: null,
    retiring: false,
    revision: 1,
    smoothActive: false,
    terminal: false,
    video,
    videoId: "video_1",
  };
  const state = {
    activeRecord: record,
    closingRecord: null,
    enabled: true,
    isolationLocked: false,
    isolationLockedRecord: null,
    isolationRetryPromise: null,
  };
  const lifecycle = context.__gloshDagVideoLabLifecycle.create({
    browser: { runtime: { sendMessage: () => Promise.resolve({ removed: true }) } },
    cancelSourceBootstrap() {}, clearRecordTimers() {},
    concealMessage: "video-lab-conceal-style",
    documentToken: () => "document_1",
    enforceMediaIsolation() {}, grantIdentity: () => ({}), postDiagnostic() {},
    postToAndroid: () => () => {}, protocolVersion: () => 2, resetFrameState() {},
    retireMessage: "video-lab-retire", safePause() {},
    scheduleScan: () => { scans += 1; }, state,
    tokenAttribute: "data-glosh-token",
  });

  assert.equal(await lifecycle.retireRecord(record, "authority_changed"), true);
  assert.equal(state.enabled, true);
  assert.equal(scans, 1, "a candidate rejected before native cover cannot deadlock the page");
});

test("video laboratory ignores controls UI and closes on preventive capability mutation", async () => {
  const animationFrames = [];
  let mutationCallback = null;
  let mutationOptions = null;
  const media = [];
  const posted = [];
  let presentationGuardVersion = "1";
  class HTMLMediaElement {
    constructor() {
      this.defaultMuted = false;
      this.muted = false;
      this.pauseCalls = 0;
      this.volume = 1;
    }
    addEventListener() {}
    pause() {
      this.pauseCalls += 1;
    }
  }
  class HTMLVideoElement extends HTMLMediaElement {
    constructor() {
      super();
      this.attributes = new Map();
      this.currentSrc = "https://media.example.test/fixture.mp4";
      this.duration = 10;
      this.isConnected = true;
      this.readyState = 4;
      this.remote = { state: "disconnected", addEventListener() {} };
      this.srcObject = null;
      this.rect = { bottom: 80, height: 80, left: 0, right: 100, top: 0, width: 100 };
    }
    getAttribute(name) {
      const attribute = name.toLowerCase();
      return this.attributes.has(attribute) ? this.attributes.get(attribute) : null;
    }
    setAttribute(name, value) {
      this.attributes.set(name.toLowerCase(), value);
    }
    removeAttribute(name) {
      this.attributes.delete(name.toLowerCase());
    }
    get disablePictureInPicture() {
      return this.attributes.has("disablepictureinpicture");
    }
    set disablePictureInPicture(enabled) {
      if (enabled) this.attributes.set("disablepictureinpicture", "");
      else this.attributes.delete("disablepictureinpicture");
    }
    get disableRemotePlayback() {
      return this.attributes.has("disableremoteplayback");
    }
    set disableRemotePlayback(enabled) {
      if (enabled) this.attributes.set("disableremoteplayback", "");
      else this.attributes.delete("disableremoteplayback");
    }
    get playsInline() {
      return this.attributes.has("playsinline");
    }
    set playsInline(enabled) {
      if (enabled) this.attributes.set("playsinline", "");
      else this.attributes.delete("playsinline");
    }
    get controlsList() {
      return {
        add: (token) => {
          const tokens = new Set((this.getAttribute("controlslist") || "").split(" ").filter(Boolean));
          tokens.add(token);
          this.attributes.set("controlslist", [...tokens].join(" "));
        },
      };
    }
    getBoundingClientRect() {
      return this.rect;
    }
    querySelectorAll() {
      return [];
    }
  }
  class MutationObserver {
    constructor(callback) {
      mutationCallback = callback;
    }
    observe(_target, options) {
      mutationOptions = options;
    }
  }
  const document = {
    documentElement: {
      getAttribute: (name) => name === "data-glosh-dag-presentation-guard"
        ? presentationGuardVersion
        : null,
      hasAttribute: () => false,
    },
    fullscreenElement: null,
    pictureInPictureElement: null,
    addEventListener() {},
    querySelectorAll(selector) {
      if (selector === "audio, video") return media;
      if (selector === "video") return media;
      return [];
    },
  };
  const context = {
    HTMLMediaElement,
    HTMLSourceElement: class {},
    MutationObserver,
    Uint32Array,
    browser: { runtime: { sendMessage: () => Promise.resolve({ enabled: true }) } },
    clearTimeout() {},
    crypto: { getRandomValues: (words) => words.fill(1) },
    document,
    innerHeight: 100,
    innerWidth: 100,
    performance: { now: () => 200 },
    requestAnimationFrame(callback) {
      animationFrames.push(callback);
      return animationFrames.length;
    },
    setTimeout: () => 1,
    addEventListener() {},
    window: { top: null },
  };
  context.window.top = context.window;
  const video = new HTMLVideoElement();
  media.push(video);

  vm.runInNewContext(await readAsset("video-protection-protocol.js"), context, { filename: "video-protection-protocol.js" });
  vm.runInNewContext(await readAsset("video-lab-geometry.js"), context, { filename: "video-lab-geometry.js" });
  vm.runInNewContext(await readAsset("video-lab-presentation.js"), context, { filename: "video-lab-presentation.js" });
  vm.runInNewContext(await readAsset("video-lab-record.js"), context, { filename: "video-lab-record.js" });
  vm.runInNewContext(await readAsset("video-lab-mutations.js"), context, { filename: "video-lab-mutations.js" });
  vm.runInNewContext(await readAsset("video-lab-isolation.js"), context, { filename: "video-lab-isolation.js" });
  vm.runInNewContext(await readAsset("video-lab-lifecycle.js"), context, { filename: "video-lab-lifecycle.js" });
  vm.runInNewContext(await readAsset("video-lab-playback.js"), context, { filename: "video-lab-playback.js" });
  vm.runInNewContext(await readAsset("video-lab-capture.js"), context, { filename: "video-lab-capture.js" });
  vm.runInNewContext(await readAsset("video-lab-viewport.js"), context, { filename: "video-lab-viewport.js" });
  vm.runInNewContext(await readAsset("video-lab-diagnostics.js"), context, { filename: "video-lab-diagnostics.js" });
  vm.runInNewContext(await readAsset("video-bootstrap-state.js"), context, { filename: "video-bootstrap-state.js" });
  vm.runInNewContext(await readAsset("video-lab-bootstrap.js"), context, { filename: "video-lab-bootstrap.js" });
  vm.runInNewContext(await readAsset("video-seek-state.js"), context, { filename: "video-seek-state.js" });
  vm.runInNewContext(await readAsset("video-lab-seek.js"), context, { filename: "video-lab-seek.js" });
  vm.runInNewContext(await readAsset("video-source-bootstrap.js"), context, { filename: "video-source-bootstrap.js" });
  vm.runInNewContext(await readAsset("video-authority-selection.js"), context, { filename: "video-authority-selection.js" });
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
    diagnostics: true,
    enabled: true,
  });
  animationFrames.shift()();

  assert.equal(video.disablePictureInPicture, true);
  assert.equal(video.disableRemotePlayback, true);
  assert.equal(video.playsInline, true);
  assert.equal(video.getAttribute("controlslist"), null);
  assert.equal(mutationOptions.attributeFilter.includes("controlslist"), false);
  mutationCallback([
    { attributeName: "disablepictureinpicture", oldValue: null, target: video, type: "attributes" },
    { attributeName: "disableremoteplayback", oldValue: null, target: video, type: "attributes" },
    { attributeName: "playsinline", oldValue: null, target: video, type: "attributes" },
  ]);
  assert.equal(posted.some((message) => message.type === "video-lab-retire"), false);

  const coverRequest = posted.find((message) => message.type === "video-lab-cover-request");
  context.__gloshDagVideoLab.onNativeMessage({
    ...coverRequest,
    type: "video-lab-cover-armed",
    version: 2,
  });
  await new Promise((resolve) => setImmediate(resolve));
  video.setAttribute("controlslist", "inline");
  assert.equal(posted.some((message) => message.type === "video-lab-retire"), false);
  video.disablePictureInPicture = false;
  mutationCallback([{
    attributeName: "disablepictureinpicture",
    oldValue: "",
    target: video,
    type: "attributes",
  }]);
  const retirement = await waitFor(() => posted.find((message) =>
    message.type === "video-lab-retire"), "preventive capability mutation retirement");
  assert.equal(retirement.reason, "active_video_mutated");
  assert.ok(posted.some((message) =>
    message.type === "video-lab-diagnostic" &&
    message.stage === "mutation_video_attribute_capability"));
  assert.ok(posted.some((message) =>
    message.type === "video-lab-diagnostic" &&
    message.stage === "mutation_source_identity_stable"));

  await new Promise((resolve) => setImmediate(resolve));
  presentationGuardVersion = null;
  context.__gloshDagVideoLab.onNativeMessage({
    type: "video-lab-config",
    version: 2,
    diagnostics: true,
    enabled: true,
  });
  while (animationFrames.length > 0) animationFrames.shift()();
  await waitFor(() => posted.some((message) =>
    message.type === "video-lab-diagnostic" && message.stage === "unsafe_guard_unverified"),
  "missing MAIN presentation guard remains fail-closed");
  presentationGuardVersion = "1";

  await new Promise((resolve) => setImmediate(resolve));
  document.pictureInPictureElement = video;
  context.__gloshDagVideoLab.onNativeMessage({
    type: "video-lab-config",
    version: 2,
    diagnostics: true,
    enabled: true,
  });
  while (animationFrames.length > 0) animationFrames.shift()();
  await waitFor(() => posted.some((message) =>
    message.type === "video-lab-diagnostic" && message.stage === "unsafe_picture_in_picture"),
  "real picture-in-picture remains unsafe");

  await new Promise((resolve) => setImmediate(resolve));
  document.pictureInPictureElement = null;
  video.remote.state = "connecting";
  context.__gloshDagVideoLab.onNativeMessage({
    type: "video-lab-config",
    version: 2,
    diagnostics: true,
    enabled: true,
  });
  while (animationFrames.length > 0) animationFrames.shift()();
  await waitFor(() => posted.some((message) =>
    message.type === "video-lab-diagnostic" && message.stage === "unsafe_remote_connecting"),
  "specific unsafe presentation diagnostic");
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(animationFrames.length, 0, "unsafe presentation remains blocked until reconfiguration");

  const diagnosticCount = posted.filter((message) => message.type === "video-lab-diagnostic").length;
  context.__gloshDagVideoLab.onNativeMessage({
    type: "video-lab-config",
    version: 2,
    diagnostics: false,
    enabled: true,
  });
  animationFrames.shift()();
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(
    posted.filter((message) => message.type === "video-lab-diagnostic").length,
    diagnosticCount,
    "non-Diagnostic mode emits no diagnostic label",
  );
  assert.equal(
    posted.filter((message) => message.type === "video-lab-retire").at(-1).reason,
    "unsafe_presentation",
    "non-Diagnostic mode remains fail-closed",
  );
  assert.equal(animationFrames.length, 0, "non-Diagnostic mode keeps unsafe presentation terminal");
});

test("failed video conceal leaves terminal media isolation locked until native retry", async () => {
  const documentListeners = new Map();
  const windowListeners = new Map();
  const animationFrames = [];
  const media = [];
  const posted = [];
  const scheduledTimeouts = [];
  let concealAttempts = 0;
  let concealSucceeds = false;
  let now = 200;
  class HTMLMediaElement {
    constructor() {
      this.defaultMuted = false;
      this.muted = false;
      this.pauseCalls = 0;
      this.volume = 1;
    }
    addEventListener() {}
    pause() {
      this.pauseCalls += 1;
    }
  }
  class HTMLVideoElement extends HTMLMediaElement {
    constructor() {
      super();
      this.currentSrc = "https://media.example.test/fixture.mp4";
      this.duration = 10;
      this.frameCallback = null;
      this.isConnected = true;
      this.readyState = 4;
      this.srcObject = null;
      this.attributes = new Map();
    }
    getAttribute(name) {
      return this.attributes.get(name) || null;
    }
    getBoundingClientRect() {
      return { bottom: 80, height: 80, left: 0, right: 100, top: 0, width: 100 };
    }
    play() {
      return Promise.resolve();
    }
    removeAttribute(name) {
      this.attributes.delete(name);
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
    setAttribute(name, value) {
      this.attributes.set(name, value);
    }
  }
  class MutationObserver {
    constructor() {}
    observe() {}
  }
  const document = {
    documentElement: {
      getAttribute: (name) => name === "data-glosh-dag-presentation-guard" ? "1" : null,
      hasAttribute: () => false,
    },
    fullscreenElement: null,
    addEventListener(type, listener, capture) {
      documentListeners.set(type, { capture, listener });
    },
    querySelectorAll(selector) {
      if (selector === "audio, video") return media;
      if (selector === "video") return media.filter((element) => element instanceof HTMLVideoElement);
      return [];
    },
  };
  const context = {
    HTMLMediaElement,
    HTMLSourceElement: class {},
    MutationObserver,
    Uint32Array,
    browser: {
      runtime: {
        sendMessage(message) {
          if (message.type === "video-lab-status") return Promise.resolve({ enabled: true });
          if (message.type === "video-lab-reveal-style") return Promise.resolve({ inserted: true });
          if (message.type === "video-lab-conceal-style") {
            concealAttempts += 1;
            return Promise.resolve({ removed: concealSucceeds });
          }
          return Promise.resolve(null);
        },
      },
    },
    clearTimeout() {},
    crypto: { getRandomValues: (words) => words.fill(1) },
    document,
    innerHeight: 100,
    innerWidth: 100,
    performance: { now: () => now },
    requestAnimationFrame(callback) {
      animationFrames.push(callback);
      return animationFrames.length;
    },
    setTimeout(callback, delay) {
      scheduledTimeouts.push({ callback, delay });
      return scheduledTimeouts.length;
    },
    addEventListener(type, listener) {
      windowListeners.set(type, listener);
    },
    window: { top: null },
  };
  context.window.top = context.window;

  const video = new HTMLVideoElement();
  const audio = new HTMLMediaElement();
  media.push(video, audio);
  vm.runInNewContext(await readAsset("video-protection-protocol.js"), context, { filename: "video-protection-protocol.js" });
  vm.runInNewContext(await readAsset("video-lab-geometry.js"), context, { filename: "video-lab-geometry.js" });
  vm.runInNewContext(await readAsset("video-lab-presentation.js"), context, { filename: "video-lab-presentation.js" });
  vm.runInNewContext(await readAsset("video-lab-record.js"), context, { filename: "video-lab-record.js" });
  vm.runInNewContext(await readAsset("video-lab-mutations.js"), context, { filename: "video-lab-mutations.js" });
  vm.runInNewContext(await readAsset("video-lab-isolation.js"), context, { filename: "video-lab-isolation.js" });
  vm.runInNewContext(await readAsset("video-lab-lifecycle.js"), context, { filename: "video-lab-lifecycle.js" });
  vm.runInNewContext(await readAsset("video-lab-playback.js"), context, { filename: "video-lab-playback.js" });
  vm.runInNewContext(await readAsset("video-lab-capture.js"), context, { filename: "video-lab-capture.js" });
  vm.runInNewContext(await readAsset("video-lab-viewport.js"), context, { filename: "video-lab-viewport.js" });
  vm.runInNewContext(await readAsset("video-lab-diagnostics.js"), context, { filename: "video-lab-diagnostics.js" });
  vm.runInNewContext(await readAsset("video-bootstrap-state.js"), context, { filename: "video-bootstrap-state.js" });
  vm.runInNewContext(await readAsset("video-lab-bootstrap.js"), context, { filename: "video-lab-bootstrap.js" });
  vm.runInNewContext(await readAsset("video-seek-state.js"), context, { filename: "video-seek-state.js" });
  vm.runInNewContext(await readAsset("video-lab-seek.js"), context, { filename: "video-lab-seek.js" });
  vm.runInNewContext(await readAsset("video-source-bootstrap.js"), context, { filename: "video-source-bootstrap.js" });
  vm.runInNewContext(await readAsset("video-authority-selection.js"), context, { filename: "video-authority-selection.js" });
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
    diagnostics: true,
    enabled: true,
  });
  assert.equal(
    posted.some((message) => message.type === "video-lab-diagnostic" && message.stage === "config_enabled"),
    true,
  );
  animationFrames.shift()();
  await waitFor(() => posted.some((message) => message.type === "video-lab-cover-request"),
    "video cover request");
  const cover = posted.find((message) => message.type === "video-lab-cover-request");
  windowListeners.get("resize")({ type: "resize" });
  assert.equal(
    posted.some((message) => message.type === "video-lab-diagnostic" &&
      message.stage === "viewport_resize_unchanged"),
    true,
    "a Gecko resize event without a geometry change preserves the native cover",
  );
  assert.equal(
    posted.some((message) => message.type === "video-lab-retire"),
    false,
    "an unchanged resize cannot retire the protected video",
  );
  now = 300;
  context.__gloshDagVideoLab.onNativeMessage({
    type: "video-lab-cover-armed",
    version: 2,
    videoId: cover.videoId,
    revision: cover.revision,
  });
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(video.frameCallback, null, "the frame waits for viewport stability");
  context.innerHeight = 90;
  windowListeners.get("resize")({ type: "resize" });
  assert.equal(
    posted.some((message) => message.type === "video-lab-diagnostic" &&
      message.stage === "viewport_transition_covered"),
    true,
    "a bounded browser reflow stays behind the already armed cover",
  );
  assert.equal(
    posted.some((message) => message.type === "video-lab-retire"),
    false,
    "a stable browser-only transition does not retire the protected video",
  );
  now = 600;
  scheduledTimeouts.findLast(({ delay }) => delay <= 150).callback();
  assert.equal(
    posted.some((message) => message.type === "video-lab-diagnostic" &&
      message.stage === "viewport_transition_stable"),
    true,
    "the final viewport identity is revalidated before opening a frame",
  );
  await waitFor(() => video.frameCallback !== null, "video frame callback");
  concealSucceeds = true;
  context.innerHeight = 80;
  now = 700;
  windowListeners.get("resize")({ type: "resize" });
  assert.equal(
    posted.some((message) => message.type === "video-lab-diagnostic" &&
      message.stage === "viewport_transition_covered"),
    true,
    "a pre-capture compositor grant remains behind the native cover during browser reflow",
  );
  now = 900;
  scheduledTimeouts.findLast(({ delay }) => delay === 150).callback();
  await waitFor(() => concealAttempts === 1, "old pre-capture grant concealment");
  await waitFor(() => video.frameCallback !== null, "revalidated video frame callback");
  concealSucceeds = false;
  video.frameCallback(0, { presentedFrames: 1 });
  const frame = await waitFor(() => posted.find((message) =>
    message.type === "video-lab-frame-request"), "video frame request");
  context.__gloshDagVideoLab.onNativeMessage({
    type: "video-lab-frame-captured",
    version: 2,
    videoId: frame.videoId,
    revision: frame.revision,
    frameSequence: frame.frameSequence,
    viewportEpoch: frame.viewportEpoch,
  });
  await waitFor(() => concealAttempts === 2, "failed conceal attempt");
  await new Promise((resolve) => setImmediate(resolve));

  // Page activity cannot clear the terminal lock after active/closing state is
  // gone. A fresh audio play must still be stopped synchronously in capture.
  windowListeners.get("scroll")();
  while (animationFrames.length > 0) animationFrames.shift()();
  audio.defaultMuted = false;
  audio.muted = false;
  audio.volume = 1;
  const pausesBeforePlay = audio.pauseCalls;
  documentListeners.get("play").listener({ target: audio });
  assert.equal(audio.defaultMuted, true);
  assert.equal(audio.muted, true);
  assert.equal(audio.volume, 0);
  assert.equal(audio.pauseCalls, pausesBeforePlay + 1);
  assert.equal(concealAttempts, 2);

  // Only a new valid native enable request retries the failed revocation.
  concealSucceeds = true;
  context.__gloshDagVideoLab.onNativeMessage({
    type: "video-lab-config",
    version: 2,
    enabled: true,
  });
  await waitFor(() => concealAttempts === 3, "explicit terminal conceal retry");
  now += 1;
});

test("first paint closes inline and changing image sources before stable reveal", async () => {
  const barrier = await readAsset("barrier.js");
  const css = await readAsset("barrier.css");
  new vm.Script(barrier);
  assert.match(barrier, /barrier-ready/u);
  assert.doesNotMatch(barrier, /__gloshDagVideoFluidTransportRunner/u);
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
  assert.match(barrier, /const stableImageSources = new WeakMap\(\)/u);
  assert.match(barrier, /const diagnosticSourceIdsByImage = new WeakMap\(\)/u);
  assert.match(barrier, /sourceInstance,/u);
  assert.match(barrier, /diagnosticSourceIdsByImage\.set\(image, decisionRecord\.diagnosticId\)/u);
  assert.match(barrier, /sourceInstance: diagnosticId/u);
  assert.match(barrier, /stableImageSources\.get\(image\) === source/u);
  assert.match(barrier, /stableImageSources\.set\(image, source\)/u);
  assert.match(barrier, /if \(stableSourceUnchanged\) continue/u);
  assert.match(barrier, /record\.target instanceof HTMLSourceElement/u);
  assert.match(barrier, /attributeFilter: \["src", "srcset", "sizes", "media", "type"\]/u);
  assert.match(
    barrier,
    /if \(hasInlineImageSource\(record\.target\)\) \{\s*settleInlineSourceMutation\(record\.target\)/u,
  );
  assert.match(barrier, /reportImagePriority\(record\.target, priority, declaredNetworkImageSource\(record\.target\)\)/u);
  assert.match(barrier, /const declaredNetworkImageSource/u);
  assert.match(
    barrier,
    /const settleInlineSourceMutation = \(image\) => \{\s*resetImage\(image\);\s*observeImage\(image\)/u,
  );
  assert.match(barrier, /\/\(\?:data:image\|blob:\)\/iu\.test\(pictureSourceSet\)/u);
  assert.ok(
    barrier.indexOf("if (hasInlineImageSource(record.target))") <
      barrier.indexOf("const stableSourceUnchanged"),
    "an inline source mutation must revoke the previous stable source before currentSrc can lag",
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
  assert.match(background, /const recordCachedDecisionDiagnostic/u);
  assert.match(background, /decisionCacheHit: true/u);
  assert.match(background, /sourceInstance:\s*typeof details\?\.sourceInstance/u);
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

test("presentation guard blocks unsafe APIs and resists method replacement", async () => {
  const attributes = new Map();
  class HTMLVideoElement {
    requestPictureInPicture() {
      return Promise.resolve("unsafe");
    }
    webkitSetPresentationMode(mode) {
      return `allowed:${mode}`;
    }
  }
  class RemotePlayback {
    prompt() {
      return Promise.resolve("unsafe");
    }
  }
  const documentPictureInPicturePrototype = {
    requestWindow() {
      return Promise.resolve("unsafe");
    },
  };
  const documentPictureInPicture = Object.create(documentPictureInPicturePrototype);
  const document = {
    documentElement: {
      getAttribute: (name) => attributes.get(name) ?? null,
      setAttribute: (name, value) => attributes.set(name, value),
    },
  };
  class MutationObserver {
    observe() {}
  }
  const context = {
    DOMException,
    HTMLVideoElement,
    MutationObserver,
    RemotePlayback,
    document,
    documentPictureInPicture,
  };
  vm.runInNewContext(await readAsset("presentation-guard.js"), context, {
    filename: "presentation-guard.js",
  });

  const video = new HTMLVideoElement();
  const remote = new RemotePlayback();
  await assert.rejects(video.requestPictureInPicture(), { name: "NotAllowedError" });
  await assert.rejects(remote.prompt(), { name: "NotAllowedError" });
  await assert.rejects(documentPictureInPicture.requestWindow(), { name: "NotAllowedError" });
  assert.throws(() => video.webkitSetPresentationMode("fullscreen"), { name: "NotAllowedError" });
  assert.throws(() => video.webkitSetPresentationMode("picture-in-picture"), { name: "NotAllowedError" });
  assert.equal(video.webkitSetPresentationMode("inline"), "allowed:inline");

  const lockedPictureInPicture = video.requestPictureInPicture;
  const lockedRemotePrompt = remote.prompt;
  try { HTMLVideoElement.prototype.requestPictureInPicture = () => Promise.resolve("replaced"); } catch {}
  try { RemotePlayback.prototype.prompt = () => Promise.resolve("replaced"); } catch {}
  assert.equal(video.requestPictureInPicture, lockedPictureInPicture);
  assert.equal(remote.prompt, lockedRemotePrompt);
  assert.equal(document.documentElement.getAttribute("data-glosh-dag-presentation-guard"), "1");
});

test("extension manifest installs presentation and scheduler guards in the main world", async () => {
  const manifest = JSON.parse(await readAsset("manifest.json"));
  const schedulerScript = manifest.content_scripts.find((script) =>
    script.js?.includes("runaway-scheduler-guard.js"));
  assert.ok(schedulerScript);
  assert.equal(schedulerScript.world, "MAIN");
  assert.equal(schedulerScript.all_frames, true);
  assert.equal(schedulerScript.match_about_blank, true);
  assert.ok(schedulerScript.js.indexOf("presentation-guard.js") <
    schedulerScript.js.indexOf("runaway-scheduler-guard.js"));

  const protectionScript = manifest.content_scripts.find((script) =>
    script.js?.includes("barrier.js"));
  assert.ok(protectionScript);
  assert.equal(protectionScript.css_origin, "user");
  assert.equal(protectionScript.js.includes("video-fluid-transport-benchmark.js"), false);
  assert.equal(protectionScript.js.includes("video-fluid-transport-runner.js"), false);
  assert.equal(protectionScript.js.includes("video-fluid-capability.js"), false);
  assert.equal(protectionScript.js.includes("video-seek-state.js"), true);
  assert.equal(protectionScript.js.includes("video-lab-seek.js"), true);
  assert.ok(protectionScript.js.indexOf("video-protection-protocol.js") <
    protectionScript.js.indexOf("video-lab-geometry.js"));
  assert.ok(protectionScript.js.indexOf("video-lab-geometry.js") <
    protectionScript.js.indexOf("video-lab-presentation.js"));
  assert.ok(protectionScript.js.indexOf("video-lab-presentation.js") <
    protectionScript.js.indexOf("video-lab-record.js"));
  assert.ok(protectionScript.js.indexOf("video-lab-record.js") <
    protectionScript.js.indexOf("video-lab-mutations.js"));
  assert.ok(protectionScript.js.indexOf("video-lab-mutations.js") <
    protectionScript.js.indexOf("video-lab-isolation.js"));
  assert.ok(protectionScript.js.indexOf("video-lab-isolation.js") <
    protectionScript.js.indexOf("video-lab-lifecycle.js"));
  assert.ok(protectionScript.js.indexOf("video-lab-lifecycle.js") <
    protectionScript.js.indexOf("video-lab-playback.js"));
  assert.ok(protectionScript.js.indexOf("video-lab-playback.js") <
    protectionScript.js.indexOf("video-lab-capture.js"));
  assert.ok(protectionScript.js.indexOf("video-lab-capture.js") <
    protectionScript.js.indexOf("video-lab-viewport.js"));
  assert.ok(protectionScript.js.indexOf("video-lab-viewport.js") <
    protectionScript.js.indexOf("video-lab-diagnostics.js"));
  assert.ok(protectionScript.js.indexOf("video-lab-diagnostics.js") <
    protectionScript.js.indexOf("video-bootstrap-state.js"));
  assert.ok(protectionScript.js.indexOf("video-bootstrap-state.js") <
    protectionScript.js.indexOf("video-lab-bootstrap.js"));
  assert.ok(protectionScript.js.indexOf("video-lab-bootstrap.js") <
    protectionScript.js.indexOf("video-seek-state.js"));
  assert.ok(protectionScript.js.indexOf("video-seek-state.js") <
    protectionScript.js.indexOf("video-lab-seek.js"));
  assert.ok(protectionScript.js.indexOf("video-lab-seek.js") <
    protectionScript.js.indexOf("video-source-bootstrap.js"));
  assert.ok(protectionScript.js.indexOf("video-source-bootstrap.js") <
    protectionScript.js.indexOf("video-authority-selection.js"));
  assert.ok(protectionScript.js.indexOf("video-authority-selection.js") <
    protectionScript.js.indexOf("video-lab.js"));
  assert.ok(protectionScript.js.indexOf("video-lab.js") < protectionScript.js.indexOf("barrier.js"));
  assert.ok(protectionScript.js.indexOf("barrier.js") < protectionScript.js.indexOf("ads.js"));
});

test("video diagnostics expose only a bounded relative ordering timeline", async () => {
  const videoIsolation = await readAsset("video-lab-isolation.js");
  const videoSourceBootstrap = await readAsset("video-source-bootstrap.js");
  const videoViewport = await readAsset("video-lab-viewport.js");
  const videoLab = await readAsset("video-lab.js");
  assert.match(videoLab, /elapsedMillis: Math\.min\(120_000/u);
  assert.match(videoSourceBootstrap, /timeline_video_seen_no_backing/u);
  assert.match(videoSourceBootstrap, /timeline_current_src_assigned/u);
  assert.match(videoSourceBootstrap, /timeline_src_attribute_assigned/u);
  assert.match(videoSourceBootstrap, /timeline_src_object_assigned/u);
  assert.match(videoSourceBootstrap, /timeline_source_child_assigned/u);
  assert.match(videoIsolation, /timeline_safe_pause_no_backing/u);
  assert.match(videoIsolation, /timeline_isolation_enforced/u);
  assert.match(videoLab, /timeline_source_mutation/u);
  assert.match(videoViewport, /timeline_reflow_observed/u);
  assert.doesNotMatch(videoLab, /elapsedMillis:[^\n]*(currentSrc|srcAttribute|srcObject)/u);
});

test("video geometry keeps source and viewport identity exact", async () => {
  const context = { globalThis: null };
  context.globalThis = context;
  vm.runInNewContext(await readAsset("video-lab-geometry.js"), context);
  const geometry = context.__gloshDagVideoLabGeometry;
  const sources = [
    { getAttribute: (name) => name === "src" ? "part-a.webm" : "video/webm" },
    { getAttribute: (name) => name === "src" ? "part-b.webm" : "video/webm" },
  ];
  const video = {
    currentSrc: "blob:opaque",
    getAttribute: (name) => name === "src" ? "fallback.mp4" : null,
    getBoundingClientRect: () => ({ bottom: 90, height: 80, left: -10, right: 110, top: 10, width: 120 }),
    isConnected: true,
    querySelectorAll: () => sources,
    srcObject: null,
  };

  assert.equal(geometry.hasBackingMedia(video), true);
  assert.equal(geometry.visibleArea(video, 100, 100), 8_000);
  assert.deepEqual(JSON.parse(JSON.stringify(geometry.rectPayload(video, 100, 100))), {
    left: -10,
    top: 10,
    width: 120,
    height: 80,
    viewportWidth: 100,
    viewportHeight: 100,
  });
  const first = geometry.viewportSignature(video, 100, 100, {
    width: 100,
    height: 90,
    offsetLeft: 0,
    offsetTop: 10,
    scale: 1,
  });
  const rectChanged = [...first];
  rectChanged[7] = 1;
  const viewportChanged = [...first];
  viewportChanged[1] = 101;
  assert.equal(geometry.sameViewportSignature(first, [...first]), true);
  assert.equal(geometry.sameViewportSignature(first, rectChanged), false);
  assert.equal(geometry.sameVideoRect(first, rectChanged), false);
  assert.equal(geometry.sameViewportBounds(first, rectChanged), true);
  assert.equal(geometry.sameVideoRect(first, viewportChanged), true);
  assert.equal(geometry.sameViewportBounds(first, viewportChanged), false);
  assert.match(geometry.sourceSignature(video), /^blob:opaque::fallback\.mp4::false::/u);
  assert.equal(Object.isFrozen(geometry), true);
});

test("video presentation policy stays provider-neutral and fail-closed", async () => {
  const context = { console };
  context.globalThis = context;
  vm.runInNewContext(await readAsset("video-lab-presentation.js"), context);
  const policy = context.__gloshDagVideoLabPresentation;
  const attributes = new Map([["data-guard", "1"]]);
  const document = {
    documentElement: { getAttribute: (name) => attributes.get(name) ?? null },
    fullscreenElement: null,
    pictureInPictureElement: null,
  };
  const video = {
    disablePictureInPicture: false,
    disableRemotePlayback: false,
    playsInline: false,
    remote: { state: "disconnected" },
    webkitPresentationMode: "inline",
    webkitCurrentPlaybackTargetIsWireless: false,
  };
  const mutations = [];
  policy.enforceCapabilities({ video }, (_record, attribute, apply) => {
    mutations.push(attribute);
    apply();
  });
  assert.deepEqual(mutations, [
    "disablepictureinpicture",
    "disableremoteplayback",
    "playsinline",
  ]);
  assert.equal(policy.capabilityFailure(video), null);
  assert.equal(policy.guardReady(document, "data-guard", "1"), true);
  assert.equal(policy.unsafeReason({ video }, document, true), null);
  document.pictureInPictureElement = {};
  assert.equal(policy.unsafeReason({ video }, document, true), "picture_in_picture");
  assert.equal(policy.unsafeReason({ video }, document, false), "guard_unverified");
});

test("video record state starts closed and clears every pending frame resource", async () => {
  const cleared = [];
  const cancelled = [];
  const context = {
    clearTimeout: (value) => cleared.push(value),
    console,
  };
  context.globalThis = context;
  vm.runInNewContext(await readAsset("video-lab-record.js"), context);
  const state = context.__gloshDagVideoLabRecord;
  const video = { cancelVideoFrameCallback: (value) => cancelled.push(value) };
  const record = state.create(
    video,
    { muted: false, defaultMuted: false, volume: 0.75 },
    (count) => `token-${count}`,
    { phase: () => "idle" },
  );
  assert.equal(record.covered, false);
  assert.equal(record.rawFrameOpen, false);
  assert.equal(record.framePending, false);
  assert.equal(record.videoId, "video_token-2");
  assert.equal(record.revealToken, "token-4");
  record.framePending = true;
  record.frameCaptured = true;
  record.frameConcealed = true;
  record.frameAllowed = true;
  record.viewportEpoch = 4;
  state.resetFrame(record);
  assert.equal(record.framePending, false);
  assert.equal(record.frameCaptured, false);
  assert.equal(record.frameConcealed, false);
  assert.equal(record.frameAllowed, null);
  assert.equal(record.frameViewportEpoch, 4);
  record.nextCaptureTimer = 1;
  record.readinessTimer = 2;
  record.resultTimer = 3;
  record.coverTimer = 4;
  record.presentationMutationClearTimer = 5;
  record.frameCallbackId = 6;
  state.clearTimers(record);
  assert.deepEqual(cleared, [1, 2, 3, 4, 5]);
  assert.deepEqual(cancelled, [6]);
  assert.equal(record.frameCallbackId, null);
});

test("video diagnostic labels stay pure, finite and content-free", async () => {
  const context = {
    globalThis: null,
    HTMLMediaElement: {
      HAVE_NOTHING: 0,
      HAVE_METADATA: 1,
      HAVE_CURRENT_DATA: 2,
      HAVE_FUTURE_DATA: 3,
      HAVE_ENOUGH_DATA: 4,
    },
  };
  context.globalThis = context;
  vm.runInNewContext(await readAsset("video-lab-diagnostics.js"), context);
  const labels = context.__gloshDagVideoLabDiagnostics;

  assert.equal(labels.readyState({ readyState: 4 }), "play_ready_enough");
  assert.equal(labels.readyState({ readyState: 99 }), "play_ready_unknown");
  assert.equal(labels.networkState({ networkState: 2 }), "play_network_loading");
  assert.equal(labels.backingScheme("blob:opaque"), "backing_scheme_blob_media_source_like");
  assert.equal(labels.backingScheme("https://media.example.test/video"), "backing_scheme_network");
  assert.equal(labels.playError({ name: "NotAllowedError" }), "play_error_not_allowed");
  assert.equal(labels.playError({ name: "private payload" }), "play_error_unknown");
  const attempt = labels.playAttempt({
    currentSrc: "https://secret.example.test/private.mp4",
    ended: false,
    getAttribute: () => "https://secret.example.test/private.mp4",
    networkState: 2,
    paused: true,
    querySelectorAll: () => [],
    readyState: 4,
    srcObject: null,
  }, 2, true);
  assert.deepEqual(Array.from(attempt), [
    "play_generation_second",
    "play_state_paused",
    "play_state_not_ended",
    "play_ready_enough",
    "play_network_loading",
    "backing_src_attribute_present",
    "backing_current_src_present",
    "backing_src_object_absent",
    "backing_source_children_none",
    "backing_scheme_network",
    "play_source_stable",
    "play_video_tracks_none",
  ]);
  assert.equal(attempt.some((label) => label.includes("secret")), false);
  assert.equal(Object.isFrozen(labels), true);
});

test("video bootstrap replay is deterministic and fail-closed for observed event orders", async () => {
  const context = { globalThis: null };
  context.globalThis = context;
  vm.runInNewContext(await readAsset("video-bootstrap-state.js"), context);
  const create = context.__gloshDagVideoBootstrapState.create;

  const loadFirst = create();
  assert.equal(loadFirst.loadStart(true), "load_pending");
  assert.equal(loadFirst.acknowledge(true), "generation");
  assert.equal(loadFirst.beginTransition(true), "transition");
  assert.equal(loadFirst.settle(true), "stable");
  assert.equal(loadFirst.mediaReady(true), "stable");
  assert.equal(loadFirst.beginPostFrameTransition(true), "post_frame_transition");
  assert.equal(loadFirst.settle(true), "stable");
  assert.equal(loadFirst.beginPostFrameTransition(true), "terminal");

  const ackFirst = create();
  assert.equal(ackFirst.acknowledge(true), "acknowledged");
  assert.equal(ackFirst.loadStart(true), "generation");
  assert.equal(ackFirst.beginTransition(true), "transition");
  assert.equal(ackFirst.settle(true), "stable");

  const alreadyBacked = create();
  assert.equal(alreadyBacked.acknowledge(true), "acknowledged");
  assert.equal(alreadyBacked.coverReady(true), "stable");
  assert.equal(alreadyBacked.beginPostFrameTransition(true), "post_frame_transition");
  assert.equal(alreadyBacked.settle(true), "stable");

  for (const replay of [
    (state) => { state.loadStart(true); state.loadStart(true); },
    (state) => { state.acknowledge(true); state.acknowledge(true); },
    (state) => { state.loadStart(false); },
    (state) => { state.loadStart(true); state.acknowledge(true); state.beginTransition(false); },
    (state) => { state.loadStart(true); state.acknowledge(true); state.beginTransition(true); state.beginTransition(true); },
    (state) => { state.loadStart(true); state.acknowledge(true); state.beginTransition(true); state.settle(false); },
    (state) => { state.loadStart(true); state.acknowledge(true); state.beginTransition(true); state.settle(true); state.terminate(); },
  ]) {
    const state = create();
    replay(state);
    assert.equal(state.phase(), "terminal");
  }
});

test("video bootstrap surface matrix stays provider-neutral and fail-closed", async () => {
  const context = { globalThis: null };
  context.globalThis = context;
  vm.runInNewContext(await readAsset("video-bootstrap-state.js"), context);
  const create = context.__gloshDagVideoBootstrapState.create;

  const surfaceClasses = [
    {
      name: "ack then backed media with one covered geometry transition",
      expected: "stable",
      replay(state) {
        state.acknowledge(true);
        state.loadStart(true);
        state.beginTransition(true);
        state.settle(true);
        state.mediaReady(true);
      },
    },
    {
      name: "backed media then ack with one covered geometry transition",
      expected: "stable",
      replay(state) {
        state.loadStart(true);
        state.acknowledge(true);
        state.beginTransition(true);
        state.settle(true);
        state.mediaReady(true);
      },
    },
    {
      name: "source replacement during covered transition",
      expected: "terminal",
      replay(state) {
        state.loadStart(true);
        state.acknowledge(true);
        state.beginTransition(true);
        state.settle(false);
      },
    },
    {
      name: "second media generation after selection",
      expected: "terminal",
      replay(state) {
        state.loadStart(true);
        state.acknowledge(true);
        state.loadStart(true);
      },
    },
    {
      name: "unsafe capability during post-frame transition",
      expected: "terminal",
      replay(state) {
        state.loadStart(true);
        state.acknowledge(true);
        state.beginTransition(true);
        state.settle(true);
        state.beginPostFrameTransition(false);
      },
    },
  ];

  for (const surface of surfaceClasses) {
    const state = create();
    surface.replay(state);
    assert.equal(state.phase(), surface.expected, surface.name);
  }
});
