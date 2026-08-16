"use strict";

(() => {
  if (globalThis.__gloshDagVideoLabPresentation !== undefined) return;

  const CAPABILITY_ATTRIBUTES = new Set([
    "disablepictureinpicture",
    "disableremoteplayback",
    "playsinline",
  ]);

  const enforceCapabilities = (record, rememberMutation) => {
    const { video } = record;
    if ("disablePictureInPicture" in video) {
      rememberMutation(record, "disablepictureinpicture", () => {
        video.disablePictureInPicture = true;
      });
    }
    if ("disableRemotePlayback" in video) {
      rememberMutation(record, "disableremoteplayback", () => {
        video.disableRemotePlayback = true;
      });
    }
    if ("playsInline" in video) {
      rememberMutation(record, "playsinline", () => {
        video.playsInline = true;
      });
    }
  };

  const capabilityFailure = (video) => {
    if ("disablePictureInPicture" in video && video.disablePictureInPicture !== true) {
      return "picture_in_picture";
    }
    if ("disableRemotePlayback" in video && video.disableRemotePlayback !== true) {
      return "remote_playback";
    }
    if ("playsInline" in video && video.playsInline !== true) return "plays_inline";
    return null;
  };

  const guardReady = (document, attribute, version) =>
    document.documentElement?.getAttribute(attribute) === version;

  const unsafeReason = (record, document, guardVerified) => {
    const remoteState = record.video.remote?.state;
    if (!guardVerified) return "guard_unverified";
    if (document.fullscreenElement !== null) return "fullscreen";
    if (document.pictureInPictureElement != null) return "picture_in_picture";
    if (record.video.webkitPresentationMode === "fullscreen") return "webkit_fullscreen";
    if (record.video.webkitPresentationMode === "picture-in-picture") {
      return "webkit_picture_in_picture";
    }
    if (record.video.webkitCurrentPlaybackTargetIsWireless === true) return "wireless_target";
    if (remoteState !== undefined && remoteState !== "disconnected") return `remote_${remoteState}`;
    return null;
  };

  globalThis.__gloshDagVideoLabPresentation = Object.freeze({
    CAPABILITY_ATTRIBUTES,
    capabilityFailure,
    enforceCapabilities,
    guardReady,
    unsafeReason,
  });
})();
