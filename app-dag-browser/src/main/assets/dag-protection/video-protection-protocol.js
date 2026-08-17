"use strict";

(() => {
  if (globalThis.__gloshDagVideoProtectionProtocol !== undefined) return;

  const messages = Object.freeze({
    config: "video-lab-config",
    status: "video-lab-status",
    diagnostic: "video-lab-diagnostic",
    coverRequest: "video-lab-cover-request",
    coverArmed: "video-lab-cover-armed",
    frameRequest: "video-lab-frame-request",
    frameCaptured: "video-lab-frame-captured",
    frameConcealed: "video-lab-frame-concealed",
    frameResult: "video-lab-frame-result",
    smoothStart: "video-lab-smooth-start",
    safeSkipNotice: "video-lab-safe-skip-notice",
    retire: "video-lab-retire",
    reveal: "video-lab-reveal-style",
    conceal: "video-lab-conceal-style",
  });

  const randomToken = (cryptoProvider, wordCount) => {
    const words = cryptoProvider.getRandomValues(new Uint32Array(wordCount));
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

  const recordMatchesMessage = (record, message, activeRecord) =>
    record !== null &&
    record === activeRecord &&
    message?.videoId === record.videoId &&
    message?.revision === record.revision;

  const frameMatchesMessage = (record, message, activeRecord) =>
    recordMatchesMessage(record, message, activeRecord) &&
    record.framePending &&
    message?.frameSequence === record.frameSequence &&
    message?.viewportEpoch === record.frameViewportEpoch;

  globalThis.__gloshDagVideoProtectionProtocol = Object.freeze({
    diagnosticMessage: messages.diagnostic,
    fixtureAttribute: "data-glosh-dag-video-lab-fixture",
    frameIdentity,
    frameMatchesMessage,
    grantIdentity,
    messages,
    presentationGuardAttribute: "data-glosh-dag-presentation-guard",
    presentationGuardVersion: "1",
    randomToken,
    recordMatchesMessage,
    tokenAttribute: "data-glosh-dag-video-lab-token",
  });
})();
