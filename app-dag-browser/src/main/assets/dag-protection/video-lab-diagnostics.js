"use strict";

(() => {
  if (globalThis.__gloshDagVideoLabDiagnostics !== undefined) return;

  const readyState = (video, mediaElement = globalThis.HTMLMediaElement) => {
    switch (video?.readyState) {
      case mediaElement?.HAVE_NOTHING: return "play_ready_nothing";
      case mediaElement?.HAVE_METADATA: return "play_ready_metadata";
      case mediaElement?.HAVE_CURRENT_DATA: return "play_ready_current";
      case mediaElement?.HAVE_FUTURE_DATA: return "play_ready_future";
      case mediaElement?.HAVE_ENOUGH_DATA: return "play_ready_enough";
      default: return "play_ready_unknown";
    }
  };

  const networkState = (video) => {
    switch (video?.networkState) {
      case 0: return "play_network_empty";
      case 1: return "play_network_idle";
      case 2: return "play_network_loading";
      case 3: return "play_network_no_source";
      default: return "play_network_unknown";
    }
  };

  const backingScheme = (value) => {
    if (value === "") return "backing_scheme_absent";
    const scheme = /^[a-z][a-z0-9+.-]*:/iu.exec(value)?.[0]?.toLowerCase() ?? "";
    if (scheme === "blob:") return "backing_scheme_blob_media_source_like";
    if (scheme === "data:") return "backing_scheme_data";
    if (scheme === "http:" || scheme === "https:") return "backing_scheme_network";
    return "backing_scheme_other";
  };

  const playError = (error) => {
    switch (error?.name) {
      case "AbortError": return "play_error_abort";
      case "NotAllowedError": return "play_error_not_allowed";
      case "NotSupportedError": return "play_error_not_supported";
      case "SecurityError": return "play_error_security";
      default: return "play_error_unknown";
    }
  };

  const backing = (video) => {
    const labels = [];
    const srcAttribute = video.getAttribute("src");
    labels.push(
      srcAttribute === null
        ? "backing_src_attribute_absent"
        : srcAttribute === ""
          ? "backing_src_attribute_empty"
          : "backing_src_attribute_present",
    );
    labels.push(video.currentSrc ? "backing_current_src_present" : "backing_current_src_absent");
    const tracks = typeof video.srcObject?.getVideoTracks === "function"
      ? video.srcObject.getVideoTracks()
      : [];
    labels.push(video.srcObject === null ? "backing_src_object_absent" : "backing_src_object_present");
    if (video.srcObject !== null) {
      labels.push(tracks.length === 0 ? "backing_object_tracks_none" : "backing_object_tracks_present");
    }
    const sourceCount = video.querySelectorAll("source").length;
    labels.push(
      sourceCount === 0
        ? "backing_source_children_none"
        : sourceCount === 1
          ? "backing_source_children_single"
          : "backing_source_children_multiple",
    );
    labels.push(backingScheme(video.currentSrc || srcAttribute || ""));
    return labels;
  };

  const playAttempt = (video, generation, sourceStable) => {
    const labels = [
      generation === 1
        ? "play_generation_initial"
        : generation === 2
          ? "play_generation_second"
          : "play_generation_later",
      video.paused ? "play_state_paused" : "play_state_playing",
      video.ended ? "play_state_ended" : "play_state_not_ended",
      readyState(video),
      networkState(video),
      ...backing(video),
      sourceStable ? "play_source_stable" : "play_source_changed",
    ];
    const tracks = typeof video.srcObject?.getVideoTracks === "function"
      ? video.srcObject.getVideoTracks()
      : [];
    labels.push(
      tracks.length === 0
        ? "play_video_tracks_none"
        : tracks.length === 1
          ? "play_video_tracks_single"
          : "play_video_tracks_multiple",
    );
    if (tracks.length > 0) {
      labels.push(tracks[0].readyState === "live" ? "play_track_live" : "play_track_ended");
      labels.push(tracks[0].muted === true ? "play_track_muted" : "play_track_unmuted");
    }
    return labels;
  };

  const viewportChange = (before, after) => {
    if (before === null || after === null) return [];
    const labels = [];
    const groups = [
      ["viewport_change_window", 0, 2],
      ["viewport_change_visual", 2, 7],
      ["viewport_change_video_rect", 7, 11],
    ];
    for (const [stage, start, end] of groups) {
      if (before.slice(start, end).some((value, index) => value !== after[start + index])) {
        labels.push(stage);
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
      if (before[index] !== after[index]) labels.push(stage);
    }
    return labels;
  };

  const createEmitter = (dependencies) => {
    const startedAt = dependencies.now();
    const timelineStages = new Set();
    let lastStage = "";

    const post = (stage) => {
      if (!dependencies.enabled() || stage === lastStage) return;
      lastStage = stage;
      try {
        dependencies.send({
          type: dependencies.type,
          stage,
          elapsedMillis: Math.min(120_000, Math.max(0, Math.round(dependencies.now() - startedAt))),
        });
      } catch {}
    };

    const timeline = (stage) => {
      if (timelineStages.has(stage)) return;
      timelineStages.add(stage);
      post(stage);
    };

    return Object.freeze({
      labels: (stages) => { if (dependencies.enabled()) stages.forEach(post); },
      post,
      reset: () => { lastStage = ""; },
      timeline,
    });
  };

  globalThis.__gloshDagVideoLabDiagnostics = Object.freeze({
    backing,
    backingScheme,
    createEmitter,
    networkState,
    playAttempt,
    playError,
    readyState,
    viewportChange,
  });
})();
