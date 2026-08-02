import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { webcrypto } from "node:crypto";
import { closeSync, existsSync, openSync } from "node:fs";
import { copyFile, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import test from "node:test";
import { fileURLToPath, pathToFileURL } from "node:url";
import vm from "node:vm";

const testRoot = dirname(fileURLToPath(import.meta.url));
const extensionRoot = resolve(testRoot, "../../main/assets/dag-protection");
const backgroundPath = join(extensionRoot, "background.js");
const barrierPath = join(extensionRoot, "barrier.js");
const barrierCssPath = join(extensionRoot, "barrier.css");

const eventChannel = () => ({
  listeners: [],
  addListener(listener) {
    this.listeners.push(listener);
  },
});

const fakeClock = () => {
  let currentMillis = 0;
  let sequence = 0;
  const timers = new Map();
  return {
    now: () => currentMillis,
    setTimeout(callback, delay = 0, ...args) {
      sequence += 1;
      timers.set(sequence, {
        dueAt: currentMillis + Math.max(0, Number(delay) || 0),
        callback,
        args,
      });
      return sequence;
    },
    clearTimeout(id) {
      timers.delete(id);
    },
    advanceBy(deltaMillis) {
      const target = currentMillis + deltaMillis;
      while (true) {
        const next = [...timers.entries()]
          .filter(([, timer]) => timer.dueAt <= target)
          .sort((left, right) => left[1].dueAt - right[1].dueAt)[0];
        if (!next) break;
        const [id, timer] = next;
        timers.delete(id);
        currentMillis = timer.dueAt;
        timer.callback(...timer.args);
      }
      currentMillis = target;
    },
  };
};

const waitFor = async (predicate, label) => {
  const deadline = Date.now() + 2_000;
  while (Date.now() < deadline) {
    const value = predicate();
    if (value) return value;
    await new Promise((resolvePromise) => setImmediate(resolvePromise));
  }
  throw new Error(`Timed out waiting for ${label}`);
};

const fetchDataUrl = async (source) => {
  if (typeof source !== "string" || !source.startsWith("data:image/")) {
    throw new Error("network_disabled_in_dag_protection_test");
  }
  const separator = source.indexOf(",");
  const metadata = source.slice(0, separator);
  const payload = source.slice(separator + 1);
  const bytes = metadata.endsWith(";base64")
    ? Uint8Array.from(Buffer.from(payload, "base64"))
    : new TextEncoder().encode(decodeURIComponent(payload));
  let delivered = false;
  return {
    ok: true,
    body: {
      getReader() {
        return {
          async read() {
            if (delivered) return { done: true, value: undefined };
            delivered = true;
            return { done: false, value: bytes };
          },
          async cancel() {},
        };
      },
    },
  };
};

const createBackgroundHarness = async () => {
  const source = await readFile(backgroundPath, "utf8");
  const clock = fakeClock();
  const runtimeMessages = eventChannel();
  const nativeMessages = eventChannel();
  const nativeDisconnects = eventChannel();
  const beforeRequests = eventChannel();
  const receivedHeaders = eventChannel();
  const removedTabs = eventChannel();
  const postedNativeMessages = [];
  const tabMessages = [];
  const filters = new Map();

  const nativePort = {
    onMessage: nativeMessages,
    onDisconnect: nativeDisconnects,
    postMessage(message) {
      postedNativeMessages.push(message);
    },
  };
  const browser = {
    runtime: {
      id: "dag-protection@glosh.local",
      onMessage: runtimeMessages,
      connectNative() {
        return nativePort;
      },
    },
    tabs: {
      onRemoved: removedTabs,
      sendMessage(tabId, message, options) {
        tabMessages.push({ tabId, message, options });
        if (message?.type === "document-token-request") {
          return Promise.resolve(undefined);
        }
        return Promise.resolve({
          type: "media-presentation-applied",
          version: 1,
          matchedCount: 1,
        });
      },
    },
    webRequest: {
      onBeforeRequest: beforeRequests,
      onHeadersReceived: receivedHeaders,
      filterResponseData(requestId) {
        const filter = {
          writes: [],
          closed: false,
          disconnected: false,
          ondata: null,
          onerror: null,
          onstop: null,
          write(data) {
            if (this.closed || this.disconnected) {
              throw new Error("write_after_stream_close");
            }
            this.writes.push(Uint8Array.from(new Uint8Array(data)));
          },
          close() {
            this.closed = true;
          },
          disconnect() {
            this.disconnected = true;
          },
        };
        filters.set(requestId, filter);
        return filter;
      },
    },
  };
  const context = vm.createContext({
    AbortController,
    ArrayBuffer,
    Date,
    URL,
    Uint8Array,
    atob,
    btoa,
    browser,
    clearTimeout: clock.clearTimeout,
    console,
    crypto: webcrypto,
    fetch: fetchDataUrl,
    performance: { now: clock.now },
    setTimeout: clock.setTimeout,
  });
  vm.runInContext(source, context, { filename: backgroundPath });

  return {
    startDocument(tabId, documentToken) {
      const sender = {
        id: browser.runtime.id,
        frameId: 0,
        tab: { id: tabId },
        url: "https://fixture.invalid/",
      };
      for (const listener of runtimeMessages.listeners) {
        listener(
          {
            type: "document-started",
            version: 1,
            documentToken,
          },
          sender,
        );
      }
    },
    removeTab(tabId) {
      for (const listener of removedTabs.listeners) {
        listener(tabId, { isWindowClosing: false });
      }
    },
    sendRuntimeMessage(message, senderOverrides = {}) {
      const sender = {
        id: browser.runtime.id,
        frameId: 0,
        tab: { id: senderOverrides.tabId ?? 7 },
        url: "https://fixture.invalid/",
        ...senderOverrides,
      };
      if (senderOverrides.tabId !== undefined && senderOverrides.tab === undefined) {
        sender.tab = { id: senderOverrides.tabId };
      }
      return runtimeMessages.listeners.map((listener) => listener(message, sender));
    },
    beforeRequest(details) {
      assert.equal(beforeRequests.listeners.length, 1);
      return beforeRequests.listeners[0](details);
    },
    filterFor(requestId) {
      return filters.get(requestId);
    },
    reservedCaptureBytes() {
      return vm.runInContext("reservedInterceptCaptureBytes", context);
    },
    async nextNativeRequest() {
      return waitFor(
        () => postedNativeMessages.findLast((message) => message?.type === "media-bytes"),
        "native media request",
      );
    },
    nativeRequests() {
      return postedNativeMessages.filter((message) => message?.type === "media-bytes");
    },
    nativeProtocolMessages(type) {
      return postedNativeMessages.filter((message) => message?.type === type);
    },
    async waitForNativeRequestCount(expectedCount) {
      return waitFor(
        () => {
          const requests = postedNativeMessages.filter(
            (message) => message?.type === "media-bytes",
          );
          return requests.length >= expectedCount ? requests : null;
        },
        `${expectedCount} native media requests`,
      );
    },
    respondToNative(request, action, reason, dimensions = {}) {
      for (const listener of nativeMessages.listeners) {
        listener({
          type: "media-decision",
          version: 1,
          candidateId: request.candidateId,
          action,
          reason,
          ...dimensions,
        });
      }
    },
    configureDiagnostics(enabled) {
      for (const listener of nativeMessages.listeners) {
        listener({
          type: "diagnostics-config",
          version: 1,
          enabled,
        });
      }
    },
    advanceBy: clock.advanceBy,
    async flush() {
      for (let turn = 0; turn < 20; turn += 1) {
        await new Promise((resolvePromise) => setImmediate(resolvePromise));
      }
    },
    tabMessages,
  };
};

const bytesWritten = (filter) => filter.writes.map((bytes) => [...bytes]);

const stopInterceptedImage = (harness, { requestId, tabId, url, marker }) => {
  const result = harness.beforeRequest({
    requestId,
    tabId,
    frameId: 0,
    type: "image",
    url,
  });
  assert.deepEqual(Reflect.ownKeys(result), []);
  const filter = harness.filterFor(requestId);
  assert.ok(filter);
  const bytes = Uint8Array.from([0x89, 0x50, 0x4e, 0x47, marker]);
  filter.ondata({ data: bytes.buffer.slice(0) });
  filter.onstop();
  return { filter, bytes: [...bytes] };
};

const exerciseIntercept = async (outcome) => {
  const harness = await createBackgroundHarness();
  const requestId = `request-${outcome}`;
  const originalBytes = Uint8Array.from([0xff, 0xd8, 0xff, 0xe0, 0x11, 0x22]);
  harness.startDocument(7, "document_a1");
  const interceptionResult =
    harness.beforeRequest({
      requestId,
      tabId: 7,
      frameId: 0,
      type: "image",
      url: `https://assets.invalid/${outcome}.jpg`,
    });
  assert.equal(
    Reflect.ownKeys(interceptionResult).length,
    0,
    "the intercepted request should remain owned by filterResponseData",
  );
  const filter = harness.filterFor(requestId);
  assert.ok(filter, "filterResponseData must own the intercepted response");
  filter.ondata({ data: originalBytes.buffer.slice(0) });
  const beforeDecision = bytesWritten(filter);
  filter.onstop();
  const nativeRequest = await harness.nextNativeRequest();

  if (outcome === "timeout") {
    harness.advanceBy(2_500);
  } else if (outcome === "allow") {
    harness.respondToNative(nativeRequest, "allow", "model_allow");
  } else if (outcome === "dev_allow") {
    harness.respondToNative(nativeRequest, "allow", "classifier_bypassed_dev");
  } else if (outcome === "block") {
    harness.respondToNative(nativeRequest, "block", "model_filter");
  } else {
    harness.respondToNative(nativeRequest, "allow", "untrusted_reason");
  }
  await harness.flush();
  return {
    originalBytes: [...originalBytes],
    beforeDecision,
    afterDecision: bytesWritten(filter),
  };
};

test("intercepted bytes stay withheld until an authenticated allow", async () => {
  const result = await exerciseIntercept("allow");
  assert.deepEqual(
    {
      beforeDecision: result.beforeDecision,
      afterDecision: result.afterDecision,
    },
    {
      beforeDecision: [],
      afterDecision: [result.originalBytes],
    },
  );
});

test("DEV classifier bypass authenticates the same exact byte release path", async () => {
  const result = await exerciseIntercept("dev_allow");
  assert.deepEqual(result.afterDecision, [result.originalBytes]);
});

test("per-image diagnostics cross the native bridge only after the DEV handshake", async () => {
  const harness = await createBackgroundHarness();
  const tabId = 9;
  harness.startDocument(tabId, "document_d1");

  stopInterceptedImage(harness, {
    requestId: "diagnostics-disabled",
    tabId,
    url: "https://assets.invalid/diagnostics-disabled.png",
    marker: 70,
  });
  const disabledRequest = await harness.nextNativeRequest();
  harness.respondToNative(disabledRequest, "allow", "model_allow");
  await harness.flush();
  assert.equal(harness.nativeProtocolMessages("media-client-metric").length, 0);
  assert.equal(harness.nativeProtocolMessages("media-presentation-status").length, 0);

  harness.configureDiagnostics(true);
  stopInterceptedImage(harness, {
    requestId: "diagnostics-enabled",
    tabId,
    url: "https://assets.invalid/diagnostics-enabled.png",
    marker: 71,
  });
  const enabledRequests = await harness.waitForNativeRequestCount(2);
  const enabledRequest = enabledRequests.at(-1);
  harness.respondToNative(enabledRequest, "block", "model_filter");
  await harness.flush();
  assert.ok(harness.nativeProtocolMessages("media-client-metric").length >= 1);
  assert.ok(harness.nativeProtocolMessages("media-presentation-status").length >= 1);
});

test("native work is tagged with the exact current document and retired on tab close", async () => {
  const harness = await createBackgroundHarness();
  const tabId = 11;
  const firstToken = "document_a2";
  const secondToken = "document_a3";
  harness.startDocument(tabId, firstToken);

  const { filter, bytes } = stopInterceptedImage(harness, {
    requestId: "native-document-contract",
    tabId,
    url: "https://assets.invalid/document-contract.png",
    marker: 77,
  });
  const request = await harness.nextNativeRequest();
  assert.equal(request.tabId, tabId);
  assert.equal(request.documentToken, firstToken);
  assert.deepEqual(
    harness.nativeProtocolMessages("media-document-current").map(
      ({ tabId: messageTabId, documentToken }) => ({
        tabId: messageTabId,
        documentToken,
      }),
    ),
    [{ tabId, documentToken: firstToken }],
  );

  harness.startDocument(tabId, secondToken);
  harness.respondToNative(request, "allow", "model_allow");
  await harness.flush();
  assert.equal(filter.closed, true);
  assert.equal(
    bytesWritten(filter).some((written) =>
      written.length === bytes.length && written.every((byte, index) => byte === bytes[index])),
    false,
    "a decision for a retired document must not release its original bytes",
  );
  assert.equal(
    harness.nativeProtocolMessages("media-document-current").at(-1)?.documentToken,
    secondToken,
  );

  harness.removeTab(tabId);
  const retired = harness.nativeProtocolMessages("media-document-retired").at(-1);
  assert.deepEqual({
    type: retired?.type,
    version: retired?.version,
    tabId: retired?.tabId,
    documentToken: retired?.documentToken,
  }, {
    type: "media-document-retired",
    version: 1,
    tabId,
    documentToken: secondToken,
  });
});

test("an authenticated allow becomes an error when its original bytes cannot be delivered", async () => {
  const harness = await createBackgroundHarness();
  const sourceUrl = "https://assets.invalid/write-failure.png";
  harness.startDocument(13, "document_a4");
  const { filter } = stopInterceptedImage(harness, {
    requestId: "allow-write-failure",
    tabId: 13,
    url: sourceUrl,
    marker: 78,
  });
  const request = await harness.nextNativeRequest();
  filter.closed = true;
  harness.respondToNative(request, "allow", "model_allow");
  await harness.flush();

  assert.deepEqual(bytesWritten(filter), []);
  assert.equal(
    harness.tabMessages.some(
      ({ message }) => message?.sourceUrl === sourceUrl && message?.action === "error",
    ),
    true,
  );
});

for (const outcome of ["block", "error", "timeout"]) {
  test(`intercepted bytes are never released after ${outcome}`, async () => {
    const result = await exerciseIntercept(outcome);
    assert.deepEqual(result.beforeDecision, []);
    assert.deepEqual(
      result.afterDecision,
      [],
      `${outcome} must close without writing original or format-mismatched replacement bytes`,
    );
  });
}

test("a model block preserves only bounded dimensions for the exact presentation target", async () => {
  const harness = await createBackgroundHarness();
  const sourceUrl = "https://assets.invalid/filtered-banner.jpg";
  harness.startDocument(17, "document_a5");
  stopInterceptedImage(harness, {
    requestId: "filtered-banner-dimensions",
    tabId: 17,
    url: sourceUrl,
    marker: 79,
  });
  const request = await harness.nextNativeRequest();
  harness.respondToNative(request, "block", "model_filter", {
    imageWidth: 1440,
    imageHeight: 900,
  });
  await harness.flush();

  const presentation = harness.tabMessages.findLast(
    ({ message }) => message?.sourceUrl === sourceUrl && message?.action === "block",
  )?.message;
  assert.equal(presentation?.imageWidth, 1440);
  assert.equal(presentation?.imageHeight, 900);
});

test("a burst of 96 small responses is admitted without increasing the byte budget", async () => {
  const harness = await createBackgroundHarness();
  const entries = [];
  harness.startDocument(19, "document_a4");

  for (let index = 0; index < 96; index += 1) {
    const requestId = `small-burst-${index}`;
    const originalBytes = Uint8Array.from([
      0x89,
      0x50,
      0x4e,
      0x47,
      index,
      0xa5 ^ index,
    ]);
    const interceptionResult = harness.beforeRequest({
      requestId,
      tabId: 19,
      frameId: 0,
      type: "image",
      url: `https://assets.invalid/small-${index}.png`,
    });
    assert.deepEqual(
      Reflect.ownKeys(interceptionResult),
      [],
      `small response ${index + 1} must be owned instead of cancelled`,
    );
    const filter = harness.filterFor(requestId);
    assert.ok(filter, `small response ${index + 1} needs a response filter`);
    filter.ondata({ data: originalBytes.buffer.slice(0) });
    filter.onstop();
    entries.push({ filter, originalBytes: [...originalBytes] });
  }

  const responded = new Set();
  for (let turn = 0; turn < 60 && entries.some(({ filter }) => !filter.closed); turn += 1) {
    await harness.flush();
    for (const request of harness.nativeRequests()) {
      if (responded.has(request.candidateId)) continue;
      responded.add(request.candidateId);
      harness.respondToNative(request, "allow", "model_allow");
    }
  }
  await harness.flush();

  assert.equal(entries.every(({ filter }) => filter.closed), true);
  assert.equal(responded.size, 96, "all unique small resources should reach native analysis");
  entries.forEach(({ filter, originalBytes }, index) => {
    assert.deepEqual(
      bytesWritten(filter),
      [originalBytes],
      `small response ${index + 1} should release only after authenticated allow`,
    );
  });
});

test("global and per-response capture budgets fail closed and release their reservations", async () => {
  const harness = await createBackgroundHarness();
  const twoMiB = new Uint8Array(2 * 1024 * 1024).fill(0x41);
  const holdingFilters = [];
  harness.startDocument(23, "document_a5");

  for (let index = 0; index < 4; index += 1) {
    const requestId = `budget-holder-${index}`;
    assert.deepEqual(
      Reflect.ownKeys(harness.beforeRequest({
        requestId,
        tabId: 23,
        frameId: 0,
        type: "image",
        url: `https://assets.invalid/holder-${index}.jpg`,
      })),
      [],
    );
    const filter = harness.filterFor(requestId);
    filter.ondata({ data: twoMiB.buffer.slice(0) });
    assert.equal(filter.closed, false);
    holdingFilters.push(filter);
  }

  const overBudgetOriginal = Uint8Array.from([0xff, 0xd8, 0xff, 0x42]);
  assert.deepEqual(
    Reflect.ownKeys(harness.beforeRequest({
      requestId: "over-global-budget",
      tabId: 23,
      frameId: 0,
      type: "image",
      url: "https://assets.invalid/over-global-budget.jpg",
    })),
    [],
  );
  const overBudgetFilter = harness.filterFor("over-global-budget");
  overBudgetFilter.ondata({ data: overBudgetOriginal.buffer.slice(0) });
  await harness.flush();
  assert.equal(overBudgetFilter.closed, true);
  assert.equal(
    bytesWritten(overBudgetFilter).some((bytes) =>
      bytes.length === overBudgetOriginal.length &&
      bytes.every((byte, index) => byte === overBudgetOriginal[index])),
    false,
    "aggregate overflow must never release the original",
  );
  assert.equal(
    harness.tabMessages.some(
      ({ message }) =>
        message?.sourceUrl === "https://assets.invalid/over-global-budget.jpg" &&
        message?.action === "error",
    ),
    true,
    "aggregate overflow must emit a technical presentation result",
  );

  holdingFilters.forEach((filter) => filter.onerror());
  await harness.flush();

  assert.deepEqual(
    Reflect.ownKeys(harness.beforeRequest({
      requestId: "after-budget-release",
      tabId: 23,
      frameId: 0,
      type: "image",
      url: "https://assets.invalid/after-release.jpg",
    })),
    [],
  );
  const afterReleaseFilter = harness.filterFor("after-budget-release");
  afterReleaseFilter.ondata({ data: twoMiB.buffer.slice(0) });
  assert.equal(afterReleaseFilter.closed, false, "released bytes must be reusable");
  afterReleaseFilter.onerror();

  assert.deepEqual(
    Reflect.ownKeys(harness.beforeRequest({
      requestId: "per-response-overflow",
      tabId: 23,
      frameId: 0,
      type: "image",
      url: "https://assets.invalid/per-response-overflow.jpg",
    })),
    [],
  );
  const perResponseFilter = harness.filterFor("per-response-overflow");
  perResponseFilter.ondata({ data: twoMiB.buffer.slice(0) });
  const overflowTail = Uint8Array.from([0x7f]);
  perResponseFilter.ondata({ data: overflowTail.buffer.slice(0) });
  assert.equal(perResponseFilter.closed, true);
  assert.equal(
    bytesWritten(perResponseFilter).some((bytes) => bytes.length === twoMiB.byteLength),
    false,
    "per-response overflow must never release the captured prefix",
  );
});

test("capture timing freezes at response stop and excludes later queue wait", async () => {
  const harness = await createBackgroundHarness();
  harness.startDocument(29, "document_a6");
  const filters = [];

  for (let index = 0; index < 5; index += 1) {
    const requestId = `capture-timing-${index}`;
    harness.beforeRequest({
      requestId,
      tabId: 29,
      frameId: 0,
      type: "image",
      url: `https://assets.invalid/capture-timing-${index}.png`,
    });
    const filter = harness.filterFor(requestId);
    const bytes = Uint8Array.from([0x89, 0x50, 0x4e, 0x47, index]);
    filter.ondata({ data: bytes.buffer.slice(0) });
    filters.push(filter);
    if (index < 4) filter.onstop();
  }

  await harness.waitForNativeRequestCount(4);
  harness.advanceBy(10);
  filters[4].onstop();
  harness.advanceBy(1_000);
  const firstRequest = harness.nativeRequests()[0];
  harness.respondToNative(firstRequest, "allow", "model_allow");
  const allRequests = await harness.waitForNativeRequestCount(5);
  const queuedRequest = allRequests.find(
    (request) => request.sourceUrl.endsWith("/capture-timing-4.png"),
  );
  assert.ok(queuedRequest);
  assert.equal(queuedRequest.captureMillis, 10);

  for (const request of allRequests) {
    if (request.candidateId !== firstRequest.candidateId) {
      harness.respondToNative(request, "allow", "model_allow");
    }
  }
  await harness.flush();
});

test("a queued nearby intercept is promoted ahead when its trusted hint becomes visible", async () => {
  const harness = await createBackgroundHarness();
  const tabId = 41;
  const documentToken = "document_a7";
  const promotedUrl = "https://assets.invalid/promoted-visible.png";
  const nearbyUrl = "https://assets.invalid/remains-nearby.png";
  harness.startDocument(tabId, documentToken);

  for (let index = 0; index < 4; index += 1) {
    stopInterceptedImage(harness, {
      requestId: `promotion-blocker-${index}`,
      tabId,
      url: `https://assets.invalid/promotion-blocker-${index}.png`,
      marker: index,
    });
  }
  const blockers = await harness.waitForNativeRequestCount(4);
  stopInterceptedImage(harness, {
    requestId: "queued-nearby-first",
    tabId,
    url: nearbyUrl,
    marker: 10,
  });
  stopInterceptedImage(harness, {
    requestId: "queued-promoted-second",
    tabId,
    url: promotedUrl,
    marker: 11,
  });
  await harness.flush();
  assert.equal(harness.nativeRequests().length, 4);

  harness.sendRuntimeMessage(
    {
      type: "media-priority-hint",
      version: 1,
      documentToken,
      sourceUrl: promotedUrl,
      priority: "visible",
    },
    { tabId },
  );
  harness.respondToNative(blockers[0], "allow", "model_allow");
  const afterPromotion = await harness.waitForNativeRequestCount(5);
  assert.equal(afterPromotion[4].sourceUrl, promotedUrl);
  assert.equal(afterPromotion[4].priority, "visible");

  harness.respondToNative(blockers[1], "allow", "model_allow");
  const afterNearby = await harness.waitForNativeRequestCount(6);
  assert.equal(afterNearby[5].sourceUrl, nearbyUrl);
  assert.equal(afterNearby[5].priority, "nearby");

  for (const request of afterNearby.slice(2)) {
    harness.respondToNative(request, "allow", "model_allow");
  }
  await harness.flush();
});

test("visible and nearby intercept queues preserve FIFO order inside each priority", async () => {
  const harness = await createBackgroundHarness();
  const tabId = 43;
  const documentToken = "document_a8";
  const queued = [
    { name: "nearby-1", priority: "nearby", marker: 20 },
    { name: "visible-1", priority: "visible", marker: 21 },
    { name: "nearby-2", priority: "nearby", marker: 22 },
    { name: "visible-2", priority: "visible", marker: 23 },
  ].map((entry) => ({
    ...entry,
    url: `https://assets.invalid/${entry.name}.png`,
  }));
  harness.startDocument(tabId, documentToken);

  for (let index = 0; index < 4; index += 1) {
    stopInterceptedImage(harness, {
      requestId: `fifo-blocker-${index}`,
      tabId,
      url: `https://assets.invalid/fifo-blocker-${index}.png`,
      marker: index + 30,
    });
  }
  const blockers = await harness.waitForNativeRequestCount(4);
  for (const entry of queued.filter(({ priority }) => priority === "visible")) {
    harness.sendRuntimeMessage(
      {
        type: "media-priority-hint",
        version: 1,
        documentToken,
        sourceUrl: entry.url,
        priority: "visible",
      },
      { tabId },
    );
  }
  for (const entry of queued) {
    stopInterceptedImage(harness, {
      requestId: `fifo-${entry.name}`,
      tabId,
      url: entry.url,
      marker: entry.marker,
    });
    // Response-stop callbacks resolve asynchronously. Admit each synthetic
    // response before creating the next one so the assertion measures the
    // production queue's FIFO policy, not Promise scheduling in the harness.
    await harness.flush();
  }
  blockers.forEach((request) => harness.respondToNative(request, "allow", "model_allow"));
  const requests = await harness.waitForNativeRequestCount(8);
  assert.deepEqual(
    requests.slice(4).map(({ sourceUrl, priority }) => ({ sourceUrl, priority })),
    [
      { sourceUrl: queued[1].url, priority: "visible" },
      { sourceUrl: queued[3].url, priority: "visible" },
      { sourceUrl: queued[0].url, priority: "nearby" },
      { sourceUrl: queued[2].url, priority: "nearby" },
    ],
  );
  requests.slice(4).forEach((request) =>
    harness.respondToNative(request, "allow", "model_allow"));
  await harness.flush();
});

test("stale or forged priority hints cannot reorder or authorize intercepted bytes", async () => {
  const harness = await createBackgroundHarness();
  const tabId = 47;
  const staleToken = "document_a9";
  const currentToken = "document_aa";
  const sourceUrl = "https://assets.invalid/forged-priority.png";
  harness.startDocument(tabId, staleToken);
  harness.startDocument(tabId, currentToken);

  harness.sendRuntimeMessage(
    {
      type: "media-priority-hint",
      version: 1,
      documentToken: staleToken,
      sourceUrl,
      priority: "visible",
    },
    { tabId },
  );
  harness.sendRuntimeMessage(
    {
      type: "media-priority-hint",
      version: 1,
      documentToken: currentToken,
      sourceUrl,
      priority: "visible",
    },
    { tabId, id: "forged-page-script" },
  );

  const { filter, bytes } = stopInterceptedImage(harness, {
    requestId: "forged-priority",
    tabId,
    url: sourceUrl,
    marker: 44,
  });
  const request = await harness.nextNativeRequest();
  assert.equal(request.priority, "nearby");
  assert.deepEqual(bytesWritten(filter), []);
  harness.respondToNative(request, "allow", "untrusted_reason");
  await harness.flush();
  assert.equal(
    bytesWritten(filter).some((written) =>
      written.length === bytes.length && written.every((byte, index) => byte === bytes[index])),
    false,
  );
});

test("a trusted subframe inline image is bound to its current top-level document", async () => {
  const harness = await createBackgroundHarness();
  const tabId = 53;
  const topDocumentToken = "document_b0";
  const frameDocumentToken = "document_b1";
  const bytes = Uint8Array.from([0x89, 0x50, 0x4e, 0x47, 0x01]);
  harness.startDocument(tabId, topDocumentToken);

  const responses = harness.sendRuntimeMessage(
    {
      type: "media-inline-request",
      version: 1,
      documentToken: frameDocumentToken,
      sourceUrl: "blob:https://fixture.invalid/subframe-image",
      byteLength: bytes.byteLength,
      bytesBase64: Buffer.from(bytes).toString("base64"),
      priority: "visible",
    },
    { tabId, frameId: 2 },
  );
  const nativeRequest = await harness.nextNativeRequest();
  assert.equal(nativeRequest.tabId, tabId);
  assert.equal(nativeRequest.documentToken, topDocumentToken);
  assert.equal(nativeRequest.sourceUrl, "https://inline-media.glosh.local/blob");
  harness.respondToNative(nativeRequest, "allow", "model_allow");
  const response = await responses[0];
  assert.equal(response?.action, "allow");
});

test("a generated data image uses a bounded queue and an internal native identity", async () => {
  const harness = await createBackgroundHarness();
  const tabId = 57;
  const documentToken = "document_b4";
  const bytes = Uint8Array.from([0x89, 0x50, 0x4e, 0x47, 0x02]);
  const sourceUrl = `data:image/png;base64,${Buffer.from(bytes).toString("base64")}`;
  harness.startDocument(tabId, documentToken);

  const responses = harness.sendRuntimeMessage({
    type: "media-fallback-request",
    version: 1,
    documentToken,
    sourceUrl,
    priority: "visible",
  }, { tabId, frameId: 0 });
  const nativeRequest = await harness.nextNativeRequest();
  assert.equal(nativeRequest.sourceUrl, "https://inline-media.glosh.local/data");
  assert.equal(nativeRequest.byteLength, bytes.byteLength);
  harness.respondToNative(nativeRequest, "allow", "model_allow");
  const response = await responses[0];
  assert.equal(response?.sourceUrl, sourceUrl);
  assert.equal(response?.action, "allow");
});

test("inline capacity is checked before decoding an untrusted Base64 body", async () => {
  const harness = await createBackgroundHarness();
  const tabId = 59;
  const documentToken = "document_b2";
  harness.startDocument(tabId, documentToken);

  for (let index = 0; index < 4; index += 1) {
    stopInterceptedImage(harness, {
      requestId: `inline-capacity-${index}`,
      tabId,
      url: `https://assets.invalid/inline-capacity-${index}.png`,
      marker: index + 50,
    });
  }
  const blockers = await harness.waitForNativeRequestCount(4);
  const responses = harness.sendRuntimeMessage({
    type: "media-inline-request",
    version: 1,
    documentToken,
    sourceUrl: "blob:https://fixture.invalid/over-capacity",
    byteLength: 1,
    bytesBase64: "%%%%",
    priority: "visible",
  }, { tabId, frameId: 0 });

  const response = await responses[0];
  assert.equal(response?.action, "error");
  assert.equal(harness.nativeRequests().length, 4);
  blockers.forEach((request) => harness.respondToNative(request, "allow", "model_allow"));
  await harness.flush();
});

test("the targeted barrier preserves native loading and never installs a permanent page-wide rewrite", async () => {
  const script = await readFile(barrierPath, "utf8");
  const style = await readFile(barrierCssPath, "utf8");
  assert.equal(
    script.includes('setAttributeIfChanged(element, "fetchpriority", "high")'),
    false,
  );
  assert.equal(
    script.includes('setAttributeIfChanged(element, "loading", "eager")'),
    false,
  );
  assert.match(script, /setAttributeIfChanged\(element, "decoding", "async"\)/u);
  assert.match(script, /type: "media-priority-hint"/u);
  assert.match(script, /new IntersectionObserver/u);
  assert.match(script, /rootMargin: "640px 0px"/u);
  assert.match(script, /mediaPriorityObserver\?\.observe/u);
  assert.match(script, /GENERATED_PROTOCOLS = new Set\(\["data:", "blob:"\]\)/u);
  assert.match(script, /const flushLayoutWork = \(\) => \{/u);
  assert.match(script, /mediaElements\.forEach\(boundsFor\);/u);
  assert.match(script, /sendPriorityHint\([^\n]+, bounds\);/u);
  assert.match(script, /setTimeout\(flushLayoutWork, 48\)/u);
  assert.doesNotMatch(script, /data-glosh-dag-media-host/u);
  assert.doesNotMatch(script, /attachHost|reconcileHost|hostByElement/u);
  assert.doesNotMatch(script, /scheduleScrollBackgroundProbe/u);
  assert.doesNotMatch(script, /MAX_BACKGROUND_PROBE_ELEMENTS/u);
  assert.match(style, /img:not\(\[data-glosh-dag-media="allow"\]\)/u);
  assert.match(style, /img\[data-glosh-dag-media="hidden"\]/u);
  assert.match(style, /img\[data-glosh-dag-media="block"\]/u);
  assert.match(style, /object-position: 99999px 99999px !important/u);
  assert.doesNotMatch(style, /^\*,/mu);
  assert.doesNotMatch(style, /list-style-image: none/u);
  assert.doesNotMatch(style, /content: none !important/u);
  assert.match(style, /prefers-reduced-motion: reduce/u);
  assert.equal(style.includes("Imagen no disponible"), false);
  assert.equal(style.includes("blur(28px)"), false);
});

test("each media state is presented on the exact element without rewriting its container", async () => {
  const script = await readFile(barrierPath, "utf8");
  const style = await readFile(barrierCssPath, "utf8");
  assert.doesNotMatch(script, /parentElement/u);
  assert.doesNotMatch(script, /MEDIA_HOST_ATTRIBUTE|elementsByHost|hostByElement/u);
  assert.match(style, /data-glosh-dag-media="block"[\s\S]*?opacity: 1 !important/u);
  const allowRule = style.slice(
    style.indexOf('img[data-glosh-dag-media="allow"]'),
    style.indexOf("}", style.indexOf('img[data-glosh-dag-media="allow"]')),
  );
  assert.doesNotMatch(allowRule, /opacity:/u);
  assert.match(style, /img\[data-glosh-dag-media="error"\]/u);
  assert.doesNotMatch(style, /data-glosh-dag-media-host/u);
  assert.doesNotMatch(style, /z-index: 2 !important/u);
});

test("late DOM insertion only releases a source with an explicit remembered decision", async () => {
  const script = await readFile(barrierPath, "utf8");
  const decisionStart = script.indexOf("const activeDecision = (sources) => {");
  const decisionEnd = script.indexOf("const sendSourcePriority", decisionStart);
  const applyStart = script.indexOf("const applyKnownDecision = (element) => {");
  const applyEnd = script.indexOf("const reconcileMediaAfterLayout", applyStart);
  const apply = script.slice(applyStart, applyEnd);

  assert.notEqual(decisionStart, -1);
  assert.match(script.slice(decisionStart, decisionEnd), /for \(const source of sources\)/u);
  assert.match(
    apply,
    /setMediaState\(element, action \|\| "hidden", activeDimensions\(sources\)\)/u,
  );
  assert.doesNotMatch(apply, /element\.complete|naturalWidth|naturalHeight/u);
});

test("capture reservations release on allow error timeout and navigation", async () => {
  const originalBytes = new Uint8Array(4_096).fill(0x5a);

  for (const outcome of ["allow", "error", "timeout", "navigation"]) {
    const harness = await createBackgroundHarness();
    harness.startDocument(31, "document_ab");
    const requestId = `release-${outcome}`;
    harness.beforeRequest({
      requestId,
      tabId: 31,
      frameId: 0,
      type: "image",
      url: `https://assets.invalid/release-${outcome}.png`,
    });
    const filter = harness.filterFor(requestId);
    filter.ondata({ data: originalBytes.buffer.slice(0) });
    assert.equal(harness.reservedCaptureBytes(), originalBytes.byteLength);

    if (outcome === "error") {
      filter.onerror();
    } else if (outcome === "navigation") {
      harness.startDocument(31, "document_ac");
    } else {
      filter.onstop();
      const request = await harness.nextNativeRequest();
      if (outcome === "allow") {
        harness.respondToNative(request, "allow", "model_allow");
      } else {
        harness.advanceBy(2_500);
      }
    }
    await harness.flush();

    assert.equal(filter.closed, true, `${outcome} must close its response stream`);
    assert.equal(
      harness.reservedCaptureBytes(),
      0,
      `${outcome} must release every reserved capture byte`,
    );
  }
});

test("the one hundred twenty-ninth active stream fails closed with a technical result", async () => {
  const harness = await createBackgroundHarness();
  const admittedFilters = [];
  harness.startDocument(37, "document_ad");

  for (let index = 0; index < 128; index += 1) {
    const requestId = `stream-ceiling-${index}`;
    const result = harness.beforeRequest({
      requestId,
      tabId: 37,
      frameId: 0,
      type: "image",
      url: `https://assets.invalid/stream-${index}.png`,
    });
    assert.deepEqual(Reflect.ownKeys(result), []);
    admittedFilters.push(harness.filterFor(requestId));
  }

  const rejectedUrl = "https://assets.invalid/stream-128.png";
  const rejected = harness.beforeRequest({
    requestId: "stream-ceiling-128",
    tabId: 37,
    frameId: 0,
    type: "image",
    url: rejectedUrl,
  });
  assert.equal(rejected.cancel, true);
  assert.deepEqual(Reflect.ownKeys(rejected), ["cancel"]);
  assert.equal(harness.filterFor("stream-ceiling-128"), undefined);
  await harness.flush();
  assert.equal(
    harness.tabMessages.some(
      ({ message }) => message?.sourceUrl === rejectedUrl && message?.action === "error",
    ),
    true,
  );

  admittedFilters.forEach((filter) => filter.onerror());
  await harness.flush();
  assert.equal(harness.reservedCaptureBytes(), 0);
});

const browserFixture = `<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8">
    <meta http-equiv="Content-Security-Policy" content="default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src data:; media-src 'none'; object-src 'none'; connect-src 'none'; frame-src 'none'">
    <link rel="stylesheet" href="barrier.css">
    <style>
      .test-visual { display: block; width: 120px; height: 120px; }
      .generated-rule {
        background-image: url("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
      }
    </style>
    <script>
      window.__dagRuntimeListeners = [];
      window.__dagRuntimeRequests = [];
      window.__dagFallbackActions = new Map();
      window.browser = {
        runtime: {
          id: "dag-protection@glosh.local",
          onMessage: {
            addListener(listener) {
              window.__dagRuntimeListeners.push(listener);
            },
          },
          connectNative() {
            return {
              postMessage() {},
              onMessage: { addListener() {} },
              onDisconnect: { addListener() {} },
            };
          },
          sendMessage(message) {
            window.__dagRuntimeRequests.push(message);
            if (message?.type === "media-fallback-request") {
              return Promise.resolve({
                type: "media-fallback-response",
                version: 1,
                sourceUrl: message.sourceUrl,
                action: window.__dagFallbackActions.get(message.sourceUrl) || "error",
              });
            }
            return Promise.resolve(undefined);
          },
        },
      };
    </script>
    <script src="barrier.js"></script>
  </head>
  <body>
    <main id="sandbox"></main>
    <pre id="dag-test-result">pending</pre>
    <script>
      (async () => {
        const results = [];
        const wait = (millis) => new Promise((resolvePromise) => setTimeout(resolvePromise, millis));
        const record = (name, passed, detail) => {
          results.push({ name, passed: Boolean(passed), detail });
        };
        const present = async (sourceUrl, action) => {
          const message = {
            type: "media-presentation-decision",
            version: 1,
            sourceUrl,
            action,
          };
          for (const listener of window.__dagRuntimeListeners) {
            const response = listener(message, {
              id: "dag-protection@glosh.local",
              frameId: 0,
              url: location.href,
            });
            if (response !== undefined) return await response;
          }
          throw new Error("presentation_listener_missing");
        };
        const sandbox = document.getElementById("sandbox");

        document.documentElement.setAttribute("data-glosh-dag-initialized", "forged");
        await wait(20);
        record(
          "page cannot forge completion of the initial visual barrier",
          document.documentElement.getAttribute("data-glosh-dag-initialized") === "true",
          document.documentElement.getAttribute("data-glosh-dag-initialized"),
        );

        const forgedMedia = document.createElement("img");
        forgedMedia.className = "test-visual";
        forgedMedia.src = "https://assets.invalid/forged-media.jpg";
        sandbox.append(forgedMedia);
        await wait(0);
        forgedMedia.setAttribute("data-glosh-dag-media", "allow");
        await wait(20);
        record(
          "page cannot forge media allow",
          getComputedStyle(forgedMedia).visibility === "hidden",
          forgedMedia.getAttribute("data-glosh-dag-media"),
        );

        const forgedVector = document.createElement("img");
        forgedVector.className = "test-visual";
        forgedVector.src = "https://assets.invalid/forged-vector.jpg";
        sandbox.append(forgedVector);
        await wait(0);
        forgedVector.setAttribute("data-glosh-dag-ui-vector", "allow");
        await wait(20);
        record(
          "page cannot forge vector allow",
          getComputedStyle(forgedVector).visibility === "hidden",
          forgedVector.getAttribute("data-glosh-dag-ui-vector"),
        );

        const svgNamespace = "http://www.w3.org/2000/svg";
        const safeVector = document.createElementNS(svgNamespace, "svg");
        safeVector.setAttribute("viewBox", "0 0 24 24");
        safeVector.append(document.createElementNS(svgNamespace, "path"));
        sandbox.append(safeVector);
        const unsafeVector = document.createElementNS(svgNamespace, "svg");
        unsafeVector.setAttribute("viewBox", "0 0 24 24");
        const externalUse = document.createElementNS(svgNamespace, "use");
        externalUse.setAttribute("href", "https://assets.invalid/sprite.svg#photo");
        unsafeVector.append(externalUse);
        sandbox.append(unsafeVector);
        await wait(20);
        record(
          "passive inline vectors remain visible while external vector imports stay closed",
          safeVector.getAttribute("data-glosh-dag-ui-vector") === "allow" &&
            getComputedStyle(safeVector).visibility === "visible" &&
            !unsafeVector.hasAttribute("data-glosh-dag-ui-vector") &&
            getComputedStyle(unsafeVector).visibility === "hidden",
          JSON.stringify({
            safe: safeVector.getAttribute("data-glosh-dag-ui-vector"),
            unsafe: unsafeVector.getAttribute("data-glosh-dag-ui-vector"),
          }),
        );

        const forgedBackground = document.createElement("div");
        forgedBackground.className = "test-visual";
        sandbox.append(forgedBackground);
        forgedBackground.setAttribute("data-glosh-dag-generated-background", "allow");
        forgedBackground.style.setProperty(
          "--glosh-dag-generated-background",
          'url("https://assets.invalid/forged-background.jpg")',
        );
        await wait(20);
        record(
          "page cannot forge css allow",
          getComputedStyle(forgedBackground).backgroundImage === "none",
          getComputedStyle(forgedBackground).backgroundImage,
        );

        const cssSource = "https://assets.invalid/intercepted-background.jpg";
        const cssBackground = document.createElement("div");
        cssBackground.className = "test-visual";
        cssBackground.style.backgroundImage = 'url("' + cssSource + '")';
        sandbox.append(cssBackground);
        await wait(650);
        const response = await present(cssSource, "allow");
        await wait(20);
        const restoredBackground = getComputedStyle(cssBackground).backgroundImage;
        record(
          "ordinary http css remains page native while its bytes stay network gated",
          response?.matchedCount === 0 &&
            !cssBackground.hasAttribute("data-glosh-dag-generated-background") &&
            restoredBackground.includes("intercepted-background.jpg"),
          JSON.stringify({
            matchedCount: response?.matchedCount,
            state: cssBackground.getAttribute("data-glosh-dag-generated-background"),
            backgroundImage: restoredBackground,
          }),
        );

        const blockedSource = "https://assets.invalid/blocked-photo.jpg";
        const filteredHost = document.createElement("div");
        filteredHost.style.cssText = "width:120px;height:120px";
        const blockedImage = document.createElement("img");
        blockedImage.className = "test-visual";
        blockedImage.src = blockedSource;
        filteredHost.append(blockedImage);
        sandbox.append(filteredHost);
        await wait(60);
        const blockResponse = await present(blockedSource, "block");
        blockedImage.dispatchEvent(new Event("error"));
        await wait(20);
        const filteredSurface = getComputedStyle(filteredHost, "::after");
        record(
          "trusted block survives decoder error with a static text-free surface",
          blockResponse?.mediaMatches === 1 &&
            blockedImage.getAttribute("data-glosh-dag-media") === "block" &&
            filteredHost.getAttribute("data-glosh-dag-media-host") === "filtered" &&
            getComputedStyle(blockedImage).visibility === "hidden" &&
            filteredSurface.content !== "none" &&
            filteredSurface.backgroundImage !== "none" &&
            filteredSurface.animationName === "none" &&
            !filteredSurface.content.includes("Imagen") &&
            !filteredSurface.content.includes("Glosh"),
          JSON.stringify({
            mediaMatches: blockResponse?.mediaMatches,
            mediaState: blockedImage.getAttribute("data-glosh-dag-media"),
            hostState: filteredHost.getAttribute("data-glosh-dag-media-host"),
            content: filteredSurface.content,
            animationName: filteredSurface.animationName,
          }),
        );

        const changedSource = "https://assets.invalid/changed-photo.jpg";
        blockedImage.setAttribute("data-src", blockedSource);
        blockedImage.src = changedSource;
        await wait(20);
        blockedImage.dispatchEvent(new Event("error"));
        await wait(20);
        const errorSurface = getComputedStyle(filteredHost, "::after");
        record(
          "a new source error is not mistaken for the previous blocked source",
          blockedImage.getAttribute("data-glosh-dag-media") === "error" &&
            filteredHost.getAttribute("data-glosh-dag-media-host") === "error" &&
            blockedImage.getAttribute("aria-description") === "Imagen no disponible" &&
            errorSurface.content !== "none" &&
            errorSurface.animationName === "none" &&
            !errorSurface.content.includes("Imagen") &&
            !errorSurface.content.includes("Glosh"),
          JSON.stringify({
            mediaState: blockedImage.getAttribute("data-glosh-dag-media"),
            hostState: filteredHost.getAttribute("data-glosh-dag-media-host"),
            description: blockedImage.getAttribute("aria-description"),
            content: errorSurface.content,
            animationName: errorSurface.animationName,
          }),
        );

        const mixedHost = document.createElement("div");
        mixedHost.style.cssText = "width:120px;height:120px";
        const pendingImage = document.createElement("img");
        pendingImage.className = "test-visual";
        const pendingSource = "https://assets.invalid/pending-product.jpg";
        pendingImage.setAttribute("data-src", pendingSource);
        const iconControl = document.createElement("button");
        const iconImage = document.createElement("img");
        iconImage.style.cssText = "width:24px;height:24px";
        const iconSource = "https://assets.invalid/icons/search.svg";
        iconImage.setAttribute("data-src", iconSource);
        iconControl.append(iconImage);
        const allowedSister = document.createElement("img");
        allowedSister.style.cssText = "width:80px;height:80px";
        const allowedSisterSource = "https://assets.invalid/allowed-sister.jpg";
        allowedSister.setAttribute("data-src", allowedSisterSource);
        mixedHost.append(pendingImage, iconControl, allowedSister);
        sandbox.append(mixedHost);
        await wait(60);
        const waitingBeforeIcon = mixedHost.getAttribute("data-glosh-dag-media-host");
        await present(iconSource, "allow");
        await present(allowedSisterSource, "allow");
        await wait(20);
        record(
          "trusted allows stay visible without clearing a pending sibling photo",
          waitingBeforeIcon === "waiting" &&
            mixedHost.getAttribute("data-glosh-dag-media-host") === "waiting" &&
            getComputedStyle(allowedSister).visibility === "visible" &&
            getComputedStyle(allowedSister).zIndex === "2" &&
            getComputedStyle(mixedHost, "::after").animationName ===
              "glosh-dag-waiting",
          JSON.stringify({
            before: waitingBeforeIcon,
            after: mixedHost.getAttribute("data-glosh-dag-media-host"),
            allowedVisibility: getComputedStyle(allowedSister).visibility,
            allowedZIndex: getComputedStyle(allowedSister).zIndex,
            animationName: getComputedStyle(mixedHost, "::after").animationName,
          }),
        );

        const generatedSource =
          "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJ" +
          "AAAADUlEQVR42mNk+M/wHwAF/gL+RrgD6wAAAABJRU5ErkJggg==";
        window.__dagFallbackActions.set(generatedSource, "allow");
        const generatedBackground = document.createElement("div");
        generatedBackground.className = "test-visual";
        generatedBackground.style.backgroundImage = 'url("' + generatedSource + '")';
        sandbox.append(generatedBackground);
        await wait(80);
        record(
          "generated raster backgrounds use the bounded fallback and restore only after allow",
          generatedBackground.getAttribute("data-glosh-dag-generated-background") === "allow" &&
            getComputedStyle(generatedBackground).backgroundImage.includes("data:image/png") &&
            window.__dagRuntimeRequests.some((message) =>
              message?.type === "media-fallback-request" &&
              message?.sourceUrl === generatedSource),
          JSON.stringify({
            state: generatedBackground.getAttribute("data-glosh-dag-generated-background"),
            backgroundImage: getComputedStyle(generatedBackground).backgroundImage,
          }),
        );

        const generatedRuleSource =
          "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwC" +
          "AAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";
        window.__dagFallbackActions.set(generatedRuleSource, "allow");
        const generatedRuleBackground = document.createElement("div");
        generatedRuleBackground.className = "test-visual generated-rule";
        sandbox.append(generatedRuleBackground);
        await wait(80);
        record(
          "generated raster backgrounds declared by css rules use the indexed fallback",
          generatedRuleBackground.getAttribute("data-glosh-dag-generated-background") === "allow" &&
            getComputedStyle(generatedRuleBackground).backgroundImage.includes("data:image/png") &&
            window.__dagRuntimeRequests.some((message) =>
              message?.type === "media-fallback-request" &&
              message?.sourceUrl === generatedRuleSource),
          JSON.stringify({
            state: generatedRuleBackground.getAttribute("data-glosh-dag-generated-background"),
            backgroundImage: getComputedStyle(generatedRuleBackground).backgroundImage,
          }),
        );

        const removedSource = "https://assets.invalid/removed-photo.jpg";
        const removedImage = document.createElement("img");
        removedImage.className = "test-visual";
        removedImage.setAttribute("data-src", removedSource);
        sandbox.append(removedImage);
        await wait(20);
        removedImage.remove();
        await wait(20);
        const removedResponse = await present(removedSource, "allow");
        record(
          "removed media releases its source binding",
          removedResponse?.matchedCount === 0 && removedResponse?.binding === "unbound",
          JSON.stringify(removedResponse),
        );

        const forgedHost = document.createElement("div");
        forgedHost.className = "test-visual";
        sandbox.append(forgedHost);
        forgedHost.setAttribute("data-glosh-dag-media-host", "filtered");
        await wait(20);
        record(
          "page cannot forge a terminal media host surface",
          !forgedHost.hasAttribute("data-glosh-dag-media-host"),
          forgedHost.getAttribute("data-glosh-dag-media-host"),
        );

        sandbox.replaceChildren();
        const encoded = btoa(JSON.stringify(results));
        document.getElementById("dag-test-result").textContent = encoded;
        document.title = results.every((result) => result.passed)
          ? "DAG_TEST_PASS"
          : "DAG_TEST_FAIL";
      })().catch((error) => {
        const encoded = btoa(JSON.stringify([{
          name: "fixture execution",
          passed: false,
          detail: String(error?.stack || error),
        }]));
        document.getElementById("dag-test-result").textContent = encoded;
        document.title = "DAG_TEST_ERROR";
      });
    </script>
  </body>
</html>`;

const findChrome = () => {
  const candidates = [
    process.env.CHROME_BIN,
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
    "/usr/bin/google-chrome",
    "/usr/bin/google-chrome-stable",
    "/usr/bin/chromium",
    "/usr/bin/chromium-browser",
  ].filter(Boolean);
  return candidates.find((candidate) => existsSync(candidate));
};

test("real DOM keeps forged attributes closed and leaves ordinary CSS to the network gate", {
  skip: process.env.DAG_PROTECTION_DOM_TEST !== "1" &&
    "set DAG_PROTECTION_DOM_TEST=1 for the external Chrome harness",
}, async () => {
  const chrome = findChrome();
  assert.ok(chrome, "Set CHROME_BIN to a Chrome or Chromium executable");
  const temporaryRoot = await mkdtemp(join(tmpdir(), "dag-protection-test-"));
  try {
    const fixturePath = join(temporaryRoot, "fixture.html");
    await Promise.all([
      copyFile(barrierPath, join(temporaryRoot, "barrier.js")),
      copyFile(barrierCssPath, join(temporaryRoot, "barrier.css")),
      writeFile(fixturePath, browserFixture, "utf8"),
    ]);
    const stdoutPath = join(temporaryRoot, "chrome.stdout");
    const stderrPath = join(temporaryRoot, "chrome.stderr");
    const stdoutFd = openSync(stdoutPath, "w");
    const stderrFd = openSync(stderrPath, "w");
    let execution;
    try {
      execution = spawnSync(chrome, [
        "--headless=new",
        "--disable-background-networking",
        "--disable-component-update",
        "--disable-default-apps",
        "--disable-gpu",
        "--disable-sync",
        "--host-resolver-rules=MAP * ~NOTFOUND",
        "--metrics-recording-only",
        "--no-default-browser-check",
        "--no-first-run",
        `--user-data-dir=${join(temporaryRoot, "profile")}`,
        "--virtual-time-budget=1500",
        "--dump-dom",
        pathToFileURL(fixturePath).href,
      ],
      {
        stdio: ["ignore", stdoutFd, stderrFd],
        timeout: 5_000,
      });
    } finally {
      closeSync(stdoutFd);
      closeSync(stderrFd);
    }
    const [chromeStdout, chromeStderr] = await Promise.all([
      readFile(stdoutPath, "utf8"),
      readFile(stderrPath, "utf8"),
    ]);
    assert.equal(
      execution.status,
      0,
      `Chrome failed: status=${execution.status} signal=${execution.signal} ` +
        `error=${execution.error || "none"} stderr=${chromeStderr || "none"}`,
    );
    const encoded = chromeStdout.match(
      /<pre id="dag-test-result">([A-Za-z0-9+/=]+)<\/pre>/u,
    )?.[1];
    assert.ok(encoded, `Missing fixture result in: ${chromeStdout.slice(-1_000)}`);
    const results = JSON.parse(Buffer.from(encoded, "base64").toString("utf8"));
    const failures = results.filter((result) => !result.passed);
    assert.deepEqual(
      failures,
      [],
      failures.map((failure) => `${failure.name}: ${failure.detail}`).join("\n"),
    );
  } finally {
    await rm(temporaryRoot, { recursive: true, force: true });
  }
});
