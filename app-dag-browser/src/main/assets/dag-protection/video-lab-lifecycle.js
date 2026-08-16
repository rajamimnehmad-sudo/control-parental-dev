"use strict";

(() => {
  if (globalThis.__gloshDagVideoLabLifecycle !== undefined) return;

  const create = (dependencies) => {
    const { state } = dependencies;

    const concealRecord = (record) => {
      record.video.removeAttribute(dependencies.tokenAttribute);
      if (!record.rawFrameOpen) return Promise.resolve(true);
      if (record.concealPromise !== null) return record.concealPromise;
      const promise = dependencies.browser.runtime.sendMessage({
        type: dependencies.concealMessage,
        version: dependencies.protocolVersion(),
        documentToken: dependencies.documentToken(),
        token: record.revealToken,
        ...(record.smoothGrantIdentity ?? dependencies.grantIdentity(record)),
      }).then((result) => result?.removed === true).catch(() => false).then((removed) => {
        dependencies.postDiagnostic(removed ? "conceal_removed" : "conceal_failed");
        record.concealFailed = !removed;
        if (removed) {
          record.rawFrameOpen = false;
          record.smoothGrantIdentity = null;
        }
        return removed;
      });
      record.concealPromise = promise;
      void promise.finally(() => {
        if (record.concealPromise === promise) record.concealPromise = null;
      });
      return promise;
    };

    const postRetire = (record, reason) => {
      const postToAndroid = dependencies.postToAndroid();
      if (postToAndroid === null) return;
      try {
        postToAndroid({
          type: dependencies.retireMessage,
          videoId: record.videoId,
          revision: record.revision,
          reason,
        });
      } catch {}
    };

    const retryTerminalIsolation = () => {
      if (!state.isolationLocked) return Promise.resolve(true);
      if (state.isolationRetryPromise !== null) return state.isolationRetryPromise;
      const record = state.isolationLockedRecord;
      if (record === null) return Promise.resolve(false);
      record.concealFailed = false;
      const promise = concealRecord(record).then((concealed) => {
        if (!concealed || state.isolationLockedRecord !== record) {
          dependencies.enforceMediaIsolation();
          return false;
        }
        state.isolationLocked = false;
        state.isolationLockedRecord = null;
        return true;
      });
      state.isolationRetryPromise = promise;
      void promise.finally(() => {
        if (state.isolationRetryPromise === promise) state.isolationRetryPromise = null;
      });
      return promise;
    };

    const retireRecord = (record, reason) => {
      if (record === null) return Promise.resolve(true);
      if (record.retirePromise !== null) return record.retirePromise;
      dependencies.clearRecordTimers(record);
      dependencies.safePause(record.video);
      record.smoothActive = false;
      record.covered = false;
      record.coverPending = false;
      record.coverAcknowledged = false;
      record.bootstrapState.terminate();
      record.bootstrapLoadStarted = false;
      record.bootstrapLoadSourceSignature = null;
      record.terminal = true;
      record.retiring = true;
      dependencies.resetFrameState(record);
      const wasActive = record === state.activeRecord;
      if (wasActive) state.activeRecord = null;
      state.closingRecord = record;
      dependencies.enforceMediaIsolation();
      const conceal = record.concealFailed ? Promise.resolve(false) : concealRecord(record);
      const promise = conceal.then((concealed) => {
        record.retiring = false;
        record.retirePromise = null;
        if (!concealed) {
          state.enabled = false;
          state.isolationLocked = true;
          state.isolationLockedRecord = record;
          if (state.closingRecord === record) state.closingRecord = null;
          dependencies.enforceMediaIsolation();
          return false;
        }
        if (state.closingRecord === record) state.closingRecord = null;
        if (reason === "frame_blocked") {
          state.isolationLocked = true;
          state.isolationLockedRecord = record;
        }
        if (wasActive) postRetire(record, reason);
        if (state.enabled && reason !== "frame_blocked") dependencies.scheduleScan();
        return true;
      });
      record.retirePromise = promise;
      return promise;
    };

    return Object.freeze({ concealRecord, retireRecord, retryTerminalIsolation });
  };

  globalThis.__gloshDagVideoLabLifecycle = Object.freeze({ create });
})();
