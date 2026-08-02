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
const MAX_ANALYSIS_BYTES = 2 * 1024 * 1024;
const MAX_INTERCEPT_CAPTURE_BYTES = MAX_ANALYSIS_BYTES;
// Modern API 29+ arm64 devices can keep lightweight response handles open, while the independent
// byte budget below remains the real memory ceiling. This admits current storefront bursts without
// allowing retained image bytes to grow with the handle count.
const MAX_ACTIVE_IMAGE_STREAMS = 128;
const MAX_INTERCEPT_CAPTURE_BUDGET_BYTES = 8 * 1024 * 1024;
const MAX_SOURCE_URL_LENGTH = 4_096;
const MAX_INLINE_IMAGE_URL_LENGTH = 512 * 1024;
const MAX_NATIVE_IN_FLIGHT = 4;
const MAX_ACTIVE_FALLBACK_ANALYSES = 2;
const MAX_QUEUED_FALLBACK_ANALYSES = 256;
const MAX_CONTENT_DECISIONS = 512;
const MAX_PRIORITY_HINTS_PER_DOCUMENT = 512;
const MAX_CONSECUTIVE_VISIBLE_INTERCEPTS = 4;
const RESPONSE_CAPTURE_TIMEOUT_MS = 5_000;
const FALLBACK_FETCH_TIMEOUT_MS = 10_000;
const NATIVE_DECISION_TIMEOUT_MS = 2_500;
const VIEWPORT_SETTLE_MS = 250;
const VIEWPORT_CAPTURE_WINDOW_MS = 750;
const NATIVE_APP = "glosh.dag.protection";
const INLINE_ANALYSIS_ORIGIN = "https://inline-media.glosh.local";
const PROTOCOL_VERSION = 1;
const PRESENTATION_DECISION_MESSAGE = "media-presentation-decision";
const PRESENTATION_APPLIED_MESSAGE = "media-presentation-applied";
const FALLBACK_REQUEST_MESSAGE = "media-fallback-request";
const FALLBACK_RESPONSE_MESSAGE = "media-fallback-response";
const INLINE_REQUEST_MESSAGE = "media-inline-request";
const INLINE_RESPONSE_MESSAGE = "media-inline-response";
const PRIORITY_HINT_MESSAGE = "media-priority-hint";
const DOCUMENT_CURRENT_MESSAGE = "media-document-current";
const DOCUMENT_RETIRED_MESSAGE = "media-document-retired";
const DIAGNOSTICS_CONFIG_MESSAGE = "diagnostics-config";
const CLIENT_METRIC_MESSAGE = "media-client-metric";
const TECHNICAL_ERROR_ACTION = "error";
const DOCUMENT_TOKEN_PATTERN = /^document_[a-f0-9]{1,16}$/;
let requestSequence = 0;
let activeImageFilters = 0;
let reservedInterceptCaptureBytes = 0;
let nativeRequestsInFlight = 0;
let presentationRequestsInFlight = 0;
let activeFallbackAnalyses = 0;
let decisionPort = null;
let decisionPortReconnectTimeout = null;
let diagnosticsEnabled = false;
const pendingNativeDecisions = new Map();
const fallbackDecisionPromises = new Map();
const contentDecisionPromises = new Map();
const contentDecisionCache = new Map();
const fallbackAnalysisQueue = [];
const visibleInterceptAnalysisQueue = [];
const nearbyInterceptAnalysisQueue = [];
const activeInterceptStreams = new Set();
const documentStatesByTab = new Map();
const documentStatesByToken = new Map();
let consecutiveVisibleIntercepts = 0;

const reserveInterceptCaptureBytes = (byteLength) => {
  if (
    !Number.isInteger(byteLength) ||
    byteLength <= 0 ||
    reservedInterceptCaptureBytes + byteLength > MAX_INTERCEPT_CAPTURE_BUDGET_BYTES
  ) {
    return false;
  }
  reservedInterceptCaptureBytes += byteLength;
  return true;
};

