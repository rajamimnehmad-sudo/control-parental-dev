"use strict";

(() => {
  if (globalThis.__gloshDagVideoPremiumFullscreen !== undefined) return;

  const create = (dependencies) => {
    let activeRecord = null;

    const exit = (record = activeRecord) => {
      if (record === null || activeRecord !== record) return false;
      record.video.removeAttribute(dependencies.videoAttribute);
      dependencies.documentElement.removeAttribute(dependencies.rootAttribute);
      record.premiumFullscreenActive = false;
      record.premiumFullscreenTransitionUntil =
        dependencies.now() + dependencies.transitionMillis;
      activeRecord = null;
      dependencies.postDiagnostic("fullscreen_exit");
      return true;
    };

    const set = (record, enabled) => {
      if (enabled !== true) return exit(record);
      if (
        record !== dependencies.activeRecord() ||
        !record.smoothActive ||
        !record.rawFrameOpen ||
        record.retiring ||
        !record.video.isConnected
      ) return false;
      if (activeRecord !== null && activeRecord !== record) exit(activeRecord);
      activeRecord = record;
      record.video.setAttribute(dependencies.videoAttribute, "true");
      dependencies.documentElement.setAttribute(dependencies.rootAttribute, "true");
      record.premiumFullscreenActive = true;
      record.premiumFullscreenTransitionUntil =
        dependencies.now() + dependencies.transitionMillis;
      dependencies.postDiagnostic("fullscreen_enter");
      return true;
    };

    const clear = (record = null) => {
      if (activeRecord === null || (record !== null && activeRecord !== record)) return;
      exit(activeRecord);
    };

    return Object.freeze({ clear, set });
  };

  globalThis.__gloshDagVideoPremiumFullscreen = Object.freeze({ create });
})();
