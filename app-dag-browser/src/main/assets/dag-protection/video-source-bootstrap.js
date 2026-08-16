"use strict";

(() => {
  if (globalThis.__gloshDagVideoSourceBootstrap !== undefined) return;

  const create = (dependencies) => {
    const attemptedVideos = new WeakSet();

    const finish = (record) => {
      clearTimeout(record.readinessTimer);
      record.readinessTimer = null;
      record.sourceBootstrapActive = false;
      dependencies.safePause(record.video);
    };

    const cancel = (record) => {
      if (record === null || !record.sourceBootstrapActive) return;
      finish(record);
    };

    const backingReady = (record) => {
      if (
        !record.sourceBootstrapActive ||
        record !== dependencies.activeRecord() ||
        !dependencies.hasBackingMedia(record.video)
      ) return false;
      finish(record);
      record.sourceBootstrapCompleted = true;
      dependencies.onReady(record);
      return true;
    };

    const start = (record) => {
      if (
        record !== dependencies.activeRecord() ||
        record.covered ||
        record.coverPending ||
        record.retiring ||
        record.terminal ||
        !record.video.isConnected ||
        dependencies.hasBackingMedia(record.video) ||
        attemptedVideos.has(record.video)
      ) return false;
      attemptedVideos.add(record.video);
      record.sourceBootstrapActive = true;
      record.sourceBootstrapCompleted = false;
      record.video.muted = true;
      record.video.defaultMuted = true;
      record.video.volume = 0;
      record.video.preload = "auto";
      dependencies.enforcePresentationCapabilities(record);
      dependencies.enforceMediaIsolation();
      record.readinessTimer = setTimeout(() => {
        record.readinessTimer = null;
        if (!record.sourceBootstrapActive) return;
        record.sourceBootstrapActive = false;
        dependencies.safePause(record.video);
        dependencies.onTimeout(record);
      }, dependencies.timeoutMillis);
      Promise.resolve().then(() => record.video.play()).then(() => {
        dependencies.onPlayStarted(record);
        backingReady(record);
      }).catch(() => {
        if (!record.sourceBootstrapActive) return;
        finish(record);
        dependencies.onPlayRejected(record);
      });
      return true;
    };

    return Object.freeze({
      backingReady,
      canAttempt: (video) => !attemptedVideos.has(video),
      cancel,
      start,
    });
  };

  const createBackingReporter = (dependencies) => {
    const snapshots = new WeakMap();
    return (video) => {
      const next = {
        currentSrc: Boolean(video.currentSrc),
        sourceAttribute: (video.getAttribute("src") || "") !== "",
        sourceObject: video.srcObject != null,
        sourceChildren: [...video.querySelectorAll("source")].some((source) =>
          (source.getAttribute("src") || "") !== ""),
      };
      const previous = snapshots.get(video);
      snapshots.set(video, next);
      if (previous === undefined) {
        dependencies.postTimeline(dependencies.hasBackingMedia(video)
          ? "timeline_video_seen_backing"
          : "timeline_video_seen_no_backing");
        return;
      }
      if (!previous.currentSrc && next.currentSrc) dependencies.postTimeline("timeline_current_src_assigned");
      if (!previous.sourceAttribute && next.sourceAttribute) dependencies.postTimeline("timeline_src_attribute_assigned");
      if (!previous.sourceObject && next.sourceObject) dependencies.postTimeline("timeline_src_object_assigned");
      if (!previous.sourceChildren && next.sourceChildren) dependencies.postTimeline("timeline_source_child_assigned");
    };
  };

  globalThis.__gloshDagVideoSourceBootstrap = Object.freeze({ create, createBackingReporter });
})();
