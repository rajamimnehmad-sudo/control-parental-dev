"use strict";

(() => {
  if (globalThis.__gloshDagBarrierInstalled === true) return;
  Object.defineProperty(globalThis, "__gloshDagBarrierInstalled", {
    value: true,
    configurable: false,
    enumerable: false,
    writable: false,
  });

  const PROTOCOL_VERSION = 1;
  const NATIVE_APP = "glosh.dag.protection";
  const MEDIA_SELECTOR = "img,image,input[type='image']";
  const BLOCKED_MEDIA_SELECTOR = "video,audio,canvas,object,embed";
  const SOURCE_ATTRIBUTES = new Set([
    "src", "srcset", "data-src", "data-srcset", "data-lazy-src",
    "data-original", "data-url", "sizes", "media", "href", "xlink:href",
  ]);
  const GENERATED_PROTOCOLS = new Set(["data:", "blob:"]);
  const INITIALIZED_ATTRIBUTE = "data-glosh-dag-initialized";
  const GENERATED_ATTRIBUTE_SELECTOR = [
    "[data-glosh-dag-generated-background]",
    "[data-glosh-dag-generated-before]",
    "[data-glosh-dag-generated-after]",
  ].join(",");
  const PROTECTED_ATTRIBUTES = new Set([
    "data-glosh-dag-media",
    "data-glosh-dag-ui-vector",
    "data-glosh-dag-generated-background",
    "data-glosh-dag-generated-before",
    "data-glosh-dag-generated-after",
    INITIALIZED_ATTRIBUTE,
  ]);
  const DECISION_ACTIONS = new Set(["allow", "block", "error"]);
  const MAX_REMEMBERED_DECISIONS = 512;
  const MAX_INLINE_ANALYSIS_BYTES = 2 * 1024 * 1024;
  const MAX_INLINE_VECTOR_ELEMENTS = 256;
  const GENERATED_SOURCE_PATTERN = /(?:data|blob):/iu;
  const GENERATED_KINDS = ["element", "before", "after"];
  const FILTERED_ACCESSIBLE_DESCRIPTION = "Protegida por Glosh";
  const ERROR_ACCESSIBLE_DESCRIPTION = "Imagen no disponible";
  const performanceDocumentToken =
    `document_${crypto.getRandomValues(new Uint32Array(1))[0].toString(16)}`;

  const decisionsBySource = new Map();
  const dimensionsBySource = new Map();
  const pendingSourceDecisions = new Map();
  const elementsBySource = new Map();
  const sourcesByElement = new WeakMap();
  const trustedMediaStates = new WeakMap();
  const generatedRecords = new Map();
  const generatedRuleTargets = [];
  const pendingLayoutElements = new Set();
  const pendingGeneratedLayout = new Map();
  const generatedKeysByElement = new WeakMap();
  const generatedIdsByElement = new WeakMap();
  const priorityBySource = new Map();
  const ownStyleSnapshots = new WeakMap();
  const presentationStylesByElement = new WeakMap();
  let nativePort = null;
  let previewEligibilityTimer = null;
  let generatedRuleRefreshScheduled = false;
  let layoutFlushScheduled = false;
  let layoutFrameId = null;
  let layoutTimerId = null;
  let nextGeneratedId = 1;
  let initialBarrierComplete = false;

  const mediaPriorityObserver = typeof IntersectionObserver === "function"
    ? new IntersectionObserver((entries) => {
        for (const entry of entries) {
          if (!entry.isIntersecting || !(entry.target instanceof Element)) continue;
          sendSourcePriority(
            sourcesByElement.get(entry.target) || indexElement(entry.target),
            "visible",
          );
          mediaPriorityObserver.unobserve(entry.target);
        }
      }, { rootMargin: "640px 0px" })
    : null;

  const setAttributeIfChanged = (element, name, value) => {
    if (element.getAttribute(name) !== value) element.setAttribute(name, value);
  };
  const removeAttributeIfPresent = (element, name) => {
    if (element.hasAttribute(name)) element.removeAttribute(name);
  };
  const normalizedSource = (rawSource) => {
    if (typeof rawSource !== "string" || rawSource.length === 0) return null;
    try {
      const url = new URL(rawSource, document.baseURI);
      url.hash = "";
      return url.href;
    } catch {
      return null;
    }
  };
  const sourcesFromSrcset = (value) =>
    String(value || "").split(",")
      .map((entry) => entry.trim().split(/\s+/u)[0])
      .map(normalizedSource).filter(Boolean);

  const candidateSourcesFor = (element) => {
    const sources = [];
    const add = (value) => {
      const normalized = normalizedSource(value);
      if (normalized && !sources.includes(normalized)) sources.push(normalized);
    };
    if (element instanceof HTMLImageElement) {
      add(element.currentSrc);
      add(element.getAttribute("src"));
      sourcesFromSrcset(element.getAttribute("srcset")).forEach(add);
      for (const name of ["data-src", "data-lazy-src", "data-original", "data-url"]) {
        add(element.getAttribute(name));
      }
      sourcesFromSrcset(element.getAttribute("data-srcset")).forEach(add);
    } else if (element instanceof SVGImageElement) {
      add(element.href?.baseVal || element.getAttribute("href"));
    } else if (element instanceof HTMLInputElement) {
      add(element.getAttribute("src"));
    }
    return sources;
  };

  const unindexElement = (element) => {
    for (const source of sourcesByElement.get(element) || []) {
      const elements = elementsBySource.get(source);
      elements?.delete(element);
      if (elements?.size === 0) {
        elementsBySource.delete(source);
        priorityBySource.delete(source);
      }
    }
    sourcesByElement.delete(element);
  };

  const indexElement = (element) => {
    unindexElement(element);
    const sources = candidateSourcesFor(element);
    sourcesByElement.set(element, sources);
    for (const source of sources) {
      let elements = elementsBySource.get(source);
      if (!elements) {
        elements = new Set();
        elementsBySource.set(source, elements);
      }
      elements.add(element);
    }
    return sources;
  };

  const updateAccessibleState = (element, action) => {
    if (action === "block") {
      setAttributeIfChanged(element, "aria-description", FILTERED_ACCESSIBLE_DESCRIPTION);
    } else if (action === "error") {
      setAttributeIfChanged(element, "aria-description", ERROR_ACCESSIBLE_DESCRIPTION);
    } else if ([FILTERED_ACCESSIBLE_DESCRIPTION, ERROR_ACCESSIBLE_DESCRIPTION]
      .includes(element.getAttribute("aria-description"))) {
      removeAttributeIfPresent(element, "aria-description");
    }
  };

  const PRESENTATION_PROPERTIES = [
    "background",
    "aspect-ratio",
    "box-shadow",
    "color",
    "object-position",
    "opacity",
    "text-indent",
    "height",
    "width",
  ];
  const BLOCK_PRESENTATION = {
    background: "linear-gradient(135deg, #dce5e9 0%, #bdcbd2 48%, #e8edef 100%)",
    "box-shadow": "inset 0 0 0 9999px rgb(207 218 224 / 38%)",
    color: "transparent",
    "object-position": "99999px 99999px",
    opacity: "1",
    "text-indent": "-99999px",
  };
  const ERROR_PRESENTATION = {
    ...BLOCK_PRESENTATION,
    background: "#ebe6e2",
    "box-shadow": "inset 0 0 0 9999px #ebe6e2",
  };
  const HIDDEN_PRESENTATION = {
    color: "transparent",
    "object-position": "99999px 99999px",
    "text-indent": "-99999px",
  };

  const restorePresentationStyle = (element) => {
    const snapshot = presentationStylesByElement.get(element);
    if (!snapshot) return;
    for (const property of PRESENTATION_PROPERTIES) {
      const original = snapshot.get(property);
      if (original?.value) {
        element.style.setProperty(property, original.value, original.priority);
      } else {
        element.style.removeProperty(property);
      }
    }
    presentationStylesByElement.delete(element);
    ownStyleSnapshots.set(element, element.getAttribute("style") || "");
  };

  const applyPresentationStyle = (element, action, dimensions = null) => {
    if (!(element instanceof HTMLImageElement) && !(element instanceof HTMLInputElement)) return;
    restorePresentationStyle(element);
    if (action === "allow") return;
    const values = action === "block"
      ? { ...BLOCK_PRESENTATION }
      : action === "error"
        ? { ...ERROR_PRESENTATION }
        : { ...HIDDEN_PRESENTATION };
    if (dimensions && ["block", "error"].includes(action)) {
      values["aspect-ratio"] = `${dimensions.width} / ${dimensions.height}`;
      values.width = "100%";
      values.height = "auto";
    }
    const snapshot = new Map();
    for (const property of PRESENTATION_PROPERTIES) {
      snapshot.set(property, {
        value: element.style.getPropertyValue(property),
        priority: element.style.getPropertyPriority(property),
      });
    }
    presentationStylesByElement.set(element, snapshot);
    for (const [property, value] of Object.entries(values)) {
      element.style.setProperty(property, value, "important");
    }
    ownStyleSnapshots.set(element, element.getAttribute("style") || "");
  };

  const setMediaState = (element, action, dimensions = null) => {
    const previousAction = trustedMediaStates.get(element);
    trustedMediaStates.set(element, action);
    setAttributeIfChanged(element, "data-glosh-dag-media", action);
    const addsMissingDimensions =
      dimensions !== null && !element.style.getPropertyValue("aspect-ratio");
    if (previousAction !== action || addsMissingDimensions) {
      applyPresentationStyle(element, action, dimensions);
    }
    updateAccessibleState(element, action);
  };

  const rememberDecision = (source, action, dimensions = null) => {
    if (!decisionsBySource.has(source) && decisionsBySource.size >= MAX_REMEMBERED_DECISIONS) {
      const oldest = decisionsBySource.keys().next().value;
      decisionsBySource.delete(oldest);
      dimensionsBySource.delete(oldest);
    }
    decisionsBySource.set(source, action);
    if (dimensions) dimensionsBySource.set(source, dimensions);
  };

  const activeDecision = (sources) => {
    for (const source of sources) {
      const decision = decisionsBySource.get(source);
      if (decision) return decision;
    }
    return null;
  };

  const activeDimensions = (sources) => {
    for (const source of sources) {
      const dimensions = dimensionsBySource.get(source);
      if (dimensions) return dimensions;
    }
    return null;
  };

  const sendSourcePriority = (sources, priority) => {
    if (window.top !== window) return;
    for (const source of sources) {
      if (!/^https?:/iu.test(source) || priorityBySource.get(source) === "visible") continue;
      if (priorityBySource.get(source) === priority) continue;
      priorityBySource.set(source, priority);
      browser.runtime.sendMessage({
        type: "media-priority-hint",
        version: PROTOCOL_VERSION,
        documentToken: performanceDocumentToken,
        sourceUrl: source,
        priority,
      }).catch(() => {});
    }
  };

  const sendPriorityHint = (sources, bounds) => {
    const visible = bounds.bottom > 0 && bounds.top < innerHeight && bounds.right > 0 && bounds.left < innerWidth;
    sendSourcePriority(sources, visible ? "visible" : "nearby");
  };

  const encodeBase64 = (bytes) => {
    let binary = "";
    for (let offset = 0; offset < bytes.byteLength; offset += 0x8000) {
      binary += String.fromCharCode(...bytes.subarray(offset, offset + 0x8000));
    }
    return btoa(binary);
  };

  const requestSourceDecision = (source, priority) => {
    if (pendingSourceDecisions.has(source)) return pendingSourceDecisions.get(source);
    const promise = (async () => {
      const protocol = new URL(source).protocol;
      if (protocol !== "blob:") {
        return browser.runtime.sendMessage({
          type: "media-fallback-request",
          version: PROTOCOL_VERSION,
          documentToken: performanceDocumentToken,
          sourceUrl: source,
          priority,
        });
      }
      const response = await fetch(source, { credentials: "include", cache: "no-store" });
      if (!response.ok) throw new Error("blob_fetch_failed");
      const bytes = new Uint8Array(await response.arrayBuffer());
      if (bytes.byteLength === 0 || bytes.byteLength > MAX_INLINE_ANALYSIS_BYTES) {
        throw new Error("blob_size_invalid");
      }
      return browser.runtime.sendMessage({
        type: "media-inline-request",
        version: PROTOCOL_VERSION,
        documentToken: performanceDocumentToken,
        sourceUrl: source,
        priority,
        byteLength: bytes.byteLength,
        bytesBase64: encodeBase64(bytes),
      });
    })().finally(() => pendingSourceDecisions.delete(source));
    pendingSourceDecisions.set(source, promise);
    return promise;
  };

  const beginGeneratedAnalysis = (source, element, bounds) => {
    if (
      !element.isConnected ||
      decisionsBySource.has(source) ||
      pendingSourceDecisions.has(source)
    ) return;
    const priority = bounds.bottom > 0 && bounds.top < innerHeight ? "visible" : "nearby";
    requestSourceDecision(source, priority)
      .then((response) => {
        if (
          response?.version === PROTOCOL_VERSION &&
          normalizedSource(response.sourceUrl) === source &&
          DECISION_ACTIONS.has(response.action)
        ) {
          rememberDecision(source, response.action);
        } else {
          rememberDecision(source, "error");
        }
      })
      .catch(() => rememberDecision(source, "error"))
      .finally(() => applyDecisionToSource(source));
  };

  const flushLayoutWork = () => {
    if (!layoutFlushScheduled) return;
    layoutFlushScheduled = false;
    if (layoutFrameId !== null) cancelAnimationFrame(layoutFrameId);
    if (layoutTimerId !== null) clearTimeout(layoutTimerId);
    layoutFrameId = null;
    layoutTimerId = null;

    const mediaElements = [...pendingLayoutElements].filter((element) => element.isConnected);
    const generatedEntries = [...pendingGeneratedLayout.entries()]
      .filter(([, element]) => element.isConnected);
    pendingLayoutElements.clear();
    pendingGeneratedLayout.clear();

    const boundsByElement = new Map();
    const boundsFor = (element) => {
      let bounds = boundsByElement.get(element);
      if (!bounds) {
        bounds = element.getBoundingClientRect();
        boundsByElement.set(element, bounds);
      }
      return bounds;
    };
    mediaElements.forEach(boundsFor);
    generatedEntries.forEach(([, element]) => boundsFor(element));

    for (const element of mediaElements) {
      const bounds = boundsFor(element);
      sendPriorityHint(sourcesByElement.get(element) || indexElement(element), bounds);
    }
    for (const [source, element] of generatedEntries) {
      beginGeneratedAnalysis(source, element, boundsFor(element));
    }
  };

  const scheduleLayoutFlush = () => {
    if (layoutFlushScheduled) return;
    layoutFlushScheduled = true;
    layoutFrameId = requestAnimationFrame(flushLayoutWork);
    layoutTimerId = setTimeout(flushLayoutWork, 48);
  };

  const analyzeGeneratedSource = (source, element) => {
    if (decisionsBySource.has(source) || pendingSourceDecisions.has(source)) return;
    const scheduled = pendingGeneratedLayout.get(source);
    if (!scheduled?.isConnected) pendingGeneratedLayout.set(source, element);
    scheduleLayoutFlush();
  };

  const applyKnownDecision = (element) => {
    if (!(element instanceof Element) || !element.matches(MEDIA_SELECTOR)) return;
    if (element instanceof HTMLImageElement && !element.hasAttribute("decoding")) {
      setAttributeIfChanged(element, "decoding", "async");
    }
    const sources = indexElement(element);
    const action = activeDecision(sources);
    setMediaState(element, action || "hidden", activeDimensions(sources));
    const generated = sources.find((source) => GENERATED_PROTOCOLS.has(new URL(source).protocol));
    if (!action && generated) analyzeGeneratedSource(generated, element);
  };

  const reconcileMediaAfterLayout = (element) => {
    if (!(element instanceof Element) || !element.matches(MEDIA_SELECTOR)) return;
    pendingLayoutElements.add(element);
    scheduleLayoutFlush();
  };

  const safeInlineSvg = (svg) => {
    if (!(svg instanceof SVGSVGElement)) return false;
    const descendants = [svg, ...svg.querySelectorAll("*")];
    if (descendants.length > MAX_INLINE_VECTOR_ELEMENTS) return false;
    const blockedNames = new Set([
      "audio", "canvas", "feimage", "foreignobject", "iframe", "image", "script", "style", "video",
    ]);
    for (const child of descendants) {
      if (blockedNames.has(child.localName?.toLowerCase() || "")) return false;
      for (const attribute of child.attributes || []) {
        const name = attribute.name.toLowerCase();
        const value = attribute.value;
        if (
          name.startsWith("on") ||
          /url\(\s*['"]?(?:https?|data|blob):/iu.test(value) ||
          (["href", "xlink:href"].includes(name) && value.length > 0 && !value.startsWith("#"))
        ) return false;
      }
    }
    return true;
  };

  const protectInlineSvg = (root) => {
    const vectors = [];
    if (root instanceof SVGSVGElement) vectors.push(root);
    root.querySelectorAll?.("svg").forEach((svg) => vectors.push(svg));
    for (const svg of vectors) {
      if (safeInlineSvg(svg)) setAttributeIfChanged(svg, "data-glosh-dag-ui-vector", "allow");
      else removeAttributeIfPresent(svg, "data-glosh-dag-ui-vector");
    }
  };

  const stopBlockedMedia = (root) => {
    const blocked = [];
    if (root instanceof Element && root.matches(BLOCKED_MEDIA_SELECTOR)) blocked.push(root);
    root.querySelectorAll?.(BLOCKED_MEDIA_SELECTOR).forEach((element) => blocked.push(element));
    for (const element of blocked) {
      if (element instanceof HTMLMediaElement) {
        element.autoplay = false;
        element.muted = true;
        element.preload = "none";
        try { element.pause(); } catch {}
      }
      setAttributeIfChanged(element, "aria-hidden", "true");
    }
  };

  const generatedSourcesFromStyle = (value) => {
    const sources = [];
    for (const match of String(value || "").matchAll(/url\(([^)]+)\)/giu)) {
      const source = normalizedSource(match[1].trim().replace(/^['"]|['"]$/gu, ""));
      if (source && GENERATED_PROTOCOLS.has(new URL(source).protocol) && !sources.includes(source)) {
        sources.push(source);
      }
    }
    return sources;
  };

  const generatedConfig = (kind) => kind === "before"
    ? { attribute: "data-glosh-dag-generated-before", property: "--glosh-dag-generated-before-background" }
    : kind === "after"
      ? { attribute: "data-glosh-dag-generated-after", property: "--glosh-dag-generated-after-background" }
      : { attribute: "data-glosh-dag-generated-background", property: "--glosh-dag-generated-background" };

  const generatedKey = (element, kind, create) => {
    let id = generatedIdsByElement.get(element);
    if (!id) {
      if (!create) return null;
      id = nextGeneratedId;
      nextGeneratedId += 1;
      generatedIdsByElement.set(element, id);
    }
    const key = `${kind}:${id}`;
    let keys = generatedKeysByElement.get(element);
    if (!keys) {
      keys = new Set();
      generatedKeysByElement.set(element, keys);
    }
    keys.add(key);
    return key;
  };

  const forgetGeneratedRecord = (element, kind, key) => {
    const config = generatedConfig(kind);
    removeAttributeIfPresent(element, config.attribute);
    removeOwnStyleProperty(element, config.property);
    generatedRecords.delete(key);
    const keys = generatedKeysByElement.get(element);
    keys?.delete(key);
    if (keys?.size === 0) generatedKeysByElement.delete(element);
  };

  const setOwnStyleProperty = (element, property, value) => {
    if (element.style.getPropertyValue(property) === value) return;
    element.style.setProperty(property, value);
    ownStyleSnapshots.set(element, element.getAttribute("style") || "");
  };

  const removeOwnStyleProperty = (element, property) => {
    if (!element.style.getPropertyValue(property)) return;
    element.style.removeProperty(property);
    ownStyleSnapshots.set(element, element.getAttribute("style") || "");
  };

  const applyGeneratedRecord = (record) => {
    if (!record.element.isConnected) {
      forgetGeneratedRecord(record.element, record.kind, record.key);
      return;
    }
    const actions = record.sources.map((source) => decisionsBySource.get(source));
    const action = actions.some((value) => value === "block")
      ? "block"
      : actions.some((value) => value === "error")
        ? "error"
        : actions.length > 0 && actions.every((value) => value === "allow")
          ? "allow"
          : "hidden";
    const config = generatedConfig(record.kind);
    setAttributeIfChanged(record.element, config.attribute, action);
    if (action === "allow") setOwnStyleProperty(record.element, config.property, record.value);
    else removeOwnStyleProperty(record.element, config.property);
    for (const source of record.sources) {
      if (!decisionsBySource.has(source)) analyzeGeneratedSource(source, record.element);
    }
  };

  const inspectGeneratedStyle = (element, kind = "element") => {
    if (!(element instanceof HTMLElement)) return;
    let style;
    try { style = getComputedStyle(element, kind === "element" ? null : `::${kind}`); } catch { return; }
    const value = style.backgroundImage;
    const sources = generatedSourcesFromStyle(value);
    if (sources.length === 0) {
      const key = generatedKey(element, kind, false);
      if (!key) return;
      const previous = generatedRecords.get(key);
      if (previous) forgetGeneratedRecord(element, kind, key);
      return;
    }
    const key = generatedKey(element, kind, true);
    const record = { key, element, kind, value, sources };
    generatedRecords.set(key, record);
    applyGeneratedRecord(record);
  };

  const generatedKindsForRule = (selector) => {
    const kinds = [];
    if (/::before/iu.test(selector)) kinds.push("before");
    if (/::after/iu.test(selector)) kinds.push("after");
    if (kinds.length === 0) kinds.push("element");
    return kinds;
  };

  const collectGeneratedRuleTargets = (rules) => {
    for (const rule of rules || []) {
      if (rule.cssRules) collectGeneratedRuleTargets(rule.cssRules);
      if (
        typeof rule.selectorText !== "string" ||
        !GENERATED_SOURCE_PATTERN.test(rule.cssText || "")
      ) continue;
      const selector = rule.selectorText.replace(/::(?:before|after)/giu, "");
      try {
        document.querySelector(selector);
        generatedRuleTargets.push({ selector, kinds: generatedKindsForRule(rule.selectorText) });
      } catch {
        // An invalid or engine-specific selector cannot become a trusted visual target.
      }
    }
  };

  const refreshGeneratedRuleIndex = () => {
    generatedRuleTargets.length = 0;
    for (const sheet of document.styleSheets) {
      try {
        collectGeneratedRuleTargets(sheet.cssRules);
      } catch {
        // Cross-origin stylesheets stay page-native; HTTP(S) assets remain network-gated.
      }
    }
  };

  const generatedKindsForElement = (element) => {
    const kinds = new Set();
    if (GENERATED_SOURCE_PATTERN.test(element.getAttribute("style") || "")) {
      GENERATED_KINDS.forEach((kind) => kinds.add(kind));
    }
    for (const target of generatedRuleTargets) {
      try {
        if (element.matches(target.selector)) target.kinds.forEach((kind) => kinds.add(kind));
      } catch {}
    }
    return kinds;
  };

  const inspectGeneratedElement = (element) => {
    if (!(element instanceof HTMLElement)) return;
    const kinds = generatedKindsForElement(element);
    for (const kind of GENERATED_KINDS) {
      const key = generatedKey(element, kind, false);
      if (kinds.has(kind) || (key && generatedRecords.has(key))) {
        inspectGeneratedStyle(element, kind);
      }
    }
  };

  const inspectGeneratedTargets = (root) => {
    const rootElement = root === document ? document.documentElement : root instanceof Element ? root : null;
    if (!rootElement) return;
    const candidates = new Set();
    const addCandidate = (element) => {
      if (element instanceof HTMLElement) candidates.add(element);
    };
    addCandidate(rootElement);
    rootElement.querySelectorAll("[style]").forEach((element) => {
      if (GENERATED_SOURCE_PATTERN.test(element.getAttribute("style") || "")) addCandidate(element);
    });
    for (const target of generatedRuleTargets) {
      try {
        if (rootElement.matches(target.selector)) addCandidate(rootElement);
        rootElement.querySelectorAll(target.selector).forEach(addCandidate);
      } catch {}
    }
    candidates.forEach(inspectGeneratedElement);
  };

  const scheduleGeneratedRuleRefresh = () => {
    if (generatedRuleRefreshScheduled) return;
    generatedRuleRefreshScheduled = true;
    queueMicrotask(() => {
      generatedRuleRefreshScheduled = false;
      refreshGeneratedRuleIndex();
      inspectGeneratedTargets(document);
    });
  };

  const registerTree = (root) => {
    if (!(root instanceof Element) && root !== document) return;
    if (initialBarrierComplete && root === document.documentElement) {
      setAttributeIfChanged(root, INITIALIZED_ATTRIBUTE, "true");
    }
    if (root instanceof Element && root.matches(MEDIA_SELECTOR)) {
      applyKnownDecision(root);
      reconcileMediaAfterLayout(root);
      mediaPriorityObserver?.observe(root);
    }
    root.querySelectorAll?.(MEDIA_SELECTOR).forEach((element) => {
      applyKnownDecision(element);
      reconcileMediaAfterLayout(element);
      mediaPriorityObserver?.observe(element);
    });
    protectInlineSvg(root);
    stopBlockedMedia(root);
  };

  const unregisterElement = (element) => {
    pendingLayoutElements.delete(element);
    mediaPriorityObserver?.unobserve(element);
    if (element.matches(MEDIA_SELECTOR)) {
      restorePresentationStyle(element);
      unindexElement(element);
      trustedMediaStates.delete(element);
    }
    for (const key of generatedKeysByElement.get(element) || []) {
      generatedRecords.delete(key);
    }
    generatedKeysByElement.delete(element);
    ownStyleSnapshots.delete(element);
    for (const [source, candidate] of pendingGeneratedLayout) {
      if (candidate === element) pendingGeneratedLayout.delete(source);
    }
  };

  const unregisterTree = (root) => {
    if (!(root instanceof Element)) return;
    if (root.matches(`${MEDIA_SELECTOR},${GENERATED_ATTRIBUTE_SELECTOR}`)) unregisterElement(root);
    root.querySelectorAll(`${MEDIA_SELECTOR},${GENERATED_ATTRIBUTE_SELECTOR}`).forEach(unregisterElement);
  };

  const applyDecisionToSource = (source) => {
    for (const element of [...(elementsBySource.get(source) || [])]) {
      if (element.isConnected) applyKnownDecision(element);
      else unindexElement(element);
    }
    for (const record of generatedRecords.values()) {
      if (record.sources.includes(source)) applyGeneratedRecord(record);
    }
  };

  browser.runtime.onMessage.addListener((message) => {
    if (message?.type === "document-token-request" && message?.version === PROTOCOL_VERSION) {
      return Promise.resolve({
        type: "document-token-response",
        version: PROTOCOL_VERSION,
        documentToken: performanceDocumentToken,
      });
    }
    if (
      message?.type !== "media-presentation-decision" ||
      message?.version !== PROTOCOL_VERSION ||
      !DECISION_ACTIONS.has(message.action)
    ) return undefined;
    const source = normalizedSource(message.sourceUrl);
    if (!source) return undefined;
    const dimensions =
      Number.isInteger(message.imageWidth) &&
      Number.isInteger(message.imageHeight) &&
      message.imageWidth > 0 &&
      message.imageHeight > 0
        ? { width: message.imageWidth, height: message.imageHeight }
        : null;
    rememberDecision(source, message.action, dimensions);
    const mediaMatches = elementsBySource.get(source)?.size || 0;
    const cssMatches = [...generatedRecords.values()].filter((record) => record.sources.includes(source)).length;
    applyDecisionToSource(source);
    return Promise.resolve({
      type: "media-presentation-applied",
      version: PROTOCOL_VERSION,
      matchedCount: mediaMatches + cssMatches,
      mediaMatches,
      cssMatches,
      binding: mediaMatches + cssMatches > 0 ? "applied" : "unbound",
    });
  });

  const sensitivePreviewSelector = [
    'input[type="password"]', 'input[autocomplete="current-password" i]',
    'input[autocomplete="new-password" i]', 'input[autocomplete^="cc-" i]',
    'iframe[src*="recaptcha" i]', 'iframe[src*="hcaptcha" i]',
    'iframe[src*="challenges.cloudflare.com" i]', "[data-sitekey]",
  ].join(",");
  const reportPreviewEligibility = () => {
    if (window.top !== window || nativePort === null) return;
    const restricted = [...document.querySelectorAll(sensitivePreviewSelector)].some((element) => {
      const bounds = element.getBoundingClientRect();
      return bounds.width > 0 && bounds.height > 0;
    });
    try {
      nativePort.postMessage({
        type: "tab-preview-eligibility",
        version: PROTOCOL_VERSION,
        documentToken: performanceDocumentToken,
        restricted,
      });
    } catch {}
  };
  const schedulePreviewEligibilityReport = () => {
    clearTimeout(previewEligibilityTimer);
    previewEligibilityTimer = setTimeout(reportPreviewEligibility, 180);
  };

  try {
    nativePort = browser.runtime.connectNative(NATIVE_APP);
  } catch {
    nativePort = null;
  }

  const reportBarrierReady = () => {
    if (window.top !== window || nativePort === null) return;
    try {
      nativePort.postMessage({
        type: "barrier-ready",
        version: PROTOCOL_VERSION,
        url: location.href,
        documentToken: performanceDocumentToken,
      });
    } catch {}
  };

  document.addEventListener("play", (event) => stopBlockedMedia(event.target), true);
  document.addEventListener("error", (event) => {
    const element = event.target;
    if (!(element instanceof Element) || !element.matches(MEDIA_SELECTOR)) return;
    const sources = indexElement(element);
    const decision = activeDecision(sources);
    setMediaState(element, decision === "block" ? "block" : decision || "error");
  }, true);
  document.addEventListener("load", (event) => {
    const element = event.target;
    if (element instanceof Element && element.matches(MEDIA_SELECTOR)) applyKnownDecision(element);
    if (element instanceof HTMLLinkElement && element.rel.includes("stylesheet")) {
      scheduleGeneratedRuleRefresh();
    }
  }, true);

  registerTree(document);
  const observer = new MutationObserver((mutations) => {
    let previewChanged = false;
    for (const mutation of mutations) {
      if (mutation.type === "childList" && mutation.target instanceof HTMLStyleElement) {
        scheduleGeneratedRuleRefresh();
      }
      for (const node of mutation.removedNodes) unregisterTree(node);
      for (const node of mutation.addedNodes) {
        registerTree(node);
        if (node instanceof Element) {
          inspectGeneratedTargets(node);
          if (
            node instanceof HTMLStyleElement ||
            (node instanceof HTMLLinkElement && node.rel.includes("stylesheet"))
          ) scheduleGeneratedRuleRefresh();
        }
        previewChanged = true;
      }
      if (mutation.type === "attributes" && mutation.target instanceof Element) {
        const name = mutation.attributeName || "";
        if (PROTECTED_ATTRIBUTES.has(name)) {
          if (name === INITIALIZED_ATTRIBUTE) {
            if (initialBarrierComplete) {
              setAttributeIfChanged(mutation.target, name, "true");
            } else {
              removeAttributeIfPresent(mutation.target, name);
            }
          } else if (name === "data-glosh-dag-media") {
            const trustedState = trustedMediaStates.get(mutation.target);
            if (trustedState) {
              setAttributeIfChanged(mutation.target, name, trustedState);
            } else {
              applyKnownDecision(mutation.target);
            }
          } else if (name === "data-glosh-dag-ui-vector") {
            if (mutation.target instanceof SVGSVGElement) protectInlineSvg(mutation.target);
            else removeAttributeIfPresent(mutation.target, name);
          } else {
            const kind = name.endsWith("-before")
              ? "before"
              : name.endsWith("-after")
                ? "after"
                : "element";
            const record = [...generatedRecords.values()]
              .find((candidate) => candidate.element === mutation.target && candidate.kind === kind);
            if (record) applyGeneratedRecord(record);
            else removeAttributeIfPresent(mutation.target, name);
          }
          continue;
        }
        if (mutation.target.matches(MEDIA_SELECTOR) && SOURCE_ATTRIBUTES.has(name)) {
          setMediaState(mutation.target, "hidden");
          applyKnownDecision(mutation.target);
          reconcileMediaAfterLayout(mutation.target);
        }
        if (
          (mutation.target instanceof HTMLStyleElement && name === "media") ||
          (
            mutation.target instanceof HTMLLinkElement &&
            mutation.target.rel.includes("stylesheet") &&
            ["href", "media", "disabled"].includes(name)
          )
        ) scheduleGeneratedRuleRefresh();
        if (["class", "style"].includes(name)) {
          if (name === "style") {
            const expectedStyle = ownStyleSnapshots.get(mutation.target);
            const currentStyle = mutation.target.getAttribute("style") || "";
            if (expectedStyle !== undefined && expectedStyle === currentStyle) {
              ownStyleSnapshots.delete(mutation.target);
              continue;
            }
            ownStyleSnapshots.delete(mutation.target);
          }
          inspectGeneratedElement(mutation.target);
        }
        if (["type", "autocomplete", "src"].includes(name)) previewChanged = true;
      }
    }
    if (previewChanged) schedulePreviewEligibilityReport();
  });
  observer.observe(document, {
    subtree: true,
    childList: true,
    attributes: true,
    attributeFilter: [
      ...SOURCE_ATTRIBUTES,
      ...PROTECTED_ATTRIBUTES,
      "class",
      "style",
      "type",
      "autocomplete",
      "disabled",
    ],
  });

  if (window.top === window) {
    browser.runtime.sendMessage({
      type: "document-started",
      version: PROTOCOL_VERSION,
      documentToken: performanceDocumentToken,
    }).catch(() => {});
  }

  const openInitialBarrier = () => {
    registerTree(document);
    refreshGeneratedRuleIndex();
    inspectGeneratedTargets(document);
    initialBarrierComplete = true;
    document.documentElement?.setAttribute(INITIALIZED_ATTRIBUTE, "true");
    reportBarrierReady();
  };

  const completeDocument = () => {
    schedulePreviewEligibilityReport();
    if (window.top === window) {
      browser.runtime.sendMessage({
        type: "document-loaded",
        version: PROTOCOL_VERSION,
        documentToken: performanceDocumentToken,
      }).catch(() => {});
    }
  };
  openInitialBarrier();
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", completeDocument, { once: true });
  } else {
    completeDocument();
  }
})();
