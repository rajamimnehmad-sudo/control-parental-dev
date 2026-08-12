"use strict";

(() => {
  if (globalThis.__gloshDagVideoLab !== undefined) return;

  const CONFIG_MESSAGE = "video-lab-config";
  const STATUS_MESSAGE = "video-lab-status";
  const COVER_REQUEST_MESSAGE = "video-lab-cover-request";
  const COVER_ARMED_MESSAGE = "video-lab-cover-armed";
  const FRAME_REQUEST_MESSAGE = "video-lab-frame-request";
  const FRAME_RESULT_MESSAGE = "video-lab-frame-result";
  const RETIRE_MESSAGE = "video-lab-retire";
  const REVEAL_MESSAGE = "video-lab-reveal-style";
  const CONCEAL_MESSAGE = "video-lab-conceal-style";
  const TOKEN_ATTRIBUTE = "data-glosh-dag-video-lab-token";
  const MAX_CAPTURE_COUNT = 3;
  const CAPTURE_DELAY_MS = 350;
  const STATUS_RETRY_MS = 50;
  const MAX_STATUS_RETRIES = 20;
  const COVER_TIMEOUT_MS = 2_500;
  const FRAME_READY_TIMEOUT_MS = 2_500;
  const FRAME_RESULT_TIMEOUT_MS = 2_500;
  const VIEWPORT_SETTLE_MS = 150;
  const MAX_CAPTURE_RECOVERIES = 1;
  const INTERNAL_FIXTURE_ATTRIBUTE = "data-glosh-dag-video-lab-fixture";

  let installed = false;
  let enabled = false;
  let protocolVersion = 0;
  let documentToken = "";
  let postToAndroid = null;
  let activeRecord = null;
  let scanScheduled = false;
  let pendingConfiguration = null;
  let lastViewportChangeAt = performance.now();
  const records = new WeakMap();

  const randomToken = (wordCount) => {
    const words = crypto.getRandomValues(new Uint32Array(wordCount));
    return Array.from(words, (word) => word.toString(16).padStart(8, "0")).join("");
  };

  const enforceMuted = (record) => {
    if (!enabled || record !== activeRecord) return;
    if (!record.video.muted) record.video.muted = true;
    if (!record.video.defaultMuted) record.video.defaultMuted = true;
    if (record.video.volume !== 0) record.video.volume = 0;
  };

  const recordFor = (video) => {
    const existing = records.get(video);
    if (existing !== undefined) return existing;
    const record = {
      video,
      videoId: `video_${randomToken(2)}`,
      revision: 1,
      captures: 0,
      covered: false,
      coverPending: false,
      framePending: false,
      sourceSignature: "",
      revealToken: randomToken(4),
      nextCaptureTimer: null,
      readinessTimer: null,
      resultTimer: null,
      coverTimer: null,
      recoveries: 0,
      coverRequestedAt: null,
      coverMillis: null,
      decodeStartedAt: null,
    };
    const keepMuted = () => enforceMuted(record);
    video.addEventListener("play", keepMuted);
    video.addEventListener("volumechange", keepMuted);
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

  const postRecord = (type, record, extra = {}) => {
    if (postToAndroid === null || record !== activeRecord || !record.video.isConnected) return false;
    postToAndroid({
      type,
      videoId: record.videoId,
      revision: record.revision,
      ...rectPayload(record.video),
      ...extra,
    });
    return true;
  };

  const clearRecordTimers = (record) => {
    clearTimeout(record.nextCaptureTimer);
    clearTimeout(record.readinessTimer);
    clearTimeout(record.resultTimer);
    clearTimeout(record.coverTimer);
    record.nextCaptureTimer = null;
    record.readinessTimer = null;
    record.resultTimer = null;
    record.coverTimer = null;
  };

  const concealRecord = (record) => {
    record.video.removeAttribute(TOKEN_ATTRIBUTE);
    return browser.runtime.sendMessage({
      type: CONCEAL_MESSAGE,
      version: protocolVersion,
      documentToken,
      token: record.revealToken,
    }).then((result) => result?.removed === true).catch(() => false);
  };

  const retireRecord = (record, reason) => {
    if (record === null) return;
    clearRecordTimers(record);
    record.video.pause();
    record.covered = false;
    record.coverPending = false;
    record.framePending = false;
    const wasActive = record === activeRecord;
    if (wasActive) activeRecord = null;
    void concealRecord(record).then((concealed) => {
      if (!concealed || !wasActive || postToAndroid === null) return;
      postToAndroid({
        type: RETIRE_MESSAGE,
        videoId: record.videoId,
        revision: record.revision,
        reason,
      });
    });
  };

  const requestCover = (record) => {
    if (!enabled || record !== activeRecord) return;
    const signature = sourceSignature(record.video);
    if (record.sourceSignature.length > 0 && record.sourceSignature !== signature) {
      if (record.framePending) {
        record.nextCaptureTimer = setTimeout(scheduleScan, STATUS_RETRY_MS);
        return;
      }
      clearRecordTimers(record);
      record.revision += 1;
      record.captures = 0;
      record.covered = false;
      record.coverPending = false;
      record.framePending = false;
      record.recoveries = 0;
      record.coverRequestedAt = null;
      record.coverMillis = null;
      record.decodeStartedAt = null;
      void concealRecord(record);
      record.revealToken = randomToken(4);
      record.video.pause();
    }
    record.sourceSignature = signature;
    if (record.covered || record.coverPending) return;
    const coverRequestedAt = performance.now();
    record.coverPending = postRecord(COVER_REQUEST_MESSAGE, record, {
      readyState: record.video.readyState,
      durationFinite: Number.isFinite(record.video.duration),
    });
    if (record.coverPending) {
      record.coverRequestedAt = coverRequestedAt;
      record.coverTimer = setTimeout(() => {
        record.coverTimer = null;
        retireRecord(record, "cover_timeout");
      }, COVER_TIMEOUT_MS);
    }
  };

  const selectVisibleVideo = () => {
    scanScheduled = false;
    if (!enabled || window.top !== window) return;
    const candidate = [...document.querySelectorAll("video")]
      .map((video) => ({ video, area: visibleArea(video) }))
      .filter(({ area }) => area > 0)
      .sort((left, right) => right.area - left.area)[0]?.video || null;
    if (candidate === activeRecord?.video) {
      if (activeRecord !== null) requestCover(activeRecord);
      return;
    }
    if (activeRecord?.framePending === true) {
      activeRecord.nextCaptureTimer = setTimeout(scheduleScan, STATUS_RETRY_MS);
      return;
    }
    retireRecord(activeRecord, "authority_changed");
    if (candidate === null) return;
    activeRecord = recordFor(candidate);
    activeRecord.covered = false;
    activeRecord.captures = 0;
    requestCover(activeRecord);
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

  const requestFrameWhenReady = (record) => {
    if (
      record !== activeRecord ||
      !record.covered ||
      record.framePending ||
      !record.video.isConnected
    ) return;
    const deadline = performance.now() + FRAME_READY_TIMEOUT_MS;
    const request = () => {
      if (
        record !== activeRecord ||
        !record.covered ||
        record.framePending ||
        !record.video.isConnected
      ) return;
      const viewportWait = VIEWPORT_SETTLE_MS - (performance.now() - lastViewportChangeAt);
      if (viewportWait > 0) {
        record.readinessTimer = setTimeout(request, viewportWait);
        return;
      }
      if (record.video.readyState < HTMLMediaElement.HAVE_CURRENT_DATA) {
        if (performance.now() >= deadline) {
          retireRecord(record, "frame_ready_timeout");
          return;
        }
        record.readinessTimer = setTimeout(request, STATUS_RETRY_MS);
        return;
      }
      record.readinessTimer = null;
      const decodeMillis = record.decodeStartedAt === null
        ? null
        : Math.max(0, performance.now() - record.decodeStartedAt);
      if (!postRecord(FRAME_REQUEST_MESSAGE, record, {
        captureIndex: record.captures,
        coverMillis: record.coverMillis,
        decodeMillis,
      })) return;
      record.framePending = true;
      record.resultTimer = setTimeout(() => {
        record.resultTimer = null;
        retireRecord(record, "frame_result_timeout");
      }, FRAME_RESULT_TIMEOUT_MS);
    };
    request();
  };

  const armCoveredVideo = async (message) => {
    const record = activeRecord;
    if (
      record === null ||
      message.videoId !== record.videoId ||
      message.revision !== record.revision ||
      !await backgroundReady()
    ) return;
    clearTimeout(record.coverTimer);
    record.coverTimer = null;
    record.coverMillis = record.coverRequestedAt === null
      ? null
      : Math.max(0, performance.now() - record.coverRequestedAt);
    const reveal = await browser.runtime.sendMessage({
      type: REVEAL_MESSAGE,
      version: protocolVersion,
      documentToken,
      token: record.revealToken,
    }).catch(() => null);
    if (
      reveal?.inserted !== true ||
      record !== activeRecord ||
      message.revision !== record.revision
    ) {
      retireRecord(record, "reveal_denied");
      return;
    }
    record.coverPending = false;
    record.covered = true;
    record.video.muted = true;
    record.video.defaultMuted = true;
    record.video.volume = 0;
    record.video.playsInline = true;
    record.video.preload = "auto";
    record.video.setAttribute(TOKEN_ATTRIBUTE, record.revealToken);
    record.decodeStartedAt = performance.now();
    if (document.documentElement.hasAttribute(INTERNAL_FIXTURE_ATTRIBUTE)) {
      record.video.load();
    }
    void record.video.play().catch(() => {});
    requestFrameWhenReady(record);
  };

  const handleFrameResult = (message) => {
    const record = activeRecord;
    if (
      record === null ||
      message.videoId !== record.videoId ||
      message.revision !== record.revision
    ) return;
    clearTimeout(record.resultTimer);
    record.resultTimer = null;
    record.framePending = false;
    if (message.captured !== true) {
      const shouldRecover = record.recoveries < MAX_CAPTURE_RECOVERIES;
      record.recoveries += 1;
      retireRecord(record, "capture_failed");
      if (shouldRecover) setTimeout(scheduleScan, VIEWPORT_SETTLE_MS);
      return;
    }
    record.captures += 1;
    scheduleScan();
    if (record.captures >= MAX_CAPTURE_COUNT) {
      record.video.pause();
      return;
    }
    record.nextCaptureTimer = setTimeout(() => {
      record.nextCaptureTimer = null;
      requestFrameWhenReady(record);
    }, CAPTURE_DELAY_MS);
  };

  const mutationObserver = new MutationObserver((recordsList) => {
    for (const mutation of recordsList) {
      if (
        mutation.type === "attributes" &&
        (mutation.target instanceof HTMLVideoElement ||
          mutation.target instanceof HTMLSourceElement)
      ) {
        scheduleScan();
        return;
      }
      if (mutation.addedNodes.length > 0 || mutation.removedNodes.length > 0) {
        scheduleScan();
        return;
      }
    }
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
        attributeFilter: ["src", "type"],
        childList: true,
        subtree: true,
      });
      const viewportChanged = () => {
        lastViewportChangeAt = performance.now();
        scheduleScan();
      };
      addEventListener("scroll", viewportChanged, { passive: true });
      addEventListener("resize", viewportChanged, { passive: true });
      addEventListener("pagehide", () => retireRecord(activeRecord, "document_retired"));
      if (pendingConfiguration?.version === protocolVersion) {
        enabled = pendingConfiguration.enabled;
        pendingConfiguration = null;
      }
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
        enabled = configuration.enabled;
        if (enabled) scheduleScan(); else retireRecord(activeRecord, "lab_disabled");
        return;
      }
      if (!enabled || message?.version !== protocolVersion) return;
      if (message.type === COVER_ARMED_MESSAGE) {
        void armCoveredVideo(message);
      } else if (message.type === FRAME_RESULT_MESSAGE) {
        handleFrameResult(message);
      }
    },
  });
})();
