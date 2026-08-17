"use strict";

const ANALYZED_RESOURCE_TYPES = new Set(["image", "imageset"]);
const BLOCKED_RESOURCE_TYPES = new Set(["object"]);
const VIDEO_MEDIA_MIME_PATTERN = /^video\//iu;
const AUDIO_MEDIA_MIME_PATTERN = /^audio\//iu;
const VIDEO_MANIFEST_MIME_PATTERN =
  /^application\/(?:dash\+xml|vnd\.apple\.mpegurl|x-mpegurl)/iu;
const VIDEO_LAB_REVEAL_MESSAGE = "video-lab-reveal-style";
const VIDEO_LAB_CONCEAL_MESSAGE = "video-lab-conceal-style";
const VIDEO_LAB_CLOSE_MESSAGE = "video-lab-close";
const VIDEO_LAB_REVOKED_MESSAGE = "video-lab-revoked";
const VIDEO_LAB_DIAGNOSTIC_MESSAGE = "video-lab-diagnostic";
const VIDEO_LAB_GRANT_ACTIVE_MESSAGE = "video-lab-grant-active";
const VIDEO_LAB_GRANT_ACTIVE_ACK_MESSAGE = "video-lab-grant-active-ack";
const VIDEO_LAB_REVOCATION_PROOF_MESSAGE = "video-lab-revocation-proof";
const VIDEO_LAB_TOKEN_ATTRIBUTE = "data-glosh-dag-video-lab-token";
const VIDEO_LAB_JOURNAL_KEY = "videoLabRevocationJournalV1";
const MAX_VIDEO_LAB_JOURNAL_RECORDS = 16;
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
const CAPTURE_IDLE_TIMEOUT_MS = 5_000;
const NATIVE_TIMEOUT_MS = 2_250;
const VIEWPORT_QUIET_MS = 250;
const MAX_PRIORITY_HINTS = 256;
const DIAGNOSTIC_FLUSH_MS = 500;
const MAX_DIAGNOSTIC_KEYS = 64;
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
let videoLabEnabled = false;
let videoLabDiagnosticsEnabled = false;
let videoLabRecoveryStage = "recovery_pending";
let diagnosticFlushTimer = null;
const pendingNative = new Map();
const pendingDecisions = new Map();
const analysisQueue = [];
const decisionCache = new Map();
const imagePriorityByUrl = new Map();
const documentStatesByTab = new Map();
const diagnosticDrops = new Map();
const diagnosticResources = new Map();
const diagnosticElements = new Map();
const diagnosticDecisions = new Map();
const responseMetadataByRequest = new Map();
const videoLabGrantsByTab = new Map();
const videoLabCloseRequestsByTab = new Map();
const videoLabCloseProofTimersByTab = new Map();
const videoLabGrantActiveAcksByTab = new Map();
const videoLabRevocationJournalByTab = new Map();

const validTabId = (value) => Number.isInteger(value) && value >= 0;
const validDocumentToken = (value) =>
  typeof value === "string" && /^document_[a-f0-9]{1,16}$/u.test(value);
const validVideoLabToken = (value) =>
  typeof value === "string" && /^[a-f0-9]{32}$/u.test(value);
const validVideoLabCloseNonce = validVideoLabToken;
const validVideoLabId = (value) =>
  typeof value === "string" && /^video_[a-f0-9]{16}$/u.test(value);
const validVideoLabCounter = (value) => Number.isInteger(value) && value >= 1 && value <= 1_000_000;
const boundedDiagnosticUrl = (value) => typeof value === "string" ? value.slice(0, 4_096) : "";
const newDocumentKey = () =>
  `document_${crypto.getRandomValues(new Uint32Array(1))[0].toString(16)}`;

const isCurrentDocument = (state) =>
  state !== null && state !== undefined && documentStatesByTab.get(state.tabId) === state;

const postDocumentLifecycle = (type, state) => {
  if (nativePort === null || !state) return;
  try {
    const message = {
      type,
      version: PROTOCOL_VERSION,
      tabId: state.tabId,
      documentKey: state.documentKey,
      pageUrl: state.pageUrl || "",
    };
    if (validDocumentToken(state.documentToken)) message.documentToken = state.documentToken;
    nativePort.postMessage(message);
  } catch {}
};

const isVideoLabEligibleSender = (sender) =>
  sender?.frameId === 0 &&
  /^https:\/\//iu.test(sender?.url || "");

const videoLabCss = (token) =>
  `:root [${VIDEO_LAB_TOKEN_ATTRIBUTE}="${token}"] { ` +
  "visibility: visible !important; opacity: 1 !important; }";

const videoLabGrantIdentity = (value) => ({
  tabId: value?.tabId,
  documentToken: value?.documentToken,
  videoId: value?.videoId,
  revision: value?.revision,
  viewportEpoch: value?.viewportEpoch,
  frameSequence: value?.frameSequence,
  token: value?.token,
});

const validVideoLabGrantIdentity = (value) =>
  validTabId(value?.tabId) &&
  validDocumentToken(value?.documentToken) &&
  validVideoLabId(value?.videoId) &&
  validVideoLabCounter(value?.revision) &&
  validVideoLabCounter(value?.viewportEpoch) &&
  validVideoLabCounter(value?.frameSequence) &&
  validVideoLabToken(value?.token);

const sameVideoLabGrantIdentity = (left, right) =>
  validVideoLabGrantIdentity(left) &&
  validVideoLabGrantIdentity(right) &&
  left.tabId === right.tabId &&
  left.documentToken === right.documentToken &&
  left.videoId === right.videoId &&
  left.revision === right.revision &&
  left.viewportEpoch === right.viewportEpoch &&
  left.frameSequence === right.frameSequence &&
  left.token === right.token;

