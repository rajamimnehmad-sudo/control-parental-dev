"use strict";

(() => {
  if (globalThis.__gloshDagVideoLabGeometry !== undefined) return;

  const sourceIdentity = (video) => ({
    currentSrc: video.currentSrc || "",
    srcAttribute: video.getAttribute("src") || "",
    hasSrcObject: video.srcObject !== null,
    sourceChildren: [...video.querySelectorAll("source")]
      .map((source) => `${source.getAttribute("src") || ""}:${source.getAttribute("type") || ""}`)
      .join("|"),
  });

  const sourceSignature = (video) => Object.values(sourceIdentity(video)).join("::");

  const hasBackingMedia = (video) => {
    if (video.currentSrc) return true;
    if ((video.getAttribute("src") || "") !== "") return true;
    if (video.srcObject != null) return true;
    return [...video.querySelectorAll("source")].some((source) =>
      (source.getAttribute("src") || "") !== "");
  };

  const visibleArea = (video, viewportWidth, viewportHeight) => {
    if (!video.isConnected) return 0;
    const rect = video.getBoundingClientRect();
    const width = Math.max(0, Math.min(rect.right, viewportWidth) - Math.max(rect.left, 0));
    const height = Math.max(0, Math.min(rect.bottom, viewportHeight) - Math.max(rect.top, 0));
    return width * height;
  };

  const rectPayload = (video, viewportWidth, viewportHeight) => {
    const rect = video.getBoundingClientRect();
    return {
      left: rect.left,
      top: rect.top,
      width: rect.width,
      height: rect.height,
      viewportWidth,
      viewportHeight,
    };
  };

  const viewportSignature = (video, viewportWidth, viewportHeight, visualViewport) => {
    const rect = video.getBoundingClientRect();
    return [
      viewportWidth,
      viewportHeight,
      visualViewport?.width ?? null,
      visualViewport?.height ?? null,
      visualViewport?.offsetLeft ?? null,
      visualViewport?.offsetTop ?? null,
      visualViewport?.scale ?? null,
      rect.left,
      rect.top,
      rect.width,
      rect.height,
    ];
  };

  const sameRange = (left, right, start, end) =>
    left !== null &&
    right !== null &&
    left.length === right.length &&
    left.slice(start, end).every((value, index) => value === right[index + start]);

  globalThis.__gloshDagVideoLabGeometry = Object.freeze({
    hasBackingMedia,
    rectPayload,
    sameVideoRect: (left, right) => sameRange(left, right, 7, 11),
    sameViewportBounds: (left, right) => sameRange(left, right, 0, 7),
    sameViewportSignature: (left, right) => sameRange(left, right, 0, left?.length ?? 0),
    sourceIdentity,
    sourceSignature,
    viewportSignature,
    visibleArea,
  });
})();
