"use strict";

(() => {
  if (globalThis.__gloshDagVideoLabViewport !== undefined) return;

  const createScanGate = (dependencies) => {
    let timer = null;
    const schedule = () => {
      if (!dependencies.required()) {
        dependencies.scheduleNow();
        return;
      }
      const remaining = dependencies.settleMillis -
        (dependencies.now() - dependencies.lastChangeAt());
      if (remaining <= 0) {
        dependencies.markStable();
        dependencies.scheduleNow();
        return;
      }
      if (timer !== null) return;
      timer = dependencies.setTimeout(() => {
        timer = null;
        schedule();
      }, Math.max(1, Math.ceil(remaining)));
    };
    return Object.freeze({ schedule });
  };

  const create = (dependencies) => {
    const invalidate = (event) => {
      const changedAt = dependencies.now();
      dependencies.setLastViewportChangeAt(changedAt);
      dependencies.postTimeline("timeline_reflow_observed");
      const activeRecord = dependencies.state.activeRecord;
      if (activeRecord === null) {
        dependencies.scheduleScan();
        return;
      }
      const nextSignature = dependencies.viewportSignature(activeRecord.video);
      if (
        (event?.type === "scroll" || activeRecord.premiumFullscreenTransitionUntil >= changedAt) &&
        activeRecord.smoothActive &&
        activeRecord.rawFrameOpen &&
        activeRecord.video.isConnected &&
        dependencies.hasDocumentToken() &&
        dependencies.sourceSignature(activeRecord.video) === activeRecord.sourceSignature &&
        !dependencies.unsafePresentationActive(activeRecord)
      ) {
        const firstEvent = !activeRecord.viewportSuspended;
        activeRecord.viewportSuspended = true;
        activeRecord.pendingViewportSignature = nextSignature;
        clearTimeout(activeRecord.nextCaptureTimer);
        activeRecord.nextCaptureTimer = null;
        clearTimeout(activeRecord.readinessTimer);
        if (firstEvent) dependencies.postDiagnostic("scroll_rebind_start");
        dependencies.rebindPremiumContinuity(activeRecord);
        activeRecord.readinessTimer = setTimeout(() => {
          const record = dependencies.state.activeRecord;
          if (record !== activeRecord || !record.viewportSuspended) return;
          record.readinessTimer = null;
          const settledSignature = dependencies.viewportSignature(record.video);
          const valid =
            record.video.isConnected &&
            dependencies.hasDocumentToken() &&
            dependencies.sourceSignature(record.video) === record.sourceSignature &&
            dependencies.presentationCapabilityFailure(record.video) === null &&
            !dependencies.unsafePresentationActive(record) &&
            dependencies.now() - dependencies.lastViewportChangeAt() >=
              dependencies.viewportSettleMillis;
          if (!valid) {
            record.viewportSuspended = false;
            record.viewportEpoch += 1;
            void dependencies.retireRecord(record, "viewport_changed");
            return;
          }
          record.viewportSignature = settledSignature;
          record.pendingViewportSignature = null;
          record.viewportEpoch += 1;
          record.viewportSuspended = false;
          dependencies.rebindPremiumContinuity(record);
          dependencies.postDiagnostic("scroll_rebind_stable");
          if (dependencies.visibleArea(record.video) > 0) {
            dependencies.scheduleNextCapture(record);
          } else {
            dependencies.postDiagnostic("scroll_video_offscreen");
          }
        }, dependencies.viewportSettleMillis);
        return;
      }
      if (
        dependencies.fixtureEnabled() &&
        event?.type === "resize" &&
        dependencies.sameVideoRect(activeRecord.viewportSignature, nextSignature)
      ) {
        activeRecord.viewportSignature = nextSignature;
        dependencies.postDiagnostic("fixture_viewport_transition");
        return;
      }
      if (
        event?.type === "resize" &&
        dependencies.sameViewportSignature(activeRecord.viewportSignature, nextSignature)
      ) {
        dependencies.postDiagnostic("viewport_resize_unchanged");
        return;
      }
      dependencies.postViewportChangeDiagnostics(activeRecord.viewportSignature, nextSignature);
      dependencies.postDiagnostic(event?.type === "scroll" ? "viewport_scroll" : "viewport_resize");
      const coveredBrowserTransition =
        event?.type === "resize" &&
        activeRecord.covered &&
        activeRecord.resultTimer === null &&
        !activeRecord.frameCaptured &&
        dependencies.sameVideoRect(activeRecord.viewportSignature, nextSignature);
      const coveredBootstrapTransition =
        event?.type === "resize" &&
        (activeRecord.covered || activeRecord.coverAcknowledged) &&
        dependencies.beginBootstrapViewportTransition(activeRecord, nextSignature);
      if (coveredBrowserTransition || coveredBootstrapTransition) {
        const now = dependencies.now();
        activeRecord.viewportTransitionStartedAt ??= now;
        if (!coveredBootstrapTransition) activeRecord.viewportTransitionCount += 1;
        const withinBound =
          now - activeRecord.viewportTransitionStartedAt <= dependencies.maximumTransitionMillis &&
          activeRecord.viewportTransitionCount <= dependencies.maximumTransitions;
        if (withinBound) {
          activeRecord.pendingViewportSignature = nextSignature;
          dependencies.postDiagnostic("viewport_transition_covered");
          clearTimeout(activeRecord.readinessTimer);
          activeRecord.readinessTimer = setTimeout(() => {
            const record = dependencies.state.activeRecord;
            if (record === null || record.viewportTransitionStartedAt === null) return;
            record.readinessTimer = null;
            const settledSignature = dependencies.viewportSignature(record.video);
            const transitionStable =
              dependencies.sameViewportSignature(record.pendingViewportSignature, settledSignature) &&
              (dependencies.sameVideoRect(record.viewportSignature, settledSignature) ||
                (record.bootstrapTransitionUsed &&
                  dependencies.sameViewportBounds(record.viewportSignature, settledSignature))) &&
              dependencies.now() - dependencies.lastViewportChangeAt() >=
                dependencies.viewportSettleMillis;
            if (!transitionStable) {
              dependencies.postDiagnostic("viewport_settle_mismatch");
              record.viewportEpoch += 1;
              void dependencies.retireRecord(record, "viewport_changed");
              return;
            }
            if (
              record.bootstrapTransitionUsed &&
              !dependencies.completeBootstrapViewportTransition(record, settledSignature)
            ) {
              void dependencies.retireRecord(record, "bootstrap_revalidation_failed");
              return;
            }
            const reopen = () => {
              if (
                record !== dependencies.state.activeRecord ||
                record.retiring ||
                record.terminal
              ) return;
              if (
                record.sourceSignature !== dependencies.sourceSignature(record.video) ||
                (record.bootstrapBackingGeneration !== 0 &&
                  record.bootstrapSourceSignature !== record.sourceSignature) ||
                dependencies.presentationCapabilityFailure(record.video) !== null ||
                dependencies.unsafePresentationActive(record) ||
                !record.video.isConnected ||
                !dependencies.hasDocumentToken()
              ) {
                void dependencies.retireRecord(record, "bootstrap_revalidation_failed");
                return;
              }
              dependencies.resetFrameState(record);
              record.viewportEpoch += 1;
              record.viewportSignature = settledSignature;
              record.pendingViewportSignature = null;
              record.viewportTransitionStartedAt = null;
              record.viewportTransitionCount = 0;
              dependencies.postDiagnostic("viewport_transition_stable");
              dependencies.requestFrameWhenReady(record);
            };
            if (!record.rawFrameOpen) {
              reopen();
              return;
            }
            dependencies.safePause(record.video);
            if (
              record.frameCallbackId !== null &&
              typeof record.video.cancelVideoFrameCallback === "function"
            ) {
              try {
                record.video.cancelVideoFrameCallback(record.frameCallbackId);
              } catch {}
              record.frameCallbackId = null;
            }
            void dependencies.concealRecord(record).then((concealed) => {
              if (!concealed) {
                void dependencies.retireRecord(record, "viewport_conceal_failed");
                return;
              }
              reopen();
            });
          }, dependencies.viewportSettleMillis);
          return;
        }
        dependencies.postDiagnostic("viewport_transition_unstable");
      }
      activeRecord.viewportEpoch += 1;
      void dependencies.retireRecord(activeRecord, "viewport_changed");
    };

    return Object.freeze({ invalidate });
  };

  globalThis.__gloshDagVideoLabViewport = Object.freeze({ create, createScanGate });
})();
