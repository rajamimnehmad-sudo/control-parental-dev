"use strict";

(() => {
  if (globalThis.__gloshDagPageBridgeInstalled === true) return;
  Object.defineProperty(globalThis, "__gloshDagPageBridgeInstalled", {
    value: true,
    configurable: false,
    enumerable: false,
    writable: false,
  });

  const PROTOCOL_VERSION = 1;
  const NATIVE_APP = "glosh.dag.protection";
  const documentToken =
    `document_${crypto.getRandomValues(new Uint32Array(1))[0].toString(16)}`;
  const sensitivePreviewSelector = [
    'input[type="password"]',
    'input[autocomplete="current-password" i]',
    'input[autocomplete="new-password" i]',
    'input[autocomplete^="cc-" i]',
    'iframe[src*="recaptcha" i]',
    'iframe[src*="hcaptcha" i]',
    'iframe[src*="challenges.cloudflare.com" i]',
    "[data-sitekey]",
  ].join(",");

  let nativePort = null;
  try {
    nativePort = browser.runtime.connectNative(NATIVE_APP);
  } catch {
    return;
  }

  const postToAndroid = (message) => {
    if (window.top !== window || nativePort === null) return;
    try {
      nativePort.postMessage({
        ...message,
        version: PROTOCOL_VERSION,
        documentToken,
      });
    } catch {}
  };

  const reportPreviewEligibility = () => {
    const restricted = document.querySelector(sensitivePreviewSelector) !== null;
    postToAndroid({ type: "tab-preview-eligibility", restricted });
  };

  postToAndroid({ type: "barrier-ready", url: location.href });
  reportPreviewEligibility();

  const completeDocument = () => reportPreviewEligibility();
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", completeDocument, { once: true });
  } else {
    completeDocument();
  }
  document.addEventListener("focusin", (event) => {
    if (event.target instanceof Element && event.target.matches(sensitivePreviewSelector)) {
      postToAndroid({ type: "tab-preview-eligibility", restricted: true });
    }
  }, true);
})();
