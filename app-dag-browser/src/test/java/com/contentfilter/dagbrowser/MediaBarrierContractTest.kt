package com.contentfilter.dagbrowser

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MediaBarrierContractTest {
    private val extensionRoot = File("src/main/assets/dag-protection")
    private val manifest by lazy { extensionRoot.resolve("manifest.json").readText() }
    private val background by lazy { extensionRoot.resolve("background.js").readText() }
    private val barrier by lazy { extensionRoot.resolve("barrier.js").readText() }
    private val videoLab by lazy { extensionRoot.resolve("video-lab.js").readText() }
    private val videoPresentation by lazy { extensionRoot.resolve("video-lab-presentation.js").readText() }
    private val videoRecord by lazy { extensionRoot.resolve("video-lab-record.js").readText() }
    private val videoIsolation by lazy { extensionRoot.resolve("video-lab-isolation.js").readText() }
    private val videoLifecycle by lazy { extensionRoot.resolve("video-lab-lifecycle.js").readText() }
    private val videoPlayback by lazy { extensionRoot.resolve("video-lab-playback.js").readText() }
    private val videoCapture by lazy { extensionRoot.resolve("video-lab-capture.js").readText() }
    private val ads by lazy { extensionRoot.resolve("ads.js").readText() }
    private val schedulerGuard by lazy { extensionRoot.resolve("runaway-scheduler-guard.js").readText() }
    private val presentationGuard by lazy { extensionRoot.resolve("presentation-guard.js").readText() }
    private val css by lazy { extensionRoot.resolve("barrier.css").readText() }
    private val activity by lazy {
        File("src/main/java/com/contentfilter/dagbrowser/DagBrowserActivity.kt").readText()
    }
    private val blockedPlaceholder by lazy {
        File("src/main/java/com/contentfilter/dagbrowser/DagVideoBlockedPlaceholderPresenter.kt").readText()
    }
    private val browserLayout by lazy { File("src/main/res/layout/activity_dag_browser.xml").readText() }

    @Test
    fun `extension starts before content and updates in place`() {
        assertContains(manifest, "\"run_at\": \"document_start\"")
        assertContains(manifest, "\"all_frames\": true")
        assertContains(manifest, "\"css_origin\": \"user\"")
        assertContains(manifest, "\"nativeMessaging\"")
        assertContains(manifest, "\"version\": \"2.0.42\"")
        assertContains(manifest, "\"world\": \"MAIN\"")
        assertContains(manifest, "\"runaway-scheduler-guard.js\"")
        assertContains(manifest, "\"presentation-guard.js\"")
        assertContains(manifest, "\"video-bootstrap-state.js\"")
        assertFalse(manifest.contains("\"video-fluid-capability.js\""))
        assertContains(activity, ".ensureBuiltIn(ExtensionLocation, ExtensionId)")
        assertFalse(activity.contains(".installBuiltIn(ExtensionLocation)"))
    }

    @Test
    fun `native fullscreen authority preserves cover and closes the exact video grant`() {
        assertContains(activity, "override fun onFullScreen(")
        assertContains(activity, "if (!fullScreen || !isVideoLabCovered(tab)) return")
        assertContains(activity, "showVideoLabCover()")
        assertContains(activity, "beginVideoLabClose(\"fullscreen_requested\")")
        assertContains(activity, "override fun onFullscreen(")
        assertContains(activity, "beginVideoLabClose(\"media_fullscreen_requested\")")
        assertContains(activity, "session.exitFullScreen()")
        assertContains(presentationGuard, "requestPictureInPicture")
        assertContains(presentationGuard, "documentPictureInPicture")
        assertContains(presentationGuard, "webkitSetPresentationMode")
        assertContains(presentationGuard, "RemotePlayback")
        assertContains(videoPresentation, "return \"guard_unverified\"")
        assertFalse(videoLab.contains("controlslist", ignoreCase = true))
    }

    @Test
    fun `runaway scheduler guard yields only sustained signal ports`() {
        assertContains(schedulerGuard, "MessagePort?.prototype")
        assertContains(schedulerGuard, "MINIMUM_SIGNAL_SPAN_MS = 1_000")
        assertContains(schedulerGuard, "MINIMUM_SIGNAL_COUNT = 12")
        assertContains(schedulerGuard, "MAIN_THREAD_YIELD_MS = 16")
        assertContains(schedulerGuard, "MAX_PENDING_SIGNALS = 64")
        assertContains(schedulerGuard, "flushPending")
        assertContains(schedulerGuard, "document.readyState === \"complete\"")
        assertContains(schedulerGuard, "Number.isSafeInteger(message)")
        assertContains(schedulerGuard, "Reflect.apply(nativePostMessage")
        assertFalse(schedulerGuard.contains("mimo", ignoreCase = true))
        assertFalse(schedulerGuard.contains("hostname"))
        assertFalse(schedulerGuard.contains("location."))
    }

    @Test
    fun `only an intercepted media revision clears stale caches before restoring tabs`() {
        val activity =
            File("src/main/java/com/contentfilter/dagbrowser/DagBrowserActivity.kt").readText()

        assertContains(activity, "clearInterceptedMediaCacheAfterUpdate()")
        assertContains(activity, "StorageController.ClearFlags.ALL_CACHES")
        assertContains(activity, "CacheMaintenanceRevisionKey, InterceptedMediaCacheRevision")
        assertContains(activity, ".remove(LegacyCacheMaintenanceVersionKey)")
        assertContains(activity, "ensureProtectionExtension()")
        assertFalse(activity.contains("CacheMaintenanceVersionKey, BuildConfig.VERSION_CODE"))
        assertFalse(activity.contains("ClearFlags.SITE_DATA).accept"))
    }

    @Test
    fun `raster response has one bounded native authority`() {
        assertContains(background, "filterResponseData")
        assertContains(background, "handlerBehaviorChanged")
        assertContains(background, "IMAGE_MIME_PATTERN.test(contentType)")
        assertContains(background, "alreadyIntercepted")
        assertContains(background, "MAX_IMAGE_BYTES = 2 * 1024 * 1024")
        assertContains(background, "MAX_CAPTURED_BYTES = 8 * 1024 * 1024")
        assertContains(background, "CAPTURE_IDLE_TIMEOUT_MS = 5_000")
        assertFalse(background.contains("capture_start_timeout"))
        assertContains(background, "filter.onstart")
        assertContains(background, "armCaptureTimeout(CAPTURE_IDLE_TIMEOUT_MS")
        assertContains(background, "MAX_NATIVE_IN_FLIGHT = 2")
        assertContains(
            File("src/main/java/com/contentfilter/dagbrowser/DagBrowserActivity.kt").readText(),
            "THREAD_PRIORITY_BACKGROUND",
        )
        assertContains(
            File("src/main/java/com/contentfilter/dagbrowser/DagBrowserActivity.kt").readText(),
            "MediaAnalysisThreads = 2",
        )
        assertContains(background, "MAX_ACTIVE_STREAMS = 128")
        assertContains(background, "MAX_QUEUED_ANALYSES = 144")
        assertContains(background, "MAX_CACHED_REPLACEMENT_BYTES = 2 * 1024 * 1024")
        assertContains(background, "cachedReplacementBytes")
        assertContains(background, "const cachedDecision")
        assertContains(background, "takeNextAnalysis")
        assertContains(background, "priorityRank")
        assertContains(background, "task.details.url === url")
        assertContains(background, "documentStatesByTab")
        assertContains(background, "frameTokens")
        assertContains(background, "media-document-current")
        assertContains(background, "media-document-retired")
        assertContains(background, "documentKey: documentState.documentKey")
        assertContains(background, "media-bytes")
        assertContains(background, "media-diagnostics-config")
        assertContains(background, "media-diagnostic-summary")
        assertContains(background, "carrier: carrier === \"inline\" ? \"inline\" : \"network\"")
        assertContains(background, "recordDiagnosticDrop")
        assertContains(background, "model_allow")
        assertContains(background, "model_filter")
        assertFalse(background.contains("safe_ui_sprite"))
        assertFalse(background.contains("model_partial_redaction"))
        assertFalse(File("src/main/java/com/contentfilter/dagbrowser/DagSafeUiSpritePolicy.kt").exists())
    }

    @Test
    fun `normal DEV does not pay diagnostic tracing cost`() {
        val activity =
            File("src/main/java/com/contentfilter/dagbrowser/DagBrowserActivity.kt").readText()
        val build = File("build.gradle.kts").readText()

        assertContains(build, "buildConfigField(\"boolean\", \"DAG_DIAGNOSTICS\", \"false\")")
        assertContains(build, "buildConfigField(\"boolean\", \"DAG_DIAGNOSTICS\", \"true\")")
        assertContains(activity, "if (BuildConfig.DAG_DIAGNOSTICS) DagMediaPipelineTrace() else null")
        assertContains(activity, ".put(\"enabled\", enabled)")
        assertContains(activity, ".put(\"diagnostics\", BuildConfig.DAG_DIAGNOSTICS)")
        assertContains(activity, "VideoLabDiagnosticMessage -> handleVideoLabDiagnostic")
        assertContains(activity, "if (!BuildConfig.DAG_DIAGNOSTICS) return")
        assertContains(activity, "elapsedMillis !in 0L..120_000L")
        assertContains(activity, "signal=\$stage relative_ms=\$elapsedMillis")
        assertContains(activity, "transport=cover_post_before")
        assertContains(activity, "transport=cover_post_after")
        assertContains(activity, "transport=cover_post_failed")
        assertFalse(activity.contains("uri=\${permission.uri}"))
        assertContains(activity, "VideoLabDiagnosticMessage -> logBackgroundVideoLabDiagnostic")
        assertContains(activity, "val quarterY = bitmap.height / 4")
        assertContains(activity, "val threeQuartersY = bitmap.height * 3 / 4")
        assertContains(background, "videoLabDiagnosticsEnabled = message.diagnostics === true")
        assertFalse(manifest.contains("\"video-fluid-capability.js\""))
        assertFalse(activity.contains("DagBinaryWebExtensionPort"))
        assertFalse(activity.contains("VideoBenchmark"))
        assertContains(background, "revoke_no_grant_no_proof")
        assertContains(background, "VIDEO_LAB_JOURNAL_KEY")
        assertContains(background, "MAX_VIDEO_LAB_JOURNAL_RECORDS = 16")
        assertContains(background, "sameVideoLabGrantIdentity")
        assertContains(background, "await browser.tabs.removeCSS(record.tabId")
        assertContains(manifest, "\"storage\"")
        assertFalse(activity.contains("packageName.endsWith(\".dev\")"))
        assertFalse(build.contains("create(\"lab\")"))
        assertFalse(build.contains("GLOSHIA_VISUAL_ENABLED"))
        assertFalse(build.contains("GLOSHIA_LAB_FIXTURE"))
    }

    @Test
    fun `covered video reuses one prepared raster authority without revealing frames`() {
        val activity =
            File("src/main/java/com/contentfilter/dagbrowser/DagBrowserActivity.kt").readText()
        val imagePolicy =
            File("src/main/java/com/contentfilter/dagbrowser/DagMediaBytesPolicy.kt").readText()
        val rasterPolicy =
            File("src/main/java/com/contentfilter/dagbrowser/DagPreparedRasterPolicy.kt").readText()

        assertContains(imagePolicy, "DagPreparedRasterPolicy.decide(")
        assertContains(activity, "DagPreparedRasterPolicy.decide(")
        assertEquals(1, Regex("DagOnDeviceImageAnalyzer\\.create\\(").findAll(activity).count())
        assertContains(activity, "videoLabKey = key")
        assertContains(activity, "activeVideoLabKey.get() == key")
        assertContains(activity, "VideoLabAnalysisLifetimeMillis = 2_500L")
        assertContains(activity, "isVideoLabEligibleSender(sender)")
        assertContains(activity, "isVideoLabTargetSender(sender)")
        assertContains(activity, "videoLabTargetTabId == tab.id")
        assertContains(activity, "videoLabMode == VideoLabMode.Fixture")
        assertContains(activity, "videoLabMode = VideoLabMode.CurrentPage")
        assertContains(activity, "!tab.url.startsWith(\"https://\", ignoreCase = true)")
        assertContains(activity, "reloadActivePage()")
        assertContains(activity, "postVideoLabConfig(port, enabled = isVideoProtectionRuntimeEnabled())")
        assertContains(activity, "DagVideoProtectionActivationPolicy.senderEnabled")
        assertContains(activity, "popup.menu.findItem(R.id.menu_video_harness)?.isVisible = false")
        assertContains(activity, "beginVideoLabClose(\"lab_disabled\")")
        assertContains(activity, "DagVideoLabCloseRequest(")
        assertContains(activity, "videoLabState.acknowledgeClose(close.key, close.nonce)")
        assertContains(activity, "videoLabState.blockClosing(close.key, close.nonce)")
        assertContains(activity, "recoverBlockedVideoLabDocument(close.key, blockedTab)")
        assertContains(activity, "videoLabState.retireBlockedDocument(key)")
        assertContains(activity, "disposeTab(blockedTab, deletePersistedPreview = true)")
        assertContains(activity, "videoLabOverlay.visibility = View.GONE")
        assertContains(activity, "VideoLabFrameCapturedMessage")
        assertContains(activity, "VideoLabFrameConcealedMessage")
        assertContains(activity, "AndroidDagImagePreprocessor.prepareVideoCapturedRaster")
        assertContains(activity, "videoBlockedPlaceholder.show(close.key)")
        assertContains(activity, "videoBlockedPlaceholder.showProtection(key)")
        assertContains(activity, "videoBlockedPlaceholder.clearForTab(tab.id)")
        assertContains(blockedPlaceholder, "overlay.setBackgroundColor(Color.TRANSPARENT)")
        assertContains(blockedPlaceholder, "showLocalized(key, blockedColor, clearOutside = true)")
        assertContains(blockedPlaceholder, "frame.setBackgroundColor(color)")
        assertContains(blockedPlaceholder, "showLocalized(key, fullCoverColor, clearOutside = false)")
        assertContains(blockedPlaceholder, "overlay.isClickable = false")
        assertContains(blockedPlaceholder, "frame.setOnTouchListener { _, _ -> true }")
        assertContains(blockedPlaceholder, "event.actionMasked == MotionEvent.ACTION_DOWN")
        assertContains(blockedPlaceholder, "!displayRect.contains(event.x.toInt(), event.y.toInt())")
        assertContains(videoLifecycle, "reason !== \"frame_blocked\"")
        assertContains(videoLifecycle, "state.isolationLocked = true")
        assertContains(browserLayout, "android:id=\"@+id/video_lab_overlay\"")
        assertContains(browserLayout, "android:clickable=\"true\"")
        assertContains(browserLayout, "android:focusableInTouchMode=\"true\"")
        assertContains(background, "browser.tabs.insertCSS")
        assertContains(background, "browser.tabs.removeCSS")
        assertContains(background, "cssOrigin: \"user\"")
        assertContains(background, "hasCurrentVideoLabGrant(details)")
        assertContains(background, "AUDIO_MEDIA_MIME_PATTERN.test(contentType)")
        assertContains(videoPlayback, "coverMillis: record.coverMillis")
        assertContains(videoPlayback, "decodeMillis,")
        assertContains(rasterPolicy, "DagOnDeviceImageAnalyzer.FilterThreshold")
        assertContains(rasterPolicy, "DagUncertainRegionalCropper.quadrantViews")
        assertFalse(videoLab.contains(".style.visibility ="))
        assertContains(videoPlayback, "const style = dependencies.getComputedStyle(record.video)")
        assertContains(videoCapture, "message.action === \"allow\"")
        assertContains(videoCapture, "record.frameConcealed")
    }

    @Test
    fun `filtered response never releases rejected pixels`() {
        assertContains(background, "BLOCKED_PLACEHOLDER_BASE64")
        assertContains(
            background,
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNgYAAAAAMAASsJTYQAAAAASUVORK5CYII=",
        )
        assertContains(background, "blockedPlaceholder")
        assertContains(background, "action === \"allow\"")
        assertFalse(background.contains("filter.write(event.data)"))
        assertFalse(background.contains("blur("))
    }

    @Test
    fun `inline and changing image sources close before stable reveal`() {
        assertContains(barrier, "barrier-ready")
        assertContains(barrier, "document-started")
        assertContains(barrier, "document-loaded")
        assertContains(barrier, "document-retired")
        assertContains(barrier, "IMAGE_STABILITY_MS = 0")
        assertContains(barrier, "MutationObserver")
        assertContains(
            barrier,
            "IMAGE_RECONCILIATION_DELAYS_MS = [100, 400, 1000, 2000, 4000, 6000, 8000, 12000]",
        )
        assertContains(barrier, "const unsettledImages = new Set()")
        assertContains(barrier, "for (const image of unsettledImages)")
        assertContains(barrier, "priorityObserver?.unobserve(image)")
        assertContains(
            barrier,
            "reconcileCompleteImages(index === IMAGE_RECONCILIATION_DELAYS_MS.length - 1)",
        )
        assertContains(barrier, "!(image.naturalWidth === 1 && image.naturalHeight === 1)")
        assertContains(barrier, "const stableImageSources = new WeakMap()")
        assertContains(barrier, "stableImageSources.get(image) === source")
        assertContains(barrier, "stableImageSources.set(image, source)")
        assertContains(barrier, "if (stableSourceUnchanged) continue")
        assertContains(barrier, "record.target instanceof HTMLSourceElement")
        assertContains(barrier, "attributeFilter: [\"src\", \"srcset\", \"sizes\", \"media\", \"type\"]")
        assertContains(barrier, "priorityObserver?.unobserve(record.target)")
        assertContains(barrier, "resetImage(record.target)")
        assertContains(barrier, "imageSource(image) === source")
        assertContains(barrier, "image.hasAttribute(STABLE_IMAGE_ATTRIBUTE)")
        assertContains(barrier, "hasInlineImageSource(record.target)")
        assertContains(barrier, "MAX_INLINE_DECISIONS = 64")
        assertFalse(barrier.contains("MAX_INLINE_IMAGES_PER_DOCUMENT"))
        assertContains(barrier, "inlineImageIsBounded")
        assertContains(barrier, "inline-raster-decision")
        assertContains(barrier, "documentToken,")
        assertContains(barrier, "priority: immediateImagePriority(image)")
        assertContains(barrier, "releaseRemovedImages")
        assertContains(barrier, "IntersectionObserver")
        assertContains(barrier, "image-priority")
        assertContains(barrier, "MAX_PRIORITY_SOURCES = 256")
        assertContains(barrier, "MAX_COMPACT_SOURCE_DIAGNOSTICS = 64")
        assertContains(barrier, "compactSourceDiagnosticsEnabled")
        assertContains(barrier, "compact-source-diagnostics-config")
        assertContains(barrier, "compact-image-source-metadata")
        assertContains(barrier, "style-raster-carrier-summary")
        assertContains(barrier, "MAX_STYLE_CARRIER_DIAGNOSTIC_ELEMENTS = 2048")
        assertContains(barrier, "requestIdleCallback")
        assertContains(barrier, "inlineImageSource(image).length > 0")
        assertContains(barrier, "hasLargerWidthCandidate")
        assertContains(barrier, "pictureSources")
        assertContains(ads, "completeInitialScan")
        assertContains(ads, "document.addEventListener(\"DOMContentLoaded\", completeInitialScan")
        assertContains(ads, "data-glosh-dag-ads-initial-ready")
        assertContains(barrier, "document-sanitized-ready")
        assertContains(barrier, "maybeReportInitialDocumentReady")
        assertContains(background, "MAX_PRIORITY_HINTS = 256")
        assertContains(background, "normalizePriority")
        assertContains(barrier, "pendingImages.get(image) !== request")
        assertContains(background, "MAX_INLINE_IMAGE_BYTES = MAX_IMAGE_BYTES")
        assertContains(background, "capturedBytes + bytes.byteLength > MAX_CAPTURED_BYTES")
        assertContains(barrier, "blobDataUrl")
        assertContains(barrier, "MAX_INLINE_DECISION_SOURCE_CHARS")
        assertFalse(barrier.contains("MAX_INLINE_NATURAL_EDGE"))
        assertFalse(barrier.contains("MAX_INLINE_RENDERED_EDGE"))
        assertContains(background, "decodeInlineRaster")
        assertContains(background, "browser.runtime.onMessage.addListener")
        assertFalse(barrier.contains(".src ="))
        assertFalse(barrier.contains(".srcset ="))
        assertContains(css, "img[src^=\"data:\" i]")
        assertContains(css, "img[src^=\"blob:\" i]")
        assertContains(css, "img[srcset*=\"data:image\" i]")
        assertContains(css, "picture:has(source[srcset*=\"data:image\" i]) img")
        assertContains(css, "svg image[href^=\"data:\" i]")
        assertContains(css, "background-image: none !important")
        assertContains(css, "img:not([data-glosh-dag-stable=\"true\"])")
        assertFalse(css.contains("[src^=\"http"))
    }

    @Test
    fun `vector UI video and advertisements stay isolated`() {
        assertContains(background, "IMAGE_MIME_PATTERN")
        assertFalse(background.contains("SAFE_UI_MIME_PATTERN"))
        assertFalse(background.contains("SAFE_UI_URL_PATTERN"))
        assertContains(background, "BLOCKED_RESOURCE_TYPES")
        assertContains(background, "VIDEO_MEDIA_MIME_PATTERN")
        assertContains(background, "AUDIO_MEDIA_MIME_PATTERN")
        assertContains(background, "VIDEO_MANIFEST_MIME_PATTERN")
        assertContains(manifest, "\"ads.js\"")
        assertContains(ads, "EXPLICIT_AD_SELECTOR")
        assertContains(ads, "SEARCH_QUERY_KEYS")
        assertContains(ads, "isSearchResultsDocument")
        assertContains(ads, "NodeFilter.SHOW_TEXT")
        assertContains(ads, "observeDynamicSponsoredResults")
        assertContains(ads, "new MutationObserver")
        assertContains(ads, "childList: true")
        assertContains(ads, "characterData: true")
        assertFalse(ads.contains("attributes: true"))
        assertFalse(ads.contains("querySelectorAll?.(\"span,div\")"))
        assertContains(css, "[data-ad-slot]")
        assertContains(css, "glosh-dag-page-ad-hidden")
        assertContains(videoLab, "INITIAL_COVERED_CAPTURE_COUNT = 2")
        assertContains(videoLab, "MAX_CAPTURE_COUNT = 7_200")
        assertContains(videoLab, "SMOOTH_CAPTURE_DELAY_MS = 500")
        assertContains(videoLab, "video-lab-cover-request")
        assertContains(videoLab, "video-lab-frame-request")
        assertContains(videoLab, "video-lab-smooth-start")
        assertContains(videoRecord, "smoothGrantIdentity: null")
        assertContains(videoPlayback, "record.smoothGrantIdentity = Object.freeze(dependencies.grantIdentity(record))")
        assertContains(videoLifecycle, "record.smoothGrantIdentity ?? dependencies.grantIdentity(record)")
        assertContains(videoIsolation, "const originalAudioStates = new WeakMap()")
        assertContains(videoRecord, "originalMuted: audioState.muted")
        assertContains(videoPlayback, "smooth_visibility_ready")
        assertContains(videoPlayback, "smooth_audio_restored")
        assertContains(background, "opacity: 1 !important")
        assertContains(activity, "handleVideoLabSmoothStart")
        assertContains(videoCapture, "record.video.muted = true")
        assertContains(videoCapture, "record.video.volume = 0")
        assertContains(videoLab, "video.addEventListener(\"volumechange\", keepMuted)")
        assertContains(
            videoCapture,
            "dependencies.document.documentElement.hasAttribute(dependencies.fixtureAttribute)",
        )
        assertContains(videoLab, "data-glosh-dag-video-lab-token")
        assertContains(videoLab, "FRAME_RESULT_TIMEOUT_MS = 2_500")
        assertContains(videoCapture, "record.framePending ||")
        assertContains(videoPlayback, "requestVideoFrameCallback")
        assertContains(videoLab, "frameSequence")
        assertContains(videoLab, "viewportEpoch")
        assertContains(background, "isVideoLabEligibleSender")
        assertContains(manifest, "\"video-lab-fixture.js\"")
        assertFalse(extensionRoot.resolve("video-lab-fixture.js").readText().contains("video.play()"))
        assertContains(activity, "PERMISSION_AUTOPLAY_INAUDIBLE")
        assertContains(activity, "DagVideoLabAutoplayPolicy.allow")
        assertContains(activity, "videoLabMode == VideoLabMode.Fixture")
        assertContains(background, "browser.tabs.insertCSS")
        assertContains(background, "browser.tabs.removeCSS")
        assertContains(background, "hasCurrentVideoLabGrant")
        assertContains(css, "video,")
        assertFalse(css.contains("data-glosh-dag-video-lab"))
    }

    @Test
    fun `active protection has no store device or document remapping exceptions`() {
        val activeProtection = "$background\n$barrier\n$videoLab\n$css"
        listOf("cheeky", "mimo", "fravega", "sm-a235", "sm-s908").forEach { forbidden ->
            assertFalse(activeProtection.contains(forbidden, ignoreCase = true))
        }
        assertFalse(background.contains("media-presentation"))
    }
}
