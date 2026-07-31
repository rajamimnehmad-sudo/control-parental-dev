"use strict";

const INTERCEPTED_RESOURCE_TYPES = new Set(["image", "imageset"]);
const BLOCKED_RESOURCE_TYPES = new Set(["media", "object"]);
const BLOCKED_MEDIA_MIME_PATTERN =
  /^(?:audio|video)\/|^application\/(?:dash\+xml|vnd\.apple\.mpegurl|x-mpegurl)/iu;
const PAGE_AD_HOSTS = new Set([
  "doubleclick.net",
  "googlesyndication.com",
  "googleadservices.com",
  "adservice.google.com",
  "adsystem.com",
  "adnxs.com",
  "amazon-adsystem.com",
  "criteo.com",
  "outbrain.com",
  "pubmatic.com",
  "rubiconproject.com",
  "taboola.com",
]);
const VIDEO_RESOURCE_TYPES = new Set(["media", "video", "audio"]);
const VIDEO_SITE_HOSTS = new Set([
  "youtube.com",
  "youtube-nocookie.com",
  "youtu.be",
  "googlevideo.com",
  "ytimg.com",
]);
const MAX_INTERCEPT_CAPTURE_BYTES = 512 * 1024;
const MAX_ANALYSIS_BYTES = 2 * 1024 * 1024;
const MAX_SOURCE_URL_LENGTH = 4_096;
const MAX_INLINE_IMAGE_URL_LENGTH = 512 * 1024;
const MAX_ACTIVE_IMAGE_FILTERS = 16;
const MAX_NATIVE_IN_FLIGHT = 10;
const MAX_ACTIVE_FALLBACK_ANALYSES = 2;
const MAX_QUEUED_FALLBACK_ANALYSES = 256;
const MAX_FALLBACK_DECISIONS = 512;
const MAX_CONTENT_DECISIONS = 512;
const RESPONSE_CAPTURE_TIMEOUT_MS = 5_000;
const FALLBACK_FETCH_TIMEOUT_MS = 10_000;
const NATIVE_DECISION_TIMEOUT_MS = 2_500;
const VIEWPORT_SETTLE_MS = 250;
const VIEWPORT_CAPTURE_WINDOW_MS = 750;
const NATIVE_APP = "glosh.dag.protection";
const PROTOCOL_VERSION = 1;
const PRESENTATION_DECISION_MESSAGE = "media-presentation-decision";
const PRESENTATION_APPLIED_MESSAGE = "media-presentation-applied";
const FALLBACK_REQUEST_MESSAGE = "media-fallback-request";
const FALLBACK_RESPONSE_MESSAGE = "media-fallback-response";
const INLINE_REQUEST_MESSAGE = "media-inline-request";
const INLINE_RESPONSE_MESSAGE = "media-inline-response";
const CLIENT_METRIC_MESSAGE = "media-client-metric";
const TECHNICAL_ERROR_ACTION = "error";
const DOCUMENT_TOKEN_PATTERN = /^[A-Za-z0-9_-]{1,80}$/;
let requestSequence = 0;
let activeImageFilters = 0;
let nativeRequestsInFlight = 0;
let presentationRequestsInFlight = 0;
let activeFallbackAnalyses = 0;
let decisionPort = null;
let decisionPortReconnectTimeout = null;
const pendingNativeDecisions = new Map();
const fallbackDecisionPromises = new Map();
const fallbackDecisionCache = new Map();
const contentDecisionPromises = new Map();
const contentDecisionCache = new Map();
const fallbackAnalysisQueue = [];
const documentStatesByTab = new Map();
const documentStatesByToken = new Map();

const hostMatches = (hostname, candidate) =>
  hostname === candidate || hostname.endsWith(`.${candidate}`);

const isVideoSiteUrl = (value) => {
  try {
    const hostname = new URL(value || "").hostname.toLowerCase();
    return [...VIDEO_SITE_HOSTS].some((candidate) => hostMatches(hostname, candidate));
  } catch {
    return false;
  }
};

const isPageAdvertisementRequest = (details) => {
  if (!details || VIDEO_RESOURCE_TYPES.has(details.type)) {
    return false;
  }
  if (details.type === "main_frame") {
    return false;
  }
  if (isVideoSiteUrl(details.documentUrl) || isVideoSiteUrl(details.originUrl)) {
    return false;
  }
  try {
    const hostname = new URL(details.url).hostname.toLowerCase();
    return [...PAGE_AD_HOSTS].some((candidate) => hostMatches(hostname, candidate));
  } catch {
    return false;
  }
};

