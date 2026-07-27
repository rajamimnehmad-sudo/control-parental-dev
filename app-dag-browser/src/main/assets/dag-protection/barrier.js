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
  const analyzableSelector = [
    "img",
    "video[poster]",
    "image",
    "input[type='image']",
  ].join(",");
  const SAFE_NATIVE_ACTIONS = new Set(["block"]);
  const MAX_CANDIDATES_PER_DOCUMENT = 512;
  const MAX_ALT_TEXT_LENGTH = 256;
  const analyzedSources = new WeakMap();
  let candidateCount = 0;

  const sourceFor = (element) => {
    let rawSource = "";
    if (element instanceof HTMLImageElement) {
      rawSource = element.currentSrc || element.getAttribute("src") || "";
    } else if (element instanceof HTMLVideoElement) {
      rawSource = element.poster || "";
    } else if (element instanceof HTMLInputElement) {
      rawSource = element.getAttribute("src") || "";
    } else if (element instanceof SVGImageElement) {
      rawSource = element.href?.baseVal || element.getAttribute("href") || "";
    }
    if (!rawSource) {
      return null;
    }
    try {
      return new URL(rawSource, document.baseURI).href;
    } catch {
      return null;
    }
  };

  const candidateId = () => {
    candidateCount += 1;
    return `media_${candidateCount}_${crypto.getRandomValues(new Uint32Array(1))[0].toString(16)}`;
  };

  const dimensionsFor = (element) => ({
    width: Math.max(0, Math.round(element.naturalWidth || element.videoWidth || element.clientWidth || 0)),
    height: Math.max(0, Math.round(element.naturalHeight || element.videoHeight || element.clientHeight || 0)),
  });

  const altTextFor = (element) => {
    const value = element.getAttribute("alt") || element.getAttribute("aria-label") || "";
    return value.slice(0, MAX_ALT_TEXT_LENGTH);
  };

  const analyzeCandidate = async (element) => {
    if (!(element instanceof Element) || !element.matches(analyzableSelector)) {
      return;
    }
    const sourceUrl = sourceFor(element);
    if (!sourceUrl || analyzedSources.get(element) === sourceUrl) {
      return;
    }
    analyzedSources.set(element, sourceUrl);
    if (candidateCount >= MAX_CANDIDATES_PER_DOCUMENT) {
      return;
    }

    const id = candidateId();
    const dimensions = dimensionsFor(element);
    try {
      const response = await browser.runtime.sendNativeMessage("glosh.dag.protection", {
        type: "media-candidate",
        version: 1,
        candidateId: id,
        sourceUrl,
        documentUrl: location.href,
        altText: altTextFor(element),
        width: dimensions.width,
        height: dimensions.height,
      });
      const validResponse =
        response?.type === "media-decision" &&
        response?.version === 1 &&
        response?.candidateId === id &&
        SAFE_NATIVE_ACTIONS.has(response?.action);
      if (validResponse) {
        element.setAttribute("data-glosh-dag-media", response.action);
      }
    } catch {
      // Any native or protocol failure leaves the media hidden.
    }
  };

  const markHidden = (root) => {
    if (!(root instanceof Element) && root !== document) {
      return;
    }
    if (root instanceof Element && root.matches(mediaSelector)) {
      root.setAttribute("data-glosh-dag-media", "hidden");
      void analyzeCandidate(root);
    }
    root.querySelectorAll?.(mediaSelector).forEach((element) => {
      element.setAttribute("data-glosh-dag-media", "hidden");
      void analyzeCandidate(element);
    });
  };

  markHidden(document);
  const observer = new MutationObserver((mutations) => {
    for (const mutation of mutations) {
      for (const node of mutation.addedNodes) {
        markHidden(node);
      }
      if (mutation.target instanceof Element && mutation.type === "attributes") {
        markHidden(mutation.target);
      }
    }
  });
  observer.observe(document, {
    attributes: true,
    attributeFilter: ["src", "srcset", "poster", "style", "alt", "aria-label"],
    childList: true,
    subtree: true,
  });

  if (window.top === window) {
    browser.runtime
      .sendNativeMessage("glosh.dag.protection", {
        type: "barrier-ready",
        version: 1,
        url: location.href,
      })
      .catch(() => {
        // Native keeps the Gecko surface invisible when the handshake fails.
      });
  }
})();
