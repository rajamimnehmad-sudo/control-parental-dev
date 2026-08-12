"use strict";

(() => {
  if (globalThis.__gloshDagPageBridgeInstalled === true) return;
  Object.defineProperty(globalThis, "__gloshDagPageBridgeInstalled", {
    value: true,
    configurable: false,
    enumerable: false,
    writable: false,
  });

  const PROTOCOL_VERSION = 2;
  const NATIVE_APP = "glosh.dag.protection";
  const IMAGE_STABILITY_MS = 0;
  const IMAGE_RECONCILIATION_DELAYS_MS = [100, 400, 1000, 2000, 4000, 6000, 8000, 12000];
  const STABLE_IMAGE_ATTRIBUTE = "data-glosh-dag-stable";
  const MAX_INLINE_IMAGE_BYTES = 2 * 1024 * 1024;
  const MAX_INLINE_DATA_URL_LENGTH = 2_800_000;
  const MAX_INLINE_DECISIONS = 64;
  const MAX_INLINE_DECISION_SOURCE_CHARS = 1024 * 1024;
  const IMAGE_PRIORITY_ROOT_MARGIN = "800px 0px";
  const MAX_PRIORITY_SOURCES = 256;
  const MAX_COMPACT_SOURCE_DIAGNOSTICS = 64;
  const COMPACT_SOURCE_DIAGNOSTIC_EDGE = 192;
  const MAX_STYLE_CARRIER_DIAGNOSTIC_ELEMENTS = 2048;
  const MAX_ELEMENT_STATE_DIAGNOSTICS = 512;
  const MAX_ELEMENT_STATE_BATCH = 64;
  const ELEMENT_STATE_FLUSH_MS = 250;
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
  const videoLab = globalThis.__gloshDagVideoLab || null;
  let initialAdScanReady = false;
  let initialDocumentReady = false;
  let initialDocumentReadyReported = false;
  let inlineDecisionSourceChars = 0;
  let compactSourceDiagnostics = 0;
  let compactSourceDiagnosticsEnabled = false;
  let styleCarrierDiagnosticsReported = false;
  let elementStateDiagnostics = 0;
  let elementStateFlushTimer = null;
  const pendingImages = new WeakMap();
  const stableImageSources = new WeakMap();
  const unsettledImages = new Set();
  const inlineDecisions = new Map();
  const priorityBySource = new Map();
  const priorityByImage = new WeakMap();
  const diagnosticSourceIdsByImage = new WeakMap();
  const lastElementStateByImage = new WeakMap();
  const pendingElementStates = new Map();
  const diagnosedCompactSources = new WeakSet();
  try {
    nativePort = browser.runtime.connectNative(NATIVE_APP);
  } catch {
    return;
  }
  nativePort.onMessage.addListener((message) => {
    videoLab?.onNativeMessage(message);
    if (
      message?.type === "compact-source-diagnostics-config" &&
      message?.version === PROTOCOL_VERSION &&
      message?.enabled === true
    ) {
      compactSourceDiagnosticsEnabled = true;
      scheduleStyleCarrierDiagnostics();
    }
  });

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

  videoLab?.install({
    protocolVersion: PROTOCOL_VERSION,
    documentToken,
    postToAndroid,
  });

  const postDocumentLifecycle = (type) => {
    void browser.runtime.sendMessage({
      type,
      version: PROTOCOL_VERSION,
      documentToken,
    }).catch(() => {});
  };

  const computedStyleContainsInlineRaster = (element, pseudoElement = null) => {
    try {
      const style = getComputedStyle(element, pseudoElement);
      return [
        style.backgroundImage,
        style.borderImageSource,
        style.listStyleImage,
        style.content,
      ].some((value) => /(?:data:image\/|blob:)/iu.test(value || ""));
    } catch {
      return false;
    }
  };

  const reportStyleCarrierDiagnostics = () => {
    if (!compactSourceDiagnosticsEnabled || styleCarrierDiagnosticsReported) return;
    styleCarrierDiagnosticsReported = true;
    let scanned = 0;
    let elementCarriers = 0;
    let pseudoCarriers = 0;
    const elements = document.querySelectorAll("*");
    for (const element of elements) {
      if (scanned >= MAX_STYLE_CARRIER_DIAGNOSTIC_ELEMENTS) break;
      scanned += 1;
      if (computedStyleContainsInlineRaster(element)) elementCarriers += 1;
      if (
        computedStyleContainsInlineRaster(element, "::before") ||
        computedStyleContainsInlineRaster(element, "::after")
      ) pseudoCarriers += 1;
    }
    postToAndroid({
      type: "style-raster-carrier-summary",
      scanned,
      elementCarriers,
      pseudoCarriers,
      truncated: elements.length > scanned,
    });
  };

  const scheduleStyleCarrierDiagnostics = () => {
    const run = () => {
      if (typeof requestIdleCallback === "function") {
        requestIdleCallback(reportStyleCarrierDiagnostics, { timeout: 1_000 });
      } else {
        setTimeout(reportStyleCarrierDiagnostics, 0);
      }
    };
    if (document.readyState === "loading") {
      document.addEventListener("DOMContentLoaded", run, { once: true });
    } else {
      run();
    }
  };

  postDocumentLifecycle("document-started");

  const imageSource = (image) => image.currentSrc || image.src || "";

  const absoluteNetworkSource = (source) => {
    if (source.length === 0) return "";
    try {
      const absolute = new URL(source, document.baseURI).href;
      return /^https?:\/\//iu.test(absolute) ? absolute : "";
    } catch {
      return "";
    }
  };

  const firstNetworkCandidate = (sourceSet) => {
    if (/(?:data:|blob:)/iu.test(sourceSet)) {
      return sourceSet.match(/https?:\/\/[^\s,]+/iu)?.[0] || "";
    }
    for (const candidate of sourceSet.split(",")) {
      const source = candidate.trim().split(/\s+/u, 1)[0] || "";
      const absolute = absoluteNetworkSource(source);
      if (absolute.length > 0) return absolute;
    }
    return "";
  };

  const declaredNetworkImageSource = (image) => {
    const declared = image.getAttribute("src") || "";
    const declaredNetwork = absoluteNetworkSource(declared);
    if (declaredNetwork.length > 0) return declaredNetwork;
    const ownCandidate = firstNetworkCandidate(image.getAttribute("srcset") || "");
    if (ownCandidate.length > 0) return ownCandidate;
    const pictureCandidate = firstNetworkCandidate(
      [...(image.closest("picture")?.querySelectorAll("source[srcset]") || [])]
        .map((source) => source.getAttribute("srcset") || "")
        .join(","),
    );
    if (pictureCandidate.length > 0) return pictureCandidate;
    const selected = imageSource(image);
    return /^https?:\/\//iu.test(selected) ? selected : "";
  };

  const reportCompactSourceMetadata = (image) => {
    if (
      !compactSourceDiagnosticsEnabled ||
      compactSourceDiagnostics >= MAX_COMPACT_SOURCE_DIAGNOSTICS ||
      diagnosedCompactSources.has(image) ||
      inlineImageSource(image).length > 0 ||
      image.naturalWidth <= 0 ||
      image.naturalHeight <= 0 ||
      Math.min(image.naturalWidth, image.naturalHeight) > COMPACT_SOURCE_DIAGNOSTIC_EDGE
    ) return;
    diagnosedCompactSources.add(image);
    compactSourceDiagnostics += 1;
    const sourceSet = image.getAttribute("srcset") || "";
    const widthDescriptors = [...sourceSet.matchAll(/(?:^|,)[^,]+\s(\d+)w(?:\s*,|$)/gu)]
      .map((match) => Number.parseInt(match[1], 10))
      .filter(Number.isSafeInteger);
    const densityDescriptors = sourceSet.match(/\s\d+(?:\.\d+)?x(?:\s*,|$)/gu) || [];
    const declaredSource = image.getAttribute("src") || "";
    postToAndroid({
      type: "compact-image-source-metadata",
      naturalWidth: image.naturalWidth,
      naturalHeight: image.naturalHeight,
      renderedWidth: image.clientWidth,
      renderedHeight: image.clientHeight,
      hasSourceSet: sourceSet.length > 0,
      sourceSetCandidates: Math.max(widthDescriptors.length + densityDescriptors.length, sourceSet.length > 0 ? 1 : 0),
      hasLargerWidthCandidate: widthDescriptors.some((width) => width > image.naturalWidth),
      hasDensityCandidate: densityDescriptors.length > 0,
      pictureSources: image.closest("picture")?.querySelectorAll("source[srcset]").length || 0,
      currentDiffersFromDeclared: declaredSource.length > 0 && image.currentSrc.length > 0 && image.currentSrc !== image.src,
      inline: false,
    });
  };

  const imagePriorityFromRect = (rect) => {
    const viewportHeight = window.innerHeight;
    if (rect.bottom > 0 && rect.top < viewportHeight) return "visible";
    if (rect.bottom > -800 && rect.top < viewportHeight + 800) return "nearby";
    return "background";
  };

  const immediateImagePriority = (image) =>
    priorityByImage.get(image) || imagePriorityFromRect(image.getBoundingClientRect());

  const reportImagePriority = (image, priority, source = imageSource(image)) => {
    priorityByImage.set(image, priority);
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
    const pictureSourceSet =
      [...(image.closest("picture")?.querySelectorAll("source[srcset]") || [])]
        .map((candidate) => candidate.getAttribute("srcset") || "")
        .join(",");
    return (
      /^(?:data:|blob:)/iu.test(source) ||
      /(?:data:image|blob:)/iu.test(sourceSet) ||
      /(?:data:image|blob:)/iu.test(pictureSourceSet)
    );
  };

  const inlineImageSource = (image) => {
    const source = image.getAttribute("src") || "";
    if (/^(?:data:image\/|blob:)/iu.test(source)) return source;
    const current = image.currentSrc || "";
    return /^(?:data:image\/|blob:)/iu.test(current) ? current : "";
  };

  const diagnosticImageSource = (image) => {
    const source = imageSource(image);
    if (source.startsWith("data:")) return "data:image";
    if (source.startsWith("blob:")) return "blob";
    return source;
  };

  const flushElementStates = () => {
    elementStateFlushTimer = null;
    if (pendingElementStates.size === 0) return;
    const events = [...pendingElementStates.values()];
    pendingElementStates.clear();
    void browser.runtime.sendMessage({
      type: "media-element-states",
      version: PROTOCOL_VERSION,
      events,
    }).catch(() => {});
  };

  const reportElementState = (image, visualState) => {
    if (elementStateDiagnostics >= MAX_ELEMENT_STATE_DIAGNOSTICS) return;
    const source = diagnosticImageSource(image);
    if (source.length === 0) return;
    const sourceInstance = diagnosticSourceIdsByImage.get(image) || "";
    const signature = `${source}:${sourceInstance}:${visualState}:${image.naturalWidth}x${image.naturalHeight}:${image.clientWidth}x${image.clientHeight}`;
    if (lastElementStateByImage.get(image) === signature) return;
    lastElementStateByImage.set(image, signature);
    elementStateDiagnostics += 1;
    pendingElementStates.set(signature, {
      url: source,
      sourceInstance,
      visualState,
      naturalWidth: image.naturalWidth,
      naturalHeight: image.naturalHeight,
      renderedWidth: image.clientWidth,
      renderedHeight: image.clientHeight,
    });
    if (pendingElementStates.size >= MAX_ELEMENT_STATE_BATCH) {
      clearTimeout(elementStateFlushTimer);
      flushElementStates();
    } else if (elementStateFlushTimer === null) {
      elementStateFlushTimer = setTimeout(flushElementStates, ELEMENT_STATE_FLUSH_MS);
    }
  };

  const encodeBase64 = (bytes) => {
    let binary = "";
    for (let offset = 0; offset < bytes.byteLength; offset += 0x8000) {
      binary += String.fromCharCode(...bytes.subarray(offset, offset + 0x8000));
    }
    return btoa(binary);
  };

  const blobDataUrl = async (source) => {
    try {
      const response = await fetch(source);
      const blob = await response.blob();
      if (blob.size <= 0 || blob.size > MAX_INLINE_IMAGE_BYTES) return null;
      const bytes = new Uint8Array(await blob.arrayBuffer());
      if (bytes.byteLength !== blob.size) return null;
      const mimeType = /^image\/[a-z\d.+-]+$/iu.test(blob.type) ? blob.type : "image/unknown";
      return `data:${mimeType};base64,${encodeBase64(bytes)}`;
    } catch {
      return null;
    }
  };

  const cachedInlineDecision = (source) => {
    const decision = inlineDecisions.get(source);
    if (decision === undefined) return undefined;
    inlineDecisions.delete(source);
    inlineDecisions.set(source, decision);
    return decision;
  };

  const newDiagnosticSourceId = () => {
    const words = crypto.getRandomValues(new Uint32Array(2));
    return `inline_${Array.from(words, (word) => word.toString(16).padStart(8, "0")).join("")}`;
  };

  const rememberInlineDecision = (source, decision, diagnosticId) => {
    const record = {
      decision,
      diagnosticId,
    };
    if (source.length > MAX_INLINE_DECISION_SOURCE_CHARS) return record;
    inlineDecisions.set(source, record);
    inlineDecisionSourceChars += source.length;
    while (
      inlineDecisions.size > MAX_INLINE_DECISIONS ||
      inlineDecisionSourceChars > MAX_INLINE_DECISION_SOURCE_CHARS
    ) {
      const oldestSource = inlineDecisions.keys().next().value;
      inlineDecisions.delete(oldestSource);
      inlineDecisionSourceChars = Math.max(0, inlineDecisionSourceChars - oldestSource.length);
    }
    return record;
  };

  const resetImage = (image) => {
    const pending = pendingImages.get(image);
    if (pending?.timeout !== undefined) clearTimeout(pending.timeout);
    pendingImages.delete(image);
    stableImageSources.delete(image);
    image.removeAttribute(STABLE_IMAGE_ATTRIBUTE);
    unsettledImages.add(image);
  };

  const closeImage = (image) => {
    const pending = pendingImages.get(image);
    if (pending?.timeout !== undefined) clearTimeout(pending.timeout);
    pendingImages.delete(image);
    unsettledImages.delete(image);
    priorityObserver?.unobserve(image);
    if (image.isConnected) reportElementState(image, "hidden");
  };

  const releaseImage = (image) => {
    closeImage(image);
    priorityByImage.delete(image);
    diagnosticSourceIdsByImage.delete(image);
  };

  const markImageStable = (image, source = imageSource(image)) => {
    stableImageSources.set(image, source);
    image.setAttribute(STABLE_IMAGE_ATTRIBUTE, "true");
    unsettledImages.delete(image);
    priorityObserver?.unobserve(image);
    reportElementState(image, "shown");
  };

  const inlineImageIsBounded = (image, source) => {
    return !(
      source.length === 0 ||
      (!source.startsWith("blob:") && source.length > MAX_INLINE_DATA_URL_LENGTH) ||
      image.naturalWidth <= 0 ||
      image.naturalHeight <= 0
    );
  };

  const inspectInlineImage = (image) => {
    const source = inlineImageSource(image);
    const pending = pendingImages.get(image);
    if (pending?.source === source && pending.decision !== undefined) return;
    resetImage(image);
    if (!inlineImageIsBounded(image, source)) {
      closeImage(image);
      return;
    }
    let decisionRecord = cachedInlineDecision(source);
    if (decisionRecord === undefined) {
      const diagnosticId = newDiagnosticSourceId();
      const decision = (async () => {
        const dataUrl = source.startsWith("blob:") ? await blobDataUrl(source) : source;
        if (dataUrl === null || dataUrl.length > MAX_INLINE_DATA_URL_LENGTH) {
          return { action: "block" };
        }
        return browser.runtime.sendMessage({
          type: "inline-raster-decision",
          version: PROTOCOL_VERSION,
          documentToken,
          dataUrl,
          mimeType: dataUrl.substring(5, dataUrl.indexOf(";")),
          priority: immediateImagePriority(image),
          sourceInstance: diagnosticId,
        });
      })().catch(() => ({ action: "block" }));
      decisionRecord = rememberInlineDecision(source, decision, diagnosticId);
    }
    if (decisionRecord === undefined) {
      closeImage(image);
      return;
    }
    diagnosticSourceIdsByImage.set(image, decisionRecord.diagnosticId);
    const request = { source, decision: decisionRecord.decision };
    pendingImages.set(image, request);
    void decisionRecord.decision.then((result) => {
      if (
        pendingImages.get(image) !== request ||
        !image.isConnected ||
        inlineImageSource(image) !== source
      ) return;
      pendingImages.delete(image);
      if (result?.action === "allow") {
        markImageStable(image, source);
      } else {
        closeImage(image);
      }
    });
  };

  const priorityObserver = typeof IntersectionObserver === "function"
    ? new IntersectionObserver((entries) => {
      for (const entry of entries) {
        const rect = entry.boundingClientRect;
        const priority = entry.isIntersecting
          ? imagePriorityFromRect(rect)
          : "background";
        reportImagePriority(entry.target, priority);
      }
    }, { rootMargin: IMAGE_PRIORITY_ROOT_MARGIN })
    : null;

  const observeImage = (image) => {
    if (
      image instanceof HTMLImageElement &&
      !image.hasAttribute(STABLE_IMAGE_ATTRIBUTE)
    ) {
      unsettledImages.add(image);
      priorityObserver?.observe(image);
    }
  };

  const stabilizeImage = (image) => {
    const source = imageSource(image);
    if (
      image.hasAttribute(STABLE_IMAGE_ATTRIBUTE) &&
      stableImageSources.get(image) === source
    ) return;
    if (pendingImages.get(image)?.source === source) return;
    reportCompactSourceMetadata(image);
    if (inlineImageSource(image).length > 0) {
      inspectInlineImage(image);
      return;
    }
    diagnosticSourceIdsByImage.delete(image);
    resetImage(image);
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
        markImageStable(image, source);
      } else {
        closeImage(image);
      }
    }, IMAGE_STABILITY_MS);
    pendingImages.set(image, { source, timeout });
  };

  const settleInlineSourceMutation = (image) => {
    resetImage(image);
    observeImage(image);
    if (!image.complete) return;
    setTimeout(() => {
      if (
        image.isConnected &&
        image.complete &&
        inlineImageSource(image).length > 0
      ) inspectInlineImage(image);
    }, 0);
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

  const releaseRemovedImages = (node) => {
    if (!(node instanceof Element)) return;
    if (node instanceof HTMLImageElement) releaseImage(node);
    for (const image of node.querySelectorAll("img")) releaseImage(image);
  };

  document.addEventListener("load", (event) => {
    if (event.target instanceof HTMLImageElement) stabilizeImage(event.target);
  }, true);

  const imageObserver = new MutationObserver((records) => {
    for (const record of records) {
      if (record.type === "attributes" && record.target instanceof HTMLImageElement) {
        priorityObserver?.unobserve(record.target);
        const priority = immediateImagePriority(record.target);
        reportImagePriority(record.target, priority, declaredNetworkImageSource(record.target));
        if (hasInlineImageSource(record.target)) {
          settleInlineSourceMutation(record.target);
          continue;
        }
        const stableSourceUnchanged =
          record.target.hasAttribute(STABLE_IMAGE_ATTRIBUTE) &&
          stableImageSources.get(record.target) === imageSource(record.target);
        if (stableSourceUnchanged) continue;
        resetImage(record.target);
        observeImage(record.target);
        if (record.target.complete) stabilizeImage(record.target);
        continue;
      }
      if (record.type === "attributes" && record.target instanceof HTMLSourceElement) {
        const image = record.target.closest("picture")?.querySelector("img");
        if (image instanceof HTMLImageElement) {
          priorityObserver?.unobserve(image);
          reportImagePriority(image, immediateImagePriority(image), declaredNetworkImageSource(image));
          const declaredSourceSet = record.target.getAttribute("srcset") || "";
          if (/(?:data:image|blob:)/iu.test(declaredSourceSet)) {
            settleInlineSourceMutation(image);
            continue;
          }
          const stableSourceUnchanged =
            image.hasAttribute(STABLE_IMAGE_ATTRIBUTE) &&
            stableImageSources.get(image) === imageSource(image);
          if (stableSourceUnchanged) continue;
          resetImage(image);
          observeImage(image);
          if (image.complete) stabilizeImage(image);
        }
        continue;
      }
      for (const node of record.addedNodes) inspectAddedImages(node);
      for (const node of record.removedNodes) releaseRemovedImages(node);
    }
  });
  imageObserver.observe(document, {
    attributes: true,
    attributeFilter: ["src", "srcset", "sizes", "media", "type"],
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
    postDocumentLifecycle("document-loaded");
    maybeReportInitialDocumentReady();
  };
  const reconcileCompleteImages = (finalPass) => {
    for (const image of unsettledImages) {
      if (!image.isConnected || image.hasAttribute(STABLE_IMAGE_ATTRIBUTE)) {
        unsettledImages.delete(image);
        priorityObserver?.unobserve(image);
        continue;
      }
      if (image.complete) stabilizeImage(image);
      if (finalPass && unsettledImages.has(image)) {
        unsettledImages.delete(image);
        priorityObserver?.unobserve(image);
      }
    }
  };
  for (const [index, delay] of IMAGE_RECONCILIATION_DELAYS_MS.entries()) {
    setTimeout(
      () => reconcileCompleteImages(index === IMAGE_RECONCILIATION_DELAYS_MS.length - 1),
      delay,
    );
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
  addEventListener("pagehide", (event) => {
    flushElementStates();
    if (!event.persisted) postDocumentLifecycle("document-retired");
  });
  addEventListener("pageshow", (event) => {
    if (!event.persisted) return;
    postDocumentLifecycle("document-started");
    if (initialDocumentReady) postDocumentLifecycle("document-loaded");
  });
})();