const isSupportedSourceUrl = (value) => {
  if (typeof value !== "string" || value.length === 0) {
    return false;
  }
  if (
    value.length <= MAX_INLINE_IMAGE_URL_LENGTH &&
    /^data:image\/(?:avif|gif|jpeg|jpg|png|webp);base64,/iu.test(value)
  ) {
    return true;
  }
  if (value.length > MAX_SOURCE_URL_LENGTH) {
    return false;
  }
  try {
    return ["http:", "https:"].includes(new URL(value).protocol);
  } catch {
    return false;
  }
};

const isTrustedContentSender = (sender) =>
  sender?.id === browser.runtime.id &&
  typeof sender?.url === "string" &&
  /^(?:https?|blob):/iu.test(sender.url);

const reportClientMediaMetric = ({
  transportPath = "unknown",
  priority = "background",
  outcome = "error",
  byteLength = -1,
  hashMillis = -1,
  encodeMillis = -1,
  nativeRoundTripMillis = -1,
}) => {
  if (decisionPort === null) {
    return;
  }
  try {
    decisionPort.postMessage({
      type: CLIENT_METRIC_MESSAGE,
      version: PROTOCOL_VERSION,
      transportPath,
      priority,
      outcome,
      byteLength,
      hashMillis,
      encodeMillis,
      nativeRoundTripMillis,
    });
  } catch {
    // DEV-only numeric diagnostics never change a protection decision.
  }
};

const resolvePendingNativeDecisions = (action = TECHNICAL_ERROR_ACTION) => {
  for (const pending of pendingNativeDecisions.values()) {
    clearTimeout(pending.timeout);
    reportClientMediaMetric({
      ...pending.metric,
      outcome: action,
      nativeRoundTripMillis: performance.now() - pending.startedAt,
    });
    pending.resolve(action);
  }
  pendingNativeDecisions.clear();
};

const connectDecisionPort = () => {
  if (decisionPort !== null) {
    return;
  }
  try {
    const port = browser.runtime.connectNative(NATIVE_APP);
    decisionPort = port;
    port.onMessage.addListener((message) => {
      const pending = pendingNativeDecisions.get(message?.candidateId);
      const valid =
        pending !== undefined &&
        message?.type === "media-decision" &&
        message?.version === PROTOCOL_VERSION &&
        ["allow", "block"].includes(message?.action);
      if (!valid) {
        return;
      }
      pendingNativeDecisions.delete(message.candidateId);
      clearTimeout(pending.timeout);
      const action =
        message.action === "allow" &&
          ["model_allow", "safe_ui_vector"].includes(message.reason)
          ? "allow"
          : message.action === "block" && message.reason === "model_filter"
            ? "block"
            : TECHNICAL_ERROR_ACTION;
      reportClientMediaMetric({
        ...pending.metric,
        outcome: action,
        nativeRoundTripMillis: performance.now() - pending.startedAt,
      });
      pending.resolve(action);
    });
    port.onDisconnect.addListener(() => {
      if (decisionPort === port) {
        decisionPort = null;
      }
      resolvePendingNativeDecisions();
      if (decisionPortReconnectTimeout === null) {
        decisionPortReconnectTimeout = setTimeout(() => {
          decisionPortReconnectTimeout = null;
          connectDecisionPort();
        }, 250);
      }
    });
  } catch {
    decisionPort = null;
  }
};

connectDecisionPort();

const clearDocumentTimers = (state) => {
  if (state?.settleTimeout !== null) {
    clearTimeout(state.settleTimeout);
    state.settleTimeout = null;
  }
  if (state?.captureTimeout !== null) {
    clearTimeout(state.captureTimeout);
    state.captureTimeout = null;
  }
};

const isCurrentDocumentState = (state) =>
  state !== null &&
  state !== undefined &&
  documentStatesByToken.get(state.documentToken) === state &&
  (state.tabId === null || documentStatesByTab.get(state.tabId) === state);

