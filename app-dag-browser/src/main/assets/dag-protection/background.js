"use strict";

const ANALYZED_RESOURCE_TYPES = new Set(["image", "imageset"]);
const BLOCKED_RESOURCE_TYPES = new Set(["media", "object"]);
const BLOCKED_MEDIA_MIME_PATTERN =
  /^(?:audio|video)\/|^application\/(?:dash\+xml|vnd\.apple\.mpegurl|x-mpegurl)/iu;
const IMAGE_MIME_PATTERN = /^image\//iu;
const PAGE_AD_HOSTS = new Set([
  "doubleclick.net", "googlesyndication.com", "googleadservices.com",
  "adservice.google.com", "adsystem.com", "adnxs.com", "amazon-adsystem.com",
  "criteo.com", "outbrain.com", "pubmatic.com", "rubiconproject.com", "taboola.com",
]);
const VIDEO_RESOURCE_TYPES = new Set(["media", "video", "audio"]);
const VIDEO_SITE_HOSTS = new Set([
  "youtube.com", "youtube-nocookie.com", "youtu.be", "googlevideo.com", "ytimg.com",
]);
const NATIVE_APP = "glosh.dag.protection";
const PROTOCOL_VERSION = 2;
const MAX_IMAGE_BYTES = 2 * 1024 * 1024;
const MAX_CAPTURED_BYTES = 8 * 1024 * 1024;
const MAX_ACTIVE_STREAMS = 128;
// Covers every bounded response stream plus the content-script inline budget. Captured network
// bytes still share MAX_CAPTURED_BYTES, so accepting the full bounded burst does not uncap memory.
const MAX_QUEUED_ANALYSES = 144;
const MAX_NATIVE_IN_FLIGHT = 2;
const MAX_CACHED_DECISIONS = 512;
const MAX_CACHED_REPLACEMENT_BYTES = 2 * 1024 * 1024;
const MAX_REPLACEMENT_BYTES = 256 * 1024;
const MAX_INLINE_IMAGE_BYTES = MAX_IMAGE_BYTES;
const MAX_INLINE_DATA_URL_LENGTH = 2_800_000;
const CAPTURE_TIMEOUT_MS = 5_000;
const NATIVE_TIMEOUT_MS = 2_250;
const VIEWPORT_QUIET_MS = 250;
const MAX_PRIORITY_HINTS = 256;
const DIAGNOSTIC_FLUSH_MS = 500;
const MAX_DIAGNOSTIC_KEYS = 32;
const BLOCKED_PLACEHOLDER_BASE64 =
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNgYAAAAAMAASsJTYQAAAAASUVORK5CYII=";

let nativePort = null;
let reconnectTimer = null;
let sequence = 0;
let activeStreams = 0;
let capturedBytes = 0;
let nativeInFlight = 0;
let cachedReplacementBytes = 0;
let diagnosticsEnabled = false;
let diagnosticFlushTimer = null;
const pendingNative = new Map();
const pendingDecisions = new Map();
const analysisQueue = [];
const decisionCache = new Map();
const imagePriorityByUrl = new Map();
const documentStatesByTab = new Map();
const diagnosticDrops = new Map();

const validTabId = (value) => Number.isInteger(value) && value >= 0;
const validDocumentToken = (value) =>
  typeof value === "string" && /^document_[a-f0-9]{1,16}$/u.test(value);
const newDocumentKey = () =>
  `document_${crypto.getRandomValues(new Uint32Array(1))[0].toString(16)}`;

const isCurrentDocument = (state) =>
  state !== null && state !== undefined && documentStatesByTab.get(state.tabId) === state;

const postDocumentLifecycle = (type, state) => {
  if (nativePort === null || !state) return;
  try {
    nativePort.postMessage({
      type,
      version: PROTOCOL_VERSION,
      tabId: state.tabId,
      documentKey: state.documentKey,
    });
  } catch {}
};

