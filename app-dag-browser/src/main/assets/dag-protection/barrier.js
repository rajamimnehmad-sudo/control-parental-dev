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
  const IMAGE_STABILITY_MS = 0;
  const IMAGE_RECONCILIATION_DELAYS_MS = [100, 400, 1000, 2000, 4000];
  const STABLE_IMAGE_ATTRIBUTE = "data-glosh-dag-stable";
  const MAX_INLINE_DATA_URL_LENGTH = 66 * 1024;
  const MAX_INLINE_IMAGES_PER_DOCUMENT = 16;
  const MAX_INLINE_NATURAL_EDGE = 128;
  const MAX_INLINE_RENDERED_EDGE = 96;
  const IMAGE_PRIORITY_ROOT_MARGIN = "800px 0px";
  const MAX_PRIORITY_SOURCES = 256;
  const INITIAL_AD_SCAN_READY_ATTRIBUTE = "data-glosh-dag-ads-initial-ready";
  const INITIAL_AD_SCAN_READY_EVENT = "glosh-dag-ads-initial-ready";
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
  let inlineImagesSubmitted = 0;
  let initialAdScanReady = false;
  let initialDocumentReady = false;
  let initialDocumentReadyReported = false;
  const pendingImages = new WeakMap();
  const inlineDecisions = new Map();
  const priorityBySource = new Map();
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

  const imageSource = (image) => image.currentSrc || image.src || "";

  const reportImagePriority = (image, priority) => {
    const source = imageSource(image);
    if (!/^https?:\/\//iu.test(source)) return;
    if (priorityBySource.get(source) === priority) return;
    if (!priorityBySource.has(source) && priorityBySource.size >= MAX_PRIORITY_SOURCES) {
      priorityBySource.delete(priorityBySource.keys().next().value);
    }
    priorityBySource.set(source, priority);
    void browser.runtime.sendMessage({
      type: "image-priority",
      version: PROTOCOL_VERSION,
      url: source,
      priority,
    }).catch(() => {});
  };

  const hasInlineImageSource = (image) => {
    const source = image.getAttribute("src") || "";
    const sourceSet = image.getAttribute("srcset") || "";
    return (
      source.startsWith("data:") ||
      source.startsWith("blob:") ||
      sourceSet.includes("data:image") ||
      sourceSet.includes("blob:")
    );
  };

  const inlineDataSource = (image) => {
    const current = image.currentSrc || "";
    if (current.startsWith("data:image/")) return current;
    const source = image.getAttribute("src") || "";
    return source.startsWith("data:image/") ? source : "";
  };

  const resetImage = (image) => {
    const pending = pendingImages.get(image);
    if (pending?.timeout !== undefined) clearTimeout(pending.timeout);
    pendingImages.delete(image);
    image.removeAttribute(STABLE_IMAGE_ATTRIBUTE);
  };

  const inlineImageIsBounded = (image, source) => {
    if (
      source.length === 0 ||
      source.length > MAX_INLINE_DATA_URL_LENGTH ||
      image.naturalWidth <= 0 ||
      image.naturalHeight <= 0 ||
      image.naturalWidth > MAX_INLINE_NATURAL_EDGE ||
      image.naturalHeight > MAX_INLINE_NATURAL_EDGE
    ) return false;
    const bounds = image.getBoundingClientRect();
    return bounds.width > 0 && bounds.height > 0 &&
      bounds.width <= MAX_INLINE_RENDERED_EDGE && bounds.height <= MAX_INLINE_RENDERED_EDGE;
  };

  const inspectInlineImage = (image) => {
    const source = inlineDataSource(image);
    resetImage(image);
    if (!inlineImageIsBounded(image, source)) return;
    let decision = inlineDecisions.get(source);
    if (decision === undefined) {
      if (inlineImagesSubmitted >= MAX_INLINE_IMAGES_PER_DOCUMENT) return;
      inlineImagesSubmitted += 1;
      decision = browser.runtime.sendMessage({
        type: "inline-raster-decision",
        version: PROTOCOL_VERSION,
        dataUrl: source,
      }).catch(() => ({ action: "block" }));
      inlineDecisions.set(source, decision);
    }
    const request = { source, decision };
    pendingImages.set(image, request);
    void decision.then((result) => {
      if (
        pendingImages.get(image) !== request ||
        !image.isConnected ||
        inlineDataSource(image) !== source
      ) return;
      pendingImages.delete(image);
      if (result?.action === "allow") image.setAttribute(STABLE_IMAGE_ATTRIBUTE, "true");
    });
  };

  const priorityObserver = typeof IntersectionObserver === "function"
    ? new IntersectionObserver((entries) => {
      const viewportHeight = window.innerHeight;
      for (const entry of entries) {
        const rect = entry.boundingClientRect;
        const priority = entry.isIntersecting
          ? rect.bottom > 0 && rect.top < viewportHeight ? "visible" : "nearby"
          : "background";
        reportImagePriority(entry.target, priority);
      }
    }, { rootMargin: IMAGE_PRIORITY_ROOT_MARGIN })
    : null;

  const observeImage = (image) => {
    if (image instanceof HTMLImageElement) {
      priorityObserver?.observe(image);
    }
  };

  const stabilizeImage = (image) => {
    if (image.hasAttribute(STABLE_IMAGE_ATTRIBUTE)) return;
    if (inlineDataSource(image).length > 0) {
      inspectInlineImage(image);
      return;
    }
    resetImage(image);
    const source = imageSource(image);
    if (
      source.length === 0 ||
      source.startsWith("data:") ||
      source.startsWith("blob:")
    ) return;
    const timeout = setTimeout(() => {
      pendingImages.delete(image);
      if (
        image.isConnected &&
        image.complete &&
        image.naturalWidth > 0 &&
        !(image.naturalWidth === 1 && image.naturalHeight === 1) &&
        imageSource(image) === source
      ) {
        image.setAttribute(STABLE_IMAGE_ATTRIBUTE, "true");
      }
    }, IMAGE_STABILITY_MS);
    pendingImages.set(image, { source, timeout });
  };

  const inspectAddedImages = (node) => {
    if (!(node instanceof Element)) return;
    if (node instanceof HTMLImageElement) {
      observeImage(node);
      if (node.complete) stabilizeImage(node);
    }
    for (const image of node.querySelectorAll("img")) {
      observeImage(image);
      if (image.complete) stabilizeImage(image);
    }
  };

  document.addEventListener("load", (event) => {
    if (event.target instanceof HTMLImageElement) stabilizeImage(event.target);
  }, true);

  const imageObserver = new MutationObserver((records) => {
    for (const record of records) {
      if (record.type === "attributes" && record.target instanceof HTMLImageElement) {
        priorityObserver?.unobserve(record.target);
        observeImage(record.target);
        if (hasInlineImageSource(record.target)) {
          if (record.target.complete) inspectInlineImage(record.target);
          else resetImage(record.target);
          continue;
        }
        if (record.target.complete) stabilizeImage(record.target);
        continue;
      }
      for (const node of record.addedNodes) inspectAddedImages(node);
    }
  });
  imageObserver.observe(document, {
    attributes: true,
    attributeFilter: ["src", "srcset", "sizes"],
    childList: true,
    subtree: true,
  });

  const reportPreviewEligibility = () => {
    const restricted = document.querySelector(sensitivePreviewSelector) !== null;
    postToAndroid({ type: "tab-preview-eligibility", restricted });
  };

  const maybeReportInitialDocumentReady = () => {
    if (!initialAdScanReady || !initialDocumentReady || initialDocumentReadyReported) return;
    initialDocumentReadyReported = true;
    postToAndroid({ type: "document-sanitized-ready" });
  };

  addEventListener(INITIAL_AD_SCAN_READY_EVENT, () => {
    initialAdScanReady = true;
    maybeReportInitialDocumentReady();
  }, { once: true });
  if (document.documentElement?.hasAttribute(INITIAL_AD_SCAN_READY_ATTRIBUTE)) {
    initialAdScanReady = true;
  }

  postToAndroid({ type: "barrier-ready", url: location.href });
  reportPreviewEligibility();

  const completeDocument = () => {
    reportPreviewEligibility();
    for (const image of document.images) {
      observeImage(image);
      if (image.complete) stabilizeImage(image);
    }
    initialDocumentReady = true;
    maybeReportInitialDocumentReady();
  };
  const reconcileCompleteImages = () => {
    for (const image of document.images) {
      if (image.complete && !image.hasAttribute(STABLE_IMAGE_ATTRIBUTE)) stabilizeImage(image);
    }
  };
  for (const delay of IMAGE_RECONCILIATION_DELAYS_MS) {
    setTimeout(reconcileCompleteImages, delay);
  }
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