const VIDEO_LAB_JOURNAL_STATES = new Set(["active", "revoked", "retired"]);

const persistVideoLabJournal = async () => {
  const records = [...videoLabRevocationJournalByTab.values()];
  if (records.length > MAX_VIDEO_LAB_JOURNAL_RECORDS) return false;
  try {
    await browser.storage.local.set({ [VIDEO_LAB_JOURNAL_KEY]: records });
    return true;
  } catch {
    return false;
  }
};

const writeVideoLabJournal = async (identity, state) => {
  if (!validVideoLabGrantIdentity(identity) || !VIDEO_LAB_JOURNAL_STATES.has(state)) return false;
  const previous = videoLabRevocationJournalByTab.get(identity.tabId);
  if (
    previous?.state === "active" &&
    !sameVideoLabGrantIdentity(previous, identity)
  ) return false;
  if (
    !videoLabRevocationJournalByTab.has(identity.tabId) &&
    videoLabRevocationJournalByTab.size >= MAX_VIDEO_LAB_JOURNAL_RECORDS
  ) return false;
  videoLabRevocationJournalByTab.set(identity.tabId, {
    ...videoLabGrantIdentity(identity),
    state,
  });
  if (await persistVideoLabJournal()) return true;
  if (previous === undefined) videoLabRevocationJournalByTab.delete(identity.tabId);
  else videoLabRevocationJournalByTab.set(identity.tabId, previous);
  return false;
};

const hasVideoLabClosureProof = (identity) => {
  const record = videoLabRevocationJournalByTab.get(identity?.tabId);
  return ["revoked", "retired"].includes(record?.state) &&
    sameVideoLabGrantIdentity(record, identity);
};

const videoLabTabIsConfirmedAbsent = async (tabId) => {
  try {
    const tabs = await browser.tabs.query({});
    if (!Array.isArray(tabs) || tabs.some((tab) => !validTabId(tab?.id))) return false;
    return !tabs.some((tab) => tab.id === tabId);
  } catch {
    return false;
  }
};

const retireVideoLabJournalForReplacedDocument = async (tabId, currentDocumentToken) => {
  const record = videoLabRevocationJournalByTab.get(tabId);
  if (
    record?.state !== "active" ||
    !validDocumentToken(currentDocumentToken) ||
    record.documentToken === currentDocumentToken
  ) return false;
  if (!await writeVideoLabJournal(record, "retired")) return false;
  postVideoLabDiagnostic("recovery_document_retired");
  return true;
};

const recoverVideoLabRevocationJournal = async () => {
  let stored;
  try {
    stored = (await browser.storage.local.get(VIDEO_LAB_JOURNAL_KEY))?.[VIDEO_LAB_JOURNAL_KEY];
  } catch {
    videoLabRecoveryStage = "recovery_storage_read_failed";
    return;
  }
  if (stored === undefined) {
    videoLabRecoveryStage = "recovery_storage_empty";
    return;
  }
  if (!Array.isArray(stored) || stored.length > MAX_VIDEO_LAB_JOURNAL_RECORDS) {
    videoLabRecoveryStage = "recovery_storage_invalid";
    return;
  }
  for (const record of stored) {
    if (!validVideoLabGrantIdentity(record) || !VIDEO_LAB_JOURNAL_STATES.has(record.state)) continue;
    videoLabRevocationJournalByTab.set(record.tabId, {
      ...videoLabGrantIdentity(record),
      state: record.state,
    });
  }
  for (const record of [...videoLabRevocationJournalByTab.values()]) {
    if (record.state !== "active") continue;
    try {
      await browser.tabs.removeCSS(record.tabId, {
        code: videoLabCss(record.token),
        cssOrigin: "user",
        frameId: 0,
      });
    } catch {
      if (await videoLabTabIsConfirmedAbsent(record.tabId)) {
        if (!await writeVideoLabJournal(record, "retired")) {
          videoLabRecoveryStage = "recovery_storage_write_failed";
          continue;
        }
        videoLabRecoveryStage = "recovery_tab_retired";
        continue;
      }
      videoLabRecoveryStage = "recovery_remove_failed";
      continue;
    }
    if (!await writeVideoLabJournal(record, "revoked")) {
      videoLabRecoveryStage = "recovery_storage_write_failed";
      continue;
    }
    videoLabRecoveryStage = "recovery_active_removed";
  }
  if (videoLabRecoveryStage === "recovery_pending") {
    videoLabRecoveryStage = videoLabRevocationJournalByTab.size > 0
      ? "recovery_proof_loaded"
      : "recovery_storage_empty";
  }
};

const videoLabJournalReady = recoverVideoLabRevocationJournal();

const postVideoLabRevoked = (request) => {
  if (nativePort === null || request === undefined) return;
  try {
    nativePort.postMessage({
      type: VIDEO_LAB_REVOKED_MESSAGE,
      version: PROTOCOL_VERSION,
      tabId: request.tabId,
      documentToken: request.documentToken,
      videoId: request.videoId,
      revision: request.revision,
      viewportEpoch: request.viewportEpoch,
      frameSequence: request.frameSequence,
      token: request.token,
      closeNonce: request.closeNonce,
    });
  } catch {}
};

const postVideoLabGrantState = (type, identity) => {
  if (nativePort === null || !validVideoLabGrantIdentity(identity)) return false;
  try {
    nativePort.postMessage({
      type,
      version: PROTOCOL_VERSION,
      ...videoLabGrantIdentity(identity),
    });
    return true;
  } catch {
    return false;
  }
};

