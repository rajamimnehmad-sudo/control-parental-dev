"use strict";

(() => {
  if (globalThis.__gloshDagVideoLabRecord !== undefined) return;

  const create = (video, audioState, randomToken, bootstrapState) => ({
    video,
    videoId: `video_${randomToken(2)}`,
    revision: 0,
    activations: 0,
    captures: 0,
    covered: false,
    coverPending: false,
    coverAcknowledged: false,
    framePending: false,
    frameCaptured: false,
    frameConcealed: false,
    frameAllowed: null,
    frameSequence: 0,
    viewportEpoch: 0,
    frameViewportEpoch: 0,
    rawFrameOpen: false,
    smoothActive: false,
    smoothGrantIdentity: null,
    originalMuted: audioState.muted,
    originalDefaultMuted: audioState.defaultMuted,
    originalVolume: audioState.volume,
    terminal: false,
    retiring: false,
    retirePromise: null,
    concealFailed: false,
    concealPromise: null,
    sourceSignature: "",
    sourceIdentity: null,
    revealToken: randomToken(4),
    nextCaptureTimer: null,
    readinessTimer: null,
    resultTimer: null,
    coverTimer: null,
    frameCallbackId: null,
    coverRequestedAt: null,
    coverMillis: null,
    decodeStartedAt: null,
    expectedPresentationMutations: [],
    presentationMutationClearTimer: null,
    viewportSignature: null,
    viewportTransitionStartedAt: null,
    viewportTransitionCount: 0,
    pendingViewportSignature: null,
    bootstrapBackingGeneration: 0,
    bootstrapLoadStarted: false,
    bootstrapLoadSourceSignature: null,
    bootstrapTransitionUsed: false,
    bootstrapSourceSignature: null,
    bootstrapState,
    playGeneration: 0,
  });

  const clearTimers = (record) => {
    clearTimeout(record.nextCaptureTimer);
    clearTimeout(record.readinessTimer);
    clearTimeout(record.resultTimer);
    clearTimeout(record.coverTimer);
    clearTimeout(record.presentationMutationClearTimer);
    record.nextCaptureTimer = null;
    record.readinessTimer = null;
    record.resultTimer = null;
    record.coverTimer = null;
    record.presentationMutationClearTimer = null;
    if (
      record.frameCallbackId !== null &&
      typeof record.video.cancelVideoFrameCallback === "function"
    ) {
      try {
        record.video.cancelVideoFrameCallback(record.frameCallbackId);
      } catch {}
    }
    record.frameCallbackId = null;
  };

  const resetFrame = (record) => {
    record.framePending = false;
    record.frameCaptured = false;
    record.frameConcealed = false;
    record.frameAllowed = null;
    record.frameViewportEpoch = record.viewportEpoch;
  };

  globalThis.__gloshDagVideoLabRecord = Object.freeze({ clearTimers, create, resetFrame });
})();
