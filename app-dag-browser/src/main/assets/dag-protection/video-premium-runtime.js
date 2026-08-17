"use strict";

(() => {
  if (globalThis.__gloshDagVideoPremiumRuntime !== undefined) return;

  const create = (dependencies) => {
    const overlay = globalThis.__gloshDagVideoPremiumOverlay.create({
      documentObject: dependencies.documentObject,
      windowObject: dependencies.windowObject,
    });
    const continuity = globalThis.__gloshDagVideoPremiumContinuity.create({
      activeRecord: dependencies.activeRecord,
      blurAttribute: dependencies.blurAttribute,
      clearTimeout: dependencies.clearTimeout,
      endMarginSeconds: 0.25,
      hideSkipControl: overlay.hide,
      maximumSkipAttempts: 5,
      minimumSkipSeconds: 0.5,
      postDiagnostic: dependencies.postDiagnostic,
      rebindSkipControl: overlay.rebind,
      requestFrameWhenReady: dependencies.requestFrameWhenReady,
      resetFrameState: dependencies.resetFrameState,
      safeSamplesRequired: 2,
      scheduleNextCapture: dependencies.scheduleNextCapture,
      seekSettleMillis: dependencies.viewportSettleMillis,
      setTimeout: dependencies.setTimeout,
      showSkipControl: overlay.show,
      skipControlDelayMillis: 2_000,
      skipStepSeconds: 2,
      sourceSignature: dependencies.sourceSignature,
    });
    return Object.freeze({ continuity });
  };

  globalThis.__gloshDagVideoPremiumRuntime = Object.freeze({ create });
})();