const pendingInitialWork = (state) =>
  state.activeImageFilters +
  state.nativeRequestsInFlight +
  state.presentationRequestsInFlight +
  state.fallbackRequestsInFlight;

const scheduleViewportReady = (state) => {
  if (!isCurrentDocumentState(state)) {
    return;
  }
  if (state.settleTimeout !== null) {
    clearTimeout(state.settleTimeout);
    state.settleTimeout = null;
  }
  if (
    !state.documentLoaded ||
    state.tabId === null ||
    !state.captureWindowClosed ||
    state.viewportReadyReported ||
    pendingInitialWork(state) !== 0
  ) {
    return;
  }
  state.settleTimeout = setTimeout(() => {
    state.settleTimeout = null;
    if (
      !isCurrentDocumentState(state) ||
      !state.documentLoaded ||
      state.tabId === null ||
      !state.captureWindowClosed ||
      state.viewportReadyReported ||
      pendingInitialWork(state) !== 0
    ) {
      return;
    }
    if (decisionPort === null) {
      return;
    }
    try {
      decisionPort.postMessage({
        type: "viewport-images-ready",
        version: PROTOCOL_VERSION,
        documentToken: state.documentToken,
      });
      state.viewportReadyReported = true;
    } catch {
      // Performance evidence is DEV-only and never changes the fail-closed barrier.
    }
  }, VIEWPORT_SETTLE_MS);
};

const resetViewportTracking = (tabId, documentToken) => {
  const previous = tabId === null ? null : documentStatesByTab.get(tabId);
  if (previous) {
    clearDocumentTimers(previous);
    documentStatesByToken.delete(previous.documentToken);
  }
  const state = {
    tabId,
    documentToken,
    documentLoaded: false,
    captureWindowClosed: false,
    viewportReadyReported: false,
    activeImageFilters: 0,
    nativeRequestsInFlight: 0,
    presentationRequestsInFlight: 0,
    fallbackRequestsInFlight: 0,
    settleTimeout: null,
    captureTimeout: null,
  };
  if (tabId !== null) {
    documentStatesByTab.set(tabId, state);
  }
  documentStatesByToken.set(documentToken, state);
  return state;
};

const bindDocumentStateToTab = (state, tabId) => {
  if (!isCurrentDocumentState(state) || !Number.isInteger(tabId) || tabId < 0) {
    return null;
  }
  const previous = documentStatesByTab.get(tabId);
  if (previous && previous !== state) {
    clearDocumentTimers(previous);
    documentStatesByToken.delete(previous.documentToken);
  }
  if (state.tabId !== null && state.tabId !== tabId) {
    documentStatesByTab.delete(state.tabId);
  }
  state.tabId = tabId;
  documentStatesByTab.set(tabId, state);
  scheduleViewportReady(state);
  return state;
};

const currentDocumentStateForTab = (tabId) => {
  const state = Number.isInteger(tabId) ? documentStatesByTab.get(tabId) : null;
  return isCurrentDocumentState(state) ? state : null;
};

const initialDocumentStateForToken = (documentToken) => {
  const state = documentStatesByToken.get(documentToken);
  return isCurrentDocumentState(state) && !state.captureWindowClosed ? state : null;
};

const adjustInitialCounter = (state, field, delta) => {
  if (!state) {
    return;
  }
  state[field] = Math.max(0, state[field] + delta);
  scheduleViewportReady(state);
};

const senderTabId = (sender) => {
  const candidate = sender?.tab?.id ?? sender?.tabId;
  return Number.isInteger(candidate) && candidate >= 0 ? candidate : null;
};

const documentStateForDetails = (details) => {
  const current = currentDocumentStateForTab(details.tabId);
  if (current) {
    return Promise.resolve(current);
  }
  const options =
    Number.isInteger(details.frameId) && details.frameId >= 0
      ? { frameId: details.frameId }
      : undefined;
  return browser.tabs
    .sendMessage(
      details.tabId,
      { type: "document-token-request", version: PROTOCOL_VERSION },
      options,
    )
    .then((response) => {
      if (
        response?.type !== "document-token-response" ||
        response?.version !== PROTOCOL_VERSION ||
        !DOCUMENT_TOKEN_PATTERN.test(response?.documentToken || "")
      ) {
        return null;
      }
      const state = documentStatesByToken.get(response.documentToken);
      return bindDocumentStateToTab(state, details.tabId);
    })
    .catch(() => null);
};

