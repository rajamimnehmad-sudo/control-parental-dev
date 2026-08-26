import { clipFromSnapshot } from "./geometry.js";

const bridgeBase = "http://127.0.0.1:8765";
const navigationSequences = new Map();
let sessionNonce = null;
let captureSequence = 0;
let debuggerAttempted = false;

function senderAuthority(sender) {
  return {
    extensionId: chrome.runtime.id,
    tabId: sender.tab?.id ?? null,
    documentId: sender.documentId ?? null,
    documentLifecycle: sender.documentLifecycle ?? null,
    frameId: sender.frameId ?? null,
    origin: sender.origin ?? null,
    url: sender.url ?? null,
  };
}

function navigationKey(authority) {
  return `${authority.tabId}:${authority.frameId}:${authority.documentId}`;
}

async function getSession() {
  if (sessionNonce) return sessionNonce;
  const response = await fetch(`${bridgeBase}/session`, { cache: "no-store" });
  if (!response.ok) throw new Error(`session_${response.status}`);
  const body = await response.json();
  if (typeof body.nonce !== "string" || body.nonce.length < 32) throw new Error("invalid_session_nonce");
  sessionNonce = body.nonce;
  return sessionNonce;
}

async function emit(kind, payload) {
  const nonce = await getSession();
  const response = await fetch(`${bridgeBase}/events`, {
    method: "POST",
    cache: "no-store",
    headers: {
      "Content-Type": "application/json",
      "X-Glosh-Lab-Session": nonce,
    },
    body: JSON.stringify({ kind, emittedAt: Date.now(), payload }),
  });
  if (!response.ok) throw new Error(`bridge_${response.status}`);
}

async function sha256Base64(base64) {
  const binary = atob(base64);
  const bytes = Uint8Array.from(binary, (character) => character.charCodeAt(0));
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest), (value) => value.toString(16).padStart(2, "0")).join("");
}

async function decodeSamples(base64) {
  if (typeof OffscreenCanvas !== "function" || typeof createImageBitmap !== "function") {
    return { decoderAvailable: false };
  }
  const bytes = Uint8Array.from(atob(base64), (character) => character.charCodeAt(0));
  const bitmap = await createImageBitmap(new Blob([bytes], { type: "image/png" }));
  try {
    const canvas = new OffscreenCanvas(bitmap.width, bitmap.height);
    const context = canvas.getContext("2d", { willReadFrequently: true });
    context.drawImage(bitmap, 0, 0);
    const sample = (x, y) => Array.from(context.getImageData(x, y, 1, 1).data);
    return {
      decoderAvailable: true,
      width: bitmap.width,
      height: bitmap.height,
      redSample: sample(Math.min(bitmap.width - 1, Math.floor(bitmap.width * 0.19)), Math.floor(bitmap.height / 2)),
      blackSample: sample(Math.min(bitmap.width - 1, Math.floor(bitmap.width * 0.06)), Math.floor(bitmap.height / 2)),
    };
  } finally {
    bitmap.close();
  }
}

async function captureSentinel(authority, snapshot) {
  if (debuggerAttempted || authority.tabId === null || authority.frameId !== 0) return;
  const target = snapshot.elements.find((element) => element.elementIdentity === "id:inline-svg" && element.visible);
  if (!target || typeof chrome.debugger?.attach !== "function") return;
  debuggerAttempted = true;
  const debuggee = { tabId: authority.tabId };
  captureSequence += 1;
  try {
    await chrome.debugger.attach(debuggee, "1.3");
    const metrics = await chrome.debugger.sendCommand(debuggee, "Page.getLayoutMetrics");
    const clip = clipFromSnapshot(target, metrics);
    const result = await chrome.debugger.sendCommand(debuggee, "Page.captureScreenshot", {
      format: "png",
      fromSurface: true,
      captureBeyondViewport: false,
      clip,
    });
    const samples = await decodeSamples(result.data);
    await emit("regional_capture", {
      ...authority,
      navigationSequence: navigationSequences.get(navigationKey(authority)) ?? 0,
      elementIdentity: target.elementIdentity,
      provenanceReason: target.provenanceReason,
      viewport: snapshot.viewport,
      clip,
      captureSequence,
      pngSha256: await sha256Base64(result.data),
      samples,
    });
  } catch (error) {
    await emit("regional_capture_error", {
      ...authority,
      captureSequence,
      name: error?.name ?? "Error",
      message: String(error?.message ?? error),
    });
  } finally {
    try {
      await chrome.debugger.detach(debuggee);
    } catch (_) {
      // A failed attach has nothing to detach.
    }
  }
}

chrome.runtime.onMessage.addListener((message, sender) => {
  const authority = senderAuthority(sender);
  const key = navigationKey(authority);
  const payload = {
    authority,
    navigationSequence: navigationSequences.get(key) ?? 0,
    message,
  };
  emit("content_message", payload).catch(() => {});
  if (message?.kind === "content_snapshot") captureSentinel(authority, message).catch(() => {});
});

function navigationEvent(kind, details, increment) {
  const authority = {
    extensionId: chrome.runtime.id,
    tabId: details.tabId,
    documentId: details.documentId ?? null,
    documentLifecycle: details.documentLifecycle ?? null,
    frameId: details.frameId,
    parentDocumentId: details.parentDocumentId ?? null,
    parentFrameId: details.parentFrameId ?? null,
    origin: null,
    url: details.url,
  };
  const key = navigationKey(authority);
  const sequence = increment ? (navigationSequences.get(key) ?? 0) + 1 : 0;
  navigationSequences.set(key, sequence);
  emit("web_navigation", { kind, authority, navigationSequence: sequence, timeStamp: details.timeStamp }).catch(() => {});
}

chrome.webNavigation?.onCommitted?.addListener((details) => navigationEvent("committed", details, false));
chrome.webNavigation?.onHistoryStateUpdated?.addListener((details) => navigationEvent("history", details, true));
chrome.webNavigation?.onReferenceFragmentUpdated?.addListener((details) => navigationEvent("fragment", details, true));

const apiMatrix = {
  debugger: typeof chrome.debugger?.attach === "function",
  debuggerSendCommand: typeof chrome.debugger?.sendCommand === "function",
  tabs: typeof chrome.tabs?.query === "function",
  captureVisibleTab: typeof chrome.tabs?.captureVisibleTab === "function",
  webNavigation: typeof chrome.webNavigation?.onCommitted?.addListener === "function",
  webRequest: typeof chrome.webRequest?.onBeforeRequest?.addListener === "function",
  storageManaged: Boolean(chrome.storage?.managed),
};

emit("heartbeat", { extensionId: chrome.runtime.id, apiMatrix }).catch(() => {});
chrome.runtime.onInstalled.addListener(() => emit("installed", { extensionId: chrome.runtime.id, apiMatrix }).catch(() => {}));
chrome.runtime.onStartup.addListener(() => emit("startup", { extensionId: chrome.runtime.id, apiMatrix }).catch(() => {}));
