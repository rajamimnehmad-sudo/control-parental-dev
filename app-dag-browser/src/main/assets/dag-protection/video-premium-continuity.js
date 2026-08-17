"use strict";

(() => {
  if (globalThis.__gloshDagVideoPremiumContinuity !== undefined) return;

  const create = (dependencies) => {
    let blurredRecord = null;
    let safeSamples = 0;
    let skipTimer = null;
    let seekTimer = null;
    let skipAttempts = 0;
    let skipPending = false;
    let skipSearchActive = false;
    let skipping = false;

    const hideSkipControl = () => {
      dependencies.clearTimeout(skipTimer);
      skipTimer = null;
      dependencies.hideSkipControl();
    };

    const leaveBlur = (record, reason) => {
      if (blurredRecord !== record) return;
      hideSkipControl();
      record.video.removeAttribute(dependencies.blurAttribute);
      blurredRecord = null;
      safeSamples = 0;
      skipAttempts = 0;
      skipPending = false;
      skipSearchActive = false;
      skipping = false;
      dependencies.clearTimeout(seekTimer);
      seekTimer = null;
      dependencies.postDiagnostic(reason);
    };

    const sameAuthority = (record) =>
      record === dependencies.activeRecord() &&
      record.video.isConnected &&
      dependencies.sourceSignature(record.video) === record.sourceSignature;

    const advance = (record) => {
      skipPending = false;
      if (!sameAuthority(record) || blurredRecord !== record) return false;
      if (skipAttempts >= dependencies.maximumSkipAttempts) {
        dependencies.postDiagnostic("skip_limit");
        return false;
      }
      const current = record.video.currentTime;
      const duration = record.video.duration;
      if (!Number.isFinite(current) || !Number.isFinite(duration) || duration <= 0) {
        dependencies.postDiagnostic("skip_time_unavailable");
        return false;
      }
      const destination = Math.min(
        current + dependencies.skipStepSeconds,
        Math.max(0, duration - dependencies.endMarginSeconds),
      );
      if (destination - current < dependencies.minimumSkipSeconds) {
        dependencies.postDiagnostic("skip_end_reached");
        return false;
      }
      dependencies.clearTimeout(record.nextCaptureTimer);
      record.nextCaptureTimer = null;
      skipping = true;
      skipAttempts += 1;
      try {
        record.video.currentTime = destination;
      } catch {
        dependencies.postDiagnostic("skip_seek_rejected");
        skipping = false;
        return false;
      }
      dependencies.postDiagnostic("skip_started");
      return true;
    };

    const requestSkip = (record) => {
      if (!sameAuthority(record) || blurredRecord !== record) return false;
      dependencies.hideSkipControl();
      dependencies.postDiagnostic("skip_button_clicked");
      skipSearchActive = true;
      if (record.framePending) {
        skipPending = true;
        return true;
      }
      return advance(record);
    };

    const enterBlur = (record) => {
      if (blurredRecord !== record) {
        if (blurredRecord !== null) leaveBlur(blurredRecord, "blur_authority_replaced");
        blurredRecord = record;
        safeSamples = 0;
        record.video.setAttribute(dependencies.blurAttribute, "true");
        dependencies.postDiagnostic("blur_enter");
      } else {
        safeSamples = 0;
      }
      if (skipTimer === null) {
        skipTimer = dependencies.setTimeout(() => {
          skipTimer = null;
          if (
            blurredRecord !== record ||
            record !== dependencies.activeRecord() ||
            !record.video.isConnected
          ) return;
          dependencies.showSkipControl(record, () => requestSkip(record));
          dependencies.postDiagnostic("skip_button_shown");
        }, dependencies.skipControlDelayMillis);
      }
    };

    const onFrameBlocked = (record) => {
      if (
        record !== dependencies.activeRecord() ||
        !record.smoothActive ||
        !record.rawFrameOpen ||
        record.retiring ||
        !record.video.isConnected
      ) return false;
      enterBlur(record);
      dependencies.clearTimeout(record.resultTimer);
      record.resultTimer = null;
      dependencies.resetFrameState(record);
      record.terminal = false;
      if (skipSearchActive || skipping || skipPending) advance(record);
      else dependencies.scheduleNextCapture(record);
      return true;
    };

    const onFrameAllowed = (record) => {
      if (blurredRecord !== record) return false;
      safeSamples += 1;
      dependencies.postDiagnostic(
        safeSamples >= dependencies.safeSamplesRequired ? "blur_safe_confirmed" : "blur_safe_pending",
      );
      if (safeSamples >= dependencies.safeSamplesRequired) leaveBlur(record, "blur_exit");
      if (skipPending) {
        dependencies.setTimeout(() => {
          dependencies.clearTimeout(record.nextCaptureTimer);
          record.nextCaptureTimer = null;
          advance(record);
        }, 0);
      }
      return true;
    };

    const onSeeking = (record) => blurredRecord === record && skipping;

    const onSeeked = (record) => {
      if (blurredRecord !== record || !skipping || !sameAuthority(record)) return false;
      dependencies.clearTimeout(seekTimer);
      seekTimer = dependencies.setTimeout(() => {
        seekTimer = null;
        if (!sameAuthority(record) || blurredRecord !== record) return;
        skipping = false;
        dependencies.postDiagnostic("skip_analyzing");
        dependencies.requestFrameWhenReady(record);
      }, dependencies.seekSettleMillis);
      return true;
    };

    const clear = (record = null) => {
      if (blurredRecord === null || (record !== null && blurredRecord !== record)) return;
      leaveBlur(blurredRecord, "blur_cleared");
    };

    const rebind = (record) => {
      if (blurredRecord === record) dependencies.rebindSkipControl(record);
    };

    return Object.freeze({
      clear,
      onFrameAllowed,
      onFrameBlocked,
      onSeeked,
      onSeeking,
      rebind,
      requestSkip,
    });
  };

  globalThis.__gloshDagVideoPremiumContinuity = Object.freeze({ create });
})();