const releaseInterceptCaptureBytes = (byteLength) => {
  if (!Number.isInteger(byteLength) || byteLength <= 0) {
    return;
  }
  reservedInterceptCaptureBytes = Math.max(0, reservedInterceptCaptureBytes - byteLength);
};

const abortInterceptStreamsForDocument = (documentState) => {
  if (!documentState) {
    return;
  }
  documentState.priorityHints?.clear();
  for (const stream of [...activeInterceptStreams]) {
    if (stream.documentState === documentState) {
      stream.failClosed();
    }
  }
};

const abortQueuedFallbackTasksForDocument = (documentState) => {
  if (!documentState) {
    return;
  }
  for (let index = fallbackAnalysisQueue.length - 1; index >= 0; index -= 1) {
    const task = fallbackAnalysisQueue[index];
    if (task?.state !== "queued" || task.documentState !== documentState) {
      continue;
    }
    fallbackAnalysisQueue.splice(index, 1);
    task.state = "settled";
    if (fallbackDecisionPromises.get(task.key) === task) {
      fallbackDecisionPromises.delete(task.key);
    }
    releaseFallbackDocumentStates(task);
    task.resolve(TECHNICAL_ERROR_ACTION);
  }
};

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
    /^data:image\/(?:avif|gif|jpeg|jpg|png|svg\+xml|webp)(?:;base64)?,/iu.test(value)
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

const priorityHintKey = (value) => {
  if (typeof value !== "string" || value.length === 0 || value.length > MAX_SOURCE_URL_LENGTH) {
    return null;
  }
  try {
    const url = new URL(value);
    if (!["http:", "https:"].includes(url.protocol)) {
      return null;
    }
    url.hash = "";
    return url.href;
  } catch {
    return null;
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
  if (!diagnosticsEnabled || decisionPort === null) {
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
      if (
        message?.type === DIAGNOSTICS_CONFIG_MESSAGE &&
        message?.version === PROTOCOL_VERSION &&
        typeof message?.enabled === "boolean"
      ) {
        diagnosticsEnabled = message.enabled;
        return;
      }
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
        diagnosticsEnabled = false;
      }
      resolvePendingNativeDecisions();
      if (decisionPortReconnectTimeout === null) {
        decisionPortReconnectTimeout = setTimeout(() => {
          decisionPortReconnectTimeout = null;
          connectDecisionPort();
        }, 250);
      }
    });
    for (const state of documentStatesByTab.values()) {
      if (
        state?.tabId !== null &&
        documentStatesByToken.get(state?.documentToken) === state &&
        documentStatesByTab.get(state.tabId) === state
      ) {
        try {
          port.postMessage({
            type: DOCUMENT_CURRENT_MESSAGE,
            version: PROTOCOL_VERSION,
            tabId: state.tabId,
            documentToken: state.documentToken,
          });
        } catch {
          break;
        }
      }
    }
  } catch {
    decisionPort = null;
  }
};

connectDecisionPort();

const postNativeDocumentCurrent = (state) => {
  if (
    decisionPort === null ||
    state?.tabId === null ||
    !Number.isInteger(state?.tabId) ||
    !DOCUMENT_TOKEN_PATTERN.test(state?.documentToken || "")
  ) {
    return;
  }
  try {
    decisionPort.postMessage({
      type: DOCUMENT_CURRENT_MESSAGE,
      version: PROTOCOL_VERSION,
      tabId: state.tabId,
      documentToken: state.documentToken,
    });
  } catch {
    // The reconnect path replays every current document before accepting more work.
  }
};