const awaitVideoLabGrantActiveAck = async (grant) => {
  if (!postVideoLabGrantState(VIDEO_LAB_GRANT_ACTIVE_MESSAGE, grant)) return false;
  return new Promise((resolve) => {
    const timer = setTimeout(() => {
      if (videoLabGrantActiveAcksByTab.get(grant.tabId)?.grant !== grant) return;
      videoLabGrantActiveAcksByTab.delete(grant.tabId);
      resolve(false);
    }, 500);
    videoLabGrantActiveAcksByTab.set(grant.tabId, { grant, resolve, timer });
  });
};

const acceptVideoLabGrantActiveAck = (message) => {
  const pending = videoLabGrantActiveAcksByTab.get(message?.tabId);
  if (pending === undefined || !sameVideoLabGrantIdentity(pending.grant, message)) return;
  clearTimeout(pending.timer);
  videoLabGrantActiveAcksByTab.delete(message.tabId);
  pending.resolve(true);
};

const postVideoLabDiagnostic = (stage) => {
  if (!videoLabDiagnosticsEnabled || nativePort === null) return;
  try {
    nativePort.postMessage({
      type: VIDEO_LAB_DIAGNOSTIC_MESSAGE,
      version: PROTOCOL_VERSION,
      stage,
    });
  } catch {}
};

const acknowledgeVideoLabClose = (request) => {
  if (videoLabCloseRequestsByTab.get(request.tabId) !== request) return;
  clearTimeout(videoLabCloseProofTimersByTab.get(request.tabId));
  videoLabCloseProofTimersByTab.delete(request.tabId);
  videoLabCloseRequestsByTab.delete(request.tabId);
  postVideoLabDiagnostic("revoke_ack_posted");
  postVideoLabRevoked(request);
};

const awaitVideoLabCloseProof = (request) => {
  clearTimeout(videoLabCloseProofTimersByTab.get(request.tabId));
  videoLabCloseRequestsByTab.set(request.tabId, request);
  const timer = setTimeout(() => {
    videoLabCloseProofTimersByTab.delete(request.tabId);
    if (videoLabCloseRequestsByTab.get(request.tabId) !== request) return;
    const grant = videoLabGrantsByTab.get(request.tabId);
    if (grant?.documentToken === request.documentToken) {
      void removeVideoLabGrant(request.tabId, grant.token);
      return;
    }
    if (hasVideoLabClosureProof(request)) {
      acknowledgeVideoLabClose(request);
      return;
    }
    postVideoLabDiagnostic(
      videoLabRevocationJournalByTab.has(request.tabId)
        ? "revoke_proof_document_mismatch"
        : "revoke_no_grant_no_proof",
    );
  }, 100);
  videoLabCloseProofTimersByTab.set(request.tabId, timer);
};

const removeVideoLabGrant = async (tabId, expectedToken = null) => {
  const grant = videoLabGrantsByTab.get(tabId);
  if (grant === undefined || (expectedToken !== null && grant.token !== expectedToken)) return false;
  if (grant.closePromise !== null) return grant.closePromise;
  // The record is registered before insertCSS begins. Retain a failed opening or
  // grant in terminal closing state so neither can authorize another reveal.
  grant.closing = true;
  grant.closePromise = (async () => {
    const inserted = await grant.insertionPromise;
    if (!inserted) {
      if (!await writeVideoLabJournal(grant, "revoked")) return false;
      postVideoLabDiagnostic("revoke_proof_marked");
      postVideoLabGrantState(VIDEO_LAB_REVOCATION_PROOF_MESSAGE, grant);
      if (videoLabGrantsByTab.get(tabId) === grant) videoLabGrantsByTab.delete(tabId);
      const closeRequest = videoLabCloseRequestsByTab.get(tabId);
      if (closeRequest?.documentToken === grant.documentToken) {
        acknowledgeVideoLabClose(closeRequest);
      }
      return true;
    }
    try {
      await browser.tabs.removeCSS(tabId, {
        code: grant.css,
        cssOrigin: "user",
        frameId: 0,
      });
    } catch {
      grant.closePromise = null;
      return false;
    }
    if (!await writeVideoLabJournal(grant, "revoked")) {
      grant.closePromise = null;
      return false;
    }
    postVideoLabDiagnostic("revoke_proof_marked");
    postVideoLabGrantState(VIDEO_LAB_REVOCATION_PROOF_MESSAGE, grant);
    if (videoLabGrantsByTab.get(tabId) === grant) videoLabGrantsByTab.delete(tabId);
    const closeRequest = videoLabCloseRequestsByTab.get(tabId);
    if (closeRequest?.documentToken === grant.documentToken) {
      acknowledgeVideoLabClose(closeRequest);
    }
    return true;
  })();
  return grant.closePromise;
};

const revokeAllVideoLabGrants = () => {
  for (const tabId of [...videoLabGrantsByTab.keys()]) void removeVideoLabGrant(tabId);
};

const hasCurrentVideoLabGrant = (details) => {
  if (
    !videoLabEnabled ||
    !validTabId(details?.tabId) ||
    details?.frameId !== 0
  ) return false;
  const state = documentStatesByTab.get(details.tabId);
  const grant = videoLabGrantsByTab.get(details.tabId);
  return isCurrentDocument(state) &&
    grant?.inserted === true &&
    grant?.closing !== true &&
    grant?.documentState === state &&
    grant.documentToken === state.documentToken;
};

