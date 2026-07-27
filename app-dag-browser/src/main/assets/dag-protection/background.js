"use strict";

const INTERCEPTED_RESOURCE_TYPES = new Set(["image", "imageset"]);
const BLOCKED_RESOURCE_TYPES = new Set(["media", "object"]);
const MAX_CAPTURE_BYTES = 256 * 1024;
const MAX_SOURCE_URL_LENGTH = 4_096;
const MAX_ACTIVE_IMAGE_FILTERS = 16;
const MAX_NATIVE_IN_FLIGHT = 10;
const RESPONSE_CAPTURE_TIMEOUT_MS = 5_000;
const NATIVE_DECISION_TIMEOUT_MS = 2_500;
const VIEWPORT_SETTLE_MS = 250;
const NATIVE_APP = "glosh.dag.protection";
const PROTOCOL_VERSION = 1;
const DOCUMENT_TOKEN_PATTERN = /^[A-Za-z0-9_-]{1,80}$/;
const TRANSPARENT_GIF = Uint8Array.from(
  atob("R0lGODlhAQABAAD/ACwAAAAAAQABAAACADs="),
  (character) => character.charCodeAt(0),
);
let requestSequence = 0;
let activeImageFilters = 0;
let nativeRequestsInFlight = 0;
let trackedDocumentToken = null;
let trackedDocumentLoaded = false;
let viewportReadyReported = false;
let viewportSettleTimeout = null;

const clearViewportSettleTimeout = () => {
  if (viewportSettleTimeout !== null) {
    clearTimeout(viewportSettleTimeout);
    viewportSettleTimeout = null;
  }
};

const scheduleViewportReady = () => {
  clearViewportSettleTimeout();
  if (
    trackedDocumentToken === null ||
    !trackedDocumentLoaded ||
    viewportReadyReported ||
    activeImageFilters !== 0 ||
    nativeRequestsInFlight !== 0
  ) {
    return;
  }
  viewportSettleTimeout = setTimeout(() => {
    viewportSettleTimeout = null;
    if (
      trackedDocumentToken === null ||
      !trackedDocumentLoaded ||
      viewportReadyReported ||
      activeImageFilters !== 0 ||
      nativeRequestsInFlight !== 0
    ) {
      return;
    }
    viewportReadyReported = true;
    browser.runtime
      .sendNativeMessage(NATIVE_APP, {
        type: "viewport-images-ready",
        version: PROTOCOL_VERSION,
        documentToken: trackedDocumentToken,
      })
      .catch(() => {
        // Performance evidence is DEV-only and never changes the fail-closed barrier.
      });
  }, VIEWPORT_SETTLE_MS);
};

const resetViewportTracking = (documentToken) => {
  clearViewportSettleTimeout();
  trackedDocumentToken = documentToken;
  trackedDocumentLoaded = false;
  viewportReadyReported = false;
};

browser.runtime.onMessage.addListener((message, sender) => {
  if (
    sender.frameId !== 0 ||
    message?.version !== PROTOCOL_VERSION ||
    !DOCUMENT_TOKEN_PATTERN.test(message?.documentToken || "")
  ) {
    return;
  }
  if (message.type === "document-started") {
    resetViewportTracking(message.documentToken);
    return;
  }
  if (
    message.type === "document-loaded" &&
    message.documentToken === trackedDocumentToken
  ) {
    trackedDocumentLoaded = true;
    scheduleViewportReady();
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

const closeWithPlaceholder = (filter) => {
  try {
    filter.write(TRANSPARENT_GIF.buffer.slice(0));
    filter.close();
  } catch {
    try {
      filter.close();
    } catch {
      // A failed stream is already closed by Gecko.
    }
  }
};

const requestNativeBlockDecision = async (details, bytes) => {
  const id = nextCandidateId();
  const response = await browser.runtime.sendNativeMessage(NATIVE_APP, {
    type: "media-bytes",
    version: PROTOCOL_VERSION,
    candidateId: id,
    sourceUrl: details.url,
    byteLength: bytes.byteLength,
    bytesBase64: encodeBase64(bytes),
  });
  return (
    response?.type === "media-decision" &&
    response?.version === PROTOCOL_VERSION &&
    response?.candidateId === id &&
    response?.action === "block"
  );
};

const interceptImageResponse = (details) => {
  if (typeof browser.webRequest.filterResponseData !== "function") {
    return { cancel: true };
  }
  if (
    typeof details.url !== "string" ||
    details.url.length === 0 ||
    details.url.length > MAX_SOURCE_URL_LENGTH ||
    activeImageFilters >= MAX_ACTIVE_IMAGE_FILTERS
  ) {
    return { cancel: true };
  }

  let filter;
  try {
    filter = browser.webRequest.filterResponseData(details.requestId);
  } catch {
    return { cancel: true };
  }
  activeImageFilters += 1;
  scheduleViewportReady();

  const chunks = [];
  let totalBytes = 0;
  let overflow = false;
  let finalized = false;
  let ownsActiveSlot = true;
  let captureTimeout = null;

  const finalize = () => {
    if (finalized) {
      return;
    }
    finalized = true;
    if (captureTimeout !== null) {
      clearTimeout(captureTimeout);
    }
    if (ownsActiveSlot) {
      ownsActiveSlot = false;
      activeImageFilters = Math.max(0, activeImageFilters - 1);
    }
    closeWithPlaceholder(filter);
    scheduleViewportReady();
  };

  filter.ondata = (event) => {
    if (finalized || overflow) {
      return;
    }
    const chunk = new Uint8Array(event.data);
    if (totalBytes + chunk.byteLength > MAX_CAPTURE_BYTES) {
      overflow = true;
      chunks.length = 0;
      totalBytes = 0;
      finalize();
      return;
    }
    chunks.push(chunk);
    totalBytes += chunk.byteLength;
  };

  filter.onerror = finalize;
  filter.onstop = () => {
    if (finalized) {
      return;
    }
    if (captureTimeout !== null) {
      clearTimeout(captureTimeout);
      captureTimeout = null;
    }
    if (overflow || totalBytes === 0) {
      finalize();
      return;
    }
    if (nativeRequestsInFlight >= MAX_NATIVE_IN_FLIGHT) {
      finalize();
      return;
    }

    nativeRequestsInFlight += 1;
    scheduleViewportReady();
    const timeout = setTimeout(finalize, NATIVE_DECISION_TIMEOUT_MS);
    const bytes = combineChunks(chunks, totalBytes);
    requestNativeBlockDecision(details, bytes)
      .catch(() => false)
      .finally(() => {
        nativeRequestsInFlight = Math.max(0, nativeRequestsInFlight - 1);
        clearTimeout(timeout);
        finalize();
        scheduleViewportReady();
      });
  };
  captureTimeout = setTimeout(finalize, RESPONSE_CAPTURE_TIMEOUT_MS);

  return {};
};

browser.webRequest.onBeforeRequest.addListener(
  (details) => {
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