const settleQueuedDocumentWork = (state) => {
  for (let index = analysisQueue.length - 1; index >= 0; index -= 1) {
    const task = analysisQueue[index];
    if (task.documentState !== state) continue;
    analysisQueue.splice(index, 1);
    state.queuedAnalyses = Math.max(0, state.queuedAnalyses - 1);
    task.settle({ action: "block", replacement: null });
  }
  for (const [candidateId, pending] of pendingNative) {
    if (pending.documentState !== state) continue;
    clearTimeout(pending.timeout);
    pendingNative.delete(candidateId);
    pending.resolve({ action: "block", cacheable: false });
  }
};

const retireDocument = (state) => {
  if (!state || state.retired) return;
  state.retired = true;
  clearTimeout(state.quietTimer);
  state.quietTimer = null;
  if (documentStatesByTab.get(state.tabId) === state) documentStatesByTab.delete(state.tabId);
  for (const cancel of [...state.streamCancellers]) cancel();
  settleQueuedDocumentWork(state);
  postDocumentLifecycle("media-document-retired", state);
};

const beginDocument = (tabId) => {
  if (!validTabId(tabId)) return null;
  retireDocument(documentStatesByTab.get(tabId));
  const state = {
    tabId,
    documentKey: newDocumentKey(),
    documentToken: null,
    frameTokens: new Map(),
    documentLoaded: false,
    viewportReadyReported: false,
    activeStreams: 0,
    queuedAnalyses: 0,
    analysesInFlight: 0,
    quietTimer: null,
    retired: false,
    streamCancellers: new Set(),
  };
  documentStatesByTab.set(tabId, state);
  postDocumentLifecycle("media-document-current", state);
  return state;
};

const currentDocumentForDetails = (details) =>
  validTabId(details?.tabId) ? documentStatesByTab.get(details.tabId) || null : null;

const flushDiagnosticDrops = () => {
  diagnosticFlushTimer = null;
  if (!diagnosticsEnabled || diagnosticDrops.size === 0 || nativePort === null) return;
  const events = [...diagnosticDrops.entries()].map(([key, count]) => {
    const [carrier, reason] = key.split(":", 2);
    return { carrier, reason, count };
  });
  diagnosticDrops.clear();
  try {
    nativePort.postMessage({
      type: "media-diagnostic-summary",
      version: PROTOCOL_VERSION,
      events,
      activeStreams,
      queuedAnalyses: analysisQueue.length,
      capturedBytes,
    });
  } catch {}
};

const recordDiagnosticDrop = (carrier, reason) => {
  if (!diagnosticsEnabled) return;
  const key = `${carrier}:${reason}`;
  if (diagnosticDrops.has(key) || diagnosticDrops.size < MAX_DIAGNOSTIC_KEYS) {
    diagnosticDrops.set(key, (diagnosticDrops.get(key) || 0) + 1);
  }
  if (diagnosticFlushTimer === null) {
    diagnosticFlushTimer = setTimeout(flushDiagnosticDrops, DIAGNOSTIC_FLUSH_MS);
  }
};

const decodeBase64 = (value) => {
  const binary = atob(value);
  return Uint8Array.from(binary, (character) => character.charCodeAt(0));
};
const blockedPlaceholder = decodeBase64(BLOCKED_PLACEHOLDER_BASE64);

const decodeInlineRaster = (value) => {
  if (typeof value !== "string" || value.length > MAX_INLINE_DATA_URL_LENGTH) return null;
  const match = /^data:image\/[a-z\d.+-]+(?:;charset=[a-z\d._-]+)?;base64,([a-z\d+/=\s]+)$/iu
    .exec(value);
  if (match === null) return null;
  try {
    const bytes = decodeBase64(match[1].replaceAll(/\s/gu, ""));
    return bytes.byteLength > 0 && bytes.byteLength <= MAX_INLINE_IMAGE_BYTES ? bytes : null;
  } catch {
    return null;
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
  if (!details || details.type === "main_frame" || VIDEO_RESOURCE_TYPES.has(details.type)) {
    return false;
  }
  if (isVideoSiteUrl(details.documentUrl) || isVideoSiteUrl(details.originUrl)) return false;
  try {
    const hostname = new URL(details.url).hostname.toLowerCase();
    return [...PAGE_AD_HOSTS].some((candidate) => hostMatches(hostname, candidate));
  } catch {
    return false;
  }
};

const encodeBase64 = (bytes) => {
  let binary = "";
  for (let offset = 0; offset < bytes.byteLength; offset += 0x8000) {
    binary += String.fromCharCode(...bytes.subarray(offset, offset + 0x8000));
  }
  return btoa(binary);
};

const contentHash = async (bytes) => {
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest), (byte) =>
    byte.toString(16).padStart(2, "0")).join("");
};

