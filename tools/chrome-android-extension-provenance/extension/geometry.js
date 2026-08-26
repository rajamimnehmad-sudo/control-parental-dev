export function clipFromSnapshot(snapshot, layoutMetrics) {
  const rect = snapshot.pageRect;
  const viewport = layoutMetrics.cssVisualViewport ?? layoutMetrics.visualViewport;
  if (!rect || !viewport) throw new Error("missing geometry");
  for (const value of [rect.x, rect.y, rect.width, rect.height, viewport.pageX, viewport.pageY]) {
    if (!Number.isFinite(value)) throw new Error("non-finite geometry");
  }
  if (rect.width <= 0 || rect.height <= 0) throw new Error("empty region");
  return {
    x: rect.x,
    y: rect.y,
    width: rect.width,
    height: rect.height,
    scale: 1,
  };
}

export function authorityKey(token) {
  return [
    token.protectionSession,
    token.tabId,
    token.documentId,
    token.frameId,
    token.navigationSequence,
    token.viewportIdentity,
    token.elementIdentity,
    token.captureSequence,
  ].join(":");
}

export function isCurrentAuthority(candidate, current) {
  return authorityKey(candidate) === authorityKey(current);
}
