"use strict";

(() => {
  if (globalThis.__gloshDagVideoLabRecordBinding !== undefined) return;

  const PLAYBACK_EVENTS = new Set([
    "play", "playing", "pause", "abort", "emptied", "waiting", "stalled",
  ]);
  const BACKING_EVENTS = new Set([
    "loadstart", "durationchange", "loadedmetadata", "canplay",
  ]);

  const create = (dependencies) => (video) => {
    const existing = dependencies.records.get(video);
    if (existing !== undefined) return existing;
    const record = dependencies.createRecord(
      video,
      dependencies.originalAudioState(video),
      dependencies.randomToken,
      dependencies.createBootstrapState(),
    );
    const reportPlaybackEvent = (event) => {
      if (!dependencies.diagnosticsEnabled() || record !== dependencies.activeRecord()) return;
      const type = event?.type;
      if (PLAYBACK_EVENTS.has(type)) dependencies.postDiagnostic(`play_event_${type}`);
    };
    const reportBackingEvent = (event) => {
      if (record !== dependencies.activeRecord() || !BACKING_EVENTS.has(event?.type)) return;
      const type = event.type;
      dependencies.postDiagnostic(`backing_event_${type}`);
      dependencies.postDiagnosticLabels(dependencies.diagnosticLabels.backing(record.video));
      dependencies.postDiagnostic(
        dependencies.diagnosticLabels.readyState(record.video)
          .replace("play_ready_", "backing_ready_"),
      );
      dependencies.postDiagnostic(
        dependencies.diagnosticLabels.networkState(record.video)
          .replace("play_network_", "backing_network_"),
      );
      if (dependencies.sourceBootstrap()?.backingReady(record) === true) return;
      if (type === "loadstart") {
        if (record.bootstrapLoadStarted || record.bootstrapBackingGeneration !== 0) {
          dependencies.postDiagnostic("bootstrap_generation_repeated");
          void dependencies.retireRecord(record, "bootstrap_generation_repeated");
          return;
        }
        const signature = dependencies.sourceSignature(record.video);
        const validLoad =
          record.captures !== 0 ||
          record.frameCaptured ||
          record.rawFrameOpen ||
          (!record.coverPending && !record.coverAcknowledged) ||
          signature !== record.sourceSignature;
        if (record.bootstrapState.loadStart(!validLoad) === "terminal") {
          void dependencies.retireRecord(record, "bootstrap_source_changed");
          return;
        }
        record.bootstrapLoadStarted = true;
        record.bootstrapLoadSourceSignature = signature;
        dependencies.postDiagnostic("bootstrap_load_started");
        if (record.coverAcknowledged) dependencies.armBootstrapGeneration(record);
      } else if (
        type === "canplay" &&
        record.bootstrapState.phase() === "stable" &&
        record.bootstrapState.mediaReady(
          record.sourceSignature === dependencies.sourceSignature(record.video),
        ) !== "stable"
      ) {
        void dependencies.retireRecord(record, "bootstrap_revalidation_failed");
      }
    };
    dependencies.bindRecord({
      video,
      keepMuted: () => dependencies.enforceMuted(record),
      reportPlaybackEvent,
      reportBackingEvent,
      onSeeking: () => {
        if (
          dependencies.premiumContinuity()?.onSeeking(record) !== true &&
          dependencies.safeSkipController()?.onSeeking(record) !== true
        ) dependencies.seekController()?.onSeeking(record);
      },
      onSeeked: () => {
        if (
          dependencies.premiumContinuity()?.onSeeked(record) !== true &&
          dependencies.safeSkipController()?.onSeeked(record) !== true
        ) dependencies.seekController()?.onSeeked(record);
      },
      onUnsafePresentation: () => dependencies.retireUnsafePresentation(record),
      onGenerationChanged: () => dependencies.noteGeneration(video),
    });
    dependencies.records.set(video, record);
    return record;
  };

  globalThis.__gloshDagVideoLabRecordBinding = Object.freeze({ create });
})();