const rememberDecision = (hash, decision) => {
  const replacementBytes = decision.replacement?.byteLength || 0;
  if (replacementBytes > MAX_CACHED_REPLACEMENT_BYTES) return;
  const existing = decisionCache.get(hash);
  if (existing !== undefined) {
    cachedReplacementBytes -= existing.replacement?.byteLength || 0;
    decisionCache.delete(hash);
  }
  while (
    decisionCache.size >= MAX_CACHED_DECISIONS ||
    cachedReplacementBytes + replacementBytes > MAX_CACHED_REPLACEMENT_BYTES
  ) {
    const oldestHash = decisionCache.keys().next().value;
    const oldest = decisionCache.get(oldestHash);
    cachedReplacementBytes -= oldest?.replacement?.byteLength || 0;
    decisionCache.delete(oldestHash);
  }
  decisionCache.set(hash, decision);
  cachedReplacementBytes += replacementBytes;
};

const cachedDecision = (hash) => {
  const decision = decisionCache.get(hash);
  if (decision === undefined) return undefined;
  decisionCache.delete(hash);
  decisionCache.set(hash, decision);
  return decision;
};

const normalizePriority = (value) =>
  value === "visible" || value === "nearby" ? value : "background";

const priorityRank = (value) => value === "visible" ? 0 : value === "nearby" ? 1 : 2;

const takeNextAnalysis = () => {
  if (analysisQueue.length === 0) return null;
  let selectedIndex = 0;
  let selectedRank = priorityRank(analysisQueue[0].priority);
  for (let index = 1; index < analysisQueue.length && selectedRank !== 0; index += 1) {
    const candidateRank = priorityRank(analysisQueue[index].priority);
    if (candidateRank < selectedRank) {
      selectedIndex = index;
      selectedRank = candidateRank;
    }
  }
  return analysisQueue.splice(selectedIndex, 1)[0];
};

