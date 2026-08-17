"use strict";

(() => {
  if (globalThis.__gloshDagVideoLab !== undefined) return;
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
  const protocol = globalThis.__gloshDagVideoProtectionProtocol;
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
  const seekRuntime = globalThis.__gloshDagVideoLabSeek;
  const seekStateRuntime = globalThis.__gloshDagVideoSeekState;
  const sourceBootstrapRuntime = globalThis.__gloshDagVideoSourceBootstrap;
  const authoritySelectionRuntime = globalThis.__gloshDagVideoAuthoritySelection;
  const blockQuarantineRuntime = globalThis.__gloshDagVideoBlockQuarantine;
  const safeSkipRuntime = globalThis.__gloshDagVideoSafeSkip;
  const eventRuntime = globalThis.__gloshDagVideoLabEvents;
  const configurationRuntime = globalThis.__gloshDagVideoLabConfiguration;
  if (
    protocol === undefined ||
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
    viewportRuntime === undefined ||
    seekRuntime === undefined ||
    seekStateRuntime === undefined ||
    sourceBootstrapRuntime === undefined ||
    authoritySelectionRuntime === undefined ||
    blockQuarantineRuntime === undefined ||
    safeSkipRuntime === undefined ||
    (eventRuntime === undefined || configurationRuntime === undefined)
  ) return;
  const {
    fixtureAttribute: INTERNAL_FIXTURE_ATTRIBUTE,
    messages,
    presentationGuardAttribute: PRESENTATION_GUARD_ATTRIBUTE,
    presentationGuardVersion: PRESENTATION_GUARD_VERSION,
    tokenAttribute: TOKEN_ATTRIBUTE,
  } = protocol;
  const {
    config: CONFIG_MESSAGE,
    status: STATUS_MESSAGE,
    coverRequest: COVER_REQUEST_MESSAGE,
    coverArmed: COVER_ARMED_MESSAGE,
    frameRequest: FRAME_REQUEST_MESSAGE,
    frameCaptured: FRAME_CAPTURED_MESSAGE,
    frameConcealed: FRAME_CONCEALED_MESSAGE,
    frameResult: FRAME_RESULT_MESSAGE,
    smoothStart: SMOOTH_START_MESSAGE,
    retire: RETIRE_MESSAGE,
    reveal: REVEAL_MESSAGE,
  } = messages;
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
  let unsafePresentationBlocked = false;
  let seekController = null;
  let safeSkipController = null;
  let lastViewportChangeAt = performance.now();
  let viewportStabilityRequired = false;
  const records = new WeakMap();
  const blockQuarantine = blockQuarantineRuntime.create(sourceSignature);
  const randomToken = (wordCount) => protocol.randomToken(crypto, wordCount);
  const grantIdentity = protocol.grantIdentity;
  const recordMatchesMessage = (record, message) =>
    protocol.recordMatchesMessage(record, message, activeRecord);
  const frameMatchesMessage = (record, message) =>
    protocol.frameMatchesMessage(record, message, activeRecord);
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
  const diagnosticEmitter = diagnosticLabels.createEmitter({
    enabled: () => diagnosticsEnabled && postToAndroid !== null,
    now: () => performance.now(),
    send: (message) => postToAndroid(message),
    type: messages.diagnostic,
  });
  const postDiagnostic = diagnosticEmitter.post;
  const postTimeline = diagnosticEmitter.timeline;
  const postDiagnosticLabels = diagnosticEmitter.labels;
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

  const enforcePresentationCapabilities = (record) =>
    presentation.enforceCapabilities(record, rememberExpectedPresentationMutation);

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
        if (sourceBootstrap?.backingReady(record) === true) return;
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
    eventRuntime.bindRecord({
      video,
      keepMuted,
      reportPlaybackEvent,
      reportBackingEvent,
      onSeeking: () => {
        if (safeSkipController?.onSeeking(record) !== true) seekController?.onSeeking(record);
      },
      onSeeked: () => {
        if (safeSkipController?.onSeeked(record) !== true) seekController?.onSeeked(record);
      },
      onUnsafePresentation: closeUnsafePresentation,
      onGenerationChanged: () => blockQuarantine.noteGeneration(video),
    });
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

  const reportBackingTransition = sourceBootstrapRuntime.createBackingReporter({
    hasBackingMedia,
    postTimeline,
  });

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
    cancelSafeSkip: () => safeSkipController?.cancel(),
    cancelSourceBootstrap: (record) => sourceBootstrap?.cancel(record),
    clearRecordTimers,
    concealMessage: messages.conceal,
    documentToken: () => documentToken,
    enforceMediaIsolation,
    grantIdentity,
    postDiagnostic,
    postToAndroid: () => postToAndroid,
    protocolVersion: () => protocolVersion,
    quarantineBlockedAuthority: (record) => blockQuarantine.block(record),
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
  let sourceBootstrap = sourceBootstrapRuntime.create({
    activeRecord: () => activeRecord,
    enforceMediaIsolation,
    enforcePresentationCapabilities,
    hasBackingMedia,
    onPlayRejected: (record) => retireRecord(record, "bootstrap_play_rejected"),
    onPlayStarted: () => postDiagnostic("bootstrap_play_started"),
    onReady: () => scheduleScan(),
    onTimeout: (record) => retireRecord(record, "bootstrap_no_backing_timeout"),
    safePause,
    timeoutMillis: FRAME_READY_TIMEOUT_MS,
  });

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
      void retireRecord(
        record,
        record.sourceBootstrapCompleted && record.captures === 0 && !record.rawFrameOpen
          ? "authority_changed"
          : "source_changed",
      );
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
    authoritySelection.scan(
      [...document.querySelectorAll("video")].filter((video) => blockQuarantine.allows(video)),
    );
  };

  const scheduleScanNow = () => {
    if (scanScheduled || unsafePresentationBlocked || seekController?.holdsScan() === true) return;
    scanScheduled = true;
    requestAnimationFrame(selectVisibleVideo);
  };
  const scanGate = viewportRuntime.createScanGate({
    lastChangeAt: () => lastViewportChangeAt,
    markStable: () => { viewportStabilityRequired = false; },
    now: () => performance.now(),
    required: () => viewportStabilityRequired,
    scheduleNow: scheduleScanNow,
    setTimeout: (callback, millis) => setTimeout(callback, millis),
    settleMillis: VIEWPORT_SETTLE_MS,
  });
  const scheduleScan = scanGate.schedule;

  const authoritySelection = authoritySelectionRuntime.create({
    activeVideo: () => activeRecord?.video ?? null,
    canBootstrapCandidate: (video) => sourceBootstrap.canAttempt(video),
    clearTimeout: (timer) => clearTimeout(timer),
    hasBackingMedia,
    maximumTransitions: 8,
    now: () => performance.now(),
    onActiveCandidate: () => requestCover(activeRecord),
    onAuthorityChanged: () => retireRecord(activeRecord, "authority_changed"),
    onHandoffWaiting: () => postDiagnostic("authority_handoff_waiting"),
    onNoCandidate: () => postDiagnostic("scan_no_candidate"),
    onSelected: (video) => {
      const record = recordFor(video);
      resetForAuthority(record);
      activeRecord = record;
      postTimeline("timeline_candidate_selected");
      postDiagnostic("candidate_selected");
      enforcePresentationCapabilities(record);
      enforceMediaIsolation();
      if (hasBackingMedia(video)) requestCover(record);
      else if (!sourceBootstrap.start(record)) void retireRecord(record, "bootstrap_unavailable");
    },
    reportBackingTransition,
    scheduleScan,
    setTimeout: (callback, millis) => setTimeout(callback, millis),
    settleMillis: VIEWPORT_SETTLE_MS,
    sourceSignature,
    viewportSignature,
    visibleArea,
  });

  seekController = seekRuntime.create({
    Phase: seekStateRuntime.Phase,
    activeRecord: () => activeRecord,
    clearTimeout: (timer) => clearTimeout(timer),
    enforceMediaIsolation,
    postDiagnostic,
    retireRecord,
    scheduleScan,
    setTimeout: (callback, millis) => setTimeout(callback, millis),
    settleMillis: VIEWPORT_SETTLE_MS,
    sourceSignature,
    stateRuntime: seekStateRuntime,
    timeoutMillis: FRAME_READY_TIMEOUT_MS,
    viewportSignature,
  });

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
    hasBackingMedia,
    grantIdentity,
    lastViewportChangeAt: () => lastViewportChangeAt,
    now: () => performance.now(),
    onFrameAllowed: (record) => safeSkipController?.onFrameAllowed(record),
    onFrameBlocked: (record) => safeSkipController?.onFrameBlocked(record) === true,
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

  safeSkipController = safeSkipRuntime.create({
    activeRecord: () => activeRecord,
    clearTimeout: (timer) => clearTimeout(timer),
    endMarginSeconds: 0.25,
    maximumAttempts: 5,
    minimumAdvanceSeconds: 0.5,
    onExhausted: (record) => {
      record.terminal = true;
      void retireRecord(record, "frame_blocked");
    },
    onRecovered: (_record, skippedSeconds) => {
      postDiagnostic("safe_skip_notice");
      if (diagnosticsEnabled) postDiagnostic(skippedSeconds >= 1 ? "safe_skip_over_one_second" : "safe_skip_short");
    },
    postDiagnostic,
    requestFrameWhenReady,
    resetFrameState,
    safePause,
    setTimeout: (callback, millis) => setTimeout(callback, millis),
    settleMillis: VIEWPORT_SETTLE_MS,
    sourceSignature,
    stepSeconds: 2,
    timeToleranceSeconds: 0.25,
    timeoutMillis: FRAME_READY_TIMEOUT_MS,
    viewportSignature,
  });

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
    setLastViewportChangeAt: (value) => {
      lastViewportChangeAt = value;
      viewportStabilityRequired = true;
    },
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
      eventRuntime.installDocument({
        documentObject: document,
        windowObject: globalThis,
        VideoElement: globalThis.HTMLVideoElement ?? null,
        mutationObserver,
        stopUnauthorizedPlayback,
        scheduleScan,
        onBackingEvent: (type, video) => {
          postTimeline(`timeline_event_${type}`);
          if (type === "loadstart") blockQuarantine.noteGeneration(video);
          reportBackingTransition(video);
        },
        invalidateForViewport,
        onPageHide: () => {
          authoritySelection.cancel();
          void retireRecord(activeRecord, "document_retired");
        },
        onFullscreen: () => {
          if (document.fullscreenElement !== null) {
            void document.exitFullscreen?.().catch(() => {});
            void retireRecord(activeRecord, "fullscreen_requested");
          }
        },
        onFullscreenError: () => void retireRecord(activeRecord, "fullscreen_error"),
      });
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
        const configuration = configurationRuntime.parse(message);
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
          diagnosticEmitter.reset();
          postDiagnostic("config_enabled");
          enforceMediaIsolation();
          if (seekController?.onNativeRearm() !== true) scheduleScan();
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
