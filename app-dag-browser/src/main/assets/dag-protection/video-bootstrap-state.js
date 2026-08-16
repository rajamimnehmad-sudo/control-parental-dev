"use strict";

(() => {
  if (globalThis.__gloshDagVideoBootstrapState !== undefined) return;

  const create = () => {
    let phase = "idle";
    let postFrameTransitionUsed = false;
    const fail = () => {
      phase = "terminal";
      return phase;
    };
    return Object.freeze({
      loadStart(valid) {
        if (!valid || !["idle", "acknowledged"].includes(phase)) return fail();
        phase = phase === "acknowledged" ? "generation" : "load_pending";
        return phase;
      },
      acknowledge(valid) {
        if (!valid || !["idle", "load_pending"].includes(phase)) return fail();
        phase = phase === "load_pending" ? "generation" : "acknowledged";
        return phase;
      },
      coverReady(valid) {
        if (!valid || phase !== "acknowledged") return fail();
        phase = "stable";
        return phase;
      },
      beginTransition(valid) {
        if (!valid || phase !== "generation") return fail();
        phase = "transition";
        return phase;
      },
      beginPostFrameTransition(valid) {
        if (!valid || phase !== "stable" || postFrameTransitionUsed) return fail();
        postFrameTransitionUsed = true;
        phase = "post_frame_transition";
        return phase;
      },
      settle(valid) {
        if (!valid || !["transition", "post_frame_transition"].includes(phase)) return fail();
        phase = "stable";
        return phase;
      },
      mediaReady(valid) {
        if (!valid || phase !== "stable") return fail();
        return phase;
      },
      terminate() { return fail(); },
      reset() { phase = "idle"; postFrameTransitionUsed = false; },
      phase() { return phase; },
    });
  };

  globalThis.__gloshDagVideoBootstrapState = Object.freeze({ create });
})();