const rememberImagePriority = (url, priority) => {
  if (typeof url !== "string" || !/^https?:\/\//iu.test(url)) return;
  if (!imagePriorityByUrl.has(url) && imagePriorityByUrl.size >= MAX_PRIORITY_HINTS) {
    imagePriorityByUrl.delete(imagePriorityByUrl.keys().next().value);
  }
  const normalized = normalizePriority(priority);
  imagePriorityByUrl.set(url, normalized);
  for (const task of analysisQueue) {
    if (task.details.url === url) task.priority = normalized;
  }
};

const imagePriority = (url) => normalizePriority(imagePriorityByUrl.get(url));

const replacementBytes = (value) => {
  if (typeof value !== "string" || value.length === 0) return null;
  try {
    const bytes = decodeBase64(value);
    const isPng = bytes.byteLength >= 8 &&
      [137, 80, 78, 71, 13, 10, 26, 10].every((byte, index) => bytes[index] === byte);
    return isPng && bytes.byteLength <= MAX_REPLACEMENT_BYTES ? bytes : null;
  } catch {
    return null;
  }
};

const scheduleDocumentQuiet = (state) => {
  if (!isCurrentDocument(state) || !state.documentLoaded || state.viewportReadyReported) return;
  clearTimeout(state.quietTimer);
  if (state.activeStreams !== 0 || state.queuedAnalyses !== 0 || state.analysesInFlight !== 0) {
    state.quietTimer = null;
    return;
  }
  state.quietTimer = setTimeout(() => {
    state.quietTimer = null;
    if (
      !isCurrentDocument(state) ||
      !state.documentLoaded ||
      state.viewportReadyReported ||
      state.activeStreams !== 0 ||
      state.queuedAnalyses !== 0 ||
      state.analysesInFlight !== 0 ||
      !validDocumentToken(state.documentToken)
    ) return;
    try {
      nativePort?.postMessage({
        type: "viewport-images-ready",
        version: PROTOCOL_VERSION,
        tabId: state.tabId,
        documentKey: state.documentKey,
        documentToken: state.documentToken,
      });
      state.viewportReadyReported = true;
    } catch {}
  }, VIEWPORT_QUIET_MS);
};

const failPendingNative = () => {
  for (const pending of pendingNative.values()) {
    clearTimeout(pending.timeout);
    pending.resolve({ action: "block", cacheable: false });
  }
  pendingNative.clear();
};

const connectNative = () => {
  if (nativePort !== null) return;
  try {
    const port = browser.runtime.connectNative(NATIVE_APP);
    nativePort = port;
    for (const state of documentStatesByTab.values()) {
      if (isCurrentDocument(state)) postDocumentLifecycle("media-document-current", state);
    }
    port.onMessage.addListener((message) => {
      if (message?.type === "media-diagnostics-config" && message?.version === PROTOCOL_VERSION) {
        diagnosticsEnabled = message.enabled === true;
        if (!diagnosticsEnabled) {
          diagnosticDrops.clear();
          clearTimeout(diagnosticFlushTimer);
          diagnosticFlushTimer = null;
        }
        return;
      }
      const pending = pendingNative.get(message?.candidateId);
      if (
        pending === undefined ||
        message?.type !== "media-decision" ||
        message?.version !== PROTOCOL_VERSION
      ) return;
      pendingNative.delete(message.candidateId);
      clearTimeout(pending.timeout);
      const allow = message.action === "allow" &&
        ["model_allow", "safe_ui_vector"].includes(message.reason);
      const modelBlock = message.action === "block" && message.reason === "model_filter";
      const replacement = replacementBytes(message.replacementBytesBase64);
      const acceptedReplacement = modelBlock ? replacement : null;
      pending.resolve({
        action: allow ? "allow" : "block",
        cacheable: allow || modelBlock,
        replacement: allow ? null : acceptedReplacement,
      });
    });
    port.onDisconnect.addListener(() => {
      if (nativePort === port) nativePort = null;
      failPendingNative();
      if (reconnectTimer === null) {
        reconnectTimer = setTimeout(() => {
          reconnectTimer = null;
          connectNative();
        }, 250);
      }
    });
  } catch {
    nativePort = null;
  }
};

const requestNativeDecision = (
  details,
  bytes,
  priority = imagePriority(details.url),
  documentState,
) => {
  if (!isCurrentDocument(documentState)) {
    return Promise.resolve({ action: "block", cacheable: false });
  }
  connectNative();
  if (nativePort === null) return Promise.resolve({ action: "block", cacheable: false });
  sequence += 1;
  const candidateId = `image_${sequence}_${crypto.getRandomValues(new Uint32Array(1))[0].toString(16)}`;
  let bytesBase64;
  try {
    bytesBase64 = encodeBase64(bytes);
  } catch {
    return Promise.resolve({ action: "block", cacheable: false });
  }
  return new Promise((resolve) => {
    const timeout = setTimeout(() => {
      pendingNative.delete(candidateId);
      recordDiagnosticDrop("native", "decision_timeout");
      resolve({ action: "block", cacheable: false });
    }, NATIVE_TIMEOUT_MS);
    pendingNative.set(candidateId, { resolve, timeout, documentState });
    try {
      nativePort.postMessage({
        type: "media-bytes",
        version: PROTOCOL_VERSION,
        candidateId,
        sourceUrl: details.url,
        byteLength: bytes.byteLength,
        bytesBase64,
        priority: normalizePriority(priority),
        tabId: documentState.tabId,
        documentKey: documentState.documentKey,
        sentAtEpochMillis: Date.now(),
      });
    } catch {
      pendingNative.delete(candidateId);
      clearTimeout(timeout);
      recordDiagnosticDrop("native", "post_failed");
      resolve({ action: "block", cacheable: false });
    }
  });
};

const drainAnalysisQueue = () => {
  while (nativeInFlight < MAX_NATIVE_IN_FLIGHT && analysisQueue.length > 0) {
    const task = takeNextAnalysis();
    if (task === null) return;
    const documentState = task.documentState;
    documentState.queuedAnalyses = Math.max(0, documentState.queuedAnalyses - 1);
    if (!isCurrentDocument(documentState)) {
      task.settle({ action: "block", replacement: null });
      continue;
    }
    nativeInFlight += 1;
    documentState.analysesInFlight += 1;
    void (async () => {
      let decision = { action: "block", replacement: null };
      try {
        const hash = await contentHash(task.bytes);
        const cached = cachedDecision(hash);
        if (cached !== undefined) {
          decision = cached;
        } else {
          const pendingKey = `${documentState.documentKey}:${hash}`;
          let sharedDecision = pendingDecisions.get(pendingKey);
          if (sharedDecision === undefined) {
            sharedDecision = requestNativeDecision(
              task.details,
              task.bytes,
              task.priority,
              documentState,
            )
              .then((result) => {
                if (result.cacheable) rememberDecision(hash, result);
                return result;
              })
              .finally(() => pendingDecisions.delete(pendingKey));
            pendingDecisions.set(pendingKey, sharedDecision);
          }
          decision = await sharedDecision;
        }
      } catch {
        decision = { action: "block", replacement: null };
      }
      task.settle(decision);
    })().finally(() => {
      nativeInFlight = Math.max(0, nativeInFlight - 1);
      documentState.analysesInFlight = Math.max(0, documentState.analysesInFlight - 1);
      drainAnalysisQueue();
      scheduleDocumentQuiet(documentState);
    });
  }
};

const enqueueAnalysis = (task) => {
  const state = task.documentState;
  if (!isCurrentDocument(state)) {
    recordDiagnosticDrop(task.carrier || "network", "stale_document");
    task.settle({ action: "block", replacement: null });
    return false;
  }
  if (analysisQueue.length >= MAX_QUEUED_ANALYSES) {
    recordDiagnosticDrop(task.carrier || "network", "queue_full");
    task.settle({ action: "block", replacement: null });
    return false;
  }
  state.queuedAnalyses += 1;
  analysisQueue.push(task);
  drainAnalysisQueue();
  return true;
};

const decideInlineRaster = (message, sender) => {
  const pageUrl = sender?.url || "";
  if (!/^https?:\/\//iu.test(pageUrl)) {
    recordDiagnosticDrop("inline", "invalid_page");
    return { action: "block" };
  }
  const tabId = sender?.tab?.id;
  const documentState = validTabId(tabId) ? documentStatesByTab.get(tabId) : null;
  const frameId = Number.isInteger(sender?.frameId) && sender.frameId >= 0 ? sender.frameId : null;
  const registeredToken = frameId === 0
    ? documentState?.documentToken
    : documentState?.frameTokens.get(frameId);
  if (
    !isCurrentDocument(documentState) ||
    frameId === null ||
    !validDocumentToken(message?.documentToken) ||
    registeredToken !== message.documentToken
  ) {
    recordDiagnosticDrop("inline", "unknown_frame_document");
    return { action: "block" };
  }
  const bytes = decodeInlineRaster(message?.dataUrl);
  if (!(bytes instanceof Uint8Array)) {
    recordDiagnosticDrop("inline", "invalid_or_oversize");
    return { action: "block" };
  }
  if (capturedBytes + bytes.byteLength > MAX_CAPTURED_BYTES) {
    recordDiagnosticDrop("inline", "byte_budget");
    bytes.fill(0);
    return { action: "block" };
  }
  capturedBytes += bytes.byteLength;
  return new Promise((resolve) => {
    enqueueAnalysis({
      details: { url: pageUrl },
      bytes,
      priority: normalizePriority(message?.priority),
      carrier: "inline",
      documentState,
      settle: (decision) => {
        capturedBytes = Math.max(0, capturedBytes - bytes.byteLength);
        bytes.fill(0);
        resolve({ action: decision.action === "allow" ? "allow" : "block" });
      },
    });
  });
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

const interceptImage = (details) => {
  const documentState = currentDocumentForDetails(details);
  if (!isCurrentDocument(documentState)) {
    recordDiagnosticDrop("network", "stale_document");
    return { cancel: true };
  }
  if (typeof browser.webRequest.filterResponseData !== "function") {
    recordDiagnosticDrop("network", "filter_unavailable");
    return { cancel: true };
  }
  if (activeStreams >= MAX_ACTIVE_STREAMS) {
    recordDiagnosticDrop("network", "stream_limit");
    return { cancel: true };
  }

  let filter;
  try {
    filter = browser.webRequest.filterResponseData(details.requestId);
  } catch {
    recordDiagnosticDrop("network", "filter_open_failed");
    return { cancel: true };
  }

  activeStreams += 1;
  documentState.activeStreams += 1;
  const chunks = [];
  let totalBytes = 0;
  let reservedBytes = 0;
  let settled = false;
  const captureTimeout = setTimeout(() => {
    recordDiagnosticDrop("network", "capture_timeout");
    settle({ action: "block", replacement: null });
  }, CAPTURE_TIMEOUT_MS);

  const release = () => {
    capturedBytes = Math.max(0, capturedBytes - reservedBytes);
    reservedBytes = 0;
    activeStreams = Math.max(0, activeStreams - 1);
    documentState.streamCancellers.delete(cancelStream);
    documentState.activeStreams = Math.max(0, documentState.activeStreams - 1);
    scheduleDocumentQuiet(documentState);
  };

  const settle = (decision, originalBytes = null) => {
    if (settled) return;
    settled = true;
    clearTimeout(captureTimeout);
    const currentDecision = isCurrentDocument(documentState)
      ? decision
      : { action: "block", replacement: null };
    const delivered = currentDecision.action === "allow" && originalBytes instanceof Uint8Array
      ? originalBytes
      : currentDecision.replacement || blockedPlaceholder;
    try {
      filter.write(delivered);
    } catch {}
    try {
      filter.close();
    } catch {}
    // Allowed bytes now belong to Gecko's output stream. StreamFilter.write() does not promise
    // synchronous copying, so mutating that buffer here can corrupt a deferred image decode.
    if (originalBytes instanceof Uint8Array && delivered !== originalBytes) originalBytes.fill(0);
    chunks.length = 0;
    release();
  };
  const cancelStream = () => settle({ action: "block", replacement: null });
  documentState.streamCancellers.add(cancelStream);

  filter.ondata = (event) => {
    if (settled) return;
    let chunk;
    try {
      chunk = new Uint8Array(event.data);
    } catch {
      recordDiagnosticDrop("network", "chunk_decode_failed");
      settle({ action: "block", replacement: null });
      return;
    }
    if (chunk.byteLength === 0) return;
    if (totalBytes + chunk.byteLength > MAX_IMAGE_BYTES) {
      recordDiagnosticDrop("network", "resource_too_large");
      settle({ action: "block", replacement: null });
      return;
    }
    if (capturedBytes + chunk.byteLength > MAX_CAPTURED_BYTES) {
      recordDiagnosticDrop("network", "byte_budget");
      settle({ action: "block", replacement: null });
      return;
    }
    const copy = chunk.slice();
    chunks.push(copy);
    totalBytes += copy.byteLength;
    reservedBytes += copy.byteLength;
    capturedBytes += copy.byteLength;
  };

  filter.onerror = () => {
    recordDiagnosticDrop("network", "stream_error");
    settle({ action: "block", replacement: null });
  };
  filter.onstop = () => {
    if (settled) return;
    if (totalBytes === 0) {
      recordDiagnosticDrop("network", "empty_response");
      settle({ action: "block", replacement: null });
      return;
    }
    const bytes = chunks.length === 1 ? chunks[0] : combineChunks(chunks, totalBytes);
    chunks.length = 0;
    enqueueAnalysis({
      details,
      bytes,
      priority: imagePriority(details.url),
      carrier: "network",
      documentState,
      settle: (decision) => settle(decision, bytes),
    });
  };

  return {};
};

connectNative();

browser.runtime.onMessage.addListener((message, sender) => {
  if (
    message?.version === PROTOCOL_VERSION &&
    ["document-started", "document-loaded", "document-retired"].includes(message?.type)
  ) {
    const tabId = sender?.tab?.id;
    const frameId = sender?.frameId;
    if (!validTabId(tabId) || !Number.isInteger(frameId) || frameId < 0 || !validDocumentToken(message.documentToken)) {
      return undefined;
    }
    let state = documentStatesByTab.get(tabId) || null;
    if (message.type === "document-started") {
      if (frameId === 0) {
        if (!state || (state.documentToken !== null && state.documentToken !== message.documentToken)) {
          state = beginDocument(tabId);
        }
        state.documentToken = message.documentToken;
        scheduleDocumentQuiet(state);
      } else if (isCurrentDocument(state)) {
        state.frameTokens.set(frameId, message.documentToken);
      }
      return undefined;
    }
    if (!isCurrentDocument(state)) return undefined;
    if (frameId !== 0) {
      if (state.frameTokens.get(frameId) !== message.documentToken) return undefined;
      if (message.type === "document-retired") state.frameTokens.delete(frameId);
      return undefined;
    }
    if (state.documentToken !== message.documentToken) return undefined;
    if (message.type === "document-loaded") {
      state.documentLoaded = true;
      scheduleDocumentQuiet(state);
    } else {
      retireDocument(state);
    }
    return undefined;
  }
  if (message?.type === "image-priority" && message?.version === PROTOCOL_VERSION) {
    rememberImagePriority(message.url, message.priority);
    return undefined;
  }
  if (message?.type !== "inline-raster-decision" || message?.version !== PROTOCOL_VERSION) {
    return undefined;
  }
  return decideInlineRaster(message, sender);
});

browser.webRequest.onBeforeRequest.addListener(
  (details) => {
    if (details.type === "main_frame") beginDocument(details.tabId);
    if (isPageAdvertisementRequest(details)) return { cancel: true };
    if (ANALYZED_RESOURCE_TYPES.has(details.type)) return interceptImage(details);
    if (BLOCKED_RESOURCE_TYPES.has(details.type)) return { cancel: true };
    return {};
  },
  { urls: ["<all_urls>"] },
  ["blocking"],
);

browser.webRequest.onHeadersReceived.addListener(
  (details) => {
    const contentType = details.responseHeaders
      ?.find((header) => header.name.toLowerCase() === "content-type")
      ?.value?.trim() || "";
    const alreadyIntercepted = ANALYZED_RESOURCE_TYPES.has(details.type);
    if (IMAGE_MIME_PATTERN.test(contentType) && !alreadyIntercepted) {
      return interceptImage(details);
    }
    return BLOCKED_MEDIA_MIME_PATTERN.test(contentType) ? { cancel: true } : {};
  },
  { urls: ["<all_urls>"] },
  ["blocking", "responseHeaders"],
);

// Gecko may otherwise satisfy in-memory responses without re-entering the registered
// webRequest filters. Flush only that transient cache once, after every listener is ready.
if (typeof browser.webRequest.handlerBehaviorChanged === "function") {
  void browser.webRequest.handlerBehaviorChanged().catch(() => {});
}
