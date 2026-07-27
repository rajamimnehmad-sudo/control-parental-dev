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

  const markHidden = (root) => {
    if (!(root instanceof Element) && root !== document) {
      return;
    }
    if (root instanceof Element && root.matches(mediaSelector)) {
      root.setAttribute("data-glosh-dag-media", "hidden");
    }
    root.querySelectorAll?.(mediaSelector).forEach((element) => {
      element.setAttribute("data-glosh-dag-media", "hidden");
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
    attributeFilter: ["src", "srcset", "poster", "style"],
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