browser.runtime.onMessage.addListener((message, sender) => {
  if (message?.type === INLINE_REQUEST_MESSAGE) {
    const sourceUrl = message?.sourceUrl;
    const declaredByteLength = message?.byteLength;
    const bytesBase64 = message?.bytesBase64;
    const valid =
      message?.version === PROTOCOL_VERSION &&
      isTrustedContentSender(sender) &&
      typeof sourceUrl === "string" &&
      sourceUrl.length <= MAX_SOURCE_URL_LENGTH &&
      sourceUrl.startsWith("blob:") &&
      Number.isInteger(declaredByteLength) &&
      declaredByteLength > 0 &&
      declaredByteLength <= MAX_ANALYSIS_BYTES &&
      typeof bytesBase64 === "string" &&
      bytesBase64.length > 0 &&
      bytesBase64.length <= Math.ceil((MAX_ANALYSIS_BYTES * 4) / 3) + 4 &&
      DOCUMENT_TOKEN_PATTERN.test(message?.documentToken || "");
    if (!valid) {
      return undefined;
    }
    let bytes;
    try {
      const binary = atob(bytesBase64);
      if (binary.length !== declaredByteLength) {
        return undefined;
      }
      bytes = Uint8Array.from(binary, (character) => character.charCodeAt(0));
    } catch {
      return undefined;
    }
    if (nativeRequestsInFlight >= MAX_NATIVE_IN_FLIGHT) {
      return Promise.resolve({
        type: INLINE_RESPONSE_MESSAGE,
        version: PROTOCOL_VERSION,
        sourceUrl,
        action: TECHNICAL_ERROR_ACTION,
      });
    }
    const documentState = initialDocumentStateForToken(message.documentToken);
    nativeRequestsInFlight += 1;
    adjustInitialCounter(documentState, "nativeRequestsInFlight", 1);
    return requestContentDecision(
      { url: sourceUrl },
      bytes,
      message?.priority === "visible" ? "visible" : "nearby",
      { transportPath: "inline" },
    ).then((action) => ({
      type: INLINE_RESPONSE_MESSAGE,
      version: PROTOCOL_VERSION,
      sourceUrl,
      action,
    })).finally(() => {
      nativeRequestsInFlight = Math.max(0, nativeRequestsInFlight - 1);
      adjustInitialCounter(documentState, "nativeRequestsInFlight", -1);
      drainFallbackQueue();
    });
  }
  if (message?.type === FALLBACK_REQUEST_MESSAGE) {
    const sourceUrl = message?.sourceUrl;
    if (
      message?.version !== PROTOCOL_VERSION ||
      !isTrustedContentSender(sender) ||
      !DOCUMENT_TOKEN_PATTERN.test(message?.documentToken || "") ||
      !isSupportedSourceUrl(sourceUrl)
    ) {
      return undefined;
    }
    const priority = message?.priority === "visible" ? "visible" : "nearby";
    const documentState =
      priority === "visible"
        ? initialDocumentStateForToken(message.documentToken)
        : null;
    return analyzeFallbackSource(sourceUrl, priority, documentState).then((action) => ({
      type: FALLBACK_RESPONSE_MESSAGE,
      version: PROTOCOL_VERSION,
      sourceUrl,
      action,
    }));
  }
  if (
    !isTrustedContentSender(sender) ||
    sender.frameId !== 0 ||
    message?.version !== PROTOCOL_VERSION ||
    !DOCUMENT_TOKEN_PATTERN.test(message?.documentToken || "")
  ) {
    return;
  }
  if (message.type === "document-started") {
    const tabId = senderTabId(sender);
    resetViewportTracking(tabId, message.documentToken);
    return;
  }
  if (message.type === "document-loaded") {
    const state = documentStatesByToken.get(message.documentToken);
    if (!isCurrentDocumentState(state)) {
      return;
    }
    state.documentLoaded = true;
    if (state.captureTimeout !== null) {
      clearTimeout(state.captureTimeout);
    }
    state.captureTimeout = setTimeout(() => {
      state.captureTimeout = null;
      if (!isCurrentDocumentState(state)) {
        return;
      }
      state.captureWindowClosed = true;
      scheduleViewportReady(state);
    }, VIEWPORT_CAPTURE_WINDOW_MS);
  }
});

