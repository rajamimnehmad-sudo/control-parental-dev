"use strict";

const ANALYZED_RESOURCE_TYPES = new Set(["image", "imageset"]);
const BLOCKED_RESOURCE_TYPES = new Set(["media", "object"]);
const BLOCKED_MEDIA_MIME_PATTERN =
  /^(?:audio|video)\/|^application\/(?:dash\+xml|vnd\.apple\.mpegurl|x-mpegurl)/iu;
const RASTER_IMAGE_MIME_PATTERN = /^image\/(?!svg\+xml|x-icon|vnd\.microsoft\.icon)/iu;
const SAFE_UI_MIME_PATTERN = /^image\/(?:svg\+xml|x-icon|vnd\.microsoft\.icon)/iu;
const SAFE_UI_URL_PATTERN = /\.(?:svgz?|ico)(?:[?#]|$)/iu;
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
const PROTOCOL_VERSION = 1;
const MAX_IMAGE_BYTES = 2 * 1024 * 1024;
const MAX_CAPTURED_BYTES = 8 * 1024 * 1024;
const MAX_ACTIVE_STREAMS = 32;
const MAX_QUEUED_ANALYSES = 24;
const MAX_NATIVE_IN_FLIGHT = 2;
const MAX_CACHED_DECISIONS = 512;
const MAX_REPLACEMENT_BYTES = 256 * 1024;
const CAPTURE_TIMEOUT_MS = 5_000;
const NATIVE_TIMEOUT_MS = 2_250;
const VIEWPORT_QUIET_MS = 250;
const BLOCKED_PLACEHOLDER_BASE64 =
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";

let nativePort = null;
let reconnectTimer = null;
let sequence = 0;
let activeStreams = 0;
let capturedBytes = 0;
let nativeInFlight = 0;
let viewportQuietTimer = null;
const pendingNative = new Map();
const analysisQueue = [];
const decisionCache = new Map();
const responseMimeByRequest = new Map();

const decodeBase64 = (value) => {
  const binary = atob(value);
  return Uint8Array.from(binary, (character) => character.charCodeAt(0));
};
const blockedPlaceholder = decodeBase64(BLOCKED_PLACEHOLDER_BASE64);

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
  if (!decisionCache.has(hash) && decisionCache.size >= MAX_CACHED_DECISIONS) {
    decisionCache.delete(decisionCache.keys().next().value);
  }
  decisionCache.set(hash, decision);
};

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

const scheduleViewportQuiet = () => {
  clearTimeout(viewportQuietTimer);
  if (activeStreams !== 0 || analysisQueue.length !== 0 || nativeInFlight !== 0) return;
  viewportQuietTimer = setTimeout(() => {
    if (activeStreams !== 0 || analysisQueue.length !== 0 || nativeInFlight !== 0) return;
    try {
      nativePort?.postMessage({ type: "viewport-images-ready", version: PROTOCOL_VERSION });
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
    port.onMessage.addListener((message) => {
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
      const safeUiSprite = message.action === "block" && message.reason === "safe_ui_sprite";
      pending.resolve({
        action: allow ? "allow" : "block",
        cacheable: allow || modelBlock || safeUiSprite,
        replacement: allow ? null : replacementBytes(message.replacementBytesBase64),
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

const requestNativeDecision = (details, bytes) => {
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
      resolve({ action: "block", cacheable: false });
    }, NATIVE_TIMEOUT_MS);
    pendingNative.set(candidateId, { resolve, timeout });
    try {
      nativePort.postMessage({
        type: "media-bytes",
        version: PROTOCOL_VERSION,
        candidateId,
        sourceUrl: details.url,
        byteLength: bytes.byteLength,
        bytesBase64,
        priority: "background",
        sentAtEpochMillis: Date.now(),
      });
    } catch {
      pendingNative.delete(candidateId);
      clearTimeout(timeout);
      resolve({ action: "block", cacheable: false });
    }
  });
};

const drainAnalysisQueue = () => {
  while (nativeInFlight < MAX_NATIVE_IN_FLIGHT && analysisQueue.length > 0) {
    const task = analysisQueue.shift();
    nativeInFlight += 1;
    void (async () => {
      let decision = { action: "block", replacement: null };
      try {
        const hash = await contentHash(task.bytes);
        const cached = decisionCache.get(hash);
        if (cached !== undefined) {
          decision = cached;
        } else {
          decision = await requestNativeDecision(task.details, task.bytes);
          if (decision.cacheable) rememberDecision(hash, decision);
        }
      } catch {
        decision = { action: "block", replacement: null };
      }
      task.settle(decision);
    })().finally(() => {
      nativeInFlight = Math.max(0, nativeInFlight - 1);
      drainAnalysisQueue();
      scheduleViewportQuiet();
    });
  }
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

const interceptImage = (details, trustSafeUiUrl = true) => {
  if (trustSafeUiUrl && SAFE_UI_URL_PATTERN.test(details.url)) return {};
  if (
    typeof browser.webRequest.filterResponseData !== "function" ||
    activeStreams >= MAX_ACTIVE_STREAMS
  ) return { cancel: true };

  let filter;
  try {
    filter = browser.webRequest.filterResponseData(details.requestId);
  } catch {
    return { cancel: true };
  }

  activeStreams += 1;
  const chunks = [];
  let totalBytes = 0;
  let reservedBytes = 0;
  let settled = false;
  const captureTimeout = setTimeout(
    () => settle({ action: "block", replacement: null }),
    CAPTURE_TIMEOUT_MS,
  );

  const release = () => {
    capturedBytes = Math.max(0, capturedBytes - reservedBytes);
    reservedBytes = 0;
    activeStreams = Math.max(0, activeStreams - 1);
    responseMimeByRequest.delete(details.requestId);
    scheduleViewportQuiet();
  };

  const settle = (decision, originalBytes = null) => {
    if (settled) return;
    settled = true;
    clearTimeout(captureTimeout);
    const delivered = decision.action === "allow" && originalBytes instanceof Uint8Array
      ? originalBytes
      : decision.replacement || blockedPlaceholder;
    try {
      filter.write(delivered);
    } catch {}
    try {
      filter.close();
    } catch {}
    if (originalBytes instanceof Uint8Array) originalBytes.fill(0);
    chunks.length = 0;
    release();
  };

  filter.ondata = (event) => {
    if (settled) return;
    let chunk;
    try {
      chunk = new Uint8Array(event.data);
    } catch {
      settle({ action: "block", replacement: null });
      return;
    }
    if (chunk.byteLength === 0) return;
    if (
      totalBytes + chunk.byteLength > MAX_IMAGE_BYTES ||
      capturedBytes + chunk.byteLength > MAX_CAPTURED_BYTES
    ) {
      settle({ action: "block", replacement: null });
      return;
    }
    const copy = chunk.slice();
    chunks.push(copy);
    totalBytes += copy.byteLength;
    reservedBytes += copy.byteLength;
    capturedBytes += copy.byteLength;
  };

  filter.onerror = () => settle({ action: "block", replacement: null });
  filter.onstop = () => {
    if (settled) return;
    if (totalBytes === 0) {
      settle({ action: "block", replacement: null });
      return;
    }
    const bytes = chunks.length === 1 ? chunks[0] : combineChunks(chunks, totalBytes);
    chunks.length = 0;
    const mimeType = responseMimeByRequest.get(details.requestId) || "";
    if (SAFE_UI_MIME_PATTERN.test(mimeType)) {
      settle({ action: "allow", replacement: null }, bytes);
      return;
    }
    if (analysisQueue.length >= MAX_QUEUED_ANALYSES) {
      settle({ action: "block", replacement: null }, bytes);
      return;
    }
    analysisQueue.push({
      details,
      bytes,
      settle: (decision) => settle(decision, bytes),
    });
    drainAnalysisQueue();
  };

  return {};
};

connectNative();

browser.webRequest.onBeforeRequest.addListener(
  (details) => {
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
    if (ANALYZED_RESOURCE_TYPES.has(details.type) && !SAFE_UI_URL_PATTERN.test(details.url)) {
      responseMimeByRequest.set(details.requestId, contentType);
    }
    const alreadyIntercepted =
      ANALYZED_RESOURCE_TYPES.has(details.type) && !SAFE_UI_URL_PATTERN.test(details.url);
    if (RASTER_IMAGE_MIME_PATTERN.test(contentType) && !alreadyIntercepted) {
      responseMimeByRequest.set(details.requestId, contentType);
      return interceptImage(details, false);
    }
    return BLOCKED_MEDIA_MIME_PATTERN.test(contentType) ? { cancel: true } : {};
  },
  { urls: ["<all_urls>"] },
  ["blocking", "responseHeaders"],
);
