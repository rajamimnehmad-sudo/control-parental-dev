"use strict";

(() => {
  if (globalThis.__gloshDagVideoLabEvents !== undefined) return;

  const PLAYBACK_EVENTS = ["play", "playing", "pause", "abort", "emptied", "waiting", "stalled"];
  const BACKING_EVENTS = ["loadstart", "durationchange", "loadedmetadata", "canplay"];
  const UNSAFE_PRESENTATION_EVENTS = [
    "enterpictureinpicture",
    "leavepictureinpicture",
    "webkitbeginfullscreen",
    "webkitendfullscreen",
    "webkitpresentationmodechanged",
    "webkitcurrentplaybacktargetiswirelesschanged",
  ];

  const bindRecord = ({
    video,
    keepMuted,
    reportPlaybackEvent,
    reportBackingEvent,
    onSeeking,
    onSeeked,
    onUnsafePresentation,
    onGenerationChanged,
  }) => {
    video.addEventListener("play", keepMuted);
    PLAYBACK_EVENTS.forEach((type) => video.addEventListener(type, reportPlaybackEvent));
    BACKING_EVENTS.forEach((type) => video.addEventListener(type, reportBackingEvent));
    video.addEventListener("volumechange", keepMuted);
    video.addEventListener("seeking", onSeeking);
    video.addEventListener("seeked", onSeeked);
    UNSAFE_PRESENTATION_EVENTS.forEach((type) =>
      video.addEventListener(type, onUnsafePresentation));
    video.addEventListener("emptied", onGenerationChanged);
    video.addEventListener("emptied", onUnsafePresentation);
    video.addEventListener("error", onUnsafePresentation);
    const remote = video.remote;
    if (typeof remote?.addEventListener === "function") {
      ["connecting", "connect", "disconnect"].forEach((type) =>
        remote.addEventListener(type, onUnsafePresentation));
    }
  };

  const installDocument = ({
    documentObject,
    windowObject,
    VideoElement,
    mutationObserver,
    stopUnauthorizedPlayback,
    scheduleScan,
    onBackingEvent,
    invalidateForViewport,
    onPageHide,
    onFullscreen,
    onFullscreenError,
  }) => {
    mutationObserver.observe(documentObject, {
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
    documentObject.addEventListener("play", stopUnauthorizedPlayback, true);
    documentObject.addEventListener("volumechange", stopUnauthorizedPlayback, true);
    BACKING_EVENTS.forEach((type) => {
      documentObject.addEventListener(type, scheduleScan, true);
      documentObject.addEventListener(type, (event) => {
        if (VideoElement !== null && event.target instanceof VideoElement) {
          onBackingEvent(type, event.target);
        }
      }, true);
    });
    windowObject.addEventListener("scroll", invalidateForViewport, { passive: true });
    windowObject.addEventListener("resize", invalidateForViewport, { passive: true });
    windowObject.addEventListener("pagehide", onPageHide);
    windowObject.addEventListener("fullscreenchange", onFullscreen);
    windowObject.addEventListener("fullscreenerror", onFullscreenError);
  };

  globalThis.__gloshDagVideoLabEvents = Object.freeze({
    bindRecord,
    installDocument,
  });
})();
