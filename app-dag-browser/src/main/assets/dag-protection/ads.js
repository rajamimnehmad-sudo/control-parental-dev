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
  const INITIAL_SCAN_READY_ATTRIBUTE = "data-glosh-dag-ads-initial-ready";
  const INITIAL_SCAN_READY_EVENT = "glosh-dag-ads-initial-ready";
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
  const SEARCH_QUERY_KEYS = ["q", "query", "search", "keyword", "keywords", "term", "k"];

  const isVideoSite = () =>
    /(?:^|\.)(?:youtube(?:-nocookie)?\.com|youtu\.be)$/iu.test(location.hostname);

  const isSearchResultsDocument = () => {
    if (/(?:^|\/)search(?:\/|$)/iu.test(location.pathname)) return true;
    const parameters = new URLSearchParams(location.search);
    return SEARCH_QUERY_KEYS.some((key) => parameters.has(key));
  };

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
    if (
      isVideoSite() ||
      !isSearchResultsDocument() ||
      (!(root instanceof Element) && root !== document)
    ) return;
    const markLabel = (label) => {
      const result = label?.closest?.("[data-snhf], [data-rpos], article, li");
      if (result instanceof HTMLElement) result.setAttribute(SPONSORED_ATTRIBUTE, "true");
    };
    if (
      root instanceof HTMLElement &&
      root.matches("span,div") &&
      SPONSORED_LABEL.test(root.textContent?.trim() || "")
    ) markLabel(root);

    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
    let textNode = walker.nextNode();
    while (textNode !== null) {
      if (SPONSORED_LABEL.test(textNode.nodeValue?.trim() || "")) {
        markLabel(textNode.parentElement);
      }
      textNode = walker.nextNode();
    }
  };

  const scan = (root) => {
    scanExplicitAds(root);
    scanExactSponsoredLabels(root);
  };

  const completeInitialScan = () => {
    scan(document);
    document.documentElement?.setAttribute(INITIAL_SCAN_READY_ATTRIBUTE, "true");
    dispatchEvent(new CustomEvent(INITIAL_SCAN_READY_EVENT));
  };

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", completeInitialScan, { once: true });
  } else {
    completeInitialScan();
  }
})();
