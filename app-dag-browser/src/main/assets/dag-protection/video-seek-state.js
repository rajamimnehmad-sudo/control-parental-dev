"use strict";

(() => {
  if (globalThis.__gloshDagVideoSeekState !== undefined) return;

  const Phase = Object.freeze({
    Idle: "idle",
    Closing: "closing",
    WaitingSeeked: "waiting_seeked",
    Stabilizing: "stabilizing",
    Ready: "ready",
    Complete: "complete",
    Terminal: "terminal",
  });

  const validSnapshot = (snapshot) =>
    typeof snapshot?.sourceSignature === "string" &&
    snapshot.sourceSignature.length > 0 &&
    typeof snapshot?.viewportSignature === "string" &&
    snapshot.viewportSignature.length > 0 &&
    Number.isFinite(snapshot?.currentTime) &&
    snapshot.currentTime >= 0;

  const sameAuthority = (left, right) =>
    left.sourceSignature === right.sourceSignature &&
    left.viewportSignature === right.viewportSignature;

  const create = () => {
    let phase = Phase.Idle;
    let origin = null;
    let destination = null;
    let revoked = false;
    let terminalReason = null;

    const terminal = (reason) => {
      phase = Phase.Terminal;
      destination = null;
      terminalReason = reason;
      return phase;
    };

    const begin = (snapshot) => {
      if (phase !== Phase.Idle) return terminal("seek_repeated");
      if (!validSnapshot(snapshot)) return terminal("seek_origin_invalid");
      origin = Object.freeze({ ...snapshot });
      phase = Phase.Closing;
      return phase;
    };

    const acknowledgeRevocation = (exact) => {
      if (phase !== Phase.Closing && phase !== Phase.WaitingSeeked) {
        return terminal("seek_revoke_unexpected");
      }
      if (exact !== true || revoked) return terminal("seek_revoke_invalid");
      revoked = true;
      phase = destination === null ? Phase.WaitingSeeked : Phase.Stabilizing;
      return phase;
    };

    const observeSeeked = (snapshot) => {
      if (phase !== Phase.Closing && phase !== Phase.WaitingSeeked) {
        return terminal("seeked_unexpected");
      }
      if (destination !== null) return terminal("seeked_repeated");
      if (!validSnapshot(snapshot) || !sameAuthority(origin, snapshot)) {
        return terminal("seek_destination_invalid");
      }
      destination = Object.freeze({ ...snapshot });
      phase = revoked ? Phase.Stabilizing : Phase.Closing;
      return phase;
    };

    const settle = (snapshot) => {
      if (phase !== Phase.Stabilizing) return terminal("seek_settle_unexpected");
      if (
        !validSnapshot(snapshot) ||
        !sameAuthority(destination, snapshot) ||
        Math.abs(snapshot.currentTime - destination.currentTime) > 0.05
      ) {
        return terminal("seek_settle_invalid");
      }
      destination = Object.freeze({ ...snapshot });
      phase = Phase.Ready;
      return phase;
    };

    const takeRearm = () => {
      if (phase !== Phase.Ready || destination === null) {
        terminal("seek_rearm_unexpected");
        return null;
      }
      const result = destination;
      phase = Phase.Complete;
      return result;
    };

    return Object.freeze({
      acknowledgeRevocation,
      begin,
      fail: (reason = "seek_failed") => terminal(reason),
      observeSeeked,
      phase: () => phase,
      reason: () => terminalReason,
      settle,
      takeRearm,
    });
  };

  globalThis.__gloshDagVideoSeekState = Object.freeze({ create, Phase });
})();
