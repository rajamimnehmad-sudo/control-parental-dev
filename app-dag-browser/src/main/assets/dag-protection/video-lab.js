"use strict";

(() => {
  if (globalThis.__gloshDagVideoLab !== undefined) return;

  const CONFIG_MESSAGE = "video-lab-config";
  const STATUS_MESSAGE = "video-lab-status";
  const DIAGNOSTIC_MESSAGE = "video-lab-diagnostic";
  const COVER_REQUEST_MESSAGE = "video-lab-cover-request";
  const COVER_ARMED_MESSAGE = "video-lab-cover-armed";
  const FRAME_REQUEST_MESSAGE = "video-lab-frame-request";
  const FRAME_CAPTURED_MESSAGE = "video-lab-frame-captured";
  const FRAME_CONCEALED_MESSAGE = "video-lab-frame-concealed";
  const FRAME_RESULT_MESSAGE = "video-lab-frame-result";
  const SMOOTH_START_MESSAGE = "video-lab-smooth-start";
  const RETIRE_MESSAGE = "video-lab-retire";
  const REVEAL_MESSAGE = "video-lab-reveal-style";
  const CONCEAL_MESSAGE = "video-lab-conceal-style";
  const TOKEN_ATTRIBUTE = "data-glosh-dag-video-lab-token";
  const PRESENTATION_GUARD_ATTRIBUTE = "data-glosh-dag-presentation-guard";
  const PRESENTATION_GUARD_VERSION = "1";
  // The diagnostic transport is intentionally finite even though it replays a
  // sequence. There is one raw compositor grant, capture and decision at once.
  const INITIAL_COVERED_CAPTURE_COUNT = 2;
  const MAX_CAPTURE_COUNT = 7_200;
  const CAPTURE_DELAY_MS = 0;
  const SMOOTH_CAPTURE_DELAY_MS = 500;
  const STATUS_RETRY_MS = 50;
  const MAX_STATUS_RETRIES = 20;
  const COVER_TIMEOUT_MS = 2_500;
  const FRAME_READY_TIMEOUT_MS = 2_500;
  const FRAME_RESULT_TIMEOUT_MS = 2_500;
  const VIEWPORT_SETTLE_MS = 150;
  const MAX_COVERED_VIEWPORT_TRANSITION_MS = 1_000;
  const MAX_COVERED_VIEWPORT_TRANSITIONS = 8;
  const INTERNAL_FIXTURE_ATTRIBUTE = "data-glosh-dag-video-lab-fixture";
  const diagnosticLabels = globalThis.__gloshDagVideoLabDiagnostics;
  const geometry = globalThis.__gloshDagVideoLabGeometry;
  const presentation = globalThis.__gloshDagVideoLabPresentation;
  const recordState = globalThis.__gloshDagVideoLabRecord;
  const mutationPolicy = globalThis.__gloshDagVideoLabMutations;
  const bootstrapRuntime = globalThis.__gloshDagVideoLabBootstrap;
  const isolationRuntime = globalThis.__gloshDagVideoLabIsolation;
  if (
    diagnosticLabels === undefined ||
    geometry === undefined ||
    presentation === undefined ||
    recordState === undefined ||
    mutationPolicy === undefined ||
    bootstrapRuntime === undefined ||
    isolationRuntime === undefined
  ) return;
  const {
    hasBackingMedia,
    sameVideoRect,
    sameViewportBounds,
    sameViewportSignature,
    sourceIdentity,
    sourceSignature,
  } = geometry;
  const PRESENTATION_CAPABILITY_ATTRIBUTES = presentation.CAPABILITY_ATTRIBUTES;
  const rectPayload = (video) => geometry.rectPayload(video, innerWidth, innerHeight);
  const viewportSignature = (video) =>
    geometry.viewportSignature(video, innerWidth, innerHeight, globalThis.visualViewport);
  const visibleArea = (video) => geometry.visibleArea(video, innerWidth, innerHeight);

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
  let diagnosticsEnabled = false;
  let fixtureEnabled = false;
  let lastDiagnosticStage = "";
  let unsafePresentationBlocked = false;
  let lastViewportChangeAt = performance.now();
  const diagnosticStartedAt = performance.now();
  const timelineStages = new Set();
  const records = new WeakMap();
  const backingSnapshots = new WeakMap();

  const randomToken = (wordCount) => {
    const words = crypto.getRandomValues(new Uint32Array(wordCount));
    return Array.from(words, (word) => word.toString(16).padStart(8, "0")).join("");
  };

  const frameIdentity = (record) => ({
    frameSequence: record.frameSequence,
    viewportEpoch: record.frameViewportEpoch,
  });

  const grantIdentity = (record) => ({
    videoId: record.videoId,
    revision: record.revision,
    ...frameIdentity(record),
    token: record.revealToken,
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

  // Diagnostic builds report only a finite state label. No URL, DOM, media
  // identifier or frame data leaves the content script through this path.
  const postDiagnostic = (stage) => {
    if (!diagnosticsEnabled || postToAndroid === null || stage === lastDiagnosticStage) return;
    lastDiagnosticStage = stage;
    try {
      postToAndroid({
        type: DIAGNOSTIC_MESSAGE,
        stage,
        elapsedMillis: Math.min(120_000, Math.max(0, Math.round(performance.now() - diagnosticStartedAt))),
      });
    } catch {}
  };

  const postTimeline = (stage) => {
    if (timelineStages.has(stage)) return;
    timelineStages.add(stage);
    postDiagnostic(stage);
  };

  const postDiagnosticLabels = (labels) => {
    if (!diagnosticsEnabled) return;
    labels.forEach(postDiagnostic);
  };

  const postPlayAttemptDiagnostics = (record) => {
    postDiagnosticLabels(
      diagnosticLabels.playAttempt(
        record.video,
        record.playGeneration,
        record.sourceSignature === sourceSignature(record.video),
      ),
    );
  };

  const postFrameRecord = (type, record, extra = {}) =>
    postRecord(type, record, {
      ...grantIdentity(record),
      ...extra,
    });

  const isolation = isolationRuntime.create({
    MediaElement: HTMLMediaElement,
    VideoElement: globalThis.HTMLVideoElement ?? null,
    activeRecord: () => activeRecord,
    closingRecord: () => closingRecord,
    enabled: () => enabled,
    hasBackingMedia,
    isolationLocked: () => isolationLocked,
    postTimeline,
  });
  const enforceMuted = isolation.enforceMuted;
  const originalAudioState = isolation.originalAudioState;
  const safePause = isolation.safePause;

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
  const enforcePresentationCapabilities = (record) =>
    presentation.enforceCapabilities(record, rememberExpectedPresentationMutation);

  // A raw compositor grant is scoped to the selected video only.  Audio and
  // every other video stay both silent and paused for the entire diagnostic
  // session; a document cannot race a newly inserted element past this guard.
  const isAuthorizedRawPlayback = isolation.isAuthorizedRawPlayback;
  const silenceAndPauseMedia = isolation.silenceAndPauseMedia;
  const mediaIsolationActive = isolation.active;
  const enforceMediaIsolation = () => isolation.enforce(document);
  const stopUnauthorizedPlayback = isolation.stopUnauthorizedPlayback;

  const clearRecordTimers = recordState.clearTimers;
  const resetFrameState = recordState.resetFrame;

  const recordFor = (video) => {
    const existing = records.get(video);
    if (existing !== undefined) return existing;
    const audioState = originalAudioState(video);
    const record = recordState.create(
      video,
      audioState,
      randomToken,
      globalThis.__gloshDagVideoBootstrapState.create(),
    );
    const keepMuted = () => enforceMuted(record);
    const closeUnsafePresentation = () => retireUnsafePresentation(record);
    const reportPlaybackEvent = (event) => {
      if (!diagnosticsEnabled || record !== activeRecord) return;
      const type = event?.type;
      if (["play", "playing", "pause", "abort", "emptied", "waiting", "stalled"].includes(type)) {
        postDiagnostic(`play_event_${type}`);
      }
    };
    const reportBackingEvent = (event) => {
      if (record !== activeRecord) return;
      const type = event?.type;
      if (["loadstart", "durationchange", "loadedmetadata", "canplay"].includes(type)) {
        postDiagnostic(`backing_event_${type}`);
        postDiagnosticLabels(diagnosticLabels.backing(record.video));
        postDiagnostic(diagnosticLabels.readyState(record.video).replace("play_ready_", "backing_ready_"));
        postDiagnostic(diagnosticLabels.networkState(record.video).replace("play_network_", "backing_network_"));
        if (type === "loadstart") {
          if (record.bootstrapLoadStarted || record.bootstrapBackingGeneration !== 0) {
            postDiagnostic("bootstrap_generation_repeated");
            void retireRecord(record, "bootstrap_generation_repeated");
            return;
          }
          const signature = sourceSignature(record.video);
          const validLoad =
            record.captures !== 0 ||
            record.frameCaptured ||
            record.rawFrameOpen ||
            (!record.coverPending && !record.coverAcknowledged) ||
            signature !== record.sourceSignature;
          const loadPhase = record.bootstrapState.loadStart(!validLoad);
          if (loadPhase === "terminal") {
            void retireRecord(record, "bootstrap_source_changed");
            return;
          }
          record.bootstrapLoadStarted = true;
          record.bootstrapLoadSourceSignature = signature;
          postDiagnostic("bootstrap_load_started");
          if (record.coverAcknowledged) armBootstrapGeneration(record);
        } else if (type === "canplay" && record.bootstrapState.phase() === "stable") {
          if (record.bootstrapState.mediaReady(record.sourceSignature === sourceSignature(record.video)) !== "stable") {
            void retireRecord(record, "bootstrap_revalidation_failed");
          }
        }
      }
    };
    const remote = video.remote;
    const closeTimelineDiscontinuity = () => {
      if (record !== activeRecord || record.retiring) return;
      void retireRecord(record, "seek_requested");
    };
    video.addEventListener("play", keepMuted);
    for (const type of ["play", "playing", "pause", "abort", "emptied", "waiting", "stalled"]) {
      video.addEventListener(type, reportPlaybackEvent);
    }
    for (const type of ["loadstart", "durationchange", "loadedmetadata", "canplay"]) {
      video.addEventListener(type, reportBackingEvent);
    }
    video.addEventListener("volumechange", keepMuted);
    video.addEventListener("seeking", closeTimelineDiscontinuity);
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

  const presentationCapabilityFailure = presentation.capabilityFailure;

  const presentationGuardReady = () =>
    presentation.guardReady(document, PRESENTATION_GUARD_ATTRIBUTE, PRESENTATION_GUARD_VERSION);

  const bootstrapTransitions = bootstrapRuntime.create({
    activeRecord: () => activeRecord,
    capabilityFailure: presentationCapabilityFailure,
    hasDocumentToken: () => documentToken !== "",
    now: () => performance.now(),
    sameViewportBounds,
    sameViewportSignature,
    sourceSignature,
    unsafeActive: (record) => unsafePresentationActive(record),
  });

  const reportBackingTransition = (video) => {
    const next = {
      currentSrc: Boolean(video.currentSrc),
      sourceAttribute: (video.getAttribute("src") || "") !== "",
      sourceObject: video.srcObject != null,
      sourceChildren: [...video.querySelectorAll("source")].some((source) =>
        (source.getAttribute("src") || "") !== ""),
    };
    const previous = backingSnapshots.get(video);
    backingSnapshots.set(video, next);
    if (previous === undefined) {
      postTimeline(hasBackingMedia(video)
        ? "timeline_video_seen_backing"
        : "timeline_video_seen_no_backing");
      return;
    }
    if (!previous.currentSrc && next.currentSrc) postTimeline("timeline_current_src_assigned");
    if (!previous.sourceAttribute && next.sourceAttribute) postTimeline("timeline_src_attribute_assigned");
    if (!previous.sourceObject && next.sourceObject) postTimeline("timeline_src_object_assigned");
    if (!previous.sourceChildren && next.sourceChildren) postTimeline("timeline_source_child_assigned");
  };

  const armBootstrapGeneration = (record) => {
    if (!bootstrapTransitions.armGeneration(record)) {
      void retireRecord(record, "bootstrap_revalidation_failed");
      return;
    }
    postDiagnostic("bootstrap_generation_started");
  };

  const beginBootstrapViewportTransition = (record, nextSignature) => {
    const phase = bootstrapTransitions.beginViewportTransition(record, nextSignature);
    if (phase === null) return false;
    postDiagnostic(phase === "transition" ? "bootstrap_transition_covered" : "post_frame_transition_covered");
    return true;
  };

  const completeBootstrapViewportTransition = (record, settledSignature) => {
    if (!bootstrapTransitions.completeViewportTransition(record, settledSignature)) return false;
    postDiagnostic("viewport_transition_stable");
    return true;
  };

  const postViewportChangeDiagnostics = (before, after) => {
    if (!diagnosticsEnabled || before === null || after === null) return;
    const groups = [
      ["viewport_change_window", 0, 2],
      ["viewport_change_visual", 2, 7],
      ["viewport_change_video_rect", 7, 11],
    ];
    for (const [stage, start, end] of groups) {
      if (before.slice(start, end).some((value, index) => value !== after[start + index])) {
        postDiagnostic(stage);
      }
    }
    const details = [
      ["viewport_window_width", 0],
      ["viewport_window_height", 1],
      ["viewport_visual_width", 2],
      ["viewport_visual_height", 3],
      ["viewport_visual_offset_left", 4],
      ["viewport_visual_offset_top", 5],
      ["viewport_visual_scale", 6],
      ["viewport_video_left", 7],
      ["viewport_video_top", 8],
      ["viewport_video_width", 9],
      ["viewport_video_height", 10],
    ];
    for (const [stage, index] of details) {
      if (before[index] !== after[index]) postDiagnostic(stage);
    }
  };

  const unsafePresentationReason = (record) =>
    presentation.unsafeReason(record, document, presentationGuardReady());

  const unsafePresentationActive = (record) => unsafePresentationReason(record) !== null;

  const retireUnsafePresentation = (record) => {
    if (record === null || record !== activeRecord) return;
    const reason = unsafePresentationReason(record);
    if (reason === null) return;
    unsafePresentationBlocked = true;
    postDiagnostic(`unsafe_${reason}`);
    void retireRecord(record, "unsafe_presentation");
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
      ...(record.smoothGrantIdentity ?? grantIdentity(record)),
    }).then((result) => result?.removed === true).catch(() => false).then((removed) => {
      postDiagnostic(removed ? "conceal_removed" : "conceal_failed");
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
    record.smoothActive = false;
    record.covered = false;
    record.coverPending = false;
    record.coverAcknowledged = false;
    record.bootstrapState.terminate();
    record.bootstrapLoadStarted = false;
    record.bootstrapLoadSourceSignature = null;
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
    record.coverAcknowledged = false;
    record.rawFrameOpen = false;
    record.smoothActive = false;
    record.smoothGrantIdentity = null;
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
    record.sourceIdentity = sourceIdentity(record.video);
    record.revealToken = randomToken(4);
    record.coverRequestedAt = null;
    record.coverMillis = null;
    record.decodeStartedAt = null;
    record.viewportSignature = viewportSignature(record.video);
    record.viewportTransitionStartedAt = null;
    record.viewportTransitionCount = 0;
    record.pendingViewportSignature = null;
    record.bootstrapBackingGeneration = 0;
    record.bootstrapLoadStarted = false;
    record.bootstrapLoadSourceSignature = null;
    record.bootstrapTransitionUsed = false;
    record.bootstrapSourceSignature = null;
    record.bootstrapState.reset();
    record.playGeneration = 0;
  };

  const requestCover = (record) => {
    if (
      !enabled ||
      record !== activeRecord ||
      record.retiring ||
      closingRecord !== null ||
      unsafePresentationActive(record)
    ) {
      retireUnsafePresentation(record);
      return;
    }
    if (record.sourceSignature !== sourceSignature(record.video)) {
      postDiagnostic("source_changed");
      void retireRecord(record, "source_changed");
      return;
    }
    if (record.covered || record.coverPending) return;
    const coverRequestedAt = performance.now();
    record.coverAcknowledged = false;
    record.bootstrapLoadStarted = false;
    record.bootstrapLoadSourceSignature = null;
    record.coverPending = postRecord(COVER_REQUEST_MESSAGE, record, {
      readyState: record.video.readyState,
      durationFinite: Number.isFinite(record.video.duration),
      viewportEpoch: record.viewportEpoch,
    });
    if (record.coverPending) {
      postDiagnostic("cover_posted");
      record.coverRequestedAt = coverRequestedAt;
      record.coverTimer = setTimeout(() => {
        record.coverTimer = null;
        void retireRecord(record, "cover_timeout");
      }, COVER_TIMEOUT_MS);
    } else {
      postDiagnostic("cover_post_rejected");
    }
  };

  const selectVisibleVideo = () => {
    scanScheduled = false;
    if (!enabled || unsafePresentationBlocked || window.top !== window || closingRecord !== null) return;
    enforceMediaIsolation();
    const candidate = [...document.querySelectorAll("video")]
      .map((video) => {
        reportBackingTransition(video);
        return { video, area: visibleArea(video) };
      })
      .filter(({ video, area }) => area > 0 && hasBackingMedia(video))
      .sort((left, right) => right.area - left.area)[0]?.video || null;
    if (candidate === activeRecord?.video) {
      if (activeRecord !== null) requestCover(activeRecord);
      return;
    }
    if (activeRecord !== null) {
      void retireRecord(activeRecord, "authority_changed");
      return;
    }
    if (candidate === null) {
      postDiagnostic("scan_no_candidate");
      return;
    }
    const record = recordFor(candidate);
    resetForAuthority(record);
    activeRecord = record;
    postTimeline("timeline_candidate_selected");
    postDiagnostic("candidate_selected");
    enforcePresentationCapabilities(record);
    enforceMediaIsolation();
    requestCover(record);
  };

  const scheduleScan = () => {
    if (scanScheduled || unsafePresentationBlocked) return;
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
      // The native side closes the bounded laboratory at this point. Stop
      // local reselection synchronously so its disable message cannot race a
      // new candidate into coverPending after the exact durable close.
      enabled = false;
      void retireRecord(record, "capture_limit");
      return;
    }
    if (!record.smoothActive && record.captures >= INITIAL_COVERED_CAPTURE_COUNT) {
      void startSmoothPlayback(record);
      return;
    }
    scheduleNextCapture(record);
  };

  const scheduleNextCapture = (record) => {
    record.nextCaptureTimer = setTimeout(() => {
      record.nextCaptureTimer = null;
      requestFrameWhenReady(record);
    }, record.smoothActive ? SMOOTH_CAPTURE_DELAY_MS : CAPTURE_DELAY_MS);
  };

  const startSmoothPlayback = async (record) => {
    if (
      record !== activeRecord || !record.covered || record.rawFrameOpen ||
      record.retiring || record.terminal || !record.video.isConnected
    ) return;
    const reveal = await browser.runtime.sendMessage({
      type: REVEAL_MESSAGE,
      version: protocolVersion,
      documentToken,
      token: record.revealToken,
      ...grantIdentity(record),
    }).catch(() => null);
    if (reveal?.inserted !== true || record !== activeRecord || !record.covered) {
      void retireRecord(record, "smooth_reveal_denied");
      return;
    }
    record.rawFrameOpen = true;
    record.smoothActive = true;
    record.smoothGrantIdentity = Object.freeze(grantIdentity(record));
    record.video.muted = true;
    record.video.defaultMuted = true;
    record.video.volume = 0;
    record.video.setAttribute(TOKEN_ATTRIBUTE, record.revealToken);
    enforcePresentationCapabilities(record);
    const style = getComputedStyle(record.video);
    const opacity = Number.parseFloat(style.opacity);
    if (
      style.visibility !== "visible" ||
      style.display === "none" ||
      !Number.isFinite(opacity) ||
      opacity <= 0 ||
      visibleArea(record.video) <= 0
    ) {
      postDiagnostic("smooth_visibility_rejected");
      void retireRecord(record, "smooth_visibility_rejected");
      return;
    }
    postDiagnostic("smooth_visibility_ready");
    try {
      await record.video.play();
    } catch {
      void retireRecord(record, "smooth_play_rejected");
      return;
    }
    if (record !== activeRecord || !record.smoothActive || unsafePresentationActive(record)) {
      void retireRecord(record, "smooth_start_invalidated");
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
      postDiagnostic("smooth_audio_restore_failed");
      void retireRecord(record, "smooth_audio_restore_failed");
      return;
    }
    postDiagnostic("smooth_audio_restored");
    if (!postRecord(SMOOTH_START_MESSAGE, record, { cadenceMillis: SMOOTH_CAPTURE_DELAY_MS })) {
      void retireRecord(record, "smooth_start_rejected");
      return;
    }
    postDiagnostic("smooth_playback_started");
    scheduleNextCapture(record);
  };

  const beginSmoothFrame = (record) => {
    record.frameSequence += 1;
    record.frameViewportEpoch = record.viewportEpoch;
    record.framePending = true;
    record.frameCaptured = false;
    record.frameConcealed = false;
    record.frameAllowed = null;
    record.decodeStartedAt = performance.now();
    requestVideoFrame(record);
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
        retireUnsafePresentation(record);
      }
      return;
    }
    if (record.viewportTransitionStartedAt !== null) return;
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
          retireUnsafePresentation(record);
        }
        return;
      }
      clearTimeout(record.readinessTimer);
      record.readinessTimer = null;
      if (!record.smoothActive) safePause(record.video);
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
      retireUnsafePresentation(record);
      return;
    }
    enforcePresentationCapabilities(record);
    if (unsafePresentationActive(record)) {
      retireUnsafePresentation(record);
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
      ...grantIdentity(record),
    }).catch(() => null);
    if (reveal?.inserted !== true) {
      const reason = typeof reveal?.reason === "string" && /^[a-z_]{1,40}$/u.test(reveal.reason)
        ? reveal.reason
        : "unknown";
      postDiagnostic(`reveal_denied_${reason}`);
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
      retireUnsafePresentation(record);
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
      retireUnsafePresentation(record);
      return;
    }
    record.playGeneration += 1;
    postPlayAttemptDiagnostics(record);
    try {
      await record.video.play();
      postDiagnostic("play_promise_resolved");
    } catch (error) {
      postDiagnostic(
        record.sourceSignature === sourceSignature(record.video)
          ? "play_reject_source_stable"
          : "play_reject_source_changed",
      );
      postDiagnostic(record.video.paused ? "play_reject_paused" : "play_reject_playing");
      postDiagnostic(record.video.ended ? "play_reject_ended" : "play_reject_not_ended");
      postDiagnostic(diagnosticLabels.readyState(record.video).replace("play_ready_", "play_reject_ready_"));
      postDiagnostic(diagnosticLabels.networkState(record.video).replace("play_network_", "play_reject_network_"));
      if (record === activeRecord && record.viewportTransitionStartedAt !== null) {
        postDiagnostic("play_aborted_for_viewport");
        return;
      }
      postDiagnostic(diagnosticLabels.playError(error));
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
        retireUnsafePresentation(record);
        return;
      }
      const viewportWait = VIEWPORT_SETTLE_MS - (performance.now() - lastViewportChangeAt);
      if (viewportWait > 0) {
        record.readinessTimer = setTimeout(request, viewportWait);
        return;
      }
      const settledSignature = viewportSignature(record.video);
      if (!sameViewportSignature(record.viewportSignature, settledSignature)) {
        if (record.pendingViewportSignature !== null) {
          if (!completeBootstrapViewportTransition(record, settledSignature)) {
            postDiagnostic("viewport_settle_mismatch");
            record.viewportEpoch += 1;
            void retireRecord(record, "viewport_changed");
            return;
          }
        } else if (beginBootstrapViewportTransition(record, settledSignature)) {
          record.readinessTimer = setTimeout(request, VIEWPORT_SETTLE_MS);
          return;
        } else {
          postViewportChangeDiagnostics(record.viewportSignature, settledSignature);
          postDiagnostic("viewport_settle_mismatch");
          record.viewportEpoch += 1;
          void retireRecord(record, "viewport_changed");
          return;
        }
      }
      if (record.viewportTransitionStartedAt !== null) {
        record.viewportTransitionStartedAt = null;
        record.viewportTransitionCount = 0;
        postDiagnostic("viewport_transition_stable");
      }
      record.readinessTimer = null;
      if (record.smoothActive) beginSmoothFrame(record);
      else void revealAndRequestFrame(record);
    };
    request();
  };

  const armCoveredVideo = async (message) => {
    postDiagnostic("cover_arm_entered");
    const record = activeRecord;
    if (!recordMatchesMessage(record, message) || !record.coverPending) return;
    postDiagnostic("cover_ack_received");
    const ackPhase = record.bootstrapState.acknowledge(true);
    if (ackPhase === "terminal") {
      void retireRecord(record, "bootstrap_revalidation_failed");
      return;
    }
    record.coverAcknowledged = true;
    if (record.bootstrapLoadStarted) armBootstrapGeneration(record);
    if (record !== activeRecord || record.retiring) return;
    postDiagnostic("background_wait_started");
    if (!await backgroundReady()) {
      postDiagnostic("background_wait_failed");
      if (record === activeRecord) record.coverAcknowledged = false;
      return;
    }
    postDiagnostic("background_wait_completed");
    if (
      !recordMatchesMessage(record, message) ||
      !record.coverPending ||
      !record.coverAcknowledged
    ) {
      record.coverAcknowledged = false;
      return;
    }
    clearTimeout(record.coverTimer);
    record.coverTimer = null;
    record.coverMillis = record.coverRequestedAt === null
      ? null
      : Math.max(0, performance.now() - record.coverRequestedAt);
    record.coverPending = false;
    record.covered = true;
    if (
      record.bootstrapState.phase() === "acknowledged" &&
      record.bootstrapState.coverReady(
        !record.bootstrapLoadStarted && record.sourceSignature === sourceSignature(record.video),
      ) !== "stable"
    ) {
      void retireRecord(record, "bootstrap_revalidation_failed");
      return;
    }
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
    if (record.smoothActive) {
      record.frameConcealed = true;
      finishFrameIfReady(record);
      return;
    }
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
      if (record.smoothActive) record.frameConcealed = false;
      if (!record.frameConcealed) {
        void concealRecord(record).then((concealed) => {
          if (!frameMatchesMessage(record, message)) return;
          if (!concealed) {
            void retireRecord(record, "terminal_frame_conceal_failed");
            return;
          }
          record.smoothActive = false;
          record.frameConcealed = true;
          finishFrameIfReady(record);
        });
      }
    }
    finishFrameIfReady(record);
  };

  const invalidateForViewport = (event) => {
    lastViewportChangeAt = performance.now();
    postTimeline("timeline_reflow_observed");
    if (activeRecord !== null) {
      const nextSignature = viewportSignature(activeRecord.video);
      if (fixtureEnabled && event?.type === "resize" && sameVideoRect(activeRecord.viewportSignature, nextSignature)) {
        activeRecord.viewportSignature = nextSignature;
        postDiagnostic("fixture_viewport_transition");
        return;
      }
      if (event?.type === "resize" && sameViewportSignature(activeRecord.viewportSignature, nextSignature)) {
        postDiagnostic("viewport_resize_unchanged");
        return;
      }
      postViewportChangeDiagnostics(activeRecord.viewportSignature, nextSignature);
      postDiagnostic(event?.type === "scroll" ? "viewport_scroll" : "viewport_resize");
      const coveredBrowserTransition =
        event?.type === "resize" &&
        activeRecord.covered &&
        activeRecord.resultTimer === null &&
        !activeRecord.frameCaptured &&
        sameVideoRect(activeRecord.viewportSignature, nextSignature);
      const coveredBootstrapTransition =
        event?.type === "resize" &&
        (activeRecord.covered || activeRecord.coverAcknowledged) &&
        beginBootstrapViewportTransition(activeRecord, nextSignature);
      if (coveredBrowserTransition || coveredBootstrapTransition) {
        const now = performance.now();
        activeRecord.viewportTransitionStartedAt ??= now;
        if (!coveredBootstrapTransition) activeRecord.viewportTransitionCount += 1;
        const withinBound =
          now - activeRecord.viewportTransitionStartedAt <= MAX_COVERED_VIEWPORT_TRANSITION_MS &&
          activeRecord.viewportTransitionCount <= MAX_COVERED_VIEWPORT_TRANSITIONS;
        if (withinBound) {
          activeRecord.pendingViewportSignature = nextSignature;
          postDiagnostic("viewport_transition_covered");
          clearTimeout(activeRecord.readinessTimer);
          activeRecord.readinessTimer = setTimeout(() => {
            const record = activeRecord;
            if (record === null || record.viewportTransitionStartedAt === null) return;
            record.readinessTimer = null;
            const settledSignature = viewportSignature(record.video);
            const transitionStable =
              sameViewportSignature(record.pendingViewportSignature, settledSignature) &&
              (sameVideoRect(record.viewportSignature, settledSignature) ||
                (record.bootstrapTransitionUsed && sameViewportBounds(record.viewportSignature, settledSignature))) &&
              performance.now() - lastViewportChangeAt >= VIEWPORT_SETTLE_MS;
            if (!transitionStable) {
              postDiagnostic("viewport_settle_mismatch");
              record.viewportEpoch += 1;
              void retireRecord(record, "viewport_changed");
              return;
            }
            if (
              record.bootstrapTransitionUsed &&
              !completeBootstrapViewportTransition(record, settledSignature)
            ) {
              void retireRecord(record, "bootstrap_revalidation_failed");
              return;
            }
            const reopen = () => {
              if (record !== activeRecord || record.retiring || record.terminal) return;
              if (
                record.sourceSignature !== sourceSignature(record.video) ||
                (record.bootstrapBackingGeneration !== 0 &&
                  record.bootstrapSourceSignature !== record.sourceSignature) ||
                presentationCapabilityFailure(record.video) !== null ||
                unsafePresentationActive(record) ||
                !record.video.isConnected ||
                documentToken === ""
              ) {
                void retireRecord(record, "bootstrap_revalidation_failed");
                return;
              }
              resetFrameState(record);
              record.viewportEpoch += 1;
              record.viewportSignature = settledSignature;
              record.pendingViewportSignature = null;
              record.viewportTransitionStartedAt = null;
              record.viewportTransitionCount = 0;
              postDiagnostic("viewport_transition_stable");
              requestFrameWhenReady(record);
            };
            if (!record.rawFrameOpen) {
              reopen();
              return;
            }
            safePause(record.video);
            if (
              record.frameCallbackId !== null &&
              typeof record.video.cancelVideoFrameCallback === "function"
            ) {
              try {
                record.video.cancelVideoFrameCallback(record.frameCallbackId);
              } catch {}
              record.frameCallbackId = null;
            }
            void concealRecord(record).then((concealed) => {
              if (!concealed) {
                void retireRecord(record, "viewport_conceal_failed");
                return;
              }
              reopen();
            });
          }, VIEWPORT_SETTLE_MS);
          return;
        }
        postDiagnostic("viewport_transition_unstable");
      }
      activeRecord.viewportEpoch += 1;
      void retireRecord(activeRecord, "viewport_changed");
      return;
    }
    scheduleScan();
  };

  const mutationRequiresTerminalClose = (mutation, record) =>
    mutationPolicy.requiresTerminalClose(
      mutation,
      record,
      HTMLSourceElement,
      PRESENTATION_CAPABILITY_ATTRIBUTES,
    );

  const postMutationDiagnostics = (mutation, record) => {
    if (!diagnosticsEnabled) return;
    mutationPolicy.diagnosticStages(
      mutation,
      record,
      HTMLSourceElement,
      PRESENTATION_CAPABILITY_ATTRIBUTES,
      sourceIdentity,
    ).forEach(postDiagnostic);
  };

  const mutationObserver = new MutationObserver((recordsList) => {
    enforceMediaIsolation();
    if (recordsList.some((mutation) =>
      (mutation.type === "attributes" && mutation.attributeName?.toLowerCase() === "src") ||
      mutation.type === "childList")) {
      postTimeline("timeline_source_mutation");
    }
    const record = activeRecord;
    const terminalMutation = record === null
      ? null
      : recordsList.find((mutation) => mutationRequiresTerminalClose(mutation, record)) ?? null;
    if (record !== null && terminalMutation !== null) {
      postMutationDiagnostics(terminalMutation, record);
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
          "playsinline",
        ],
        attributeOldValue: true,
        childList: true,
        subtree: true,
      });
      document.addEventListener("play", stopUnauthorizedPlayback, true);
      document.addEventListener("volumechange", stopUnauthorizedPlayback, true);
      for (const type of ["loadstart", "durationchange", "loadedmetadata", "canplay"]) {
        document.addEventListener(type, scheduleScan, true);
        document.addEventListener(type, (event) => {
          postTimeline(`timeline_event_${type}`);
          if (event.target instanceof HTMLVideoElement) reportBackingTransition(event.target);
        }, true);
      }
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
        diagnosticsEnabled = pendingConfiguration.diagnostics;
        fixtureEnabled = pendingConfiguration.fixture;
        if (fixtureEnabled) globalThis.__gloshDagInstallVideoFixture?.();
        enabled = pendingConfiguration.enabled;
        pendingConfiguration = null;
      }
      enforceMediaIsolation();
      scheduleScan();
    },
    onNativeMessage(message) {
      if (message?.type === CONFIG_MESSAGE) {
        const configuration = {
          version: message.version,
          diagnostics: message.diagnostics === true,
          enabled: message.enabled === true,
          fixture: message.fixture === true,
        };
        if (!installed) {
          pendingConfiguration = configuration;
          return;
        }
        if (configuration.version !== protocolVersion) return;
        diagnosticsEnabled = configuration.diagnostics;
        fixtureEnabled = configuration.fixture;
        if (fixtureEnabled) globalThis.__gloshDagInstallVideoFixture?.();
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
          unsafePresentationBlocked = false;
          lastDiagnosticStage = "";
          postDiagnostic("config_enabled");
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
        postDiagnostic("cover_message_received");
        void armCoveredVideo(message);
      }
    },
  });
})();
