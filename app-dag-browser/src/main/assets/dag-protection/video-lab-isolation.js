"use strict";

(() => {
  if (globalThis.__gloshDagVideoLabIsolation !== undefined) return;

  const create = (dependencies) => {
    const originalAudioStates = new WeakMap();

    const originalAudioState = (media) => {
      const existing = originalAudioStates.get(media);
      if (existing !== undefined) return existing;
      const state = Object.freeze({
        muted: media.muted === true,
        defaultMuted: media.defaultMuted === true,
        volume: Number.isFinite(media.volume) ? Math.min(1, Math.max(0, media.volume)) : 1,
      });
      originalAudioStates.set(media, state);
      return state;
    };

    const safePause = (video) => {
      const backedVideo = dependencies.VideoElement !== null &&
        video instanceof dependencies.VideoElement &&
        dependencies.hasBackingMedia(video);
      dependencies.postTimeline(
        backedVideo ? "timeline_safe_pause_backing" : "timeline_safe_pause_no_backing",
      );
      try {
        video.pause();
      } catch {}
    };

    const enforceMuted = (record) => {
      if (
        record !== dependencies.activeRecord() ||
        record.retiring ||
        record.smoothActive
      ) return;
      if (!record.video.muted) record.video.muted = true;
      if (!record.video.defaultMuted) record.video.defaultMuted = true;
      if (record.video.volume !== 0) record.video.volume = 0;
    };

    const isAuthorizedRawPlayback = (media) => {
      const record = dependencies.activeRecord();
      return record !== null &&
        media === record.video &&
        record.rawFrameOpen && (
          (record.framePending && !record.smoothActive) ||
          (record.smoothActive && record.covered)
        ) &&
        !dependencies.isolationLocked() &&
        !record.retiring &&
        !record.terminal;
    };

    const silenceAndPauseMedia = (media) => {
      if (!(media instanceof dependencies.MediaElement)) return;
      if (isAuthorizedRawPlayback(media)) return;
      originalAudioState(media);
      if (!media.muted) media.muted = true;
      if (!media.defaultMuted) media.defaultMuted = true;
      if (media.volume !== 0) media.volume = 0;
      if (!isAuthorizedRawPlayback(media)) safePause(media);
    };

    const active = () =>
      dependencies.isolationLocked() ||
      dependencies.enabled() ||
      dependencies.activeRecord() !== null ||
      dependencies.closingRecord() !== null;

    const enforce = (document) => {
      if (!active()) return;
      dependencies.postTimeline("timeline_isolation_enforced");
      for (const media of document.querySelectorAll("audio, video")) {
        silenceAndPauseMedia(media);
      }
    };

    const stopUnauthorizedPlayback = (event) => {
      if (!active()) return;
      dependencies.postTimeline("timeline_play_event");
      silenceAndPauseMedia(event.target);
    };

    return Object.freeze({
      active,
      enforce,
      enforceMuted,
      isAuthorizedRawPlayback,
      originalAudioState,
      safePause,
      silenceAndPauseMedia,
      stopUnauthorizedPlayback,
    });
  };

  globalThis.__gloshDagVideoLabIsolation = Object.freeze({ create });
})();