const nextCandidateId = () => {
  requestSequence += 1;
  const random = crypto.getRandomValues(new Uint32Array(1))[0].toString(16);
  return `response_${requestSequence}_${random}`;
};

const combineChunks = (chunks, totalBytes) => {
  const combined = new Uint8Array(totalBytes);
  let offset = 0;
  for (const chunk of chunks) {
    combined.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return combined;
};

const encodeBase64 = (bytes) => {
  let binary = "";
  const chunkSize = 0x8000;
  for (let offset = 0; offset < bytes.byteLength; offset += chunkSize) {
    binary += String.fromCharCode(...bytes.subarray(offset, offset + chunkSize));
  }
  return btoa(binary);
};

const requestNativeDecision = (
  details,
  bytes,
  priority = "background",
  context = {},
) => {
  connectDecisionPort();
  if (decisionPort === null) {
    return Promise.resolve(TECHNICAL_ERROR_ACTION);
  }
  const id = nextCandidateId();
  const encodeStartedAt = performance.now();
  let bytesBase64;
  try {
    bytesBase64 = encodeBase64(bytes);
  } catch {
    return Promise.resolve(TECHNICAL_ERROR_ACTION);
  }
  const encodeMillis = performance.now() - encodeStartedAt;
  const metric = {
    transportPath: context.transportPath || "intercept",
    priority,
    byteLength: bytes.byteLength,
    hashMillis: context.hashMillis ?? -1,
    encodeMillis,
  };
  return new Promise((resolve) => {
    const startedAt = performance.now();
    const timeout = setTimeout(() => {
      pendingNativeDecisions.delete(id);
      reportClientMediaMetric({
        ...metric,
        outcome: "timeout",
        nativeRoundTripMillis: performance.now() - startedAt,
      });
      resolve(TECHNICAL_ERROR_ACTION);
    }, NATIVE_DECISION_TIMEOUT_MS);
    pendingNativeDecisions.set(id, { resolve, timeout, startedAt, metric });
    try {
      decisionPort.postMessage({
        type: "media-bytes",
        version: PROTOCOL_VERSION,
        candidateId: id,
        sourceUrl: details.url.startsWith("data:image/") || details.url.startsWith("blob:")
          ? `https://inline-image.glosh.local/${id}`
          : details.url,
        byteLength: bytes.byteLength,
        bytesBase64,
        priority,
        transportPath: metric.transportPath,
        captureMillis: context.captureMillis ?? -1,
        fetchMillis: context.fetchMillis ?? -1,
        hashMillis: metric.hashMillis,
        encodeMillis,
        sentAtEpochMillis: Date.now(),
      });
    } catch {
      pendingNativeDecisions.delete(id);
      clearTimeout(timeout);
      resolve(TECHNICAL_ERROR_ACTION);
    }
  });
};

const contentHash = async (bytes) => {
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest), (byte) =>
    byte.toString(16).padStart(2, "0")).join("");
};

const rememberContentDecision = (hash, action) => {
  if (!contentDecisionCache.has(hash) && contentDecisionCache.size >= MAX_CONTENT_DECISIONS) {
    contentDecisionCache.delete(contentDecisionCache.keys().next().value);
  }
  contentDecisionCache.set(hash, action);
};

const requestContentDecision = async (
  details,
  bytes,
  priority = "background",
  context = {},
) => {
  let hash;
  const hashStartedAt = performance.now();
  try {
    hash = await contentHash(bytes);
  } catch {
    return requestNativeDecision(details, bytes, priority, context);
  }
  const hashMillis = performance.now() - hashStartedAt;
  const metric = {
    transportPath: context.transportPath || "intercept",
    priority,
    byteLength: bytes.byteLength,
    hashMillis,
  };
  const cached = contentDecisionCache.get(hash);
  if (cached) {
    reportClientMediaMetric({ ...metric, outcome: "cache_hit", nativeRoundTripMillis: 0 });
    return cached;
  }
  const pending = contentDecisionPromises.get(hash);
  if (pending) {
    reportClientMediaMetric({ ...metric, outcome: "deduplicated", nativeRoundTripMillis: 0 });
    return pending;
  }
  const decisionPromise = requestNativeDecision(details, bytes, priority, {
    ...context,
    hashMillis,
  })
    .then((action) => {
      if (action !== TECHNICAL_ERROR_ACTION) {
        rememberContentDecision(hash, action);
      }
      return action;
    })
    .finally(() => {
      if (contentDecisionPromises.get(hash) === decisionPromise) {
        contentDecisionPromises.delete(hash);
      }
    });
  contentDecisionPromises.set(hash, decisionPromise);
  return decisionPromise;
};

