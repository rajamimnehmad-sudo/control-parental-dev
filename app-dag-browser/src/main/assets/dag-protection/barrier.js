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
  const PLAYABLE_MEDIA_SELECTOR = "video, audio";
  const FILTERED_ACCESSIBLE_DESCRIPTION = "Protegida por Glosh";
  const MAX_REMEMBERED_DECISIONS = 512;
  const FALLBACK_DELAY_MS = 80;
  const FALLBACK_RETRY_BASE_MS = 600;
  const FALLBACK_RETRY_MAX_MS = 6_000;
  const MAX_FALLBACK_ATTEMPTS = 4;
  const FALLBACK_ROOT_MARGIN = "640px 0px";
  const SOURCE_RECONCILE_DELAY_MS = 160;
  const SOURCE_MUTATION_ATTRIBUTES = new Set([
    "src",
    "srcset",
    "data-src",
    "data-srcset",
    "data-lazy-src",
    "data-original",
    "data-url",
    "poster",
  ]);
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
  const FUNCTIONAL_ICON_ATTRIBUTE = "data-glosh-dag-functional-icon";
  const APPROVED_PRESENTATION_SELECTOR = [
    '[data-glosh-dag-media="allow"]',
    '[data-glosh-dag-ui-vector="allow"]',
    '[data-glosh-dag-css-media="allow"]',
    '[data-glosh-dag-css-before="allow"]',
    '[data-glosh-dag-css-after="allow"]',
    `[${FUNCTIONAL_ICON_ATTRIBUTE}]`,
  ].join(",");
  const MAX_CSS_BACKGROUND_ELEMENTS = 512;
  const MAX_BACKGROUND_PROBE_ELEMENTS = 1_500;
  const BACKGROUND_PROBE_DELAY_MS = 180;
  const MIN_BACKGROUND_PROBE_INTERVAL_MS = 450;
  const BACKGROUND_SCROLL_SETTLE_MS = 160;
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
  const mediaHostsByElement = new WeakMap();
  const indexedSourcesByElement = new WeakMap();
  const mediaElementsBySource = new Map();
  const pendingSourceChanges = new WeakSet();
  const sourceReconcileTimers = new WeakMap();
  const cssBackgroundRecords = new Map();
  const cssBeforeRecords = new Map();
  const cssAfterRecords = new Map();
  const cssFallbackTimers = new Map();
  const pendingBackgroundProbeRoots = new Set();
  const ownStyleSnapshots = new WeakMap();
  let backgroundProbeTimer = null;
  let scrollBackgroundProbeTimer = null;
  let lastBackgroundProbeAt = Number.NEGATIVE_INFINITY;
  let sponsoredScanTimer = null;
  const performanceDocumentToken =
    `document_${crypto.getRandomValues(new Uint32Array(1))[0].toString(16)}`;

  const setAttributeIfChanged = (element, name, value) => {
    if (element.getAttribute(name) !== value) {
      element.setAttribute(name, value);
    }
  };

  const removeAttributeIfPresent = (element, name) => {
    if (element.hasAttribute(name)) {
      element.removeAttribute(name);
    }
  };

  const rememberOwnStyleMutation = (element) => {
    ownStyleSnapshots.set(element, element.getAttribute("style") || "");
  };

  const setStylePropertyIfChanged = (element, property, value) => {
    if (element.style.getPropertyValue(property) === value) {
      return;
    }
    element.style.setProperty(property, value);
    rememberOwnStyleMutation(element);
  };

  const removeStylePropertyIfPresent = (element, property) => {
    if (!element.style.getPropertyValue(property)) {
      return;
    }
    element.style.removeProperty(property);
    rememberOwnStyleMutation(element);
  };

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

  const removeElementFromSourceIndex = (element) => {
    const previousSources = indexedSourcesByElement.get(element) || [];
    for (const sourceUrl of previousSources) {
      const elements = mediaElementsBySource.get(sourceUrl);
      elements?.delete(element);
      if (elements?.size === 0) {
        mediaElementsBySource.delete(sourceUrl);
      }
    }
    indexedSourcesByElement.delete(element);
  };

  const updateElementSourceIndex = (element, sources) => {
    const previousSources = indexedSourcesByElement.get(element) || [];
    if (
      previousSources.length === sources.length &&
      previousSources.every((sourceUrl, index) => sourceUrl === sources[index])
    ) {
      return;
    }
    removeElementFromSourceIndex(element);
    if (sources.length === 0) {
      return;
    }
    indexedSourcesByElement.set(element, sources);
    for (const sourceUrl of sources) {
      let elements = mediaElementsBySource.get(sourceUrl);
      if (!elements) {
        elements = new Set();
        mediaElementsBySource.set(sourceUrl, elements);
      }
      elements.add(element);
    }
  };

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
    updateElementSourceIndex(element, sources);
    return sources;
  };

  const stopPlayableMedia = (element) => {
    if (!(element instanceof HTMLMediaElement)) {
      return;
    }
    element.autoplay = false;
    element.defaultMuted = true;
    if (!element.muted) {
      element.muted = true;
    }
    setAttributeIfChanged(element, "preload", "none");
    setAttributeIfChanged(element, "aria-hidden", "true");
    try {
      element.pause();
    } catch {
      // A detached or not-yet-initialized media element is already fail-closed.
    }
    if (element.srcObject !== null) {
      try {
        element.srcObject = null;
      } catch {
        // Some page-owned streams expose a read-only assignment boundary.
      }
    }
  };

  const stopPlayableMediaIn = (root) => {
    if (!(root instanceof Element) && root !== document) {
      return;
    }
    if (root instanceof HTMLMediaElement) {
      stopPlayableMedia(root);
    }
    root.querySelectorAll?.(PLAYABLE_MEDIA_SELECTOR).forEach(stopPlayableMedia);
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
      setAttributeIfChanged(element, UI_VECTOR_ATTRIBUTE, "allow");
      clearWaitingMediaHostsAround(element);
      return true;
    }
    removeAttributeIfPresent(element, UI_VECTOR_ATTRIBUTE);
    setAttributeIfChanged(element, "data-glosh-dag-media", "hidden");
    return false;
  };

  const rememberDecision = (sourceUrl, action) => {
    if (!decisionsBySource.has(sourceUrl) && decisionsBySource.size >= MAX_REMEMBERED_DECISIONS) {
      decisionsBySource.delete(decisionsBySource.keys().next().value);
    }
    decisionsBySource.set(sourceUrl, action);
    failedSources.delete(sourceUrl);
  };

  function clearWaitingMediaHostsAround(element) {
    let current = element;
    for (let depth = 0; current instanceof Element && depth < 4; depth += 1) {
      if (current.getAttribute(MEDIA_HOST_ATTRIBUTE) === "waiting") {
        removeAttributeIfPresent(current, MEDIA_HOST_ATTRIBUTE);
      }
      current = current.parentElement;
    }
  }

  const applyDecisionToMediaSource = (sourceUrl) => {
    const indexedElements = mediaElementsBySource.get(sourceUrl);
    const candidates =
      indexedElements?.size > 0
        ? [...indexedElements]
        : [...document.querySelectorAll(mediaSelector)];
    let matchedCount = 0;
    for (const element of candidates) {
      if (!element.isConnected) {
        removeElementFromSourceIndex(element);
        continue;
      }
      const stillReferencesDecisionSource =
        candidateSourcesFor(element).includes(sourceUrl);
      const appliedCurrentDecision = applyKnownDecision(element);
      if (stillReferencesDecisionSource && appliedCurrentDecision) {
        matchedCount += 1;
      }
    }
    return matchedCount;
  };

  const applyDecisionToSource = (sourceUrl) => {
    applyDecisionToMediaSource(sourceUrl);
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

  const functionalIconKind = (element) => {
    const bounds = element.getBoundingClientRect();
    if (
      !Number.isFinite(bounds.width) ||
      !Number.isFinite(bounds.height) ||
      bounds.width <= 0 ||
      bounds.height <= 0 ||
      bounds.width > 64 ||
      bounds.height > 64
    ) {
      return null;
    }
    const link = element.closest("a, button");
    const semantics = [
      element.id,
      element.className,
      element.getAttribute("aria-label"),
      element.getAttribute("title"),
      link?.id,
      link?.className,
      link?.getAttribute("aria-label"),
      link?.getAttribute("title"),
      link?.getAttribute("href"),
    ]
      .filter((value) => typeof value === "string")
      .join(" ");
    return /\b(?:favorite|favourite|favorito|wishlist|iheart|fav-heart)\b/iu.test(semantics)
      ? "favorite"
      : null;
  };

  const applyFunctionalIconFallback = (element) => {
    const kind = functionalIconKind(element);
    if (!kind) {
      removeAttributeIfPresent(element, FUNCTIONAL_ICON_ATTRIBUTE);
      return false;
    }
    setAttributeIfChanged(element, FUNCTIONAL_ICON_ATTRIBUTE, kind);
    clearWaitingMediaHostsAround(element);
    return true;
  };

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
      removeAttributeIfPresent(element, FUNCTIONAL_ICON_ATTRIBUTE);
      setStylePropertyIfChanged(element, CSS_MEDIA_VALUE_PROPERTY, backgroundImage);
      if (element.getAttribute(CSS_MEDIA_ATTRIBUTE) !== "allow") {
        element.setAttribute(CSS_MEDIA_ATTRIBUTE, "allow");
      }
      clearWaitingMediaHostsAround(element);
      return true;
    }
    const actions = sources.map((sourceUrl) => decisionsBySource.get(sourceUrl));
    if (actions.some((action) => action === "block")) {
      removeStylePropertyIfPresent(element, CSS_MEDIA_VALUE_PROPERTY);
      applyFunctionalIconFallback(element);
      if (element.getAttribute(CSS_MEDIA_ATTRIBUTE) !== "block") {
        element.setAttribute(CSS_MEDIA_ATTRIBUTE, "block");
      }
      return true;
    }
    if (
      actions.some((action) => action === "error") ||
      sources.some((sourceUrl) => failedSources.has(sourceUrl))
    ) {
      removeStylePropertyIfPresent(element, CSS_MEDIA_VALUE_PROPERTY);
      applyFunctionalIconFallback(element);
      setAttributeIfChanged(element, CSS_MEDIA_ATTRIBUTE, "error");
      return true;
    }
    if (actions.every((action) => action === "allow")) {
      removeAttributeIfPresent(element, FUNCTIONAL_ICON_ATTRIBUTE);
      setStylePropertyIfChanged(element, CSS_MEDIA_VALUE_PROPERTY, backgroundImage);
      if (element.getAttribute(CSS_MEDIA_ATTRIBUTE) !== "allow") {
        element.setAttribute(CSS_MEDIA_ATTRIBUTE, "allow");
      }
      clearWaitingMediaHostsAround(element);
      return true;
    }
    removeStylePropertyIfPresent(element, CSS_MEDIA_VALUE_PROPERTY);
    removeAttributeIfPresent(element, FUNCTIONAL_ICON_ATTRIBUTE);
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
      setStylePropertyIfChanged(element, config.backgroundProperty, backgroundImage);
      setStylePropertyIfChanged(element, config.contentProperty, content);
      if (element.getAttribute(config.attribute) !== "allow") {
        element.setAttribute(config.attribute, "allow");
      }
      clearWaitingMediaHostsAround(element);
      return true;
    }
    for (const property of [config.backgroundProperty, config.contentProperty]) {
      removeStylePropertyIfPresent(element, property);
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

  const rememberBackgroundProbeRoot = (root) => {
    const normalizedRoot =
      root === document || root === document.documentElement
        ? document
        : root instanceof Element && root.isConnected
          ? root
          : null;
    if (!normalizedRoot || pendingBackgroundProbeRoots.has(document)) {
      return;
    }
    if (normalizedRoot === document) {
      pendingBackgroundProbeRoots.clear();
      pendingBackgroundProbeRoots.add(document);
      return;
    }
    for (const existing of pendingBackgroundProbeRoots) {
      if (existing instanceof Element && existing.contains(normalizedRoot)) {
        return;
      }
      if (existing instanceof Element && normalizedRoot.contains(existing)) {
        pendingBackgroundProbeRoots.delete(existing);
      }
    }
    pendingBackgroundProbeRoots.add(normalizedRoot);
  };

  function* backgroundProbeCandidates(root) {
    if (root === document) {
      yield* document.querySelectorAll("*");
      return;
    }
    yield root;
    yield* root.querySelectorAll("*");
  }

  const probeCssBackgrounds = () => {
    backgroundProbeTimer = null;
    lastBackgroundProbeAt = performance.now();
    const documentRoot = document.documentElement;
    if (!documentRoot) {
      return;
    }
    const probeRoots =
      pendingBackgroundProbeRoots.size > 0
        ? [...pendingBackgroundProbeRoots]
        : [document];
    pendingBackgroundProbeRoots.clear();
    const discovered = [];
    documentRoot.setAttribute(BACKGROUND_PROBE_ATTRIBUTE, "true");
    try {
      let inspected = 0;
      for (const probeRoot of probeRoots) {
        for (const element of backgroundProbeCandidates(probeRoot)) {
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
        if (inspected >= MAX_BACKGROUND_PROBE_ELEMENTS) {
          break;
        }
      }
    } finally {
      documentRoot.removeAttribute(BACKGROUND_PROBE_ATTRIBUTE);
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

  const scheduleCssBackgroundProbe = (
    root = document,
    delayMs = BACKGROUND_PROBE_DELAY_MS,
  ) => {
    rememberBackgroundProbeRoot(root);
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

  const scheduleScrollBackgroundProbe = () => {
    if (scrollBackgroundProbeTimer !== null) {
      clearTimeout(scrollBackgroundProbeTimer);
    }
    scrollBackgroundProbeTimer = setTimeout(() => {
      scrollBackgroundProbeTimer = null;
      scheduleCssBackgroundProbe(document, 0);
    }, BACKGROUND_SCROLL_SETTLE_MS);
  };

  const isGoogleSearchDocument = () =>
    window.top === window &&
    /(^|\.)google\./iu.test(location.hostname) &&
    location.pathname === "/search";

  const markSponsoredGoogleResults = () => {
    if (!isGoogleSearchDocument()) {
      return;
    }
    const knownAdContainers = [
      "[data-text-ad]",
      "[data-pla-slot]",
      "[data-ta-slot]",
      "#tads",
      "#taw",
      "[aria-label='Anuncios']",
      "[aria-label='Sponsored products']",
      "[aria-label='Productos patrocinados']",
      ".uEierd",
    ].join(",");
    document.querySelectorAll(knownAdContainers).forEach((container) => {
      setAttributeIfChanged(container, SPONSORED_RESULT_ATTRIBUTE, "true");
    });
    const collapseSponsoredResults =
      /^(ocultar resultados patrocinados|hide sponsored results)\b/iu;
    document.querySelectorAll("button, [role='button'], a, span, div").forEach((control) => {
      if (
        collapseSponsoredResults.test(control.textContent?.trim() || "") &&
        control.getAttribute(SPONSORED_RESULT_ATTRIBUTE) !== "collapsed"
      ) {
        setAttributeIfChanged(control, SPONSORED_RESULT_ATTRIBUTE, "collapsed");
        (control.closest("button, [role='button'], a") || control).click();
      }
    });
    const sponsoredLabel = /^(patrocinado|sponsored)$/iu;
    document.querySelectorAll("span, div").forEach((label) => {
      if (!sponsoredLabel.test(label.textContent?.trim() || "")) {
        return;
      }
      const container = label.closest(knownAdContainers);
      if (container) {
        setAttributeIfChanged(container, SPONSORED_RESULT_ATTRIBUTE, "true");
      }
    });
  };

  const markExplicitAdvertisementFrames = () => {
    const explicitAdvertisementFrames = [
      "iframe[title='Advertisement' i]",
      "iframe[aria-label='Advertisement' i]",
      "iframe[name^='google_ads_iframe']",
    ].join(",");
    document.querySelectorAll(explicitAdvertisementFrames).forEach((frame) => {
      setAttributeIfChanged(frame, SPONSORED_RESULT_ATTRIBUTE, "true");
      let ancestor = frame.parentElement;
      for (let depth = 0; ancestor && depth < 4; depth += 1) {
        const bounds = ancestor.getBoundingClientRect();
        const position = getComputedStyle(ancestor).position;
        const isLargeOverlay =
          ["fixed", "sticky"].includes(position) &&
          bounds.width >= window.innerWidth * 0.6 &&
          bounds.height >= window.innerHeight * 0.3;
        if (isLargeOverlay) {
          setAttributeIfChanged(ancestor, SPONSORED_RESULT_ATTRIBUTE, "true");
          break;
        }
        ancestor = ancestor.parentElement;
      }
    });
  };

  const scanSponsoredContent = () => {
    sponsoredScanTimer = null;
    markExplicitAdvertisementFrames();
    markSponsoredGoogleResults();
  };

  const scheduleSponsoredScan = (delayMs = 120) => {
    if (sponsoredScanTimer !== null) {
      return;
    }
    sponsoredScanTimer = setTimeout(scanSponsoredContent, delayMs);
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

  const reconcileMediaHostState = (host) => {
    if (!(host instanceof Element)) return;
    if (host.matches(APPROVED_PRESENTATION_SELECTOR) || host.querySelector(APPROVED_PRESENTATION_SELECTOR)) {
      removeAttributeIfPresent(host, MEDIA_HOST_ATTRIBUTE);
      return;
    }
    const trackedImages =
      Array.from(host.children)
        .filter((child) => child instanceof HTMLImageElement)
        .filter((child) => mediaHostsByElement.get(child) === host && child.isConnected);
    if (trackedImages.length === 0) {
      removeAttributeIfPresent(host, MEDIA_HOST_ATTRIBUTE);
      return;
    }
    const siblingStates =
      trackedImages
        .filter((child) => {
          const bounds = child.getBoundingClientRect();
          return bounds.width >= 1 && bounds.height >= 1;
        })
        .map((child) => child.getAttribute("data-glosh-dag-media"));
    const hostState =
      siblingStates.includes("allow")
          ? null
          : siblingStates.includes("hidden")
            ? "waiting"
            : siblingStates.includes("error")
              ? "error"
              : null;
    if (hostState === null) {
      removeAttributeIfPresent(host, MEDIA_HOST_ATTRIBUTE);
    } else {
      setAttributeIfChanged(host, MEDIA_HOST_ATTRIBUTE, hostState);
    }
  };

  const releaseMediaHost = (element) => {
    if (!(element instanceof HTMLImageElement)) return;
    const previousHost = mediaHostsByElement.get(element);
    mediaHostsByElement.delete(element);
    reconcileMediaHostState(previousHost);
  };

  const releaseMediaHostsIn = (root) => {
    if (!(root instanceof Element)) return;
    const releaseTrackedMedia = (element) => {
      if (element instanceof HTMLImageElement) {
        releaseMediaHost(element);
      }
      removeElementFromSourceIndex(element);
      stopFallbackObservation(element);
    };
    if (root.matches(mediaSelector)) {
      releaseTrackedMedia(root);
    }
    root.querySelectorAll(mediaSelector).forEach(releaseTrackedMedia);
  };

  const updateMediaHostState = (element, presentationState) => {
    if (!(element instanceof HTMLImageElement)) return;
    if (presentationState === "filtered") {
      releaseMediaHost(element);
      return;
    }
    const previousHost = mediaHostsByElement.get(element);
    const host = element.parentElement;
    if (previousHost && previousHost !== host) {
      mediaHostsByElement.delete(element);
      reconcileMediaHostState(previousHost);
    }
    if (!host) return;
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
      mediaHostsByElement.delete(element);
      reconcileMediaHostState(host);
      return;
    }
    mediaHostsByElement.set(element, host);
    reconcileMediaHostState(host);
  };

  const updateAccessibleMediaState = (element, action) => {
    if (!(element instanceof Element)) {
      return;
    }
    if (action === "block") {
      setAttributeIfChanged(
        element,
        "aria-description",
        FILTERED_ACCESSIBLE_DESCRIPTION,
      );
    } else if (
      element.getAttribute("aria-description") === FILTERED_ACCESSIBLE_DESCRIPTION
    ) {
      removeAttributeIfPresent(element, "aria-description");
    }
  };

  const clearSourceReconcileTimer = (element) => {
    const timer = sourceReconcileTimers.get(element);
    if (timer !== undefined) {
      clearTimeout(timer);
      sourceReconcileTimers.delete(element);
    }
  };

  const protectSourceMutation = (element) => {
    if (!(element instanceof Element) || !element.matches(mediaSelector)) {
      return;
    }
    pendingSourceChanges.add(element);
    clearSourceReconcileTimer(element);
    analyzedSources.delete(element);
    stopFallbackObservation(element);
    removeAttributeIfPresent(element, UI_VECTOR_ATTRIBUTE);
    setAttributeIfChanged(element, "data-glosh-dag-media", "hidden");
    updateAccessibleMediaState(element, "hidden");
    updateMediaHostState(element, "waiting");
    candidateSourcesFor(element);
    const timer = setTimeout(() => {
      sourceReconcileTimers.delete(element);
      if (!element.isConnected) {
        pendingSourceChanges.delete(element);
        return;
      }
      pendingSourceChanges.delete(element);
      applyKnownDecision(element);
    }, SOURCE_RECONCILE_DELAY_MS);
    sourceReconcileTimers.set(element, timer);
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
    const candidateSources = candidateSourcesFor(element);
    const activeSource = candidateSources[0];
    const sourceChangePending = pendingSourceChanges.has(element);
    const pendingSafetySource = sourceChangePending
      ? candidateSources.find((candidate) =>
          ["block", "error"].includes(decisionsBySource.get(candidate)),
        )
      : null;
    const sourceUrl =
      pendingSafetySource ||
      (!sourceChangePending && activeSource && decisionsBySource.has(activeSource)
        ? activeSource
        : null);
    const action = sourceUrl ? decisionsBySource.get(sourceUrl) : null;
    if (sourceUrl && isSafeRemoteUiVector(element, sourceUrl)) {
      analyzedSources.set(element, sourceUrl);
      stopFallbackObservation(element);
      setAttributeIfChanged(element, UI_VECTOR_ATTRIBUTE, "allow");
      setAttributeIfChanged(element, "data-glosh-dag-media", "allow");
      updateAccessibleMediaState(element, "allow");
      updateMediaHostState(element, "allow");
      clearWaitingMediaHostsAround(element);
      return true;
    }
    removeAttributeIfPresent(element, UI_VECTOR_ATTRIBUTE);
    const failedSource = sourceChangePending
      ? candidateSources.find((candidate) => failedSources.has(candidate))
      : activeSource && failedSources.has(activeSource)
        ? activeSource
        : null;
    if (failedSource) {
      analyzedSources.set(element, failedSource);
      stopFallbackObservation(element);
      setAttributeIfChanged(element, "data-glosh-dag-media", "error");
      updateAccessibleMediaState(element, "error");
      updateMediaHostState(element, "error");
      return true;
    }
    if (!sourceUrl || !action) {
      if (!sourceUrl || analyzedSources.get(element) !== sourceUrl) {
        setAttributeIfChanged(element, "data-glosh-dag-media", "hidden");
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
    setAttributeIfChanged(element, "data-glosh-dag-media", action);
    updateAccessibleMediaState(element, action);
    updateMediaHostState(
      element,
      action === "block" ? "filtered" : action === "error" ? "error" : "allow",
    );
    if (action === "allow") {
      clearWaitingMediaHostsAround(element);
    }
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
    const matchedCount = applyDecisionToMediaSource(sourceUrl);
    return Promise.resolve({
      type: PRESENTATION_APPLIED_MESSAGE,
      version: 1,
      matchedCount,
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
        documentToken: performanceDocumentToken,
      });
    }
  } catch {
    // Without the authenticated native channel, media remains hidden.
  }

  let previewEligibilityTimer = null;
  let lastPreviewRestriction = null;
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
  const hasSensitivePreviewContent = () =>
    Array.from(document.querySelectorAll(sensitivePreviewSelector)).some(isVisibleNow);

  const reportPreviewEligibility = () => {
    if (window.top !== window || nativeDecisionPort === null) {
      return;
    }
    const restricted = hasSensitivePreviewContent();
    if (restricted === lastPreviewRestriction) {
      return;
    }
    lastPreviewRestriction = restricted;
    try {
      nativeDecisionPort.postMessage({
        type: "tab-preview-eligibility",
        version: 1,
        documentToken: performanceDocumentToken,
        restricted,
      });
    } catch {
      // Missing preview eligibility keeps the native thumbnail fail-closed.
    }
  };

  const schedulePreviewEligibilityReport = () => {
    if (window.top !== window) {
      return;
    }
    if (previewEligibilityTimer !== null) {
      clearTimeout(previewEligibilityTimer);
    }
    previewEligibilityTimer = setTimeout(() => {
      previewEligibilityTimer = null;
      reportPreviewEligibility();
    }, 180);
  };

  const markHidden = (root) => {
    if (!(root instanceof Element) && root !== document) {
      return;
    }
    if (root instanceof Element && root.matches(mediaSelector)) {
      applyKnownDecision(root);
    }
    stopPlayableMediaIn(root);
    root.querySelectorAll?.(mediaSelector).forEach((element) => {
      applyKnownDecision(element);
    });
  };

  for (const eventName of ["play", "playing", "volumechange", "loadedmetadata"]) {
    document.addEventListener(
      eventName,
      (event) => stopPlayableMedia(event.target),
      true,
    );
  }

  document.addEventListener(
    "load",
    (event) => {
      if (event.target instanceof Element && event.target.matches(mediaSelector)) {
        pendingSourceChanges.delete(event.target);
        clearSourceReconcileTimer(event.target);
      }
      applyKnownDecision(event.target);
    },
    true,
  );

  markHidden(document);
  scheduleCssBackgroundProbe(document, 0);
  scheduleSponsoredScan(0);
  const observer = new MutationObserver((mutations) => {
    let shouldScanSponsoredContent = false;
    let shouldReportPreviewEligibility = false;
    for (const mutation of mutations) {
      for (const node of mutation.removedNodes) {
        releaseMediaHostsIn(node);
      }
      if (mutation.removedNodes.length > 0) {
        scheduleCssBackgroundProbe(mutation.target);
        shouldReportPreviewEligibility = true;
      }
      for (const node of mutation.addedNodes) {
        markHidden(node);
        scheduleCssBackgroundProbe(
          node instanceof Element ? node : mutation.target,
        );
        shouldScanSponsoredContent = true;
        shouldReportPreviewEligibility = true;
      }
      if (mutation.target instanceof Element && mutation.type === "attributes") {
        const attributeName = mutation.attributeName || "";
        if (attributeName === "style") {
          const ownStyle = ownStyleSnapshots.get(mutation.target);
          const currentStyle = mutation.target.getAttribute("style") || "";
          if (ownStyle !== undefined && ownStyle === currentStyle) {
            continue;
          }
          ownStyleSnapshots.delete(mutation.target);
        }
        if (mutation.target.matches(mediaSelector)) {
          if (SOURCE_MUTATION_ATTRIBUTES.has(attributeName)) {
            protectSourceMutation(mutation.target);
          } else {
            applyKnownDecision(mutation.target);
          }
          stopPlayableMediaIn(mutation.target);
        }
        if (["class", "style", "alt", "aria-label"].includes(attributeName)) {
          scheduleCssBackgroundProbe(mutation.target);
        }
        if (["class", "aria-label"].includes(attributeName)) {
          shouldScanSponsoredContent = true;
        }
        if (["autocomplete", "type", "src", "aria-label"].includes(attributeName)) {
          shouldReportPreviewEligibility = true;
        }
      }
    }
    if (shouldScanSponsoredContent) {
      scheduleSponsoredScan();
    }
    if (shouldReportPreviewEligibility) {
      schedulePreviewEligibilityReport();
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
      "autocomplete",
      "type",
    ],
    childList: true,
    subtree: true,
  });
  window.addEventListener(
    "scroll",
    scheduleScrollBackgroundProbe,
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
      "DOMContentLoaded",
      schedulePreviewEligibilityReport,
      { once: true },
    );
    window.addEventListener(
      "load",
      () => {
        markHidden(document);
        scheduleCssBackgroundProbe(document, 0);
        scheduleSponsoredScan(0);
        schedulePreviewEligibilityReport();
        reportDocumentState("document-loaded");
      },
      { once: true },
    );
  }
})();
