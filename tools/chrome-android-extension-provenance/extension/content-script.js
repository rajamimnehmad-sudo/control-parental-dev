(() => {
  "use strict";

  const controlled = new Map([
    ["data-url", { reason: "IMG_DATA_URL" }],
    ["blob-url", { reason: "IMG_BLOB_URL" }],
    ["canvas-2d", { reason: "CANVAS", carrier: "RENDERER_LOCAL" }],
    ["webgl", { reason: "CANVAS", carrier: "WEBGL" }],
    ["inline-svg", { reason: "INLINE_SVG" }],
    ["external-js", { reason: "CANVAS", carrier: "JAVASCRIPT" }],
    ["json", { reason: "CANVAS", carrier: "JSON" }],
    ["wasm", { reason: "CANVAS", carrier: "WASM" }],
    ["service-worker", { reason: "SERVICE_WORKER_RESPONSE" }],
    ["cache-storage", { reason: "CACHE_STORAGE_RESPONSE" }],
  ]);
  let geometrySequence = 0;
  let scheduled = false;

  const viewport = () => {
    const visual = window.visualViewport;
    return {
      pageLeft: visual?.pageLeft ?? window.scrollX,
      pageTop: visual?.pageTop ?? window.scrollY,
      offsetLeft: visual?.offsetLeft ?? 0,
      offsetTop: visual?.offsetTop ?? 0,
      width: visual?.width ?? window.innerWidth,
      height: visual?.height ?? window.innerHeight,
      scale: visual?.scale ?? 1,
      devicePixelRatio: window.devicePixelRatio,
      scrollX: window.scrollX,
      scrollY: window.scrollY,
    };
  };

  const describe = (element, identity, provenance) => {
    const rect = element.getBoundingClientRect();
    const visual = viewport();
    const style = getComputedStyle(element);
    const intersection = {
      left: Math.max(rect.left, 0),
      top: Math.max(rect.top, 0),
      right: Math.min(rect.right, visual.width),
      bottom: Math.min(rect.bottom, visual.height),
    };
    const intersects = intersection.right > intersection.left && intersection.bottom > intersection.top;
    return {
      elementIdentity: `id:${identity}`,
      tagName: element.tagName,
      provenanceReason: provenance.reason,
      carrier: provenance.carrier ?? null,
      clientRect: { x: rect.x, y: rect.y, width: rect.width, height: rect.height },
      pageRect: {
        x: rect.left + visual.pageLeft,
        y: rect.top + visual.pageTop,
        width: rect.width,
        height: rect.height,
      },
      intersection,
      visible:
        intersects &&
        element.getClientRects().length > 0 &&
        style.display !== "none" &&
        style.visibility === "visible" &&
        Number(style.opacity) > 0,
    };
  };

  const report = () => {
    scheduled = false;
    geometrySequence += 1;
    const elements = [];
    for (const [identity, provenance] of controlled) {
      const element = document.getElementById(identity);
      if (element) elements.push(describe(element, identity, provenance));
    }
    chrome.runtime.sendMessage({
      kind: "content_snapshot",
      geometrySequence,
      monotonicTimestamp: performance.timeOrigin + performance.now(),
      url: location.href,
      viewport: viewport(),
      elements,
    });
  };

  const schedule = () => {
    if (scheduled) return;
    scheduled = true;
    requestAnimationFrame(report);
  };

  chrome.runtime.sendMessage({
    kind: "document_start",
    monotonicTimestamp: performance.timeOrigin + performance.now(),
    url: location.href,
  });
  addEventListener("scroll", schedule, { passive: true, capture: true });
  addEventListener("resize", schedule, { passive: true });
  window.visualViewport?.addEventListener("scroll", schedule, { passive: true });
  window.visualViewport?.addEventListener("resize", schedule, { passive: true });
  addEventListener("DOMContentLoaded", schedule, { once: true });
  addEventListener("load", schedule, { once: true });
})();