const rememberFallbackDecision = (sourceUrl, action) => {
  if (!fallbackDecisionCache.has(sourceUrl) && fallbackDecisionCache.size >= MAX_FALLBACK_DECISIONS) {
    fallbackDecisionCache.delete(fallbackDecisionCache.keys().next().value);
  }
  fallbackDecisionCache.set(sourceUrl, action);
};

const fetchFallbackDecision = async (sourceUrl, priority) => {
  const fetchStartedAt = performance.now();
  const controller = new AbortController();
  const fetchTimeout = setTimeout(() => controller.abort(), FALLBACK_FETCH_TIMEOUT_MS);
  try {
    const response = await fetch(sourceUrl, {
      credentials: "include",
      cache: "force-cache",
      signal: controller.signal,
    });
    if (!response.ok || !response.body) {
      return "retry";
    }
    const reader = response.body.getReader();
    const chunks = [];
    let totalBytes = 0;
    while (true) {
      const { done, value } = await reader.read();
      if (done) {
        break;
      }
      if (
        !(value instanceof Uint8Array) ||
        totalBytes + value.byteLength > MAX_ANALYSIS_BYTES
      ) {
        await reader.cancel();
        return "retry";
      }
      chunks.push(value);
      totalBytes += value.byteLength;
    }
    if (totalBytes === 0) {
      return "retry";
    }
    return requestContentDecision(
      { url: sourceUrl },
      combineChunks(chunks, totalBytes),
      priority,
      {
        transportPath: "fallback",
        fetchMillis: performance.now() - fetchStartedAt,
      },
    );
  } catch {
    return "retry";
  } finally {
    clearTimeout(fetchTimeout);
  }
};

const promoteFallbackTask = (task) => {
  if (task.priority === "visible" || task.state !== "queued") {
    return;
  }
  const queueIndex = fallbackAnalysisQueue.indexOf(task);
  if (queueIndex >= 0) {
    fallbackAnalysisQueue.splice(queueIndex, 1);
    task.priority = "visible";
    fallbackAnalysisQueue.unshift(task);
  }
};

const attachFallbackDocumentState = (task, documentState) => {
  if (!documentState || task.documentStates.has(documentState)) {
    return;
  }
  task.documentStates.add(documentState);
  adjustInitialCounter(documentState, "fallbackRequestsInFlight", 1);
};

const releaseFallbackDocumentStates = (task) => {
  for (const documentState of task.documentStates) {
    adjustInitialCounter(documentState, "fallbackRequestsInFlight", -1);
  }
  task.documentStates.clear();
};

const drainFallbackQueue = () => {
  while (
    activeFallbackAnalyses < MAX_ACTIVE_FALLBACK_ANALYSES &&
    nativeRequestsInFlight < MAX_NATIVE_IN_FLIGHT &&
    fallbackAnalysisQueue.length > 0
  ) {
    const task = fallbackAnalysisQueue.shift();
    if (!task || task.state !== "queued") {
      continue;
    }
    task.state = "running";
    activeFallbackAnalyses += 1;
    nativeRequestsInFlight += 1;
    void fetchFallbackDecision(task.sourceUrl, task.priority)
      .then((action) => {
        if (action !== "retry" && action !== TECHNICAL_ERROR_ACTION) {
          rememberFallbackDecision(task.sourceUrl, action);
        }
        task.resolve(action);
      })
      .catch(() => {
        task.resolve("retry");
      })
      .finally(() => {
        if (fallbackDecisionPromises.get(task.sourceUrl) === task) {
          fallbackDecisionPromises.delete(task.sourceUrl);
        }
        activeFallbackAnalyses = Math.max(0, activeFallbackAnalyses - 1);
        nativeRequestsInFlight = Math.max(0, nativeRequestsInFlight - 1);
        releaseFallbackDocumentStates(task);
        drainFallbackQueue();
      });
  }
};

