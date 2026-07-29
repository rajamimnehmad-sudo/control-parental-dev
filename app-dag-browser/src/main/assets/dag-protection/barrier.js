"use strict";

(() => {
  if (globalThis.__gloshDagBarrierInstalled === true) {
    return;
  }
  Object.defineProperty(globalThis, "__gloshDagBarrierInstalled", {
    value: true,
    configurable: false,
    enumerable: false,
    writable: false,
  });

  const mediaSelector = [
    "img",
    "picture",
    "video",
    "audio",
    "canvas",
    "svg",
    "image",
    "input[type='image']",
    "object",
    "embed",
  ].join(",");
  const PRESENTATION_DECISION_MESSAGE = "media-presentation-decision";
  const PRESENTATION_APPLIED_MESSAGE = "media-presentation-applied";
  const FALLBACK_REQUEST_MESSAGE = "media-fallback-request";
  const FALLBACK_RESPONSE_MESSAGE = "media-fallback-response";
  const INLINE_REQUEST_MESSAGE = "media-inline-request";
  const INLINE_RESPONSE_MESSAGE = "media-inline-response";
  const DECISION_ACTIONS = ["allow", "block", "error"];
  const FILTERED_ACCESSIBLE_DESCRIPTION = "Protegida por Glosh";
  const MAX_REMEMBERED_DECISIONS = 512;
  const FALLBACK_DELAY_MS = 80;
  const FALLBACK_RETRY_BASE_MS = 600;
  const FALLBACK_RETRY_MAX_MS = 6_000;
  const MAX_FALLBACK_ATTEMPTS = 4;
  const FALLBACK_ROOT_MARGIN = "640px 0px";
  const UI_VECTOR_ATTRIBUTE = "data-glosh-dag-ui-vector";
  const CSS_MEDIA_ATTRIBUTE = "data-glosh-dag-css-media";
  const CSS_MEDIA_VALUE_PROPERTY = "--glosh-dag-background-image";
  const CSS_BEFORE_ATTRIBUTE = "data-glosh-dag-css-before";
  const CSS_AFTER_ATTRIBUTE = "data-glosh-dag-css-after";
  const CSS_BEFORE_VALUE_PROPERTY = "--glosh-dag-before-background-image";
  const CSS_AFTER_VALUE_PROPERTY = "--glosh-dag-after-background-image";
  const CSS_BEFORE_CONTENT_PROPERTY = "--glosh-dag-before-content";
  const CSS_AFTER_CONTENT_PROPERTY = "--glosh-dag-after-content";
  const BACKGROUND_PROBE_ATTRIBUTE = "data-glosh-dag-background-probe";
  const SPONSORED_RESULT_ATTRIBUTE = "data-glosh-dag-sponsored-result";
  const MEDIA_HOST_ATTRIBUTE = "data-glosh-dag-media-host";
  const MAX_CSS_BACKGROUND_ELEMENTS = 512;
  const MAX_BACKGROUND_PROBE_ELEMENTS = 1_500;
  const BACKGROUND_PROBE_DELAY_MS = 180;
  const MIN_BACKGROUND_PROBE_INTERVAL_MS = 450;
  const MAX_UI_VECTOR_RENDERED_WIDTH = 1_024;
  const MAX_UI_VECTOR_RENDERED_HEIGHT = 160;
  const MAX_UI_VECTOR_RENDERED_AREA = 96_000;
  const MAX_INLINE_VECTOR_ELEMENTS = 256;
  const MAX_INLINE_ANALYSIS_BYTES = 2 * 1024 * 1024;
  const BLOCKED_INLINE_VECTOR_ELEMENTS = new Set([
    "audio",
    "canvas",
    "feimage",
    "foreignobject",
    "iframe",
    "image",
    "script",
    "video",
  ]);
  const analyzedSources = new WeakMap();
  const decisionsBySource = new Map();
  const failedSources = new Set();
  const fallbackPendingSources = new Set();
  const fallbackAttemptsBySource = new Map();
  const fallbackObservedSources = new WeakMap();
  const fallbackNearElements = new WeakSet();
  const fallbackTimers = new WeakMap();
  const cssBackgroundRecords = new Map();
  const cssBeforeRecords = new Map();
  const cssAfterRecords = new Map();
  const cssFallbackTimers = new Map();
  let backgroundProbeTimer = null;
  let lastBackgroundProbeAt = Number.NEGATIVE_INFINITY;
  let sponsoredScanTimer = null;
  const performanceDocumentToken =
    `document_${crypto.getRandomValues(new Uint32Array(1))[0].toString(16)}`;

  const normalizedSource = (rawSource) => {
    if (!rawSource) {
      return null;
    }
    try {
      const url = new URL(rawSource, document.baseURI);
      url.hash = "";
      return url.href;
    } catch {
      return null;
    }
  };

  const sourcesFromSrcset = (srcset) =>
    (srcset || "")
      .split(",")
      .map((candidate) => candidate.trim().split(/\s+/u)[0])
      .map(normalizedSource)
      .filter(Boolean);

  const candidateSourcesFor = (element) => {
    const sources = [];
    const addSource = (rawSource) => {
      const sourceUrl = normalizedSource(rawSource);
      if (sourceUrl && !sources.includes(sourceUrl)) {
        sources.push(sourceUrl);
      }
    };
    if (element instanceof HTMLImageElement) {
      addSource(element.currentSrc);
      addSource(element.getAttribute("src"));
      sourcesFromSrcset(element.getAttribute("srcset")).forEach(addSource);
      for (const attribute of ["data-src", "data-lazy-src", "data-original", "data-url"]) {
        addSource(element.getAttribute(attribute));
      }
      sourcesFromSrcset(element.getAttribute("data-srcset")).forEach(addSource);
    } else if (element instanceof HTMLVideoElement) {
      addSource(element.poster);
    } else if (element instanceof HTMLInputElement) {
      addSource(element.getAttribute("src"));
    } else if (element instanceof SVGImageElement) {
      addSource(element.href?.baseVal || element.getAttribute("href"));
    }
    return sources;
  };

  const isSvgSource = (sourceUrl) => {
    try {
      return new URL(sourceUrl).pathname.toLowerCase().endsWith(".svg");
    } catch {
      return false;
    }
  };

  const hasExternalCssUrl = (value) => {
    const matches = String(value || "").matchAll(/url\(([^)]+)\)/giu);
    for (const match of matches) {
      const target = match[1].trim().replace(/^['"]|['"]$/gu, "");
      if (!target.startsWith("#")) {
        return true;
      }
    }
    return false;
  };

  const hasSafeUiBounds = (element) => {
    const bounds = element.getBoundingClientRect();
    return (
      Number.isFinite(bounds.width) &&
      Number.isFinite(bounds.height) &&
      bounds.width > 0 &&
      bounds.height > 0 &&
      bounds.width <= MAX_UI_VECTOR_RENDERED_WIDTH &&
      bounds.height <= MAX_UI_VECTOR_RENDERED_HEIGHT &&
      bounds.width * bounds.height <= MAX_UI_VECTOR_RENDERED_AREA
    );
  };

  const isSafeInlineUiVector = (element) => {
    if (!(element instanceof SVGSVGElement) || !hasSafeUiBounds(element)) {
      return false;
    }
    const descendants = [element, ...element.querySelectorAll("*")];
    if (descendants.length > MAX_INLINE_VECTOR_ELEMENTS) {
      return false;
    }
    for (const descendant of descendants) {
      const localName = descendant.localName?.toLowerCase() || "";
      if (BLOCKED_INLINE_VECTOR_ELEMENTS.has(localName)) {
        return false;
      }
      if (
        localName === "style" &&
        (hasExternalCssUrl(descendant.textContent) || /@import/iu.test(descendant.textContent || ""))
      ) {
        return false;
      }
      for (const attribute of descendant.attributes || []) {
        const name = attribute.name.toLowerCase();
        const value = attribute.value.trim();
        if (name.startsWith("on") || hasExternalCssUrl(value)) {
          return false;
        }
        if (
          ["use", "feimage"].includes(localName) &&
          ["href", "xlink:href"].includes(name) &&
          value.length > 0 &&
          !value.startsWith("#")
        ) {
          return false;
        }
      }
    }
    return true;
  };

  const isSafeRemoteUiVector = (element, sourceUrl) =>
    element instanceof HTMLImageElement &&
    element.complete &&
    element.naturalWidth > 0 &&
    element.naturalHeight > 0 &&
    isSvgSource(sourceUrl) &&
    hasSafeUiBounds(element);

  const applyInlineUiVectorDecision = (element) => {
    if (!(element instanceof SVGSVGElement)) {
      return false;
    }
    if (isSafeInlineUiVector(element)) {
      element.setAttribute(UI_VECTOR_ATTRIBUTE, "allow");
      return true;
    }
    element.removeAttribute(UI_VECTOR_ATTRIBUTE);
    element.setAttribute("data-glosh-dag-media", "hidden");
    return false;
  };

  const rememberDecision = (sourceUrl, action) => {
    if (!decisionsBySource.has(sourceUrl) && decisionsBySource.size >= MAX_REMEMBERED_DECISIONS) {
      decisionsBySource.delete(decisionsBySource.keys().next().value);
    }
    decisionsBySource.set(sourceUrl, action);
    failedSources.delete(sourceUrl);
  };

  const applyDecisionToSource = (sourceUrl) => {
    document.querySelectorAll(mediaSelector).forEach((element) => {
      if (candidateSourcesFor(element).includes(sourceUrl)) {
        applyKnownDecision(element);
      }
    });
    applyCssBackgroundsForSource(sourceUrl);
  };

  const hasLoadedPixels = (element) =>
    element instanceof HTMLImageElement &&
    element.complete &&
    element.naturalWidth > 1 &&
    element.naturalHeight > 1;

  const isVisibleNow = (element) => {
    const bounds = element.getBoundingClientRect();
    return (
      bounds.width > 0 &&
      bounds.height > 0 &&
      bounds.bottom > 0 &&
      bounds.right > 0 &&
      bounds.top < window.innerHeight &&
      bounds.left < window.innerWidth
    );
  };

  const isNearViewport = (element) => {
    const bounds = element.getBoundingClientRect();
    const margin = 640;
    return (
      bounds.width > 0 &&
      bounds.height > 0 &&
      bounds.bottom > -margin &&
      bounds.right > 0 &&
      bounds.top < window.innerHeight + margin &&
      bounds.left < window.innerWidth
    );
  };

  const backgroundSourcesFromValue = (backgroundImage) => {
    const sources = [];
    const matches = String(backgroundImage || "").matchAll(/url\(([^)]+)\)/giu);
    for (const match of matches) {
      const sourceUrl = normalizedSource(match[1].trim().replace(/^['"]|['"]$/gu, ""));
      const protocol = sourceUrl ? new URL(sourceUrl).protocol : "";
      const supportedDataImage =
        protocol === "data:" &&
        /^data:image\/(?:avif|gif|jpeg|jpg|png|webp);base64,/iu.test(sourceUrl);
      if (
        sourceUrl &&
        (["http:", "https:", "blob:"].includes(protocol) || supportedDataImage)
      ) {
        if (!sources.includes(sourceUrl)) {
          sources.push(sourceUrl);
        }
      }
    }
    return sources;
  };

  const forgetDisconnectedCssBackgrounds = () => {
    for (const records of [cssBackgroundRecords, cssBeforeRecords, cssAfterRecords]) {
      for (const element of records.keys()) {
        if (!element.isConnected) {
          records.delete(element);
        }
      }
    }
  };

  const rememberCssBackground = (element, backgroundImage, sources) => {
    forgetDisconnectedCssBackgrounds();
    if (
      !cssBackgroundRecords.has(element) &&
      cssBackgroundRecords.size >= MAX_CSS_BACKGROUND_ELEMENTS
    ) {
      cssBackgroundRecords.delete(cssBackgroundRecords.keys().next().value);
    }
    cssBackgroundRecords.set(element, { backgroundImage, sources });
  };

  const isSafeCssUiVector = (element, sources) =>
    sources.length > 0 && sources.every(isSvgSource) && hasSafeUiBounds(element);

  const pseudoRecordConfig = (pseudo) => {
    if (pseudo === "before") {
      return {
        records: cssBeforeRecords,
        attribute: CSS_BEFORE_ATTRIBUTE,
        backgroundProperty: CSS_BEFORE_VALUE_PROPERTY,
        contentProperty: CSS_BEFORE_CONTENT_PROPERTY,
      };
    }
    return {
      records: cssAfterRecords,
      attribute: CSS_AFTER_ATTRIBUTE,
      backgroundProperty: CSS_AFTER_VALUE_PROPERTY,
      contentProperty: CSS_AFTER_CONTENT_PROPERTY,
    };
  };

  const applyCssBackgroundDecision = (element) => {
    const record = cssBackgroundRecords.get(element);
    if (!record || !element.isConnected) {
      cssBackgroundRecords.delete(element);
      return false;
    }
    const { backgroundImage, sources } = record;
    if (sources.length === 0 || isSafeCssUiVector(element, sources)) {
      if (element.style.getPropertyValue(CSS_MEDIA_VALUE_PROPERTY) !== backgroundImage) {
        element.style.setProperty(CSS_MEDIA_VALUE_PROPERTY, backgroundImage);
      }
      if (element.getAttribute(CSS_MEDIA_ATTRIBUTE) !== "allow") {
        element.setAttribute(CSS_MEDIA_ATTRIBUTE, "allow");
      }
      return true;
    }
    const actions = sources.map((sourceUrl) => decisionsBySource.get(sourceUrl));
    if (actions.some((action) => action === "block")) {
      if (element.style.getPropertyValue(CSS_MEDIA_VALUE_PROPERTY)) {
        element.style.removeProperty(CSS_MEDIA_VALUE_PROPERTY);
      }
      if (element.getAttribute(CSS_MEDIA_ATTRIBUTE) !== "block") {
        element.setAttribute(CSS_MEDIA_ATTRIBUTE, "block");
      }
      return true;
    }
    if (
      actions.some((action) => action === "error") ||
      sources.some((sourceUrl) => failedSources.has(sourceUrl))
    ) {
      element.style.removeProperty(CSS_MEDIA_VALUE_PROPERTY);
      element.setAttribute(CSS_MEDIA_ATTRIBUTE, "error");
      return true;
    }
    if (actions.every((action) => action === "allow")) {
      if (element.style.getPropertyValue(CSS_MEDIA_VALUE_PROPERTY) !== backgroundImage) {
        element.style.setProperty(CSS_MEDIA_VALUE_PROPERTY, backgroundImage);
      }
      if (element.getAttribute(CSS_MEDIA_ATTRIBUTE) !== "allow") {
        element.setAttribute(CSS_MEDIA_ATTRIBUTE, "allow");
      }
      return true;
    }
    if (element.style.getPropertyValue(CSS_MEDIA_VALUE_PROPERTY)) {
      element.style.removeProperty(CSS_MEDIA_VALUE_PROPERTY);
    }
    if (element.getAttribute(CSS_MEDIA_ATTRIBUTE) !== "hidden") {
      element.setAttribute(CSS_MEDIA_ATTRIBUTE, "hidden");
    }
    return false;
  };

  const applyPseudoCssDecision = (element, pseudo) => {
    const config = pseudoRecordConfig(pseudo);
    const record = config.records.get(element);
    if (!record || !element.isConnected) {
      config.records.delete(element);
      return false;
    }
    const { backgroundImage, content, sources } = record;
    const actions = sources.map((sourceUrl) => decisionsBySource.get(sourceUrl));
    const shouldAllow =
      sources.length === 0 ||
      isSafeCssUiVector(element, sources) ||
      actions.every((action) => action === "allow");
    const shouldBlock = actions.some((action) => action === "block");
    const shouldError =
      actions.some((action) => action === "error") ||
      sources.some((sourceUrl) => failedSources.has(sourceUrl));
    if (shouldAllow) {
      if (element.style.getPropertyValue(config.backgroundProperty) !== backgroundImage) {
        element.style.setProperty(config.backgroundProperty, backgroundImage);
      }
      if (element.style.getPropertyValue(config.contentProperty) !== content) {
        element.style.setProperty(config.contentProperty, content);
      }
      if (element.getAttribute(config.attribute) !== "allow") {
        element.setAttribute(config.attribute, "allow");
      }
      return true;
    }
    for (const property of [config.backgroundProperty, config.contentProperty]) {
      if (element.style.getPropertyValue(property)) {
        element.style.removeProperty(property);
      }
    }
    const state = shouldBlock ? "block" : shouldError ? "error" : "hidden";
    if (element.getAttribute(config.attribute) !== state) {
      element.setAttribute(config.attribute, state);
    }
    return shouldBlock;
  };

  const applyCssBackgroundsForSource = (sourceUrl) => {
    for (const [element, record] of cssBackgroundRecords.entries()) {
      if (record.sources.includes(sourceUrl)) {
        applyCssBackgroundDecision(element);
      }
    }
    for (const [pseudo, records] of [
      ["before", cssBeforeRecords],
      ["after", cssAfterRecords],
    ]) {
      for (const [element, record] of records.entries()) {
        if (record.sources.includes(sourceUrl)) {
          applyPseudoCssDecision(element, pseudo);
        }
      }
    }
  };

  const scheduleCssFallbackDecision = (sourceUrl, delayMs = FALLBACK_DELAY_MS) => {
    if (
      decisionsBySource.has(sourceUrl) ||
      fallbackPendingSources.has(sourceUrl) ||
      cssFallbackTimers.has(sourceUrl)
    ) {
      return;
    }
    const timer = setTimeout(() => {
      cssFallbackTimers.delete(sourceUrl);
      requestCssFallbackDecision(sourceUrl);
    }, delayMs);
    cssFallbackTimers.set(sourceUrl, timer);
  };

  const requestCssFallbackDecision = (sourceUrl) => {
    if (decisionsBySource.has(sourceUrl) || fallbackPendingSources.has(sourceUrl)) {
      return;
    }
    const recordEntry = [
      ...cssBackgroundRecords.entries(),
      ...cssBeforeRecords.entries(),
      ...cssAfterRecords.entries(),
    ].find(([, record]) => record.sources.includes(sourceUrl));
    if (!recordEntry) {
      return;
    }
    const [element] = recordEntry;
    fallbackPendingSources.add(sourceUrl);
    requestSourceDecision(
      sourceUrl,
      isVisibleNow(element) ? "visible" : "nearby",
    )
      .then((response) => {
        fallbackPendingSources.delete(sourceUrl);
        if (
          [FALLBACK_RESPONSE_MESSAGE, INLINE_RESPONSE_MESSAGE].includes(response?.type) &&
          response?.version === 1 &&
          normalizedSource(response.sourceUrl) === sourceUrl &&
          DECISION_ACTIONS.includes(response.action)
        ) {
          fallbackAttemptsBySource.delete(sourceUrl);
          rememberDecision(sourceUrl, response.action);
          applyCssBackgroundsForSource(sourceUrl);
          return;
        }
        const retryDelay = retryDelayOrMarkError(sourceUrl);
        if (retryDelay !== null) scheduleCssFallbackDecision(sourceUrl, retryDelay);
      })
      .catch(() => {
        fallbackPendingSources.delete(sourceUrl);
        const retryDelay = retryDelayOrMarkError(sourceUrl);
        if (retryDelay !== null) scheduleCssFallbackDecision(sourceUrl, retryDelay);
      });
  };

  const recordCssBackground = (element, backgroundImage) => {
    if (
      !(element instanceof HTMLElement) ||
      !backgroundImage ||
      backgroundImage === "none"
    ) {
      return;
    }
    const sources = backgroundSourcesFromValue(backgroundImage);
    rememberCssBackground(element, backgroundImage, sources);
    if (applyCssBackgroundDecision(element)) {
      return;
    }
    for (const sourceUrl of sources) {
      scheduleCssFallbackDecision(sourceUrl);
    }
  };

  const recordPseudoCssVisual = (element, pseudo, backgroundImage, content) => {
    const hasBackground = backgroundImage && backgroundImage !== "none";
    const hasContent = content && !["none", "normal"].includes(content);
    if (!(element instanceof HTMLElement) || (!hasBackground && !hasContent)) {
      return;
    }
    const config = pseudoRecordConfig(pseudo);
    forgetDisconnectedCssBackgrounds();
    if (!config.records.has(element) && config.records.size >= MAX_CSS_BACKGROUND_ELEMENTS) {
      config.records.delete(config.records.keys().next().value);
    }
    const normalizedBackground = hasBackground ? backgroundImage : "none";
    const normalizedContent = hasContent ? content : "\"\"";
    const sources = backgroundSourcesFromValue(
      `${normalizedBackground} ${normalizedContent}`,
    );
    config.records.set(element, {
      backgroundImage: normalizedBackground,
      content: normalizedContent,
      sources,
    });
    if (applyPseudoCssDecision(element, pseudo)) {
      return;
    }
    for (const sourceUrl of sources) {
      scheduleCssFallbackDecision(sourceUrl);
    }
  };

  const probeCssBackgrounds = () => {
    backgroundProbeTimer = null;
    lastBackgroundProbeAt = performance.now();
    const root = document.documentElement;
    if (!root) {
      return;
    }
    const discovered = [];
    root.setAttribute(BACKGROUND_PROBE_ATTRIBUTE, "true");
    try {
      let inspected = 0;
      for (const element of document.querySelectorAll("*")) {
        if (inspected >= MAX_BACKGROUND_PROBE_ELEMENTS) {
          break;
        }
        if (!(element instanceof HTMLElement) || !isNearViewport(element)) {
          continue;
        }
        inspected += 1;
        const backgroundImage = getComputedStyle(element).backgroundImage;
        if (backgroundImage && backgroundImage !== "none") {
          discovered.push({ element, backgroundImage, pseudo: null, content: "normal" });
        }
        for (const pseudo of ["before", "after"]) {
          const style = getComputedStyle(element, `::${pseudo}`);
          if (
            (style.backgroundImage && style.backgroundImage !== "none") ||
            (style.content && !["none", "normal"].includes(style.content))
          ) {
            discovered.push({
              element,
              backgroundImage: style.backgroundImage,
              pseudo,
              content: style.content,
            });
          }
        }
      }
    } finally {
      root.removeAttribute(BACKGROUND_PROBE_ATTRIBUTE);
    }
    for (const record of discovered) {
      if (record.pseudo) {
        recordPseudoCssVisual(
          record.element,
          record.pseudo,
          record.backgroundImage,
          record.content,
        );
      } else {
        recordCssBackground(record.element, record.backgroundImage);
      }
    }
  };

  const scheduleCssBackgroundProbe = (delayMs = BACKGROUND_PROBE_DELAY_MS) => {
    if (backgroundProbeTimer !== null) {
      return;
    }
    const elapsed = performance.now() - lastBackgroundProbeAt;
    const remainingThrottle = Math.max(0, MIN_BACKGROUND_PROBE_INTERVAL_MS - elapsed);
    backgroundProbeTimer = setTimeout(
      probeCssBackgrounds,
      Math.max(delayMs, remainingThrottle),
    );
  };

  const isGoogleSearchDocument = () =>
    window.top === window &&
    /(^|\.)google\./iu.test(location.hostname) &&
    location.pathname === "/search";

  const markSponsoredGoogleResults = () => {
    sponsoredScanTimer = null;
    if (!isGoogleSearchDocument()) {
      return;
    }
    const knownAdContainers = [
      "[data-text-ad]",
      "[data-pla-slot]",
      "[data-ta-slot]",
      ".uEierd",
    ].join(",");
    document.querySelectorAll(knownAdContainers).forEach((container) => {
      container.setAttribute(SPONSORED_RESULT_ATTRIBUTE, "true");
    });
    const collapseSponsoredResults =
      /^(ocultar resultados patrocinados|hide sponsored results)\b/iu;
    document.querySelectorAll("button, [role='button'], a, span, div").forEach((control) => {
      if (
        collapseSponsoredResults.test(control.textContent?.trim() || "") &&
        control.getAttribute(SPONSORED_RESULT_ATTRIBUTE) !== "collapsed"
      ) {
        control.setAttribute(SPONSORED_RESULT_ATTRIBUTE, "collapsed");
        (control.closest("button, [role='button'], a") || control).click();
      }
    });
    const sponsoredLabel = /^(patrocinado|sponsored)$/iu;
    document.querySelectorAll("span, div").forEach((label) => {
      if (!sponsoredLabel.test(label.textContent?.trim() || "")) {
        return;
      }
      label.closest(knownAdContainers)?.setAttribute(
        SPONSORED_RESULT_ATTRIBUTE,
        "true",
      );
    });
  };

  const scheduleSponsoredGoogleScan = (delayMs = 120) => {
    if (!isGoogleSearchDocument() || sponsoredScanTimer !== null) {
      return;
    }
    sponsoredScanTimer = setTimeout(markSponsoredGoogleResults, delayMs);
  };

  const stopFallbackObservation = (element) => {
    fallbackNearElements.delete(element);
    fallbackObservedSources.delete(element);
    fallbackObserver?.unobserve(element);
    const scheduled = fallbackTimers.get(element);
    if (scheduled) {
      clearTimeout(scheduled.timer);
      fallbackTimers.delete(element);
    }
  };

  const rememberFallbackAttempt = (sourceUrl) => {
    if (
      !fallbackAttemptsBySource.has(sourceUrl) &&
      fallbackAttemptsBySource.size >= MAX_REMEMBERED_DECISIONS
    ) {
      fallbackAttemptsBySource.delete(fallbackAttemptsBySource.keys().next().value);
    }
    const attempt = (fallbackAttemptsBySource.get(sourceUrl) || 0) + 1;
    fallbackAttemptsBySource.set(sourceUrl, attempt);
    return attempt;
  };

  const retryDelayOrMarkError = (sourceUrl) => {
    const attempt = rememberFallbackAttempt(sourceUrl);
    if (attempt >= MAX_FALLBACK_ATTEMPTS) {
      if (!failedSources.has(sourceUrl) && failedSources.size >= MAX_REMEMBERED_DECISIONS) {
        failedSources.delete(failedSources.values().next().value);
      }
      failedSources.add(sourceUrl);
      fallbackAttemptsBySource.delete(sourceUrl);
      applyDecisionToSource(sourceUrl);
      return null;
    }
    return Math.min(
      FALLBACK_RETRY_MAX_MS,
      FALLBACK_RETRY_BASE_MS * 2 ** Math.min(attempt - 1, 4),
    );
  };

  const encodeBase64 = (bytes) => {
    let binary = "";
    const chunkSize = 0x8000;
    for (let offset = 0; offset < bytes.byteLength; offset += chunkSize) {
      binary += String.fromCharCode(...bytes.subarray(offset, offset + chunkSize));
    }
    return btoa(binary);
  };

  const requestSourceDecision = async (sourceUrl, priority) => {
    if (new URL(sourceUrl).protocol !== "blob:") {
      return browser.runtime.sendMessage({
        type: FALLBACK_REQUEST_MESSAGE,
        version: 1,
        sourceUrl,
        priority,
      });
    }
    const response = await fetch(sourceUrl, {
      credentials: "include",
      cache: "no-store",
    });
    if (!response.ok) {
      throw new Error("blob_fetch_failed");
    }
    const bytes = new Uint8Array(await response.arrayBuffer());
    if (bytes.byteLength === 0 || bytes.byteLength > MAX_INLINE_ANALYSIS_BYTES) {
      throw new Error("blob_size_invalid");
    }
    return browser.runtime.sendMessage({
      type: INLINE_REQUEST_MESSAGE,
      version: 1,
      sourceUrl,
      priority,
      byteLength: bytes.byteLength,
      bytesBase64: encodeBase64(bytes),
    });
  };

  const requestFallbackDecision = (element, sourceUrl) => {
    if (
      decisionsBySource.has(sourceUrl) ||
      fallbackPendingSources.has(sourceUrl) ||
      !hasLoadedPixels(element) ||
      !candidateSourcesFor(element).includes(sourceUrl)
    ) {
      return;
    }
    fallbackPendingSources.add(sourceUrl);
    requestSourceDecision(
      sourceUrl,
      isVisibleNow(element) ? "visible" : "nearby",
    )
      .then((response) => {
        fallbackPendingSources.delete(sourceUrl);
        if (
          [FALLBACK_RESPONSE_MESSAGE, INLINE_RESPONSE_MESSAGE].includes(response?.type) &&
          response?.version === 1 &&
          normalizedSource(response.sourceUrl) === sourceUrl &&
          DECISION_ACTIONS.includes(response.action)
        ) {
          fallbackAttemptsBySource.delete(sourceUrl);
          rememberDecision(sourceUrl, response.action);
          applyDecisionToSource(sourceUrl);
          return;
        }
        const retryDelay = retryDelayOrMarkError(sourceUrl);
        if (retryDelay !== null) scheduleFallbackDecision(element, sourceUrl, retryDelay);
      })
      .catch(() => {
        fallbackPendingSources.delete(sourceUrl);
        const retryDelay = retryDelayOrMarkError(sourceUrl);
        if (retryDelay !== null) scheduleFallbackDecision(element, sourceUrl, retryDelay);
      });
  };

  const scheduleFallbackDecision = (
    element,
    sourceUrl,
    delayMs = FALLBACK_DELAY_MS,
  ) => {
    if (
      !hasLoadedPixels(element) ||
      decisionsBySource.has(sourceUrl) ||
      fallbackPendingSources.has(sourceUrl) ||
      !candidateSourcesFor(element).includes(sourceUrl) ||
      (fallbackObserver && !fallbackNearElements.has(element))
    ) {
      return;
    }
    const scheduled = fallbackTimers.get(element);
    if (scheduled?.sourceUrl === sourceUrl) {
      return;
    }
    if (scheduled) {
      clearTimeout(scheduled.timer);
    }
    const timer = setTimeout(() => {
      fallbackTimers.delete(element);
      requestFallbackDecision(element, sourceUrl);
    }, delayMs);
    fallbackTimers.set(element, { sourceUrl, timer });
  };

  const observeFallbackDecision = (element, sourceUrl) => {
    if (!hasLoadedPixels(element)) {
      return;
    }
    fallbackObservedSources.set(element, sourceUrl);
    if (fallbackObserver) {
      fallbackObserver.observe(element);
      if (isNearViewport(element)) {
        fallbackNearElements.add(element);
        scheduleFallbackDecision(element, sourceUrl);
      }
    } else {
      scheduleFallbackDecision(element, sourceUrl);
    }
  };

  const fallbackObserver =
    typeof IntersectionObserver === "function"
      ? new IntersectionObserver(
          (entries) => {
            for (const entry of entries) {
              const element = entry.target;
              const sourceUrl = fallbackObservedSources.get(element);
              if (!sourceUrl) {
                continue;
              }
              if (entry.isIntersecting) {
                fallbackNearElements.add(element);
                scheduleFallbackDecision(element, sourceUrl);
              } else {
                fallbackNearElements.delete(element);
                const scheduled = fallbackTimers.get(element);
                if (scheduled) {
                  clearTimeout(scheduled.timer);
                  fallbackTimers.delete(element);
                }
              }
            }
          },
          { rootMargin: FALLBACK_ROOT_MARGIN },
        )
      : null;

  const updateMediaHostState = (element) => {
    if (!(element instanceof HTMLImageElement)) {
      return;
    }
    const host = element.parentElement;
    if (!host) {
      return;
    }
    const mediaBounds = element.getBoundingClientRect();
    const hostBounds = host.getBoundingClientRect();
    const mediaArea = mediaBounds.width * mediaBounds.height;
    const hostArea = hostBounds.width * hostBounds.height;
    if (
      mediaBounds.width < 48 ||
      mediaBounds.height < 48 ||
      hostArea <= 0 ||
      hostArea > mediaArea * 2.5
    ) {
      host.removeAttribute(MEDIA_HOST_ATTRIBUTE);
      return;
    }
    const siblingStates =
      Array.from(host.children)
        .filter((child) => child instanceof HTMLImageElement)
        .map((child) => child.getAttribute("data-glosh-dag-media"));
    const hostState =
      siblingStates.includes("hidden")
        ? "waiting"
        : siblingStates.includes("error")
          ? "error"
          : siblingStates.includes("block")
            ? "filtered"
            : null;
    if (hostState === null) {
      host.removeAttribute(MEDIA_HOST_ATTRIBUTE);
    } else {
      host.setAttribute(MEDIA_HOST_ATTRIBUTE, hostState);
    }
  };

  const updateAccessibleMediaState = (element, action) => {
    if (!(element instanceof Element)) {
      return;
    }
    if (action === "block") {
      element.setAttribute("aria-description", FILTERED_ACCESSIBLE_DESCRIPTION);
    } else if (
      element.getAttribute("aria-description") === FILTERED_ACCESSIBLE_DESCRIPTION
    ) {
      element.removeAttribute("aria-description");
    }
  };

  const applyKnownDecision = (element) => {
    if (!(element instanceof Element) || !element.matches(mediaSelector)) {
      return false;
    }
    if (applyInlineUiVectorDecision(element)) {
      stopFallbackObservation(element);
      updateAccessibleMediaState(element, "allow");
      updateMediaHostState(element, "allow");
      return true;
    }
    const sourceUrl = candidateSourcesFor(element).find((source) => decisionsBySource.has(source));
    const action = sourceUrl ? decisionsBySource.get(sourceUrl) : null;
    if (sourceUrl && isSafeRemoteUiVector(element, sourceUrl)) {
      analyzedSources.set(element, sourceUrl);
      stopFallbackObservation(element);
      element.setAttribute(UI_VECTOR_ATTRIBUTE, "allow");
      element.setAttribute("data-glosh-dag-media", "allow");
      updateAccessibleMediaState(element, "allow");
      updateMediaHostState(element, "allow");
      return true;
    }
    element.removeAttribute(UI_VECTOR_ATTRIBUTE);
    const failedSource = candidateSourcesFor(element).find((source) => failedSources.has(source));
    if (failedSource) {
      analyzedSources.set(element, failedSource);
      stopFallbackObservation(element);
      element.setAttribute("data-glosh-dag-media", "error");
      updateAccessibleMediaState(element, "error");
      updateMediaHostState(element, "error");
      return true;
    }
    if (!sourceUrl || !action) {
      if (!sourceUrl || analyzedSources.get(element) !== sourceUrl) {
        element.setAttribute("data-glosh-dag-media", "hidden");
      }
      updateAccessibleMediaState(element, "hidden");
      updateMediaHostState(element, "waiting");
      const fallbackSource = candidateSourcesFor(element)[0];
      if (fallbackSource) {
        observeFallbackDecision(element, fallbackSource);
      }
      return false;
    }
    analyzedSources.set(element, sourceUrl);
    stopFallbackObservation(element);
    element.setAttribute("data-glosh-dag-media", action);
    updateAccessibleMediaState(element, action);
    updateMediaHostState(
      element,
      action === "block" ? "filtered" : action === "error" ? "error" : "allow",
    );
    return true;
  };

  browser.runtime.onMessage.addListener((message) => {
    if (
      message?.type !== PRESENTATION_DECISION_MESSAGE ||
      message?.version !== 1 ||
      !DECISION_ACTIONS.includes(message?.action)
    ) {
      return undefined;
    }
    const sourceUrl = normalizedSource(message.sourceUrl);
    if (!sourceUrl || !["http:", "https:"].includes(new URL(sourceUrl).protocol)) {
      return undefined;
    }
    rememberDecision(sourceUrl, message.action);
    let matchedCount = 0;
    const matchedStates = [];
    document.querySelectorAll(mediaSelector).forEach((element) => {
      if (candidateSourcesFor(element).includes(sourceUrl)) {
        if (applyKnownDecision(element)) {
          matchedCount += 1;
          if (matchedStates.length < 3) {
            const style = getComputedStyle(element);
            matchedStates.push(
              `${element.tagName}:${element.getAttribute("alt") || ""}:` +
                `display=${style.display}:visibility=${style.visibility}:` +
                `opacity=${style.opacity}:size=${element.naturalWidth || 0}x` +
                `${element.naturalHeight || 0}`,
            );
          }
        }
      }
    });
    return Promise.resolve({
      type: PRESENTATION_APPLIED_MESSAGE,
      version: 1,
      sourceUrl,
      matchedCount,
      matchedStates: matchedStates.join("|").slice(0, 900),
    });
  });

  let nativeDecisionPort = null;
  try {
    nativeDecisionPort = browser.runtime.connectNative("glosh.dag.protection");
    if (window.top === window) {
      nativeDecisionPort.postMessage({
        type: "barrier-ready",
        version: 1,
        url: location.href,
      });
    }
  } catch {
    // Without the authenticated native channel, media remains hidden.
  }

  const markHidden = (root) => {
    if (!(root instanceof Element) && root !== document) {
      return;
    }
    if (root instanceof Element && root.matches(mediaSelector)) {
      applyKnownDecision(root);
    }
    root.querySelectorAll?.(mediaSelector).forEach((element) => {
      applyKnownDecision(element);
    });
  };

  document.addEventListener(
    "load",
    (event) => {
      applyKnownDecision(event.target);
    },
    true,
  );

  markHidden(document);
  scheduleCssBackgroundProbe(0);
  scheduleSponsoredGoogleScan(0);
  const observer = new MutationObserver((mutations) => {
    let shouldProbeBackgrounds = false;
    for (const mutation of mutations) {
      for (const node of mutation.addedNodes) {
        markHidden(node);
        shouldProbeBackgrounds = true;
      }
      if (mutation.target instanceof Element && mutation.type === "attributes") {
        markHidden(mutation.target);
        shouldProbeBackgrounds = true;
      }
    }
    if (shouldProbeBackgrounds) {
      scheduleCssBackgroundProbe();
      scheduleSponsoredGoogleScan();
    }
  });
  observer.observe(document, {
    attributes: true,
    attributeFilter: [
      "class",
      "src",
      "srcset",
      "data-src",
      "data-srcset",
      "data-lazy-src",
      "data-original",
      "data-url",
      "poster",
      "style",
      "alt",
      "aria-label",
    ],
    childList: true,
    subtree: true,
  });
    window.addEventListener(
    "scroll",
    () => scheduleCssBackgroundProbe(),
    { passive: true },
  );

  if (window.top === window) {
    const reportDocumentState = (type) => {
      browser.runtime
        .sendMessage({
          type,
          version: 1,
          documentToken: performanceDocumentToken,
        })
        .catch(() => {
          // Missing performance evidence never weakens the media barrier.
        });
    };
    reportDocumentState("document-started");
  window.addEventListener(
      "load",
      () => {
        markHidden(document);
        scheduleCssBackgroundProbe(0);
        scheduleSponsoredGoogleScan(0);
        reportDocumentState("document-loaded");
      },
      { once: true },
    );
  }
})();