const postNativeDocumentRetired = (tabId, documentToken) => {
  if (
    decisionPort === null ||
    !Number.isInteger(tabId) ||
    !DOCUMENT_TOKEN_PATTERN.test(documentToken || "")
  ) {
    return;
  }
  try {
    decisionPort.postMessage({
      type: DOCUMENT_RETIRED_MESSAGE,
      version: PROTOCOL_VERSION,
      tabId,
      documentToken,
    });
  } catch {
    // A disconnected port cannot retain useful native work for the retired document.
  }
};

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
  if (!diagnosticsEnabled || !isCurrentDocumentState(state)) {
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
    abortInterceptStreamsForDocument(previous);
    abortQueuedFallbackTasksForDocument(previous);
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
    priorityHints: new Map(),
    settleTimeout: null,
    captureTimeout: null,
  };
  if (tabId !== null) {
    documentStatesByTab.set(tabId, state);
  }
  documentStatesByToken.set(documentToken, state);
  postNativeDocumentCurrent(state);
  return state;
};

const bindDocumentStateToTab = (state, tabId) => {
  if (!isCurrentDocumentState(state) || !Number.isInteger(tabId) || tabId < 0) {
    return null;
  }
  const previous = documentStatesByTab.get(tabId);
  if (previous && previous !== state) {
    abortInterceptStreamsForDocument(previous);
    abortQueuedFallbackTasksForDocument(previous);
    clearDocumentTimers(previous);
    documentStatesByToken.delete(previous.documentToken);
  }
  if (state.tabId !== null && state.tabId !== tabId) {
    documentStatesByTab.delete(state.tabId);
  }
  state.tabId = tabId;
  documentStatesByTab.set(tabId, state);
  postNativeDocumentCurrent(state);
  scheduleViewportReady(state);
  return state;
};