const analyzeFallbackSource = (sourceUrl, priority, documentState = null) => {
  const cached = fallbackDecisionCache.get(sourceUrl);
  if (cached) {
    return Promise.resolve(cached);
  }
  const existing = fallbackDecisionPromises.get(sourceUrl);
  if (existing) {
    attachFallbackDocumentState(existing, documentState);
    if (priority === "visible") {
      promoteFallbackTask(existing);
    }
    return existing.promise;
  }
  if (fallbackAnalysisQueue.length >= MAX_QUEUED_FALLBACK_ANALYSES) {
    return Promise.resolve("retry");
  }
  let resolveTask;
  const promise = new Promise((resolve) => {
    resolveTask = resolve;
  });
  const task = {
    sourceUrl,
    priority,
    state: "queued",
    promise,
    resolve: resolveTask,
    documentStates: new Set(),
  };
  attachFallbackDocumentState(task, documentState);
  fallbackDecisionPromises.set(sourceUrl, task);
  if (priority === "visible") {
    fallbackAnalysisQueue.unshift(task);
  } else {
    fallbackAnalysisQueue.push(task);
  }
  drainFallbackQueue();
  return promise;
};

const notifyPresentationDecision = (details, action) => {
  if (!Number.isInteger(details.tabId) || details.tabId < 0) {
    return Promise.resolve(false);
  }
  const message = {
    type: PRESENTATION_DECISION_MESSAGE,
    version: PROTOCOL_VERSION,
    sourceUrl: details.url,
    action,
  };
  const response =
    Number.isInteger(details.frameId) && details.frameId >= 0
      ? browser.tabs.sendMessage(details.tabId, message, { frameId: details.frameId })
      : browser.tabs.sendMessage(details.tabId, message);
  return response
    .then((response) => {
      if (decisionPort !== null) {
        try {
          decisionPort.postMessage({
            type: "media-presentation-status",
            version: PROTOCOL_VERSION,
            action,
            frameId: details.frameId,
            matchedCount: response?.matchedCount ?? -1,
          });
        } catch {
          // DEV presentation evidence never changes the fail-closed decision.
        }
      }
      return (
        response?.type === PRESENTATION_APPLIED_MESSAGE &&
        response?.version === PROTOCOL_VERSION
      );
    })
    .catch(() => false);
};

const presentDecision = (
  details,
  action,
  documentGeneration = null,
  initialDocumentState = null,
) => {
  if (documentGeneration && !isCurrentDocumentState(documentGeneration)) {
    return Promise.resolve(false);
  }
  presentationRequestsInFlight += 1;
  adjustInitialCounter(initialDocumentState, "presentationRequestsInFlight", 1);
  return notifyPresentationDecision(details, action).finally(() => {
    presentationRequestsInFlight = Math.max(0, presentationRequestsInFlight - 1);
    adjustInitialCounter(initialDocumentState, "presentationRequestsInFlight", -1);
  });
};

