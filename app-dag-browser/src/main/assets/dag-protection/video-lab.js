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
  const lifecycleRuntime = globalThis.__gloshDagVideoLabLifecycle;
  const playbackRuntime = globalThis.__gloshDagVideoLabPlayback;
  const captureRuntime = globalThis.__gloshDagVideoLabCapture;
  const viewportRuntime = globalThis.__gloshDagVideoLabViewport;
  if (
    diagnosticLabels === undefined ||
    geometry === undefined ||
    presentation === undefined ||
    recordState === undefined ||
    mutationPolicy === undefined ||
    bootstrapRuntime === undefined ||
    isolationRuntime === undefined ||
    lifecycleRuntime === undefined ||
    playbackRuntime === undefined ||
    captureRuntime === undefined ||
    viewportRuntime === undefined
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
    if (!diagnosticsEnabled) return;
    diagnosticLabels.viewportChange(before, after).forEach(postDiagnostic);
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

  const lifecycleState = {
    get activeRecord() { return activeRecord; },
    set activeRecord(value) { activeRecord = value; },
    get closingRecord() { return closingRecord; },
    set closingRecord(value) { closingRecord = value; },
    get enabled() { return enabled; },
    set enabled(value) { enabled = value; },
    get isolationLocked() { return isolationLocked; },
    set isolationLocked(value) { isolationLocked = value; },
    get isolationLockedRecord() { return isolationLockedRecord; },
    set isolationLockedRecord(value) { isolationLockedRecord = value; },
    get isolationRetryPromise() { return isolationRetryPromise; },
    set isolationRetryPromise(value) { isolationRetryPromise = value; },
  };
  const lifecycle = lifecycleRuntime.create({
    browser,
    clearRecordTimers,
    concealMessage: CONCEAL_MESSAGE,
    documentToken: () => documentToken,
    enforceMediaIsolation,
    grantIdentity,
    postDiagnostic,
    postToAndroid: () => postToAndroid,
    protocolVersion: () => protocolVersion,
    resetFrameState,
    retireMessage: RETIRE_MESSAGE,
    safePause,
    scheduleScan: () => scheduleScan(),
    state: lifecycleState,
    tokenAttribute: TOKEN_ATTRIBUTE,
  });
  const concealRecord = lifecycle.concealRecord;
  const retireRecord = lifecycle.retireRecord;
  const retryTerminalIsolation = lifecycle.retryTerminalIsolation;

  const resetForAuthority = (record) => {
    recordState.resetAuthority(record, {
      revealToken: randomToken(4),
      sourceIdentity: sourceIdentity(record.video),
      sourceSignature: sourceSignature(record.video),
      viewportSignature: viewportSignature(record.video),
    });
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

  const playback = playbackRuntime.create({
    browser,
    captureDelayMillis: CAPTURE_DELAY_MS,
    documentToken: () => documentToken,
    enforcePresentationCapabilities,
    frameRequestMessage: FRAME_REQUEST_MESSAGE,
    frameResultTimeoutMillis: FRAME_RESULT_TIMEOUT_MS,
    getComputedStyle: (element) => getComputedStyle(element),
    grantIdentity,
    initialCoveredCaptureCount: INITIAL_COVERED_CAPTURE_COUNT,
    maximumCaptureCount: MAX_CAPTURE_COUNT,
    now: () => performance.now(),
    postDiagnostic,
    postFrameRecord,
    postRecord,
    protocolVersion: () => protocolVersion,
    requestFrameWhenReady: (record) => requestFrameWhenReady(record),
    resetFrameState,
    retireRecord,
    retireUnsafePresentation,
    revealMessage: REVEAL_MESSAGE,
    safePause,
    smoothCaptureDelayMillis: SMOOTH_CAPTURE_DELAY_MS,
    smoothStartMessage: SMOOTH_START_MESSAGE,
    state: lifecycleState,
    tokenAttribute: TOKEN_ATTRIBUTE,
    unsafePresentationActive,
    visibleArea,
  });
  const beginSmoothFrame = playback.beginSmoothFrame;
  const finishFrameIfReady = playback.finishFrameIfReady;
  const requestVideoFrame = playback.requestVideoFrame;
  const scheduleNextCapture = playback.scheduleNextCapture;
  const startSmoothPlayback = playback.startSmoothPlayback;

  const capture = captureRuntime.create({
    armBootstrapGeneration,
    backgroundReady,
    beginBootstrapViewportTransition,
    beginSmoothFrame,
    browser,
    completeBootstrapViewportTransition,
    concealRecord,
    diagnosticLabels,
    document,
    documentToken: () => documentToken,
    enforceMediaIsolation,
    enforcePresentationCapabilities,
    finishFrameIfReady,
    fixtureAttribute: INTERNAL_FIXTURE_ATTRIBUTE,
    frameConcealedMessage: FRAME_CONCEALED_MESSAGE,
    frameMatchesMessage,
    frameReadyTimeoutMillis: FRAME_READY_TIMEOUT_MS,
    grantIdentity,
    lastViewportChangeAt: () => lastViewportChangeAt,
    now: () => performance.now(),
    postDiagnostic,
    postFrameRecord,
    postPlayAttemptDiagnostics,
    postViewportChangeDiagnostics,
    protocolVersion: () => protocolVersion,
    recordMatchesMessage,
    requestVideoFrame,
    retireRecord,
    retireUnsafePresentation,
    revealMessage: REVEAL_MESSAGE,
    safePause,
    sameViewportSignature,
    sourceSignature,
    state: lifecycleState,
    tokenAttribute: TOKEN_ATTRIBUTE,
    unsafePresentationActive,
    viewportSettleMillis: VIEWPORT_SETTLE_MS,
    viewportSignature,
  });
  const armCoveredVideo = capture.armCoveredVideo;
  const handleFrameCaptured = capture.handleFrameCaptured;
  const handleFrameResult = capture.handleFrameResult;
  const requestFrameWhenReady = capture.requestFrameWhenReady;

  const viewportController = viewportRuntime.create({
    beginBootstrapViewportTransition,
    completeBootstrapViewportTransition,
    concealRecord,
    fixtureEnabled: () => fixtureEnabled,
    hasDocumentToken: () => documentToken !== "",
    lastViewportChangeAt: () => lastViewportChangeAt,
    maximumTransitionMillis: MAX_COVERED_VIEWPORT_TRANSITION_MS,
    maximumTransitions: MAX_COVERED_VIEWPORT_TRANSITIONS,
    now: () => performance.now(),
    postDiagnostic,
    postTimeline,
    postViewportChangeDiagnostics,
    presentationCapabilityFailure,
    requestFrameWhenReady,
    resetFrameState,
    retireRecord,
    safePause,
    sameVideoRect,
    sameViewportBounds,
    sameViewportSignature,
    scheduleScan,
    setLastViewportChangeAt: (value) => { lastViewportChangeAt = value; },
    sourceSignature,
    state: lifecycleState,
    unsafePresentationActive,
    viewportSettleMillis: VIEWPORT_SETTLE_MS,
    viewportSignature,
  });
  const invalidateForViewport = viewportController.invalidate;

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