const closeVideoLabFromNative = async (message) => {
  await videoLabJournalReady;
  if (
    !validTabId(message?.tabId) ||
    !validDocumentToken(message?.documentToken) ||
    !validVideoLabId(message?.videoId) ||
    !validVideoLabCounter(message?.revision) ||
    !validVideoLabCounter(message?.viewportEpoch) ||
    !validVideoLabCounter(message?.frameSequence) ||
    !validVideoLabToken(message?.token) ||
    !validVideoLabCloseNonce(message?.closeNonce)
  ) return;
  const request = {
    tabId: message.tabId,
    documentToken: message.documentToken,
    videoId: message.videoId,
    revision: message.revision,
    viewportEpoch: message.viewportEpoch,
    frameSequence: message.frameSequence,
    token: message.token,
    closeNonce: message.closeNonce,
  };
  // Deny only this exact document while its grant is being removed. A close in
  // one tab must never disable every later video authority in the background.
  videoLabCloseRequestsByTab.set(message.tabId, request);
  const grant = videoLabGrantsByTab.get(message.tabId);
  if (grant === undefined) {
    // GeckoView can briefly keep two background contexts during native-port
    // reconnection. Refresh the durable journal at close time so a context
    // whose startup snapshot predates another context's removal cannot treat
    // its stale memory as authoritative.
    if (!hasVideoLabClosureProof(request)) {
      await recoverVideoLabRevocationJournal();
    }
    // A missing in-memory grant is not enough after a background restart: a
    // user stylesheet may still exist. Only this process's successful removal
    // (or a failed insertion) proves that this exact tab/document is closed.
    if (!hasVideoLabClosureProof(request)) {
      postVideoLabDiagnostic("revoke_waiting_for_proof");
      awaitVideoLabCloseProof(request);
      return;
    }
    videoLabCloseRequestsByTab.set(message.tabId, request);
    acknowledgeVideoLabClose(request);
    return;
  }
  // Never let an old close request revoke or acknowledge a newer document's
  // grant. Its CSS is still removed immediately and only the stale
  // acknowledgement is withheld.
  if (!sameVideoLabGrantIdentity(grant, request)) {
    postVideoLabDiagnostic("revoke_document_mismatch");
    void removeVideoLabGrant(message.tabId, grant.token);
    return;
  }
  videoLabCloseRequestsByTab.set(message.tabId, request);
  postVideoLabDiagnostic("revoke_remove_requested");
  void removeVideoLabGrant(message.tabId, grant.token);
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
  void removeVideoLabGrant(state.tabId);
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
    pageUrl: "",
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
  if (!diagnosticsEnabled || nativePort === null) return;
  const events = [...diagnosticDrops.values()].map(({ event, count }) => ({ ...event, count }));
  const resources = [...diagnosticResources.values()];
  const elements = [...diagnosticElements.values()];
  const decisions = [...diagnosticDecisions.values()];
  if (
    events.length === 0 &&
    resources.length === 0 &&
    elements.length === 0 &&
    decisions.length === 0
  ) return;
  diagnosticDrops.clear();
  diagnosticResources.clear();
  diagnosticElements.clear();
  diagnosticDecisions.clear();
  try {
    nativePort.postMessage({
      type: "media-diagnostic-summary",
      version: PROTOCOL_VERSION,
      events,
      resources,
      elements,
      decisions,
      activeStreams,
      queuedAnalyses: analysisQueue.length,
      capturedBytes,
    });
  } catch {}
};

const scheduleDiagnosticFlush = () => {
  if (diagnosticFlushTimer === null) {
    diagnosticFlushTimer = setTimeout(flushDiagnosticDrops, DIAGNOSTIC_FLUSH_MS);
  }
};

const diagnosticContext = (details, state) => ({
  tabId: validTabId(details?.tabId) ? details.tabId : state?.tabId,
  documentKey: typeof state?.documentKey === "string" ? state.documentKey : "",
  pageUrl: boundedDiagnosticUrl(details?.documentUrl || details?.originUrl || state?.pageUrl),
  sourceUrl: boundedDiagnosticUrl(details?.url),
  requestId: typeof details?.requestId === "string" ? details.requestId : "",
  resourceType: typeof details?.type === "string" ? details.type : "unknown",
  sourceKind: typeof details?.sourceKind === "string"
    ? details.sourceKind
    : /^data:/iu.test(details?.url || "") ? "data" : /^blob:/iu.test(details?.url || "") ? "blob" : "network",
  sourceInstance:
    typeof details?.sourceInstance === "string" && /^[a-z0-9_]{1,40}$/u.test(details.sourceInstance)
      ? details.sourceInstance
      : "",
  frameId: Number.isInteger(details?.frameId) ? details.frameId : 0,
  statusCode: Number.isInteger(details?.statusCode) ? details.statusCode : undefined,
  fromCache: typeof details?.fromCache === "boolean" ? details.fromCache : undefined,
  activeStreams,
  queuedAnalyses: analysisQueue.length,
  capturedBytes,
});

const responseMetadata = (details) => {
  const requestId = typeof details?.requestId === "string" ? details.requestId : "";
  let metadata = responseMetadataByRequest.get(requestId);
  if (metadata === undefined) {
    metadata = {
      headersReceived: false,
      statusCode: undefined,
      fromCache: undefined,
      mimeType: "",
    };
    if (requestId.length > 0) responseMetadataByRequest.set(requestId, metadata);
  }
  return metadata;
};

const detailsWithResponseMetadata = (details, metadata) => ({
  ...details,
  statusCode: metadata.statusCode,
  fromCache: metadata.fromCache,
  mimeType: metadata.mimeType,
});

