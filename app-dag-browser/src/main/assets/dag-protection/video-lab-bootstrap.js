"use strict";

(() => {
  if (globalThis.__gloshDagVideoLabBootstrap !== undefined) return;

  const create = (dependencies) => {
    const validSharedState = (record) =>
      record.sourceSignature === dependencies.sourceSignature(record.video) &&
      (record.bootstrapBackingGeneration === 0 ||
        record.bootstrapSourceSignature === record.sourceSignature) &&
      dependencies.capabilityFailure(record.video) === null &&
      !dependencies.unsafeActive(record) &&
      record.video.isConnected;

    const armGeneration = (record) => {
      if (
        record !== dependencies.activeRecord() ||
        !record.coverAcknowledged ||
        !record.bootstrapLoadStarted ||
        record.bootstrapBackingGeneration !== 0 ||
        record.bootstrapLoadSourceSignature !== record.sourceSignature ||
        record.sourceSignature !== dependencies.sourceSignature(record.video) ||
        !record.video.isConnected ||
        record.rawFrameOpen ||
        record.frameCaptured ||
        record.captures !== 0
      ) return false;
      record.bootstrapBackingGeneration = 1;
      record.bootstrapSourceSignature = record.sourceSignature;
      return true;
    };

    const beginViewportTransition = (record, nextSignature) => {
      const commonInvalid =
        record !== dependencies.activeRecord() ||
        record.rawFrameOpen ||
        record.frameCaptured ||
        record.resultTimer !== null ||
        !validSharedState(record) ||
        !dependencies.sameViewportBounds(record.viewportSignature, nextSignature) ||
        !dependencies.hasDocumentToken();
      const bootstrapPhase = record.bootstrapState.phase();
      const phase = record.bootstrapBackingGeneration === 1 &&
          record.captures <= 1 &&
          !record.framePending &&
          bootstrapPhase === "generation"
        ? record.bootstrapState.beginTransition(!commonInvalid)
        : record.captures === 1 && !record.framePending && bootstrapPhase === "stable"
          ? record.bootstrapState.beginPostFrameTransition(!commonInvalid)
          : "terminal";
      if (!["transition", "post_frame_transition"].includes(phase)) return null;
      record.bootstrapTransitionUsed = true;
      record.viewportTransitionStartedAt = dependencies.now();
      record.viewportTransitionCount = 1;
      record.pendingViewportSignature = nextSignature;
      return phase;
    };

    const completeViewportTransition = (record, settledSignature) => {
      const invalid =
        !record.bootstrapTransitionUsed ||
        record.pendingViewportSignature === null ||
        !dependencies.sameViewportSignature(record.pendingViewportSignature, settledSignature) ||
        !validSharedState(record) ||
        !dependencies.sameViewportBounds(record.viewportSignature, settledSignature);
      if (record.bootstrapState.settle(!invalid) !== "stable") return false;
      record.viewportEpoch += 1;
      record.viewportSignature = settledSignature;
      record.pendingViewportSignature = null;
      record.viewportTransitionStartedAt = null;
      record.viewportTransitionCount = 0;
      return true;
    };

    return Object.freeze({ armGeneration, beginViewportTransition, completeViewportTransition });
  };

  globalThis.__gloshDagVideoLabBootstrap = Object.freeze({ create });
})();
