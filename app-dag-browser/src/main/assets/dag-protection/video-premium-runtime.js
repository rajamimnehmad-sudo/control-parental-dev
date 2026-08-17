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
    const fullscreen = globalThis.__gloshDagVideoPremiumFullscreen.create({
      activeRecord: dependencies.activeRecord,
      documentElement: dependencies.documentObject.documentElement,
      now: dependencies.now,
      postDiagnostic: dependencies.postDiagnostic,
      rootAttribute: dependencies.fullscreenRootAttribute,
      transitionMillis: dependencies.fullscreenTransitionMillis,
      videoAttribute: dependencies.fullscreenVideoAttribute,
    });
    return Object.freeze({ continuity, fullscreen });
  };

  globalThis.__gloshDagVideoPremiumRuntime = Object.freeze({ create });
})();