const recordDiagnosticDrop = (carrier, reason, details = null, state = null) => {
  if (!diagnosticsEnabled) return;
  const context = diagnosticContext(details, state);
  const key = context.requestId || `${carrier}:${reason}`;
  if (diagnosticDrops.has(key) || diagnosticDrops.size < MAX_DIAGNOSTIC_KEYS) {
    const existing = diagnosticDrops.get(key);
    diagnosticDrops.set(key, {
      event: { carrier, reason, ...context },
      count: (existing?.count || 0) + 1,
    });
  }
  scheduleDiagnosticFlush();
};

const recordResourceDiagnostic = (details, mimeType) => {
  if (!diagnosticsEnabled || diagnosticResources.size >= MAX_DIAGNOSTIC_KEYS) return;
  const context = diagnosticContext(details, currentDocumentForDetails(details));
  diagnosticResources.set(context.requestId || `${context.sourceUrl}:${context.statusCode}`, {
    ...context,
    mimeType,
  });
  scheduleDiagnosticFlush();
};

const recordElementDiagnostic = (message, sender) => {
  if (!diagnosticsEnabled || diagnosticElements.size >= MAX_DIAGNOSTIC_KEYS) return;
  const tabId = sender?.tab?.id;
  const frameId = sender?.frameId;
  const state = validTabId(tabId) ? documentStatesByTab.get(tabId) : null;
  if (!isCurrentDocument(state) || !Number.isInteger(frameId) || frameId < 0) return;
  const sourceUrl = boundedDiagnosticUrl(message.url);
  const sourceInstance =
    typeof message.sourceInstance === "string" && /^[a-z0-9_]{1,40}$/u.test(message.sourceInstance)
      ? message.sourceInstance
      : "";
  const key = `${frameId}:${sourceUrl}:${sourceInstance}:${message.visualState}`;
  diagnosticElements.set(key, {
    tabId,
    pageUrl: boundedDiagnosticUrl(sender?.url || state.pageUrl),
    sourceUrl,
    sourceInstance,
    resourceType: "image",
    sourceKind: /^data:/iu.test(sourceUrl) ? "data" : sourceUrl === "blob" || /^blob:/iu.test(sourceUrl) ? "blob" : "network",
    frameId,
    visualState: message.visualState,
    naturalWidth: message.naturalWidth,
    naturalHeight: message.naturalHeight,
    renderedWidth: message.renderedWidth,
    renderedHeight: message.renderedHeight,
  });
  scheduleDiagnosticFlush();
};

const recordCachedDecisionDiagnostic = (task, decision) => {
  if (!diagnosticsEnabled || diagnosticDecisions.size >= MAX_DIAGNOSTIC_KEYS) return;
  const context = diagnosticContext(task.details, task.documentState);
  const key = context.requestId || `${context.sourceUrl}:${task.carrier || "network"}`;
  diagnosticDecisions.set(key, {
    ...context,
    carrier: task.carrier === "inline" ? "inline" : "network",
    priority: normalizePriority(task.priority),
    action: decision.action === "allow" ? "allow" : "block",
    reason: typeof decision.reason === "string" ? decision.reason : "cached_decision",
    width: Number.isInteger(decision.width) ? decision.width : undefined,
    height: Number.isInteger(decision.height) ? decision.height : undefined,
    decisionCacheHit: true,
  });
  scheduleDiagnosticFlush();
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
    if (diagnosticsEnabled && (
      diagnosticDrops.size > 0 ||
      diagnosticResources.size > 0 ||
      diagnosticElements.size > 0 ||
      diagnosticDecisions.size > 0
    )) scheduleDiagnosticFlush();
    for (const state of documentStatesByTab.values()) {
      if (isCurrentDocument(state)) postDocumentLifecycle("media-document-current", state);
    }
    port.onMessage.addListener((message) => {
      if (message?.type === VIDEO_LAB_CLOSE_MESSAGE && message?.version === PROTOCOL_VERSION) {
        void closeVideoLabFromNative(message);
        return;
      }
      if (message?.type === VIDEO_LAB_GRANT_ACTIVE_ACK_MESSAGE && message?.version === PROTOCOL_VERSION) {
        acceptVideoLabGrantActiveAck(message);
        return;
      }
      if (message?.type === "media-diagnostics-config" && message?.version === PROTOCOL_VERSION) {
        diagnosticsEnabled = message.enabled === true;
        if (!diagnosticsEnabled) {
          diagnosticDrops.clear();
          diagnosticResources.clear();
          diagnosticElements.clear();
          diagnosticDecisions.clear();
          clearTimeout(diagnosticFlushTimer);
          diagnosticFlushTimer = null;
        }
        return;
      }
      if (message?.type === "video-lab-config" && message?.version === PROTOCOL_VERSION) {
        videoLabDiagnosticsEnabled = message.diagnostics === true;
        postVideoLabDiagnostic(videoLabRecoveryStage);
        videoLabEnabled = message.enabled === true;
        if (!videoLabEnabled) revokeAllVideoLabGrants();
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
        reason: typeof message.reason === "string" ? message.reason : "invalid_decision",
        width: Number.isInteger(message.imageWidth) ? message.imageWidth : undefined,
        height: Number.isInteger(message.imageHeight) ? message.imageHeight : undefined,
      });
    });
    port.onDisconnect.addListener(() => {
      if (nativePort === port) {
        nativePort = null;
        videoLabEnabled = false;
        revokeAllVideoLabGrants();
      }
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
    videoLabEnabled = false;
    revokeAllVideoLabGrants();
  }
};

