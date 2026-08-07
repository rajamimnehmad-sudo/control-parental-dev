"use strict";

(() => {
  const portPrototype = globalThis.MessagePort?.prototype;
  const nativePostMessage = portPrototype?.postMessage;
  if (typeof nativePostMessage !== "function") return;

  const DETECTION_WINDOW_MS = 2_000;
  const MINIMUM_SIGNAL_SPAN_MS = 1_000;
  const MINIMUM_SIGNAL_COUNT = 12;
  const IDLE_RESET_MS = 1_000;
  const MAIN_THREAD_YIELD_MS = 16;
  const MAX_PENDING_SIGNALS = 64;
  const states = new WeakMap();
  let documentReady = document.readyState === "complete";
  if (!documentReady) {
    addEventListener("load", () => {
      documentReady = true;
    }, { once: true });
  }

  const isSchedulerSignal = (message, argumentCount) =>
    argumentCount === 1 &&
    (message === null || (Number.isSafeInteger(message) && message >= 0));

  const stateFor = (port) => {
    let state = states.get(port);
    if (state) return state;
    state = {
      active: false,
      lastRequestedAt: Number.NEGATIVE_INFINITY,
      pending: [],
      timer: null,
      timestamps: [],
    };
    states.set(port, state);
    return state;
  };

  const scheduleNext = (port, state) => {
    if (state.timer !== null || state.pending.length === 0) return;
    state.timer = setTimeout(() => {
      state.timer = null;
      const message = state.pending.shift();
      nativePostMessage.call(port, message);
      scheduleNext(port, state);
    }, MAIN_THREAD_YIELD_MS);
  };

  const flushPending = (port, state) => {
    if (state.timer !== null) {
      clearTimeout(state.timer);
      state.timer = null;
    }
    const pending = state.pending.splice(0);
    for (const message of pending) nativePostMessage.call(port, message);
    state.active = false;
    state.timestamps.length = 0;
  };

  Object.defineProperty(portPrototype, "postMessage", {
    configurable: true,
    enumerable: false,
    writable: true,
    value(message) {
      if (!isSchedulerSignal(message, arguments.length)) {
        return Reflect.apply(nativePostMessage, this, arguments);
      }
      if (!documentReady) return nativePostMessage.call(this, message);

      const now = performance.now();
      const state = stateFor(this);
      if (now - state.lastRequestedAt > IDLE_RESET_MS) {
        state.active = false;
        state.timestamps.length = 0;
      }
      state.lastRequestedAt = now;
      state.timestamps.push(now);
      const cutoff = now - DETECTION_WINDOW_MS;
      while (state.timestamps[0] < cutoff) state.timestamps.shift();

      if (
        !state.active &&
        state.timestamps.length >= MINIMUM_SIGNAL_COUNT &&
        now - state.timestamps[0] >= MINIMUM_SIGNAL_SPAN_MS
      ) {
        state.active = true;
      }

      if (!state.active) return nativePostMessage.call(this, message);
      if (state.pending.length >= MAX_PENDING_SIGNALS) {
        flushPending(this, state);
        return nativePostMessage.call(this, message);
      }
      state.pending.push(message);
      scheduleNext(this, state);
    },
  });
})();
