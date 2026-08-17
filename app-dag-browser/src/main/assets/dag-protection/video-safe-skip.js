"use strict";

(() => {
  if (globalThis.__gloshDagVideoSafeSkip !== undefined) return;

  const create = (dependencies) => {
    let active = null;

    const clearTimers = () => {
      if (active === null) return;
      dependencies.clearTimeout(active.settleTimer);
      dependencies.clearTimeout(active.timeoutTimer);
      active.settleTimer = null;
      active.timeoutTimer = null;
    };

    const clear = () => {
      clearTimers();
      active = null;
    };

    const sameAuthority = (entry) =>
      entry.record === dependencies.activeRecord() &&
      entry.record.video.isConnected &&
      dependencies.sourceSignature(entry.record.video) === entry.sourceSignature &&
      JSON.stringify(dependencies.viewportSignature(entry.record.video)) === entry.viewportSignature;

    const exhaust = (reason) => {
      const entry = active;
      if (entry === null) return;
      clear();
      dependencies.postDiagnostic(reason);
      dependencies.onExhausted(entry.record, reason);
    };

    const settle = () => {
      if (active === null || active.phase !== "settling") return;
      active.settleTimer = null;
      if (
        active.record.video.seeking === true ||
        !sameAuthority(active) ||
        Math.abs(active.record.video.currentTime - active.destination) > dependencies.timeToleranceSeconds
      ) {
        exhaust("safe_skip_settle_invalid");
        return;
      }
      dependencies.clearTimeout(active.timeoutTimer);
      active.timeoutTimer = null;
      active.phase = "analyzing";
      dependencies.postDiagnostic("safe_skip_analyzing");
      dependencies.requestFrameWhenReady(active.record);
    };

    const begin = (record) => {
      const previousAttempts = active?.record === record ? active.attempts : 0;
      const attempts = previousAttempts + 1;
      clearTimers();
      active = null;
      if (attempts > dependencies.maximumAttempts) {
        dependencies.postDiagnostic("safe_skip_limit");
        return false;
      }
      const currentTime = record.video.currentTime;
      const duration = record.video.duration;
      if (!Number.isFinite(currentTime) || currentTime < 0 || !Number.isFinite(duration) || duration <= 0) {
        dependencies.postDiagnostic("safe_skip_time_unavailable");
        return false;
      }
      const destination = Math.min(
        currentTime + dependencies.stepSeconds,
        Math.max(0, duration - dependencies.endMarginSeconds),
      );
      if (destination - currentTime < dependencies.minimumAdvanceSeconds) {
        dependencies.postDiagnostic("safe_skip_end_reached");
        return false;
      }
      dependencies.resetFrameState(record);
      record.terminal = false;
      record.smoothActive = false;
      dependencies.safePause(record.video);
      active = {
        attempts,
        destination,
        from: currentTime,
        phase: "seeking",
        record,
        settleTimer: null,
        sourceSignature: dependencies.sourceSignature(record.video),
        timeoutTimer: null,
        viewportSignature: JSON.stringify(dependencies.viewportSignature(record.video)),
      };
      active.timeoutTimer = dependencies.setTimeout(
        () => exhaust("safe_skip_timeout"),
        dependencies.timeoutMillis,
      );
      try {
        record.video.currentTime = destination;
      } catch {
        exhaust("safe_skip_seek_rejected");
        return true;
      }
      dependencies.postDiagnostic("safe_skip_started");
      return true;
    };

    const onSeeking = (record) => {
      if (active === null || active.record !== record) return false;
      if (active.phase === "analyzing") {
        clear();
        return false;
      }
      if (active.phase !== "seeking" && active.phase !== "settling") {
        exhaust("safe_skip_seek_unexpected");
        return true;
      }
      dependencies.clearTimeout(active.settleTimer);
      active.settleTimer = null;
      active.phase = "seeking";
      return true;
    };

    const onSeeked = (record) => {
      if (active === null || active.record !== record) return false;
      if (active.phase !== "seeking" || !sameAuthority(active)) {
        exhaust("safe_skip_destination_invalid");
        return true;
      }
      active.phase = "settling";
      active.settleTimer = dependencies.setTimeout(settle, dependencies.settleMillis);
      return true;
    };

    const onFrameAllowed = (record) => {
      if (active === null || active.record !== record || active.phase !== "analyzing") return false;
      const skippedSeconds = Math.max(0, active.destination - active.from);
      clear();
      dependencies.postDiagnostic("safe_skip_recovered");
      dependencies.onRecovered(record, skippedSeconds);
      return true;
    };

    return Object.freeze({
      cancel: clear,
      onFrameAllowed,
      onFrameBlocked: begin,
      onSeeked,
      onSeeking,
    });
  };

  globalThis.__gloshDagVideoSafeSkip = Object.freeze({ create });
})();