const requestNativeDecision = (
  details,
  bytes,
  priority = imagePriority(details.url),
  documentState,
  carrier = "network",
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
      recordDiagnosticDrop("native", "decision_timeout", details, documentState);
      resolve({ action: "block", cacheable: false });
    }, NATIVE_TIMEOUT_MS);
    pendingNative.set(candidateId, { resolve, timeout, documentState });
    try {
      nativePort.postMessage({
        type: "media-bytes",
        version: PROTOCOL_VERSION,
        candidateId,
        sourceUrl: details.url,
        pageUrl: details.documentUrl || details.originUrl || documentState.pageUrl || "",
        requestId: typeof details.requestId === "string" ? details.requestId : "",
        resourceType: typeof details.type === "string" ? details.type : "image",
        sourceKind: typeof details.sourceKind === "string"
          ? details.sourceKind
          : /^data:/iu.test(details.url || "") ? "data" : /^blob:/iu.test(details.url || "") ? "blob" : "network",
        sourceInstance:
          typeof details.sourceInstance === "string" && /^[a-z0-9_]{1,40}$/u.test(details.sourceInstance)
            ? details.sourceInstance
            : "",
        mimeType: typeof details.mimeType === "string" ? details.mimeType : "",
        frameId: Number.isInteger(details.frameId) ? details.frameId : 0,
        statusCode: Number.isInteger(details.statusCode) ? details.statusCode : undefined,
        fromCache: typeof details.fromCache === "boolean" ? details.fromCache : undefined,
        activeStreams,
        queuedAnalyses: analysisQueue.length,
        capturedBytes,
        byteLength: bytes.byteLength,
        bytesBase64,
        priority: normalizePriority(priority),
        carrier: carrier === "inline" ? "inline" : "network",
        tabId: documentState.tabId,
        documentKey: documentState.documentKey,
        sentAtEpochMillis: Date.now(),
      });
    } catch {
      pendingNative.delete(candidateId);
      clearTimeout(timeout);
      recordDiagnosticDrop("native", "post_failed", details, documentState);
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
          recordCachedDecisionDiagnostic(task, decision);
        } else {
          const pendingKey = `${documentState.documentKey}:${hash}`;
          let sharedDecision = pendingDecisions.get(pendingKey);
          if (sharedDecision === undefined) {
            sharedDecision = requestNativeDecision(
              task.details,
              task.bytes,
              task.priority,
              documentState,
              task.carrier,
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
    recordDiagnosticDrop(task.carrier || "network", "stale_document", task.details, state);
    task.settle({ action: "block", replacement: null });
    return false;
  }
  if (analysisQueue.length >= MAX_QUEUED_ANALYSES) {
    recordDiagnosticDrop(task.carrier || "network", "queue_full", task.details, state);
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
  const inlineDetails = {
    url: pageUrl,
    documentUrl: pageUrl,
    tabId: sender?.tab?.id,
    frameId: sender?.frameId,
    type: "image",
    sourceKind: "inline",
    sourceInstance:
      typeof message?.sourceInstance === "string" && /^[a-z0-9_]{1,40}$/u.test(message.sourceInstance)
        ? message.sourceInstance
        : "",
    mimeType: typeof message?.mimeType === "string" ? message.mimeType : "",
  };
  if (!/^https?:\/\//iu.test(pageUrl)) {
    recordDiagnosticDrop("inline", "invalid_page", inlineDetails);
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
    recordDiagnosticDrop("inline", "unknown_frame_document", inlineDetails, documentState);
    return { action: "block" };
  }
  const bytes = decodeInlineRaster(message?.dataUrl);
  if (!(bytes instanceof Uint8Array)) {
    recordDiagnosticDrop("inline", "invalid_or_oversize", inlineDetails, documentState);
    return { action: "block" };
  }
  if (capturedBytes + bytes.byteLength > MAX_CAPTURED_BYTES) {
    recordDiagnosticDrop("inline", "byte_budget", inlineDetails, documentState);
    bytes.fill(0);
    return { action: "block" };
  }
  capturedBytes += bytes.byteLength;
  return new Promise((resolve) => {
    enqueueAnalysis({
      details: inlineDetails,
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
    recordDiagnosticDrop("network", "stale_document", details, documentState);
    responseMetadataByRequest.delete(details.requestId);
    return { cancel: true };
  }
  if (typeof browser.webRequest.filterResponseData !== "function") {
    recordDiagnosticDrop("network", "filter_unavailable", details, documentState);
    responseMetadataByRequest.delete(details.requestId);
    return { cancel: true };
  }
  if (activeStreams >= MAX_ACTIVE_STREAMS) {
    recordDiagnosticDrop("network", "stream_limit", details, documentState);
    responseMetadataByRequest.delete(details.requestId);
    return { cancel: true };
  }

  let filter;
  try {
    filter = browser.webRequest.filterResponseData(details.requestId);
  } catch {
    recordDiagnosticDrop("network", "filter_open_failed", details, documentState);
    responseMetadataByRequest.delete(details.requestId);
    return { cancel: true };
  }
  const metadata = responseMetadata(details);

  activeStreams += 1;
  documentState.activeStreams += 1;
  const chunks = [];
  let totalBytes = 0;
  let reservedBytes = 0;
  let settled = false;
  let captureTimeout = null;

  const release = () => {
    capturedBytes = Math.max(0, capturedBytes - reservedBytes);
    reservedBytes = 0;
    activeStreams = Math.max(0, activeStreams - 1);
    documentState.streamCancellers.delete(cancelStream);
    documentState.activeStreams = Math.max(0, documentState.activeStreams - 1);
    responseMetadataByRequest.delete(details.requestId);
    scheduleDocumentQuiet(documentState);
  };

  const closeWithoutBody = (originalBytes = null) => {
    if (settled) return;
    settled = true;
    clearTimeout(captureTimeout);
    captureTimeout = null;
    try {
      filter.close();
    } catch {}
    if (originalBytes instanceof Uint8Array) originalBytes.fill(0);
    chunks.length = 0;
    release();
  };

  const settle = (decision, originalBytes = null) => {
    if (settled) return;
    if (!isCurrentDocument(documentState)) {
      closeWithoutBody(originalBytes);
      return;
    }
    settled = true;
    clearTimeout(captureTimeout);
    captureTimeout = null;
    const delivered = decision.action === "allow" && originalBytes instanceof Uint8Array
      ? originalBytes
      : decision.replacement || blockedPlaceholder;
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
  const cancelStream = () => closeWithoutBody();
  documentState.streamCancellers.add(cancelStream);

  const armCaptureTimeout = (delayMillis, reason) => {
    clearTimeout(captureTimeout);
    captureTimeout = setTimeout(() => {
      recordDiagnosticDrop("network", reason, details, documentState);
      settle({ action: "block", replacement: null });
    }, delayMillis);
  };

  filter.onstart = () => {
    if (!settled) armCaptureTimeout(CAPTURE_IDLE_TIMEOUT_MS, "capture_idle_timeout");
  };

  filter.ondata = (event) => {
    if (settled) return;
    let chunk;
    try {
      chunk = new Uint8Array(event.data);
    } catch {
      recordDiagnosticDrop("network", "chunk_decode_failed", details, documentState);
      settle({ action: "block", replacement: null });
      return;
    }
    if (chunk.byteLength === 0) return;
    if (totalBytes + chunk.byteLength > MAX_IMAGE_BYTES) {
      recordDiagnosticDrop("network", "resource_too_large", details, documentState);
      settle({ action: "block", replacement: null });
      return;
    }
    if (capturedBytes + chunk.byteLength > MAX_CAPTURED_BYTES) {
      recordDiagnosticDrop("network", "byte_budget", details, documentState);
      settle({ action: "block", replacement: null });
      return;
    }
    const copy = chunk.slice();
    chunks.push(copy);
    totalBytes += copy.byteLength;
    reservedBytes += copy.byteLength;
    capturedBytes += copy.byteLength;
    armCaptureTimeout(CAPTURE_IDLE_TIMEOUT_MS, "capture_idle_timeout");
  };

  filter.onerror = () => {
    recordDiagnosticDrop("network", "stream_error", details, documentState);
    settle({ action: "block", replacement: null });
  };
  filter.onstop = () => {
    if (settled) return;
    if (totalBytes === 0) {
      const enrichedDetails = detailsWithResponseMetadata(details, metadata);
      const reason = metadata.statusCode === 204
        ? "no_content_response"
        : metadata.headersReceived ? "empty_response" : "cancelled_before_headers";
      recordDiagnosticDrop("network", reason, enrichedDetails, documentState);
      closeWithoutBody();
      return;
    }
    const bytes = chunks.length === 1 ? chunks[0] : combineChunks(chunks, totalBytes);
    chunks.length = 0;
    enqueueAnalysis({
      details: detailsWithResponseMetadata(details, metadata),
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
    message?.type === VIDEO_LAB_REVEAL_MESSAGE &&
    message?.version === PROTOCOL_VERSION
  ) {
    const tabId = sender?.tab?.id;
    const state = validTabId(tabId) ? documentStatesByTab.get(tabId) : null;
    if (
      !videoLabEnabled ||
      !isVideoLabEligibleSender(sender) ||
      !isCurrentDocument(state) ||
      state.pageUrl !== sender.url ||
      state.documentToken !== message.documentToken ||
      !validVideoLabId(message.videoId) ||
      !validVideoLabCounter(message.revision) ||
      !validVideoLabCounter(message.viewportEpoch) ||
      !validVideoLabCounter(message.frameSequence) ||
      !validVideoLabToken(message.token)
    ) {
      const reason = !videoLabEnabled
        ? "background_disabled"
        : !isVideoLabEligibleSender(sender)
          ? "sender_ineligible"
          : !isCurrentDocument(state)
            ? "document_not_current"
            : state.pageUrl !== sender.url
              ? "url_mismatch"
              : state.documentToken !== message.documentToken
                ? "document_mismatch"
                : "token_invalid";
      return Promise.resolve({ inserted: false, reason });
    }
    return (async () => {
      await videoLabJournalReady;
      await retireVideoLabJournalForReplacedDocument(tabId, state.documentToken);
      // One CSS grant is one raw compositor frame. Replacing an outstanding
      // grant would create a window where a failed revocation looks like a
      // successful new authorization, so an opening is registered before its
      // asynchronous CSS insertion begins.
      if (videoLabGrantsByTab.has(tabId)) return { inserted: false, reason: "grant_exists" };
      const css = videoLabCss(message.token);
      const grant = {
        css,
        tabId,
        documentState: state,
        documentToken: state.documentToken,
        videoId: message.videoId,
        revision: message.revision,
        viewportEpoch: message.viewportEpoch,
        frameSequence: message.frameSequence,
        token: message.token,
        inserted: false,
        closing: false,
        closePromise: null,
        insertionPromise: null,
      };
      if (!await writeVideoLabJournal(grant, "active")) {
        return { inserted: false, reason: "journal_unavailable" };
      }
      if (!await awaitVideoLabGrantActiveAck(grant)) {
        await writeVideoLabJournal(grant, "revoked");
        return { inserted: false, reason: "native_journal_unavailable" };
      }
      postVideoLabDiagnostic("revoke_proof_cleared");
      videoLabGrantsByTab.set(tabId, grant);
      grant.insertionPromise = Promise.resolve().then(() => browser.tabs.insertCSS(tabId, {
          code: css,
          cssOrigin: "user",
          frameId: 0,
        })).then(() => {
          grant.inserted = true;
          return true;
        }).catch(() => false);
      const inserted = await grant.insertionPromise;
      if (!inserted) {
        if (!await writeVideoLabJournal(grant, "revoked")) {
          grant.closing = true;
          return { inserted: false, reason: "journal_unavailable" };
        }
        postVideoLabDiagnostic("revoke_proof_marked");
        postVideoLabGrantState(VIDEO_LAB_REVOCATION_PROOF_MESSAGE, grant);
        if (videoLabGrantsByTab.get(tabId) === grant) videoLabGrantsByTab.delete(tabId);
        return { inserted: false, reason: "insert_failed" };
      }
      if (
        videoLabGrantsByTab.get(tabId) !== grant ||
        grant.closing ||
        !videoLabEnabled ||
        !isCurrentDocument(state) ||
        state.documentToken !== grant.documentToken
      ) {
        await removeVideoLabGrant(tabId, grant.token);
        return { inserted: false, reason: "insert_invalidated" };
      }
      return { inserted: true };
    })();
  }
  if (
    message?.type === VIDEO_LAB_CONCEAL_MESSAGE &&
    message?.version === PROTOCOL_VERSION
  ) {
    if (
      !isVideoLabEligibleSender(sender) ||
      !validTabId(sender?.tab?.id) ||
      !validDocumentToken(message.documentToken) ||
      !validVideoLabId(message.videoId) ||
      !validVideoLabCounter(message.revision) ||
      !validVideoLabCounter(message.viewportEpoch) ||
      !validVideoLabCounter(message.frameSequence) ||
      !validVideoLabToken(message.token)
    ) {
      return Promise.resolve({ removed: false });
    }
    const state = documentStatesByTab.get(sender.tab.id);
    if (
      !isCurrentDocument(state) ||
      state.pageUrl !== sender.url ||
      state.documentToken !== message.documentToken
    ) return Promise.resolve({ removed: false });
    const grant = videoLabGrantsByTab.get(sender.tab.id);
    if (
      grant?.documentState?.pageUrl !== sender.url ||
      !sameVideoLabGrantIdentity(grant, {
        tabId: sender.tab.id,
        documentToken: message.documentToken,
        videoId: message.videoId,
        revision: message.revision,
        viewportEpoch: message.viewportEpoch,
        frameSequence: message.frameSequence,
        token: message.token,
      })
    ) return Promise.resolve({ removed: false });
    return removeVideoLabGrant(sender.tab.id, message.token).then((removed) => ({ removed }));
  }
  if (message?.type === "video-lab-status" && message?.version === PROTOCOL_VERSION) {
    const state = validTabId(sender?.tab?.id) ? documentStatesByTab.get(sender.tab.id) : null;
    postVideoLabDiagnostic("background_status_received");
    const enabled =
      videoLabEnabled &&
      isVideoLabEligibleSender(sender) &&
      isCurrentDocument(state) &&
      state.documentToken === message.documentToken &&
      videoLabCloseRequestsByTab.get(sender.tab.id)?.documentToken !== message.documentToken &&
      videoLabGrantsByTab.get(sender.tab.id)?.closing !== true;
    postVideoLabDiagnostic(enabled ? "background_status_enabled" : "background_status_rejected");
    return Promise.resolve({
      enabled,
    });
  }
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
        state.pageUrl = boundedDiagnosticUrl(sender?.url);
        postDocumentLifecycle("media-document-current", state);
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
  if (message?.type === "media-element-states" && message?.version === PROTOCOL_VERSION) {
    const events = Array.isArray(message.events) ? message.events.slice(0, MAX_DIAGNOSTIC_KEYS) : [];
    for (const event of events) recordElementDiagnostic(event, sender);
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
    if (details.type === "media") {
      return hasCurrentVideoLabGrant(details) ? {} : { cancel: true };
    }
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
    if (alreadyIntercepted || IMAGE_MIME_PATTERN.test(contentType)) {
      const metadata = responseMetadata(details);
      metadata.headersReceived = true;
      metadata.statusCode = Number.isInteger(details.statusCode) ? details.statusCode : undefined;
      metadata.fromCache = typeof details.fromCache === "boolean" ? details.fromCache : undefined;
      metadata.mimeType = contentType;
      recordResourceDiagnostic(details, contentType);
    }
    if (IMAGE_MIME_PATTERN.test(contentType) && !alreadyIntercepted) {
      return interceptImage(details);
    }
    if (AUDIO_MEDIA_MIME_PATTERN.test(contentType)) return { cancel: true };
    if (
      VIDEO_MEDIA_MIME_PATTERN.test(contentType) ||
      VIDEO_MANIFEST_MIME_PATTERN.test(contentType)
    ) {
      return hasCurrentVideoLabGrant(details) ? {} : { cancel: true };
    }
    return {};
  },
  { urls: ["<all_urls>"] },
  ["blocking", "responseHeaders"],
);

// Gecko may otherwise satisfy in-memory responses without re-entering the registered
// webRequest filters. Flush only that transient cache once, after every listener is ready.
if (typeof browser.webRequest.handlerBehaviorChanged === "function") {
  void browser.webRequest.handlerBehaviorChanged().catch(() => {});
}
