"use strict";

(() => {
  if (globalThis.__gloshDagVideoLabCapture !== undefined) return;

  const create = (dependencies) => {
    const revealAndRequestFrame = async (record) => {
      if (
        record !== dependencies.state.activeRecord ||
        !record.covered ||
        record.framePending ||
        record.retiring ||
        record.terminal ||
        !record.video.isConnected
      ) return;
      if (dependencies.unsafePresentationActive(record)) {
        dependencies.retireUnsafePresentation(record);
        return;
      }
      dependencies.enforcePresentationCapabilities(record);
      if (dependencies.unsafePresentationActive(record)) {
        dependencies.retireUnsafePresentation(record);
        return;
      }
      record.frameSequence += 1;
      record.frameViewportEpoch = record.viewportEpoch;
      record.framePending = true;
      record.frameCaptured = false;
      record.frameConcealed = false;
      record.frameAllowed = null;
      const reveal = await dependencies.browser.runtime.sendMessage({
        type: dependencies.revealMessage,
        version: dependencies.protocolVersion(),
        documentToken: dependencies.documentToken(),
        token: record.revealToken,
        ...dependencies.grantIdentity(record),
      }).catch(() => null);
      if (reveal?.inserted !== true) {
        const reason = typeof reveal?.reason === "string" && /^[a-z_]{1,40}$/u.test(reveal.reason)
          ? reveal.reason
          : "unknown";
        dependencies.postDiagnostic(`reveal_denied_${reason}`);
        void dependencies.retireRecord(record, "reveal_denied");
        return;
      }
      record.rawFrameOpen = true;
      if (
        record !== dependencies.state.activeRecord ||
        !dependencies.state.enabled ||
        record.retiring ||
        record.terminal ||
        dependencies.unsafePresentationActive(record)
      ) {
        void dependencies.retireRecord(record, "reveal_invalidated");
        return;
      }
      dependencies.enforceMediaIsolation();
      dependencies.enforcePresentationCapabilities(record);
      if (dependencies.unsafePresentationActive(record)) {
        dependencies.retireUnsafePresentation(record);
        return;
      }
      record.video.muted = true;
      record.video.defaultMuted = true;
      record.video.volume = 0;
      record.video.preload = "auto";
      record.video.setAttribute(dependencies.tokenAttribute, record.revealToken);
      record.decodeStartedAt = dependencies.now();
      if (
        record.captures === 0 &&
        dependencies.document.documentElement.hasAttribute(dependencies.fixtureAttribute)
      ) record.video.load();
      record.readinessTimer = setTimeout(() => {
        record.readinessTimer = null;
        void dependencies.retireRecord(record, "frame_ready_timeout");
      }, dependencies.frameReadyTimeoutMillis);
      dependencies.enforcePresentationCapabilities(record);
      if (dependencies.unsafePresentationActive(record)) {
        dependencies.retireUnsafePresentation(record);
        return;
      }
      record.playGeneration += 1;
      dependencies.postPlayAttemptDiagnostics(record);
      try {
        await record.video.play();
        dependencies.postDiagnostic("play_promise_resolved");
      } catch (error) {
        dependencies.postDiagnostic(
          record.sourceSignature === dependencies.sourceSignature(record.video)
            ? "play_reject_source_stable"
            : "play_reject_source_changed",
        );
        dependencies.postDiagnostic(record.video.paused ? "play_reject_paused" : "play_reject_playing");
        dependencies.postDiagnostic(record.video.ended ? "play_reject_ended" : "play_reject_not_ended");
        dependencies.postDiagnostic(
          dependencies.diagnosticLabels.readyState(record.video).replace("play_ready_", "play_reject_ready_"),
        );
        dependencies.postDiagnostic(
          dependencies.diagnosticLabels.networkState(record.video).replace("play_network_", "play_reject_network_"),
        );
        if (
          record === dependencies.state.activeRecord &&
          record.viewportTransitionStartedAt !== null
        ) {
          dependencies.postDiagnostic("play_aborted_for_viewport");
          return;
        }
        dependencies.postDiagnostic(dependencies.diagnosticLabels.playError(error));
        void dependencies.retireRecord(record, "play_rejected");
        return;
      }
      dependencies.requestVideoFrame(record);
    };

    const requestFrameWhenReady = (record) => {
      if (
        record !== dependencies.state.activeRecord ||
        !record.covered ||
        record.framePending ||
        record.retiring ||
        record.terminal ||
        !record.video.isConnected
      ) return;
      const request = () => {
        if (
          record !== dependencies.state.activeRecord ||
          !record.covered ||
          record.framePending ||
          record.retiring ||
          record.terminal ||
          !record.video.isConnected
        ) return;
        if (dependencies.unsafePresentationActive(record)) {
          dependencies.retireUnsafePresentation(record);
          return;
        }
        const viewportWait = dependencies.viewportSettleMillis -
          (dependencies.now() - dependencies.lastViewportChangeAt());
        if (viewportWait > 0) {
          record.readinessTimer = setTimeout(request, viewportWait);
          return;
        }
        const settledSignature = dependencies.viewportSignature(record.video);
        if (!dependencies.sameViewportSignature(record.viewportSignature, settledSignature)) {
          if (record.pendingViewportSignature !== null) {
            if (!dependencies.completeBootstrapViewportTransition(record, settledSignature)) {
              dependencies.postDiagnostic("viewport_settle_mismatch");
              record.viewportEpoch += 1;
              void dependencies.retireRecord(record, "viewport_changed");
              return;
            }
          } else if (dependencies.beginBootstrapViewportTransition(record, settledSignature)) {
            record.readinessTimer = setTimeout(request, dependencies.viewportSettleMillis);
            return;
          } else {
            dependencies.postViewportChangeDiagnostics(record.viewportSignature, settledSignature);
            dependencies.postDiagnostic("viewport_settle_mismatch");
            record.viewportEpoch += 1;
            void dependencies.retireRecord(record, "viewport_changed");
            return;
          }
        }
        if (record.viewportTransitionStartedAt !== null) {
          record.viewportTransitionStartedAt = null;
          record.viewportTransitionCount = 0;
          dependencies.postDiagnostic("viewport_transition_stable");
        }
        record.readinessTimer = null;
        if (record.smoothActive) dependencies.beginSmoothFrame(record);
        else void revealAndRequestFrame(record);
      };
      request();
    };

    const armCoveredVideo = async (message) => {
      dependencies.postDiagnostic("cover_arm_entered");
      const record = dependencies.state.activeRecord;
      if (!dependencies.recordMatchesMessage(record, message) || !record.coverPending) return;
      dependencies.postDiagnostic("cover_ack_received");
      const ackPhase = record.bootstrapState.acknowledge(true);
      if (ackPhase === "terminal") {
        void dependencies.retireRecord(record, "bootstrap_revalidation_failed");
        return;
      }
      record.coverAcknowledged = true;
      if (record.bootstrapLoadStarted) dependencies.armBootstrapGeneration(record);
      if (record !== dependencies.state.activeRecord || record.retiring) return;
      dependencies.postDiagnostic("background_wait_started");
      if (!await dependencies.backgroundReady()) {
        dependencies.postDiagnostic("background_wait_failed");
        if (record === dependencies.state.activeRecord) record.coverAcknowledged = false;
        return;
      }
      dependencies.postDiagnostic("background_wait_completed");
      if (
        !dependencies.recordMatchesMessage(record, message) ||
        !record.coverPending ||
        !record.coverAcknowledged
      ) {
        record.coverAcknowledged = false;
        return;
      }
      clearTimeout(record.coverTimer);
      record.coverTimer = null;
      record.coverMillis = record.coverRequestedAt === null
        ? null
        : Math.max(0, dependencies.now() - record.coverRequestedAt);
      record.coverPending = false;
      record.covered = true;
      if (
        record.bootstrapState.phase() === "acknowledged" &&
        record.bootstrapState.coverReady(
          !record.bootstrapLoadStarted &&
            record.sourceSignature === dependencies.sourceSignature(record.video),
        ) !== "stable"
      ) {
        void dependencies.retireRecord(record, "bootstrap_revalidation_failed");
        return;
      }
      record.video.muted = true;
      record.video.defaultMuted = true;
      record.video.volume = 0;
      dependencies.enforcePresentationCapabilities(record);
      dependencies.enforceMediaIsolation();
      if (!dependencies.hasBackingMedia(record.video)) {
        void dependencies.retireRecord(record, "source_changed");
        return;
      }
      requestFrameWhenReady(record);
    };

    const handleFrameCaptured = (message) => {
      const record = dependencies.state.activeRecord;
      if (!dependencies.frameMatchesMessage(record, message) || !record.rawFrameOpen) return;
      record.frameCaptured = true;
      if (record.smoothActive) {
        record.frameConcealed = true;
        dependencies.finishFrameIfReady(record);
        return;
      }
      void dependencies.concealRecord(record).then((concealed) => {
        if (!dependencies.frameMatchesMessage(record, message)) return;
        if (!concealed) {
          void dependencies.retireRecord(record, "frame_conceal_failed");
          return;
        }
        record.frameConcealed = true;
        if (!dependencies.postFrameRecord(dependencies.frameConcealedMessage, record)) {
          void dependencies.retireRecord(record, "frame_concealed_rejected");
          return;
        }
        dependencies.finishFrameIfReady(record);
      });
    };

    const handleFrameResult = (message) => {
      const record = dependencies.state.activeRecord;
      if (!dependencies.frameMatchesMessage(record, message)) return;
      record.frameAllowed = message.captured === true && message.action === "allow";
      if (!record.frameAllowed) {
        record.terminal = true;
        dependencies.safePause(record.video);
        if (record.smoothActive) record.frameConcealed = false;
        if (!record.frameConcealed) {
          void dependencies.concealRecord(record).then((concealed) => {
            if (!dependencies.frameMatchesMessage(record, message)) return;
            if (!concealed) {
              void dependencies.retireRecord(record, "terminal_frame_conceal_failed");
              return;
            }
            record.smoothActive = false;
            record.frameConcealed = true;
            void dependencies.retireRecord(record, "frame_blocked");
          });
        } else {
          void dependencies.retireRecord(record, "frame_blocked");
        }
        return;
      }
      dependencies.finishFrameIfReady(record);
    };

    return Object.freeze({
      armCoveredVideo,
      handleFrameCaptured,
      handleFrameResult,
      requestFrameWhenReady,
    });
  };

  globalThis.__gloshDagVideoLabCapture = Object.freeze({ create });
})();
