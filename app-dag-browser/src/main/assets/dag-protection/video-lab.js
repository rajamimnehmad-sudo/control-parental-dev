"use strict";

(() => {
  if (globalThis.__gloshDagVideoLab !== undefined) return;

  const CONFIG_MESSAGE = "video-lab-config";
  const STATUS_MESSAGE = "video-lab-status";
  const COVER_REQUEST_MESSAGE = "video-lab-cover-request";
  const COVER_ARMED_MESSAGE = "video-lab-cover-armed";
  const FRAME_REQUEST_MESSAGE = "video-lab-frame-request";
  const FRAME_CAPTURED_MESSAGE = "video-lab-frame-captured";
  const FRAME_CONCEALED_MESSAGE = "video-lab-frame-concealed";
  const FRAME_RESULT_MESSAGE = "video-lab-frame-result";
  const RETIRE_MESSAGE = "video-lab-retire";
  const REVEAL_MESSAGE = "video-lab-reveal-style";
  const CONCEAL_MESSAGE = "video-lab-conceal-style";
  const TOKEN_ATTRIBUTE = "data-glosh-dag-video-lab-token";
  const PRESENTATION_CAPABILITY_ATTRIBUTES = new Set([
    "disablepictureinpicture",
    "disableremoteplayback",
    "controlslist",
    "playsinline",
  ]);
  // The diagnostic transport is intentionally finite even though it replays a
  // sequence. There is one raw compositor grant, capture and decision at once.
  const MAX_CAPTURE_COUNT = 120;
  const CAPTURE_DELAY_MS = 0;
  const STATUS_RETRY_MS = 50;
  const MAX_STATUS_RETRIES = 20;
  const COVER_TIMEOUT_MS = 2_500;
  const FRAME_READY_TIMEOUT_MS = 2_500;
  const FRAME_RESULT_TIMEOUT_MS = 2_500;
  const VIEWPORT_SETTLE_MS = 150;
  const INTERNAL_FIXTURE_ATTRIBUTE = "data-glosh-dag-video-lab-fixture";

  let installed = false;
  let enabled = false;
  let protocolVersion = 0;
  let documentToken = "";
  let postToAndroid = null;
  let activeRecord = null;
  let closingRecord = null;
  let isolationLocked = false;
  let isolationLockedRecord = null;
  let isolationRetryPromise = null;
  let configurationEpoch = 0;
  let scanScheduled = false;
  let pendingConfiguration = null;
  let lastViewportChangeAt = performance.now();
  const records = new WeakMap();

  const randomToken = (wordCount) => {
    const words = crypto.getRandomValues(new Uint32Array(wordCount));
    return Array.from(words, (word) => word.toString(16).padStart(8, "0")).join("");
  };

  const frameIdentity = (record) => ({
    frameSequence: record.frameSequence,
    viewportEpoch: record.frameViewportEpoch,
  });

  const recordMatchesMessage = (record, message) =>
    record !== null &&
    record === activeRecord &&
    message?.videoId === record.videoId &&
    message?.revision === record.revision;

  const frameMatchesMessage = (record, message) =>
    recordMatchesMessage(record, message) &&
    record.framePending &&
    message?.frameSequence === record.frameSequence &&
    message?.viewportEpoch === record.frameViewportEpoch;

  const postRecord = (type, record, extra = {}) => {
    if (
      postToAndroid === null ||
      record !== activeRecord ||
      record.retiring ||
      !record.video.isConnected
    ) return false;
    postToAndroid({
      type,
      videoId: record.videoId,
      revision: record.revision,
      ...rectPayload(record.video),
      ...extra,
    });
    return true;
  };

  const postFrameRecord = (type, record, extra = {}) =>
    postRecord(type, record, {
      ...frameIdentity(record),
      ...extra,
    });

  const enforceMuted = (record) => {
    if (record !== activeRecord || record.retiring) return;
    if (!record.video.muted) record.video.muted = true;
    if (!record.video.defaultMuted) record.video.defaultMuted = true;
    if (record.video.volume !== 0) record.video.volume = 0;
  };

  const safePause = (video) => {
    try {
      video.pause();
    } catch {}
  };

  const rememberExpectedPresentationMutation = (record, attribute, apply) => {
    const before = record.video.getAttribute(attribute);
    try {
      apply();
    } catch {
      return;
    }
    if (before === record.video.getAttribute(attribute)) return;
    record.expectedPresentationMutations.push({ attribute, oldValue: before });
    clearTimeout(record.presentationMutationClearTimer);
    record.presentationMutationClearTimer = setTimeout(() => {
      record.presentationMutationClearTimer = null;
      record.expectedPresentationMutations.length = 0;
    }, 0);
  };

  // These are preventive HTML-media capabilities only. The native cover and
  // exact grant/revocation protocol remain the authority for fail-closed
  // presentation; a hostile MAIN world is not trusted by this helper.
  const enforcePresentationCapabilities = (record) => {
    const { video } = record;
    if ("disablePictureInPicture" in video) {
      rememberExpectedPresentationMutation(record, "disablepictureinpicture", () => {
        video.disablePictureInPicture = true;
      });
    }
    if ("disableRemotePlayback" in video) {
      rememberExpectedPresentationMutation(record, "disableremoteplayback", () => {
        video.disableRemotePlayback = true;
      });
    }
    if ("playsInline" in video) {
      rememberExpectedPresentationMutation(record, "playsinline", () => {
        video.playsInline = true;
      });
    }
    const controlsList = video.controlsList;
    if (typeof controlsList?.add !== "function") return;
    rememberExpectedPresentationMutation(record, "controlslist", () => {
      controlsList.add("nofullscreen");
    });
    rememberExpectedPresentationMutation(record, "controlslist", () => {
      controlsList.add("noremoteplayback");
    });
  };

  // A raw compositor grant is scoped to the selected video only.  Audio and
  // every other video stay both silent and paused for the entire diagnostic
  // session; a document cannot race a newly inserted element past this guard.
  const isAuthorizedRawPlayback = (media) => {
    const record = activeRecord;
    return record !== null &&
      media === record.video &&
      record.rawFrameOpen &&
      record.framePending &&
      !isolationLocked &&
      !record.retiring &&
      !record.terminal;
  };

  const silenceAndPauseMedia = (media) => {
    if (!(media instanceof HTMLMediaElement)) return;
    if (!media.muted) media.muted = true;
    if (!media.defaultMuted) media.defaultMuted = true;
    if (media.volume !== 0) media.volume = 0;
    if (!isAuthorizedRawPlayback(media)) safePause(media);
  };

  const mediaIsolationActive = () =>
    isolationLocked || enabled || activeRecord !== null || closingRecord !== null;

  const enforceMediaIsolation = () => {
    if (!mediaIsolationActive()) return;
    for (const media of document.querySelectorAll("audio, video")) {
      silenceAndPauseMedia(media);
    }
  };

  const stopUnauthorizedPlayback = (event) => {
    if (!mediaIsolationActive()) return;
    silenceAndPauseMedia(event.target);
  };

  const clearRecordTimers = (record) => {
    clearTimeout(record.nextCaptureTimer);
    clearTimeout(record.readinessTimer);
    clearTimeout(record.resultTimer);
    clearTimeout(record.coverTimer);
    clearTimeout(record.presentationMutationClearTimer);
    record.nextCaptureTimer = null;
    record.readinessTimer = null;
    record.resultTimer = null;
    record.coverTimer = null;
    record.presentationMutationClearTimer = null;
    if (
      record.frameCallbackId !== null &&
      typeof record.video.cancelVideoFrameCallback === "function"
    ) {
      try {
        record.video.cancelVideoFrameCallback(record.frameCallbackId);
      } catch {}
    }
    record.frameCallbackId = null;
  };

  const resetFrameState = (record) => {
    record.framePending = false;
    record.frameCaptured = false;
    record.frameConcealed = false;
    record.frameAllowed = null;
    record.frameViewportEpoch = record.viewportEpoch;
  };

  const recordFor = (video) => {
    const existing = records.get(video);
    if (existing !== undefined) return existing;
    const record = {
      video,
      videoId: `video_${randomToken(2)}`,
      revision: 0,
      activations: 0,
      captures: 0,
      covered: false,
      coverPending: false,
      framePending: false,
      frameCaptured: false,
      frameConcealed: false,
      frameAllowed: null,
      frameSequence: 0,
      viewportEpoch: 0,
      frameViewportEpoch: 0,
      rawFrameOpen: false,
      terminal: false,
      retiring: false,
      retirePromise: null,
      concealFailed: false,
      concealPromise: null,
      sourceSignature: "",
      revealToken: randomToken(4),
      nextCaptureTimer: null,
      readinessTimer: null,
      resultTimer: null,
      coverTimer: null,
      frameCallbackId: null,
      coverRequestedAt: null,
      coverMillis: null,
      decodeStartedAt: null,
      expectedPresentationMutations: [],
      presentationMutationClearTimer: null,
    };
    const keepMuted = () => enforceMuted(record);
    const closeUnsafePresentation = () => {
      if (record === activeRecord) void retireRecord(record, "unsafe_presentation");
    };
    const remote = video.remote;
    video.addEventListener("play", keepMuted);
    video.addEventListener("volumechange", keepMuted);
    video.addEventListener("enterpictureinpicture", closeUnsafePresentation);
    video.addEventListener("leavepictureinpicture", closeUnsafePresentation);
    video.addEventListener("webkitbeginfullscreen", closeUnsafePresentation);
    video.addEventListener("webkitendfullscreen", closeUnsafePresentation);
    video.addEventListener("webkitpresentationmodechanged", closeUnsafePresentation);
    video.addEventListener(
      "webkitcurrentplaybacktargetiswirelesschanged",
      closeUnsafePresentation,
    );
    video.addEventListener("emptied", closeUnsafePresentation);
    video.addEventListener("error", closeUnsafePresentation);
    if (typeof remote?.addEventListener === "function") {
      remote.addEventListener("connecting", closeUnsafePresentation);
      remote.addEventListener("connect", closeUnsafePresentation);
      remote.addEventListener("disconnect", closeUnsafePresentation);
    }
    records.set(video, record);
    return record;
  };

  const sourceSignature = (video) => [
    video.currentSrc || "",
    video.getAttribute("src") || "",
    video.srcObject === null ? "no_stream" : "stream",
    [...video.querySelectorAll("source")]
      .map((source) => `${source.getAttribute("src") || ""}:${source.getAttribute("type") || ""}`)
      .join("|"),
  ].join("::");

  const visibleArea = (video) => {
    if (!video.isConnected) return 0;
    const rect = video.getBoundingClientRect();
    const width = Math.max(0, Math.min(rect.right, innerWidth) - Math.max(rect.left, 0));
    const height = Math.max(0, Math.min(rect.bottom, innerHeight) - Math.max(rect.top, 0));
    return width * height;
  };

  const rectPayload = (video) => {
    const rect = video.getBoundingClientRect();
    return {
      left: rect.left,
      top: rect.top,
      width: rect.width,
      height: rect.height,
      viewportWidth: innerWidth,
      viewportHeight: innerHeight,
    };
  };

  const unsafePresentationActive = (record) => {
    const remoteState = record.video.remote?.state;
    return document.fullscreenElement !== null ||
      document.pictureInPictureElement !== null ||
      record.video.webkitPresentationMode === "fullscreen" ||
      record.video.webkitPresentationMode === "picture-in-picture" ||
      record.video.webkitCurrentPlaybackTargetIsWireless === true ||
      (remoteState !== undefined && remoteState !== "disconnected");
  };

  const concealRecord = (record) => {
    record.video.removeAttribute(TOKEN_ATTRIBUTE);
    if (!record.rawFrameOpen) return Promise.resolve(true);
    if (record.concealPromise !== null) return record.concealPromise;
    const promise = browser.runtime.sendMessage({
      type: CONCEAL_MESSAGE,
      version: protocolVersion,
      documentToken,
      token: record.revealToken,
    }).then((result) => result?.removed === true).catch(() => false).then((removed) => {
      record.concealFailed = !removed;
      if (removed) record.rawFrameOpen = false;
      return removed;
    });
    record.concealPromise = promise;
    void promise.finally(() => {
      if (record.concealPromise === promise) record.concealPromise = null;
    });
    return promise;
  };

  const postRetire = (record, reason) => {
    if (postToAndroid === null) return;
    try {
      postToAndroid({
        type: RETIRE_MESSAGE,
        videoId: record.videoId,
        revision: record.revision,
        reason,
      });
    } catch {}
  };

  // A failed CSS removal means the old user-origin reveal may still exist.
  // Keep a separate latch and record reference instead of relying on active or
  // closing state: those can legitimately be cleared while the page continues
  // firing media events. Only a later explicit native reconfiguration may
  // retry revocation and release this latch after a confirmed success.
  const retryTerminalIsolation = () => {
    if (!isolationLocked) return Promise.resolve(true);
    if (isolationRetryPromise !== null) return isolationRetryPromise;
    const record = isolationLockedRecord;
    if (record === null) return Promise.resolve(false);
    record.concealFailed = false;
    const promise = concealRecord(record).then((concealed) => {
      if (!concealed || isolationLockedRecord !== record) {
        enforceMediaIsolation();
        return false;
      }
      isolationLocked = false;
      isolationLockedRecord = null;
      return true;
    });
    isolationRetryPromise = promise;
    void promise.finally(() => {
      if (isolationRetryPromise === promise) isolationRetryPromise = null;
    });
    return promise;
  };

  const retireRecord = (record, reason) => {
    if (record === null) return Promise.resolve(true);
    if (record.retirePromise !== null) return record.retirePromise;
    clearRecordTimers(record);
    safePause(record.video);
    record.covered = false;
    record.coverPending = false;
    record.terminal = true;
    record.retiring = true;
    resetFrameState(record);
    const wasActive = record === activeRecord;
    if (wasActive) activeRecord = null;
    closingRecord = record;
    enforceMediaIsolation();
    const conceal = record.concealFailed ? Promise.resolve(false) : concealRecord(record);
    const promise = conceal.then((concealed) => {
      record.retiring = false;
      record.retirePromise = null;
      if (!concealed) {
        // The background retains the failed grant in closing state and Android
        // keeps its native cover. Keep media isolation alive independently: a
        // later page event must not make audio or another video resume locally.
        enabled = false;
        isolationLocked = true;
        isolationLockedRecord = record;
        if (closingRecord === record) closingRecord = null;
        enforceMediaIsolation();
        return false;
      }
      if (closingRecord === record) closingRecord = null;
      if (wasActive) postRetire(record, reason);
      if (enabled) scheduleScan();
      return true;
    });
    record.retirePromise = promise;
    return promise;
  };

  const resetForAuthority = (record) => {
    clearRecordTimers(record);
    record.activations += 1;
    record.revision += 1;
    record.captures = 0;
    record.covered = false;
    record.coverPending = false;
    record.rawFrameOpen = false;
    record.terminal = false;
    record.retiring = false;
    record.retirePromise = null;
    record.concealFailed = false;
    record.concealPromise = null;
    record.expectedPresentationMutations.length = 0;
    clearTimeout(record.presentationMutationClearTimer);
    record.presentationMutationClearTimer = null;
    record.frameSequence = 0;
    record.viewportEpoch += 1;
    resetFrameState(record);
    record.sourceSignature = sourceSignature(record.video);
    record.revealToken = randomToken(4);
    record.coverRequestedAt = null;
    record.coverMillis = null;
    record.decodeStartedAt = null;
  };

  const requestCover = (record) => {
    if (
      !enabled ||
      record !== activeRecord ||
      record.retiring ||
      closingRecord !== null ||
      unsafePresentationActive(record)
    ) {
      if (record === activeRecord && unsafePresentationActive(record)) {
        void retireRecord(record, "unsafe_presentation");
      }
      return;
    }
    if (record.sourceSignature !== sourceSignature(record.video)) {
      void retireRecord(record, "source_changed");
      return;
    }
    if (record.covered || record.coverPending) return;
    const coverRequestedAt = performance.now();
    record.coverPending = postRecord(COVER_REQUEST_MESSAGE, record, {
      readyState: record.video.readyState,
      durationFinite: Number.isFinite(record.video.duration),
      viewportEpoch: record.viewportEpoch,
    });
    if (record.coverPending) {
      record.coverRequestedAt = coverRequestedAt;
      record.coverTimer = setTimeout(() => {
        record.coverTimer = null;
        void retireRecord(record, "cover_timeout");
      }, COVER_TIMEOUT_MS);
    }
  };

  const selectVisibleVideo = () => {
    scanScheduled = false;
    if (!enabled || window.top !== window || closingRecord !== null) return;
    enforceMediaIsolation();
    const candidate = [...document.querySelectorAll("video")]
      .map((video) => ({ video, area: visibleArea(video) }))
      .filter(({ area }) => area > 0)
      .sort((left, right) => right.area - left.area)[0]?.video || null;
    if (candidate === activeRecord?.video) {
      if (activeRecord !== null) requestCover(activeRecord);
      return;
    }
    if (activeRecord !== null) {
      void retireRecord(activeRecord, "authority_changed");
      return;
    }
    if (candidate === null) return;
    const record = recordFor(candidate);
    resetForAuthority(record);
    activeRecord = record;
    enforcePresentationCapabilities(record);
    enforceMediaIsolation();
    requestCover(record);
  };

  const scheduleScan = () => {
    if (scanScheduled) return;
    scanScheduled = true;
    requestAnimationFrame(selectVisibleVideo);
  };

  const backgroundReady = async () => {
    for (let attempt = 0; attempt < MAX_STATUS_RETRIES; attempt += 1) {
      try {
        const status = await browser.runtime.sendMessage({
          type: STATUS_MESSAGE,
          version: protocolVersion,
          documentToken,
        });
        if (status?.enabled === true) return true;
      } catch {}
      await new Promise((resolve) => setTimeout(resolve, STATUS_RETRY_MS));
    }
    return false;
  };

  const finishFrameIfReady = (record) => {
    if (
      record !== activeRecord ||
      !record.framePending ||
      !record.frameConcealed ||
      record.frameAllowed === null
    ) return;
    clearTimeout(record.resultTimer);
    record.resultTimer = null;
    const allowed = record.frameAllowed === true;
    resetFrameState(record);
    if (!allowed || !enabled || record.terminal) {
      record.terminal = true;
      safePause(record.video);
      return;
    }
    record.captures += 1;
    if (record.captures >= MAX_CAPTURE_COUNT) {
      void retireRecord(record, "capture_limit");
      return;
    }
    record.nextCaptureTimer = setTimeout(() => {
      record.nextCaptureTimer = null;
      requestFrameWhenReady(record);
    }, CAPTURE_DELAY_MS);
  };

  const requestVideoFrame = (record) => {
    if (
      record !== activeRecord ||
      !record.framePending ||
      record.retiring ||
      record.terminal ||
      unsafePresentationActive(record)
    ) {
      if (record === activeRecord && unsafePresentationActive(record)) {
        void retireRecord(record, "unsafe_presentation");
      }
      return;
    }
    if (typeof record.video.requestVideoFrameCallback !== "function") {
      void retireRecord(record, "frame_callback_unavailable");
      return;
    }
    record.frameCallbackId = record.video.requestVideoFrameCallback((_now, metadata) => {
      record.frameCallbackId = null;
      if (
        record !== activeRecord ||
        !record.framePending ||
        record.retiring ||
        record.terminal ||
        unsafePresentationActive(record)
      ) {
        if (record === activeRecord && unsafePresentationActive(record)) {
          void retireRecord(record, "unsafe_presentation");
        }
        return;
      }
      clearTimeout(record.readinessTimer);
      record.readinessTimer = null;
      safePause(record.video);
      const decodeMillis = record.decodeStartedAt === null
        ? null
        : Math.max(0, performance.now() - record.decodeStartedAt);
      if (!postFrameRecord(FRAME_REQUEST_MESSAGE, record, {
        captureIndex: record.captures,
        coverMillis: record.coverMillis,
        decodeMillis,
        presentedFrames: Number.isFinite(metadata?.presentedFrames) ? metadata.presentedFrames : null,
      })) {
        void retireRecord(record, "frame_request_rejected");
        return;
      }
      record.resultTimer = setTimeout(() => {
        record.resultTimer = null;
        void retireRecord(record, "frame_result_timeout");
      }, FRAME_RESULT_TIMEOUT_MS);
    });
  };

  const revealAndRequestFrame = async (record) => {
    if (
      record !== activeRecord ||
      !record.covered ||
      record.framePending ||
      record.retiring ||
      record.terminal ||
      !record.video.isConnected
    ) return;
    if (unsafePresentationActive(record)) {
      void retireRecord(record, "unsafe_presentation");
      return;
    }
    enforcePresentationCapabilities(record);
    if (unsafePresentationActive(record)) {
      void retireRecord(record, "unsafe_presentation");
      return;
    }
    record.frameSequence += 1;
    record.frameViewportEpoch = record.viewportEpoch;
    record.framePending = true;
    record.frameCaptured = false;
    record.frameConcealed = false;
    record.frameAllowed = null;
    const reveal = await browser.runtime.sendMessage({
      type: REVEAL_MESSAGE,
      version: protocolVersion,
      documentToken,
      token: record.revealToken,
    }).catch(() => null);
    if (reveal?.inserted !== true) {
      void retireRecord(record, "reveal_denied");
      return;
    }
    record.rawFrameOpen = true;
    if (
      record !== activeRecord ||
      !enabled ||
      record.retiring ||
      record.terminal ||
      unsafePresentationActive(record)
    ) {
      void retireRecord(record, "reveal_invalidated");
      return;
    }
    enforceMediaIsolation();
    enforcePresentationCapabilities(record);
    if (unsafePresentationActive(record)) {
      void retireRecord(record, "unsafe_presentation");
      return;
    }
    record.video.muted = true;
    record.video.defaultMuted = true;
    record.video.volume = 0;
    record.video.preload = "auto";
    record.video.setAttribute(TOKEN_ATTRIBUTE, record.revealToken);
    record.decodeStartedAt = performance.now();
    if (
      record.captures === 0 &&
      document.documentElement.hasAttribute(INTERNAL_FIXTURE_ATTRIBUTE)
    ) {
      record.video.load();
    }
    record.readinessTimer = setTimeout(() => {
      record.readinessTimer = null;
      void retireRecord(record, "frame_ready_timeout");
    }, FRAME_READY_TIMEOUT_MS);
    enforcePresentationCapabilities(record);
    if (unsafePresentationActive(record)) {
      void retireRecord(record, "unsafe_presentation");
      return;
    }
    try {
      await record.video.play();
    } catch {
      void retireRecord(record, "play_rejected");
      return;
    }
    requestVideoFrame(record);
  };

  const requestFrameWhenReady = (record) => {
    if (
      record !== activeRecord ||
      !record.covered ||
      record.framePending ||
      record.retiring ||
      record.terminal ||
      !record.video.isConnected
    ) return;
    const request = () => {
      if (
        record !== activeRecord ||
        !record.covered ||
        record.framePending ||
        record.retiring ||
        record.terminal ||
        !record.video.isConnected
      ) return;
      if (unsafePresentationActive(record)) {
        void retireRecord(record, "unsafe_presentation");
        return;
      }
      const viewportWait = VIEWPORT_SETTLE_MS - (performance.now() - lastViewportChangeAt);
      if (viewportWait > 0) {
        record.readinessTimer = setTimeout(request, viewportWait);
        return;
      }
      record.readinessTimer = null;
      void revealAndRequestFrame(record);
    };
    request();
  };

  const armCoveredVideo = async (message) => {
    const record = activeRecord;
    if (
      !recordMatchesMessage(record, message) ||
      !record.coverPending ||
      !await backgroundReady()
    ) return;
    clearTimeout(record.coverTimer);
    record.coverTimer = null;
    record.coverMillis = record.coverRequestedAt === null
      ? null
      : Math.max(0, performance.now() - record.coverRequestedAt);
    record.coverPending = false;
    record.covered = true;
    record.video.muted = true;
    record.video.defaultMuted = true;
    record.video.volume = 0;
    enforcePresentationCapabilities(record);
    enforceMediaIsolation();
    // The cover acknowledgement does not open a raw frame. The next method
    // performs the only ephemeral reveal, after the Android cover is confirmed.
    requestFrameWhenReady(record);
  };

  const handleFrameCaptured = (message) => {
    const record = activeRecord;
    if (!frameMatchesMessage(record, message) || !record.rawFrameOpen) return;
    record.frameCaptured = true;
    void concealRecord(record).then((concealed) => {
      if (!frameMatchesMessage(record, message)) return;
      if (!concealed) {
        void retireRecord(record, "frame_conceal_failed");
        return;
      }
      record.frameConcealed = true;
      if (!postFrameRecord(FRAME_CONCEALED_MESSAGE, record)) {
        void retireRecord(record, "frame_concealed_rejected");
        return;
      }
      finishFrameIfReady(record);
    });
  };

  const handleFrameResult = (message) => {
    const record = activeRecord;
    if (!frameMatchesMessage(record, message)) return;
    record.frameAllowed = message.captured === true && message.action === "allow";
    if (!record.frameAllowed) {
      // A block never schedules another raw compositor frame. If Android's
      // capture acknowledgement is still racing (or capture failed before it
      // could be sent), concealment remains required immediately.
      record.terminal = true;
      safePause(record.video);
      if (!record.frameConcealed) {
        void concealRecord(record).then((concealed) => {
          if (!frameMatchesMessage(record, message)) return;
          if (!concealed) {
            void retireRecord(record, "terminal_frame_conceal_failed");
            return;
          }
          record.frameConcealed = true;
          finishFrameIfReady(record);
        });
      }
    }
    finishFrameIfReady(record);
  };

  const invalidateForViewport = () => {
    lastViewportChangeAt = performance.now();
    if (activeRecord !== null) {
      activeRecord.viewportEpoch += 1;
      void retireRecord(activeRecord, "viewport_changed");
      return;
    }
    scheduleScan();
  };

  const mutationTouchesActiveVideo = (mutation, record) => {
    if (mutation.target === record.video) return true;
    if (mutation.target instanceof HTMLSourceElement && mutation.target.parentElement === record.video) {
      return true;
    }
    for (const node of [...mutation.addedNodes, ...mutation.removedNodes]) {
      if (node === record.video) return true;
      if (node instanceof HTMLSourceElement && node.parentElement === record.video) return true;
    }
    return false;
  };

  const consumeExpectedPresentationMutation = (mutation, record) => {
    if (mutation.type !== "attributes" || mutation.target !== record.video) return false;
    const attribute = mutation.attributeName?.toLowerCase();
    if (!PRESENTATION_CAPABILITY_ATTRIBUTES.has(attribute)) return false;
    const expectedIndex = record.expectedPresentationMutations.findIndex((expected) =>
      expected.attribute === attribute && expected.oldValue === mutation.oldValue);
    if (expectedIndex < 0) return false;
    record.expectedPresentationMutations.splice(expectedIndex, 1);
    return true;
  };

  const mutationRequiresTerminalClose = (mutation, record) =>
    mutationTouchesActiveVideo(mutation, record) &&
    !consumeExpectedPresentationMutation(mutation, record);

  const mutationObserver = new MutationObserver((recordsList) => {
    enforceMediaIsolation();
    const record = activeRecord;
    if (record !== null && recordsList.some((mutation) => mutationRequiresTerminalClose(mutation, record))) {
      void retireRecord(record, "active_video_mutated");
      return;
    }
    scheduleScan();
  });

  globalThis.__gloshDagVideoLab = Object.freeze({
    install(configuration) {
      if (installed) return;
      installed = true;
      protocolVersion = configuration.protocolVersion;
      documentToken = configuration.documentToken;
      postToAndroid = configuration.postToAndroid;
      mutationObserver.observe(document, {
        attributes: true,
        attributeFilter: [
          "src",
          "type",
          "disablepictureinpicture",
          "disableremoteplayback",
          "controlslist",
          "playsinline",
        ],
        attributeOldValue: true,
        childList: true,
        subtree: true,
      });
      document.addEventListener("play", stopUnauthorizedPlayback, true);
      document.addEventListener("volumechange", stopUnauthorizedPlayback, true);
      addEventListener("scroll", invalidateForViewport, { passive: true });
      addEventListener("resize", invalidateForViewport, { passive: true });
      addEventListener("pagehide", () => void retireRecord(activeRecord, "document_retired"));
      addEventListener("fullscreenchange", () => {
        if (document.fullscreenElement !== null) {
          void document.exitFullscreen?.().catch(() => {});
          void retireRecord(activeRecord, "fullscreen_requested");
        }
      });
      addEventListener("fullscreenerror", () => void retireRecord(activeRecord, "fullscreen_error"));
      if (pendingConfiguration?.version === protocolVersion) {
        enabled = pendingConfiguration.enabled;
        pendingConfiguration = null;
      }
      enforceMediaIsolation();
      scheduleScan();
    },
    onNativeMessage(message) {
      if (message?.type === CONFIG_MESSAGE) {
        const configuration = { version: message.version, enabled: message.enabled === true };
        if (!installed) {
          pendingConfiguration = configuration;
          return;
        }
        if (configuration.version !== protocolVersion) return;
        configurationEpoch += 1;
        const epoch = configurationEpoch;
        if (configuration.enabled && isolationLocked) {
          // Do not reinterpret a page event or a duplicate message as a safe
          // reopen. The fresh native configuration can only resume after the
          // original privileged CSS is confirmed removed.
          enabled = false;
          void retryTerminalIsolation().then((recovered) => {
            if (!recovered || epoch !== configurationEpoch) return;
            enabled = true;
            enforceMediaIsolation();
            scheduleScan();
          });
        } else if (configuration.enabled) {
          enabled = true;
          enforceMediaIsolation();
          scheduleScan();
        } else {
          enabled = false;
          void retireRecord(activeRecord, "lab_disabled");
        }
        return;
      }
      if (message?.version !== protocolVersion) return;
      // A capture acknowledgement must still remove the one raw CSS grant if
      // native has just disabled the lab; it never resumes playback while off.
      if (message.type === FRAME_CAPTURED_MESSAGE) {
        handleFrameCaptured(message);
      } else if (message.type === FRAME_RESULT_MESSAGE) {
        handleFrameResult(message);
      } else if (enabled && message.type === COVER_ARMED_MESSAGE) {
        void armCoveredVideo(message);
      }
    },
  });
})();
