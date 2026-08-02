"use strict";

(() => {
  if (globalThis.__gloshDagAdsInstalled === true) return;
  Object.defineProperty(globalThis, "__gloshDagAdsInstalled", {
    value: true,
    configurable: false,
    enumerable: false,
    writable: false,
  });

  const HIDDEN_CLASS = "glosh-dag-page-ad-hidden";
  const SPONSORED_ATTRIBUTE = "data-glosh-dag-sponsored-result";
  const EXPLICIT_AD_SELECTOR = [
    "[data-ad]",
    "[data-ad-slot]",
    "[data-advertisement]",
    "[data-text-ad]",
    "[data-pla-slot]",
    "#tads",
    "iframe[name^='google_ads_iframe']",
    "[aria-label='Productos patrocinados']",
  ].join(",");
  const SPONSORED_LABEL = /^(?:patrocinado|sponsored)$/iu;

  const isVideoSite = () =>
    /(?:^|\.)(?:youtube(?:-nocookie)?\.com|youtu\.be)$/iu.test(location.hostname);

  const hideExplicitAd = (element) => {
    if (!(element instanceof HTMLElement)) return;
    element.classList.add(HIDDEN_CLASS);
  };

  const scanExplicitAds = (root) => {
    if (isVideoSite() || (!(root instanceof Element) && root !== document)) return;
    if (root instanceof HTMLElement && root.matches(EXPLICIT_AD_SELECTOR)) hideExplicitAd(root);
    root.querySelectorAll?.(EXPLICIT_AD_SELECTOR).forEach(hideExplicitAd);
  };

  const scanExactSponsoredLabels = (root) => {
    if (isVideoSite() || (!(root instanceof Element) && root !== document)) return;
    const candidates = [];
    if (root instanceof HTMLElement) candidates.push(root);
    root.querySelectorAll?.("span,div").forEach((element) => candidates.push(element));
    for (const label of candidates) {
      if (!SPONSORED_LABEL.test(label.textContent?.trim() || "")) continue;
      const result = label.closest("[data-snhf], [data-rpos], article, li");
      if (result instanceof HTMLElement) result.setAttribute(SPONSORED_ATTRIBUTE, "true");
    }
  };

  const scan = (root) => {
    scanExplicitAds(root);
    scanExactSponsoredLabels(root);
  };

  const observer = new MutationObserver((mutations) => {
    for (const mutation of mutations) {
      for (const node of mutation.addedNodes) scan(node);
      if (mutation.type === "attributes") scan(mutation.target);
    }
  });
  observer.observe(document, {
    subtree: true,
    childList: true,
    attributes: true,
    attributeFilter: ["data-ad", "data-ad-slot", "data-advertisement", "aria-label"],
  });

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", () => scan(document), { once: true });
  } else {
    scan(document);
  }
})();
