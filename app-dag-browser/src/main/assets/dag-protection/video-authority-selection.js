"use strict";

(() => {
  if (globalThis.__gloshDagVideoAuthoritySelection !== undefined) return;

  const sameValues = (left, right) =>
    left.length === right.length && left.every((value, index) => value === right[index]);

  const create = (dependencies) => {
    let handoffActive = false;
    let observedVideo = null;
    let observedSource = "";
    let observedViewport = [];
    let observedAt = 0;
    let selectedSnapshot = null;
    let transitionCount = 0;
    let verificationTimer = null;

    const clearVerification = () => {
      if (verificationTimer !== null) dependencies.clearTimeout(verificationTimer);
      verificationTimer = null;
    };

    const cancel = () => {
      clearVerification();
      handoffActive = false;
      observedVideo = null;
      observedSource = "";
      observedViewport = [];
      observedAt = 0;
      transitionCount = 0;
      selectedSnapshot = null;
    };

    const scheduleVerification = (delayMillis) => {
      clearVerification();
      if (transitionCount > dependencies.maximumTransitions) return;
      verificationTimer = dependencies.setTimeout(() => {
        verificationTimer = null;
        dependencies.scheduleScan();
      }, Math.max(1, Math.ceil(delayMillis)));
    };

    const remember = (snapshot) => {
      clearVerification();
      observedVideo = snapshot.video;
      observedSource = snapshot.sourceSignature;
      observedViewport = snapshot.viewportSignature.slice();
      observedAt = dependencies.now();
      transitionCount += 1;
      scheduleVerification(dependencies.settleMillis);
    };

    const beginHandoff = (snapshot) => {
      cancel();
      handoffActive = true;
      if (snapshot !== null) remember(snapshot);
    };

    const stableCandidate = (snapshot) => {
      if (!handoffActive) return snapshot?.video ?? null;
      if (snapshot === null) {
        clearVerification();
        observedVideo = null;
        observedSource = "";
        observedViewport = [];
        return null;
      }
      if (
        snapshot.video !== observedVideo ||
        snapshot.sourceSignature !== observedSource ||
        !sameValues(snapshot.viewportSignature, observedViewport)
      ) {
        remember(snapshot);
        return null;
      }
      const remaining = dependencies.settleMillis - (dependencies.now() - observedAt);
      if (remaining > 0) {
        scheduleVerification(remaining);
        return null;
      }
      const selected = snapshot.video;
      cancel();
      return selected;
    };

    const snapshotFor = (video) => ({
      video,
      backed: dependencies.hasBackingMedia(video),
      sourceSignature: dependencies.sourceSignature(video),
      viewportSignature: dependencies.viewportSignature(video),
    });

    const select = (snapshot) => {
      cancel();
      selectedSnapshot = snapshot;
      dependencies.onSelected(snapshot.video);
    };

    const scan = (videos) => {
      const candidate = videos
        .map((video) => {
          dependencies.reportBackingTransition(video);
          return {
            video,
            area: dependencies.visibleArea(video),
            backed: dependencies.hasBackingMedia(video),
          };
        })
        .filter(({ video, area, backed }) =>
          area > 0 && (backed || dependencies.canBootstrapCandidate(video)))
        .sort((left, right) => Number(right.backed) - Number(left.backed) || right.area - left.area)[0]?.video ?? null;
      const activeVideo = dependencies.activeVideo();
      if (activeVideo !== null && candidate === activeVideo) {
        selectedSnapshot = snapshotFor(candidate);
        dependencies.onActiveCandidate();
        return;
      }
      if (activeVideo !== null) {
        beginHandoff(candidate === null ? selectedSnapshot : snapshotFor(candidate));
        selectedSnapshot = null;
        void Promise.resolve(dependencies.onAuthorityChanged()).then((retired) => {
          if (retired !== true) cancel();
        });
        return;
      }
      if (candidate === null) {
        stableCandidate(null);
        dependencies.onNoCandidate();
        return;
      }
      const snapshot = snapshotFor(candidate);
      if (snapshot.backed) {
        select(snapshot);
        return;
      }
      if (!dependencies.hasBackingMedia(candidate) && !handoffActive) {
        beginHandoff(snapshot);
        dependencies.onHandoffWaiting();
        return;
      }
      const selected = stableCandidate(snapshot);
      if (selected === null) {
        dependencies.onHandoffWaiting();
        return;
      }
      select(snapshotFor(selected));
    };

    return Object.freeze({ cancel, scan });
  };

  globalThis.__gloshDagVideoAuthoritySelection = Object.freeze({ create });
})();