const currentDocumentStateForTab = (tabId) => {
  const state = Number.isInteger(tabId) ? documentStatesByTab.get(tabId) : null;
  return isCurrentDocumentState(state) ? state : null;
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

const currentDocumentForSender = (sender, claimedDocumentToken) => {
  const tabId = senderTabId(sender);
  if (tabId === null || !Number.isInteger(sender?.frameId) || sender.frameId < 0) {
    return null;
  }
  const state = currentDocumentStateForTab(tabId);
  if (!state || (sender.frameId === 0 && state.documentToken !== claimedDocumentToken)) {
    return null;
  }
  return state;
};

const removeQueuedInterceptTask = (task) => {
  for (const queue of [visibleInterceptAnalysisQueue, nearbyInterceptAnalysisQueue]) {
    const index = queue.indexOf(task);
    if (index >= 0) {
      queue.splice(index, 1);
      return true;
    }
  }
  return false;
};

const promoteInterceptTasks = (documentState, sourceKey) => {
  for (let index = 0; index < nearbyInterceptAnalysisQueue.length;) {
    const task = nearbyInterceptAnalysisQueue[index];
    if (
      task?.state === "queued" &&
      task.documentState === documentState &&
      task.sourceKey === sourceKey
    ) {
      nearbyInterceptAnalysisQueue.splice(index, 1);
      task.priority = "visible";
      visibleInterceptAnalysisQueue.push(task);
    } else {
      index += 1;
    }
  }
};

const rememberInterceptPriorityHint = (documentState, sourceKey, priority) => {
  if (!isCurrentDocumentState(documentState)) {
    return false;
  }
  const previous = documentState.priorityHints.get(sourceKey);
  if (previous === "visible" || previous === priority) {
    return true;
  }
  if (
    previous === undefined &&
    documentState.priorityHints.size >= MAX_PRIORITY_HINTS_PER_DOCUMENT
  ) {
    documentState.priorityHints.delete(documentState.priorityHints.keys().next().value);
  }
  documentState.priorityHints.set(sourceKey, priority);
  if (priority === "visible") {
    promoteInterceptTasks(documentState, sourceKey);
  }
  return true;
};

const interceptPriorityFor = (documentState, sourceKey) =>
  documentState?.priorityHints.get(sourceKey) === "visible" ? "visible" : "nearby";

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
  if (message?.type === PRIORITY_HINT_MESSAGE) {
    const sourceKey = priorityHintKey(message?.sourceUrl);
    const tabId = senderTabId(sender);
    const documentState = documentStatesByToken.get(message?.documentToken);
    const valid =
      message?.version === PROTOCOL_VERSION &&
      isTrustedContentSender(sender) &&
      sender.frameId === 0 &&
      DOCUMENT_TOKEN_PATTERN.test(message?.documentToken || "") &&
      ["visible", "nearby"].includes(message?.priority) &&
      sourceKey !== null &&
      tabId !== null &&
      documentState?.tabId === tabId &&
      isCurrentDocumentState(documentState);
    if (!valid) {
      return undefined;
    }
    rememberInterceptPriorityHint(documentState, sourceKey, message.priority);
    // A priority hint only reorders work. It never returns or creates a media decision.
    return undefined;
  }
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
    const senderTab = senderTabId(sender);
    const currentDocumentState = currentDocumentForSender(sender, message.documentToken);
    if (!currentDocumentState || senderTab === null) {
      return Promise.resolve({
        type: INLINE_RESPONSE_MESSAGE,
        version: PROTOCOL_VERSION,
        sourceUrl,
        action: TECHNICAL_ERROR_ACTION,
      });
    }
    if (nativeRequestsInFlight >= MAX_NATIVE_IN_FLIGHT) {
      return Promise.resolve({
        type: INLINE_RESPONSE_MESSAGE,
        version: PROTOCOL_VERSION,
        sourceUrl,
        action: TECHNICAL_ERROR_ACTION,
      });
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
    const documentState = currentDocumentState.captureWindowClosed
      ? null
      : currentDocumentState;
    nativeRequestsInFlight += 1;
    adjustInitialCounter(documentState, "nativeRequestsInFlight", 1);
    return requestContentDecision(
      { url: `${INLINE_ANALYSIS_ORIGIN}/blob`, tabId: senderTab },
      bytes,
      message?.priority === "visible" ? "visible" : "nearby",
      { transportPath: "inline", documentState: currentDocumentState },
    ).then((action) => ({
      type: INLINE_RESPONSE_MESSAGE,
      version: PROTOCOL_VERSION,
      sourceUrl,
      action,
    })).finally(() => {
      nativeRequestsInFlight = Math.max(0, nativeRequestsInFlight - 1);
      adjustInitialCounter(documentState, "nativeRequestsInFlight", -1);
      drainAnalysisQueues();
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
    const senderTab = senderTabId(sender);
    const documentState = currentDocumentForSender(sender, message.documentToken);
    if (!documentState || senderTab === null) {
      return Promise.resolve({
        type: FALLBACK_RESPONSE_MESSAGE,
        version: PROTOCOL_VERSION,
        sourceUrl,
        action: TECHNICAL_ERROR_ACTION,
      });
    }
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
    if (tabId === null) {
      return;
    }
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

browser.tabs.onRemoved?.addListener((tabId) => {
  const state = currentDocumentStateForTab(tabId);
  if (!state) {
    return;
  }
  abortInterceptStreamsForDocument(state);
  abortQueuedFallbackTasksForDocument(state);
  clearDocumentTimers(state);
  documentStatesByTab.delete(tabId);
  documentStatesByToken.delete(state.documentToken);
  postNativeDocumentRetired(tabId, state.documentToken);
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
  const documentState = context.documentState;
  if (!isCurrentDocumentState(documentState) || documentState.tabId !== details.tabId) {
    return Promise.resolve(TECHNICAL_ERROR_ACTION);
  }
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
        tabId: documentState.tabId,
        documentToken: documentState.documentToken,
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
  const documentToken = context.documentState?.documentToken || "unbound";
  const pendingKey = `${hash}:${documentToken}`;
  const pending = contentDecisionPromises.get(pendingKey);
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
      if (contentDecisionPromises.get(pendingKey) === decisionPromise) {
        contentDecisionPromises.delete(pendingKey);
      }
    });
  contentDecisionPromises.set(pendingKey, decisionPromise);
  return decisionPromise;
};

const fetchFallbackDecision = async (sourceUrl, priority, documentState) => {
  if (!isCurrentDocumentState(documentState)) {
    return TECHNICAL_ERROR_ACTION;
  }
  if (!/^data:image\/(?:avif|gif|jpeg|jpg|png|svg\+xml|webp)(?:;base64)?,/iu.test(sourceUrl)) {
    return TECHNICAL_ERROR_ACTION;
  }
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
      return TECHNICAL_ERROR_ACTION;
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
        return TECHNICAL_ERROR_ACTION;
      }
      chunks.push(value);
      totalBytes += value.byteLength;
    }
    if (totalBytes === 0) {
      return TECHNICAL_ERROR_ACTION;
    }
    return requestContentDecision(
      { url: `${INLINE_ANALYSIS_ORIGIN}/data`, tabId: documentState.tabId },
      combineChunks(chunks, totalBytes),
      priority,
      {
        transportPath: "fallback",
        fetchMillis: performance.now() - fetchStartedAt,
        documentState,
      },
    );
  } catch {
    return TECHNICAL_ERROR_ACTION;
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

const enqueueInterceptTask = (task) => {
  task.state = "queued";
  if (task.priority === "visible") {
    visibleInterceptAnalysisQueue.push(task);
  } else {
    nearbyInterceptAnalysisQueue.push(task);
  }
};

const takeNextInterceptTask = () => {
  if (
    visibleInterceptAnalysisQueue.length > 0 &&
    (nearbyInterceptAnalysisQueue.length === 0 ||
      consecutiveVisibleIntercepts < MAX_CONSECUTIVE_VISIBLE_INTERCEPTS)
  ) {
    consecutiveVisibleIntercepts += 1;
    return visibleInterceptAnalysisQueue.shift();
  }
  if (nearbyInterceptAnalysisQueue.length > 0) {
    consecutiveVisibleIntercepts = 0;
    return nearbyInterceptAnalysisQueue.shift();
  }
  consecutiveVisibleIntercepts = 0;
  return visibleInterceptAnalysisQueue.shift();
};

const drainInterceptQueue = () => {
  while (
    nativeRequestsInFlight < MAX_NATIVE_IN_FLIGHT &&
    (visibleInterceptAnalysisQueue.length > 0 || nearbyInterceptAnalysisQueue.length > 0)
  ) {
    const task = takeNextInterceptTask();
    if (!task || task.isSettled()) {
      continue;
    }
    if (!task.isCurrent()) {
      task.complete(TECHNICAL_ERROR_ACTION);
      continue;
    }
    task.state = "running";
    nativeRequestsInFlight += 1;
    adjustInitialCounter(task.initialDocumentState, "nativeRequestsInFlight", 1);
    void task
      .analyze()
      .then((action) => task.complete(action))
      .catch(() => task.complete(TECHNICAL_ERROR_ACTION))
      .finally(() => {
        nativeRequestsInFlight = Math.max(0, nativeRequestsInFlight - 1);
        adjustInitialCounter(task.initialDocumentState, "nativeRequestsInFlight", -1);
        drainAnalysisQueues();
      });
  }
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
    void fetchFallbackDecision(task.sourceUrl, task.priority, task.documentState)
      .then((action) => task.resolve(action))
      .catch(() => {
        task.resolve(TECHNICAL_ERROR_ACTION);
      })
      .finally(() => {
        if (fallbackDecisionPromises.get(task.key) === task) {
          fallbackDecisionPromises.delete(task.key);
        }
        activeFallbackAnalyses = Math.max(0, activeFallbackAnalyses - 1);
        nativeRequestsInFlight = Math.max(0, nativeRequestsInFlight - 1);
        releaseFallbackDocumentStates(task);
        drainAnalysisQueues();
      });
  }
};

const drainAnalysisQueues = () => {
  drainInterceptQueue();
  drainFallbackQueue();
};

const analyzeFallbackSource = (sourceUrl, priority, documentState = null) => {
  if (!isCurrentDocumentState(documentState)) {
    return Promise.resolve(TECHNICAL_ERROR_ACTION);
  }
  const key = `${documentState.documentToken}:${sourceUrl}`;
  const existing = fallbackDecisionPromises.get(key);
  if (existing) {
    attachFallbackDocumentState(existing, documentState);
    if (priority === "visible") {
      promoteFallbackTask(existing);
    }
    return existing.promise;
  }
  if (fallbackAnalysisQueue.length >= MAX_QUEUED_FALLBACK_ANALYSES) {
    return Promise.resolve(TECHNICAL_ERROR_ACTION);
  }
  let resolveTask;
  const promise = new Promise((resolve) => {
    resolveTask = resolve;
  });
  const task = {
    key,
    sourceUrl,
    priority,
    state: "queued",
    promise,
    resolve: resolveTask,
    documentState,
    documentStates: new Set(),
  };
  attachFallbackDocumentState(task, documentState);
  fallbackDecisionPromises.set(key, task);
  if (priority === "visible") {
    fallbackAnalysisQueue.unshift(task);
  } else {
    fallbackAnalysisQueue.push(task);
  }
  drainAnalysisQueues();
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
      if (diagnosticsEnabled && decisionPort !== null) {
        try {
          decisionPort.postMessage({
            type: "media-presentation-status",
            version: PROTOCOL_VERSION,
            action,
            frameId: details.frameId,
            matchedCount: response?.matchedCount ?? -1,
            mediaMatches: response?.mediaMatches ?? -1,
            cssMatches: response?.cssMatches ?? -1,
            binding: response?.binding || "unknown",
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

const rejectInterceptBeforeFilter = (details) => {
  const documentGeneration = currentDocumentStateForTab(details.tabId);
  const initialDocumentState =
    documentGeneration && !documentGeneration.captureWindowClosed
      ? documentGeneration
      : null;
  reportClientMediaMetric({
    transportPath: "intercept",
    priority: "nearby",
    outcome: TECHNICAL_ERROR_ACTION,
  });
  void presentDecision(
    details,
    TECHNICAL_ERROR_ACTION,
    documentGeneration,
    initialDocumentState,
  );
  return { cancel: true };
};

const interceptImageResponse = (details) => {
  if (!isSupportedSourceUrl(details.url)) {
    return { cancel: true };
  }
  const sourceKey = priorityHintKey(details.url);
  if (
    typeof browser.webRequest.filterResponseData !== "function" ||
    activeImageFilters >= MAX_ACTIVE_IMAGE_STREAMS
  ) {
    return rejectInterceptBeforeFilter(details);
  }

  let filter;
  try {
    filter = browser.webRequest.filterResponseData(details.requestId);
  } catch {
    return rejectInterceptBeforeFilter(details);
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
  let streamRecord = null;
  const documentStatePromise = documentStateForDetails(details).then((resolvedState) => {
    if (resolvedState) {
      documentGeneration = resolvedState;
      if (streamRecord) {
        streamRecord.documentState = resolvedState;
      }
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
  let reservedBytes = 0;
  let overflow = false;
  let captureComplete = false;
  let streamSettled = false;
  let ownsActiveSlot = true;
  let captureTimeout = null;
  let captureMillis = -1;

  const releaseCaptureBudget = () => {
    if (reservedBytes <= 0) {
      return;
    }
    releaseInterceptCaptureBytes(reservedBytes);
    reservedBytes = 0;
  };

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

  const writeStreamBytes = (bytes) => {
    try {
      filter.write(bytes);
      return true;
    } catch {
      return false;
    }
  };

  const settleStream = (action, originalBytes = null) => {
    if (streamSettled) {
      return;
    }
    streamSettled = true;
    if (captureTimeout !== null) {
      clearTimeout(captureTimeout);
      captureTimeout = null;
    }
    if (streamRecord?.task) {
      removeQueuedInterceptTask(streamRecord.task);
      streamRecord.task.state = "settled";
      streamRecord.task = null;
    }
    if (sourceKey !== null) {
      documentGeneration?.priorityHints.delete(sourceKey);
    }
    activeInterceptStreams.delete(streamRecord);
    const currentAction =
      action === "allow" &&
      originalBytes instanceof Uint8Array &&
      documentGeneration &&
      isCurrentDocumentState(documentGeneration)
        ? "allow"
        : action === "block"
          ? "block"
          : TECHNICAL_ERROR_ACTION;
    const deliveredAction =
      currentAction === "allow" && !writeStreamBytes(originalBytes)
        ? TECHNICAL_ERROR_ACTION
        : currentAction;
    closeStream();
    chunks.length = 0;
    totalBytes = 0;
    releaseCaptureBudget();
    void presentDecision(details, deliveredAction, documentGeneration, documentState);
    releaseActiveSlot();
  };

  streamRecord = {
    documentState: documentGeneration,
    task: null,
    failClosed: () => settleStream(TECHNICAL_ERROR_ACTION),
  };
  activeInterceptStreams.add(streamRecord);

  filter.ondata = (event) => {
    if (streamSettled || captureComplete) {
      return;
    }
    let chunk;
    try {
      chunk = new Uint8Array(event.data);
    } catch {
      settleStream(TECHNICAL_ERROR_ACTION);
      return;
    }
    if (
      overflow ||
      chunk.byteLength === 0 ||
      totalBytes + chunk.byteLength > MAX_INTERCEPT_CAPTURE_BYTES ||
      !reserveInterceptCaptureBytes(chunk.byteLength)
    ) {
      overflow = true;
      settleStream(TECHNICAL_ERROR_ACTION);
      return;
    }
    reservedBytes += chunk.byteLength;
    try {
      chunks.push(chunk.slice());
      totalBytes += chunk.byteLength;
    } catch {
      settleStream(TECHNICAL_ERROR_ACTION);
    }
  };

  filter.onerror = () => settleStream(TECHNICAL_ERROR_ACTION);
  filter.onstop = () => {
    if (streamSettled || captureComplete) {
      return;
    }
    captureComplete = true;
    captureMillis = performance.now() - captureStartedAt;
    if (captureTimeout !== null) {
      clearTimeout(captureTimeout);
      captureTimeout = null;
    }
    if (overflow || totalBytes === 0) {
      settleStream(TECHNICAL_ERROR_ACTION);
      return;
    }
    let bytes;
    try {
      bytes = chunks.length === 1 ? chunks[0] : combineChunks(chunks, totalBytes);
    } catch {
      settleStream(TECHNICAL_ERROR_ACTION);
      return;
    }
    chunks.length = 0;
    totalBytes = 0;
    void documentStatePromise.then((resolvedState) => {
      if (streamSettled) {
        return;
      }
      if (!resolvedState || !isCurrentDocumentState(resolvedState)) {
        settleStream(TECHNICAL_ERROR_ACTION);
        return;
      }
      documentGeneration = resolvedState;
      documentState = resolvedState.captureWindowClosed ? null : resolvedState;
      const priority = interceptPriorityFor(resolvedState, sourceKey);
      const task = {
        documentState: resolvedState,
        sourceKey,
        priority,
        state: "new",
        initialDocumentState: documentState,
        isSettled: () => streamSettled,
        isCurrent: () =>
          Boolean(documentGeneration && isCurrentDocumentState(documentGeneration)),
        analyze: () =>
          requestContentDecision(details, bytes, task.priority, {
            transportPath: "intercept",
            captureMillis,
            documentState: resolvedState,
          }),
        complete: (action) => settleStream(action, bytes),
      };
      streamRecord.task = task;
      enqueueInterceptTask(task);
      drainAnalysisQueues();
    }).catch(() => settleStream(TECHNICAL_ERROR_ACTION));
  };
  captureTimeout = setTimeout(
    () => settleStream(TECHNICAL_ERROR_ACTION),
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
