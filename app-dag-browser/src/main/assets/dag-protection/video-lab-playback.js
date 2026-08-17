"use strict";

(() => {
  if (globalThis.__gloshDagVideoLabPlayback !== undefined) return;

  const create = (dependencies) => {
    const finishFrameIfReady = (record) => {
      if (
        record !== dependencies.state.activeRecord ||
        !record.framePending ||
        !record.frameConcealed ||
        record.frameAllowed === null
      ) return;
      clearTimeout(record.resultTimer);
      record.resultTimer = null;
      const allowed = record.frameAllowed === true;
      dependencies.resetFrameState(record);
      if (!allowed || !dependencies.state.enabled || record.terminal) {
        record.terminal = true;
        dependencies.safePause(record.video);
        void dependencies.retireRecord(
          record,
          !allowed ? "frame_blocked" : "runtime_disabled",
        );
        return;
      }
      record.captures += 1;
      if (record.captures >= dependencies.maximumCaptureCount) {
        dependencies.state.enabled = false;
        void dependencies.retireRecord(record, "capture_limit");
        return;
      }
      if (!record.smoothActive && record.captures >= dependencies.initialCoveredCaptureCount) {
        void startSmoothPlayback(record);
        return;
      }
      scheduleNextCapture(record);
    };

    const scheduleNextCapture = (record) => {
      record.nextCaptureTimer = setTimeout(() => {
        record.nextCaptureTimer = null;
        dependencies.requestFrameWhenReady(record);
      }, record.smoothActive ? dependencies.smoothCaptureDelayMillis : dependencies.captureDelayMillis);
    };

    const startSmoothPlayback = async (record) => {
      if (
        record !== dependencies.state.activeRecord ||
        !record.covered ||
        record.rawFrameOpen ||
        record.retiring ||
        record.terminal ||
        !record.video.isConnected
      ) return;
      const reveal = await dependencies.browser.runtime.sendMessage({
        type: dependencies.revealMessage,
        version: dependencies.protocolVersion(),
        documentToken: dependencies.documentToken(),
        token: record.revealToken,
        ...dependencies.grantIdentity(record),
      }).catch(() => null);
      if (
        reveal?.inserted !== true ||
        record !== dependencies.state.activeRecord ||
        !record.covered
      ) {
        void dependencies.retireRecord(record, "smooth_reveal_denied");
        return;
      }
      record.rawFrameOpen = true;
      record.smoothActive = true;
      record.smoothGrantIdentity = Object.freeze(dependencies.grantIdentity(record));
      record.video.muted = true;
      record.video.defaultMuted = true;
      record.video.volume = 0;
      record.video.setAttribute(dependencies.tokenAttribute, record.revealToken);
      dependencies.enforcePresentationCapabilities(record);
      const style = dependencies.getComputedStyle(record.video);
      const opacity = Number.parseFloat(style.opacity);
      if (
        style.visibility !== "visible" ||
        style.display === "none" ||
        !Number.isFinite(opacity) ||
        opacity <= 0 ||
        dependencies.visibleArea(record.video) <= 0
      ) {
        dependencies.postDiagnostic("smooth_visibility_rejected");
        void dependencies.retireRecord(record, "smooth_visibility_rejected");
        return;
      }
      dependencies.postDiagnostic("smooth_visibility_ready");
      try {
        await record.video.play();
      } catch {
        void dependencies.retireRecord(record, "smooth_play_rejected");
        return;
      }
      if (
        record !== dependencies.state.activeRecord ||
        !record.smoothActive ||
        dependencies.unsafePresentationActive(record)
      ) {
        void dependencies.retireRecord(record, "smooth_start_invalidated");
        return;
      }
      record.video.muted = record.originalMuted;
      record.video.defaultMuted = record.originalDefaultMuted;
      record.video.volume = record.originalVolume;
      if (
        record.video.muted !== record.originalMuted ||
        record.video.defaultMuted !== record.originalDefaultMuted ||
        record.video.volume !== record.originalVolume
      ) {
        dependencies.postDiagnostic("smooth_audio_restore_failed");
        void dependencies.retireRecord(record, "smooth_audio_restore_failed");
        return;
      }
      dependencies.postDiagnostic("smooth_audio_restored");
      if (!dependencies.postRecord(dependencies.smoothStartMessage, record, {
        cadenceMillis: dependencies.smoothCaptureDelayMillis,
      })) {
        void dependencies.retireRecord(record, "smooth_start_rejected");
        return;
      }
      dependencies.postDiagnostic("smooth_playback_started");
      scheduleNextCapture(record);
    };

    const requestVideoFrame = (record) => {
      if (
        record !== dependencies.state.activeRecord ||
        !record.framePending ||
        record.retiring ||
        record.terminal ||
        dependencies.unsafePresentationActive(record)
      ) {
        if (
          record === dependencies.state.activeRecord &&
          dependencies.unsafePresentationActive(record)
        ) dependencies.retireUnsafePresentation(record);
        return;
      }
      if (record.viewportTransitionStartedAt !== null) return;
      if (typeof record.video.requestVideoFrameCallback !== "function") {
        void dependencies.retireRecord(record, "frame_callback_unavailable");
        return;
      }
      record.frameCallbackId = record.video.requestVideoFrameCallback((_now, metadata) => {
        record.frameCallbackId = null;
        if (
          record !== dependencies.state.activeRecord ||
          !record.framePending ||
          record.retiring ||
          record.terminal ||
          dependencies.unsafePresentationActive(record)
        ) {
          if (
            record === dependencies.state.activeRecord &&
            dependencies.unsafePresentationActive(record)
          ) dependencies.retireUnsafePresentation(record);
          return;
        }
        clearTimeout(record.readinessTimer);
        record.readinessTimer = null;
        if (!record.smoothActive) dependencies.safePause(record.video);
        const decodeMillis = record.decodeStartedAt === null
          ? null
          : Math.max(0, dependencies.now() - record.decodeStartedAt);
        if (!dependencies.postFrameRecord(dependencies.frameRequestMessage, record, {
          captureIndex: record.captures,
          coverMillis: record.coverMillis,
          decodeMillis,
          presentedFrames: Number.isFinite(metadata?.presentedFrames) ? metadata.presentedFrames : null,
        })) {
          void dependencies.retireRecord(record, "frame_request_rejected");
          return;
        }
        record.resultTimer = setTimeout(() => {
          record.resultTimer = null;
          void dependencies.retireRecord(record, "frame_result_timeout");
        }, dependencies.frameResultTimeoutMillis);
      });
    };

    const beginSmoothFrame = (record) => {
      if (record.viewportSuspended) return;
      record.frameSequence += 1;
      record.frameViewportEpoch = record.viewportEpoch;
      record.framePending = true;
      record.frameCaptured = false;
      record.frameConcealed = false;
      record.frameAllowed = null;
      record.decodeStartedAt = dependencies.now();
      requestVideoFrame(record);
    };

    return Object.freeze({
      beginSmoothFrame,
      finishFrameIfReady,
      requestVideoFrame,
      scheduleNextCapture,
      startSmoothPlayback,
    });
  };

  globalThis.__gloshDagVideoLabPlayback = Object.freeze({ create });
})();