const interceptImageResponse = (details) => {
  if (!isSupportedSourceUrl(details.url)) {
    return { cancel: true };
  }
  if (
    typeof browser.webRequest.filterResponseData !== "function" ||
    activeImageFilters >= MAX_ACTIVE_IMAGE_FILTERS
  ) {
    return {};
  }

  let filter;
  try {
    filter = browser.webRequest.filterResponseData(details.requestId);
  } catch {
    return {};
  }
  activeImageFilters += 1;
  let documentGeneration = currentDocumentStateForTab(details.tabId);
  let documentState =
    documentGeneration && !documentGeneration.captureWindowClosed
      ? documentGeneration
      : null;
  let imageCounterAttached = documentState !== null;
  if (imageCounterAttached) {
    adjustInitialCounter(documentState, "activeImageFilters", 1);
  }
  const documentStatePromise = documentStateForDetails(details).then((resolvedState) => {
    if (resolvedState) {
      documentGeneration = resolvedState;
      if (!imageCounterAttached && ownsActiveSlot && !resolvedState.captureWindowClosed) {
        documentState = resolvedState;
        imageCounterAttached = true;
        adjustInitialCounter(documentState, "activeImageFilters", 1);
      }
    }
    return resolvedState;
  });

  const chunks = [];
  const captureStartedAt = performance.now();
  let totalBytes = 0;
  let overflow = false;
  let finalized = false;
  let ownsActiveSlot = true;
  let captureTimeout = null;

  const releaseActiveSlot = () => {
    if (ownsActiveSlot) {
      ownsActiveSlot = false;
      activeImageFilters = Math.max(0, activeImageFilters - 1);
      if (imageCounterAttached) {
        imageCounterAttached = false;
        adjustInitialCounter(documentState, "activeImageFilters", -1);
      }
    }
  };

  const closeStream = () => {
    try {
      filter.close();
    } catch {
      // A failed or timed-out response stream may already be closed by Gecko.
    }
  };

  const disconnectStream = () => {
    try {
      filter.disconnect();
    } catch {
      closeStream();
    }
  };

  const finalizeWithoutDecision = (disconnect = false) => {
    if (finalized) {
      return;
    }
    finalized = true;
    if (captureTimeout !== null) {
      clearTimeout(captureTimeout);
      captureTimeout = null;
    }
    if (disconnect) {
      disconnectStream();
    } else {
      closeStream();
    }
    releaseActiveSlot();
  };

  filter.ondata = (event) => {
    if (finalized) {
      return;
    }
    const chunk = new Uint8Array(event.data);
    const capturedChunk = overflow ? null : chunk.slice();
    try {
      filter.write(event.data);
    } catch {
      finalizeWithoutDecision();
      return;
    }
    if (!overflow) {
      if (totalBytes + capturedChunk.byteLength > MAX_INTERCEPT_CAPTURE_BYTES) {
        overflow = true;
        chunks.length = 0;
        totalBytes = 0;
      } else {
        chunks.push(capturedChunk);
        totalBytes += capturedChunk.byteLength;
      }
    }
  };

  filter.onerror = () => finalizeWithoutDecision();
  filter.onstop = () => {
    if (finalized) {
      return;
    }
    if (captureTimeout !== null) {
      clearTimeout(captureTimeout);
      captureTimeout = null;
    }
    finalized = true;
    closeStream();
    releaseActiveSlot();
    if (overflow || totalBytes === 0) {
      return;
    }
    const bytes = combineChunks(chunks, totalBytes);
    void documentStatePromise.then(() => {
      if (!documentGeneration || nativeRequestsInFlight >= MAX_NATIVE_IN_FLIGHT) {
        return;
      }
      const initialState =
        documentGeneration && !documentGeneration.captureWindowClosed
          ? documentGeneration
          : null;
      nativeRequestsInFlight += 1;
      adjustInitialCounter(initialState, "nativeRequestsInFlight", 1);
      requestContentDecision(details, bytes, "nearby", {
        transportPath: "intercept",
        captureMillis: performance.now() - captureStartedAt,
      })
        .then((action) =>
          presentDecision(details, action, documentGeneration, initialState))
        .catch(() =>
          presentDecision(
            details,
            TECHNICAL_ERROR_ACTION,
            documentGeneration,
            initialState,
          ))
        .finally(() => {
          nativeRequestsInFlight = Math.max(0, nativeRequestsInFlight - 1);
          adjustInitialCounter(initialState, "nativeRequestsInFlight", -1);
          drainFallbackQueue();
        });
    });
  };
  captureTimeout = setTimeout(
    () => finalizeWithoutDecision(true),
    RESPONSE_CAPTURE_TIMEOUT_MS,
  );

  return {};
};

browser.webRequest.onBeforeRequest.addListener(
  (details) => {
    if (isPageAdvertisementRequest(details)) {
      return { cancel: true };
    }
    if (INTERCEPTED_RESOURCE_TYPES.has(details.type)) {
      return interceptImageResponse(details);
    }
    if (BLOCKED_RESOURCE_TYPES.has(details.type)) {
      return { cancel: true };
    }
    return {};
  },
  { urls: ["<all_urls>"] },
  ["blocking"],
);

browser.webRequest.onHeadersReceived.addListener(
  (details) => {
    const contentType = details.responseHeaders
      ?.find((header) => header.name.toLowerCase() === "content-type")
      ?.value?.trim();
    return contentType && BLOCKED_MEDIA_MIME_PATTERN.test(contentType)
      ? { cancel: true }
      : {};
  },
  { urls: ["<all_urls>"] },
  ["blocking", "responseHeaders"],
);
