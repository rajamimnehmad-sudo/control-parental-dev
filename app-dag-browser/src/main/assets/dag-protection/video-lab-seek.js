"use strict";

(() => {
  if (globalThis.__gloshDagVideoLabSeek !== undefined) return;

  const create = (dependencies) => {
    let pending = null;

    const snapshot = (record) => ({
      sourceSignature: dependencies.sourceSignature(record.video),
      viewportSignature: JSON.stringify(dependencies.viewportSignature(record.video)),
      currentTime: record.video.currentTime,
    });

    const clearTimers = () => {
      if (pending === null) return;
      dependencies.clearTimeout(pending.timeout);
      dependencies.clearTimeout(pending.settleTimer);
      pending.timeout = null;
      pending.settleTimer = null;
    };

    const fail = (reason) => {
      if (pending === null) return;
      clearTimers();
      pending.machine.fail(reason);
      dependencies.enforceMediaIsolation();
      dependencies.postDiagnostic(reason);
    };

    const finishSettle = () => {
      if (pending === null || pending.machine.phase() !== dependencies.Phase.Stabilizing) return;
      pending.settleTimer = null;
      if (pending.record.video.seeking === true) {
        fail("seek_still_moving");
        return;
      }
      if (pending.machine.settle(snapshot(pending.record)) !== dependencies.Phase.Ready) {
        fail("seek_settle_invalid");
        return;
      }
      const destination = pending.machine.takeRearm();
      if (destination === null) {
        fail("seek_rearm_invalid");
        return;
      }
      clearTimers();
      pending = null;
      dependencies.scheduleScan();
    };

    const scheduleSettle = () => {
      if (pending === null || pending.machine.phase() !== dependencies.Phase.Stabilizing) return;
      dependencies.clearTimeout(pending.settleTimer);
      pending.settleTimer = dependencies.setTimeout(
        finishSettle,
        dependencies.settleMillis,
      );
    };

    const onSeeking = (record) => {
      if (pending !== null) {
        dependencies.clearTimeout(pending.settleTimer);
        pending.settleTimer = null;
        const phase = pending.machine.observeSeeking(snapshot(record));
        if (phase === dependencies.Phase.Terminal) fail("seek_destination_invalid");
        return true;
      }
      if (record !== dependencies.activeRecord() || record.retiring) return false;
      const machine = dependencies.stateRuntime.create();
      if (machine.begin(snapshot(record)) !== dependencies.Phase.Closing) return false;
      pending = {
        machine,
        record,
        settleTimer: null,
        timeout: dependencies.setTimeout(
          () => fail("seek_timeout"),
          dependencies.timeoutMillis,
        ),
      };
      dependencies.enforceMediaIsolation();
      void dependencies.retireRecord(record, "seek_requested");
      return true;
    };

    const onSeeked = (record) => {
      if (pending === null || pending.record !== record) return false;
      const phase = pending.machine.observeSeeked(snapshot(record));
      if (phase === dependencies.Phase.Terminal) {
        fail("seek_destination_invalid");
      } else {
        scheduleSettle();
      }
      return true;
    };

    const onNativeRearm = () => {
      if (pending === null) return false;
      if (pending.machine.phase() === dependencies.Phase.Terminal) return true;
      const phase = pending.machine.acknowledgeRevocation(true);
      if (phase === dependencies.Phase.Terminal) {
        fail("seek_revoke_invalid");
      } else {
        scheduleSettle();
      }
      return true;
    };

    return Object.freeze({
      holdsScan: () => pending !== null,
      onNativeRearm,
      onSeeked,
      onSeeking,
    });
  };

  globalThis.__gloshDagVideoLabSeek = Object.freeze({ create });
})();
