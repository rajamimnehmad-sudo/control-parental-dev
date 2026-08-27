package com.contentfilter.user.chromedataplane

import com.contentfilter.feature.accessibility.chromevisual.ChromeVisualShieldLabControl
import java.util.Base64

/** Controlled DEV origin for transport/identity/cleanup validation. It grants no production authority. */
internal object ChromeVisualShieldFixture {
    const val ControlPath = "/web13br/control"
    const val SentinelPath = "/web13br/sentinel"
    const val SecondPath = "/web13br/second"
    const val DelayedPath = "/web13br/delayed"
    const val CanvasPrefix = "/web13br/canvas/"
    const val PayloadPrefix = "/web13br/payload/"
    const val RenderedPrefix = "/web13br/rendered/"
    const val RenderIdentityPath = "/web13br/render-identity"
    const val SafeCanvasPath = "${CanvasPrefix}safe"
    const val BlockCanvasPath = "${CanvasPrefix}block"
    const val SafePayloadPath = "${PayloadPrefix}safe"
    const val BlockPayloadPath = "${PayloadPrefix}block"

    fun responseFor(request: ChromePhotosProxyRequest): ChromePhotosFixtureResponse? {
        val path = request.target.substringBefore('?').substringBefore('#')
        ChromeVisualShieldRegionDiscoveryFixture.responseFor(request, path)?.let { return it }
        val response =
            when (path) {
                ControlPath -> pageResponse(path, page("control", sentinel = false, delayed = false))
                SentinelPath -> pageResponse(path, page("sentinel", sentinel = true, delayed = false))
                SecondPath -> pageResponse(path, page("second-document", sentinel = true, delayed = false))
                DelayedPath -> pageResponse(path, page("delayed", sentinel = false, delayed = true))
                RenderIdentityPath -> renderIdentityResponse(path)
                else -> dynamicSampleResponse(request, path) ?: return null
            }
        return response
    }

    fun canvasPath(sample: ChromeVisualShieldFixtureSample): String = CanvasPrefix + sample.wireName

    fun payloadPath(sample: ChromeVisualShieldFixtureSample): String = PayloadPrefix + sample.wireName

    fun renderedPath(sample: ChromeVisualShieldFixtureSample): String = RenderedPrefix + sample.wireName

    private fun dynamicSampleResponse(
        request: ChromePhotosProxyRequest,
        path: String,
    ): ChromePhotosFixtureResponse? {
        val sampleName =
            when {
                path.startsWith(RenderedPrefix) -> path.removePrefix(RenderedPrefix)
                path.startsWith(CanvasPrefix) -> path.removePrefix(CanvasPrefix)
                path.startsWith(PayloadPrefix) -> path.removePrefix(PayloadPrefix)
                else -> return null
            }
        val sample = ChromeVisualShieldFixtureSample.fromWireName(sampleName) ?: return null
        return when {
            path.startsWith(CanvasPrefix) -> canvasResponse(path, sample)
            path.startsWith(PayloadPrefix) -> payloadResponse(path, sample)
            else -> renderedResponse(request, path, sample)
        }
    }

    private fun renderedResponse(
        request: ChromePhotosProxyRequest,
        path: String,
        sample: ChromeVisualShieldFixtureSample,
    ): ChromePhotosFixtureResponse {
        val renderIdentityToken = ChromeVisualShieldLabControl.currentRenderIdentityToken()
        var acceptedAttestation: ChromeVisualShieldRenderAttestation? = null
        val body =
            if (
                request.method != "POST" ||
                request.body.size > MaxRenderAttestationBytes ||
                !ChromeVisualShieldFixtureSampleStore.isReady(sample) ||
                renderIdentityToken == null
            ) {
                "result=render_attestation_request_invalid"
            } else {
                ChromeVisualShieldRenderAttestationStore.record(
                    sample,
                    request.body.toString(Charsets.UTF_8),
                    renderIdentityToken,
                ).also {
                    if (it.startsWith("result=render_attested")) {
                        acceptedAttestation =
                            ChromeVisualShieldRenderAttestationStore.peek(sample, renderIdentityToken)
                    }
                }
            }
        val result =
            acceptedAttestation?.let { attestation ->
                val nativeResult =
                    ChromeVisualShieldLabControl.renderAttested(
                        renderIdentityToken = attestation.renderIdentityToken,
                        exactDrawOracle = attestation.exactDrawOracle(),
                    )
                if (nativeResult == "result=render_identity_attested") {
                    body
                } else {
                    ChromeVisualShieldRenderAttestationStore.clear(sample)
                    "result=render_attestation_identity_mismatch sample=${sample.wireName}"
                }
            } ?: body
        return ChromePhotosFixtureResponse(
            resourceId = "chrome-visual-shield-${path.substringAfterLast('/')}-rendered",
            contentType = "text/plain; charset=utf-8",
            originalBytes = result.toByteArray(Charsets.UTF_8),
        )
    }

    private fun renderIdentityResponse(path: String): ChromePhotosFixtureResponse {
        val token = ChromeVisualShieldLabControl.beginFixtureRender()
        val body = token?.let { "result=render_identity token=$it" } ?: "result=render_identity_unavailable"
        return ChromePhotosFixtureResponse(
            resourceId = "chrome-visual-shield-${path.substringAfterLast('/')}",
            contentType = "text/plain; charset=utf-8",
            originalBytes = body.toByteArray(Charsets.UTF_8),
        )
    }

    private fun pageResponse(
        path: String,
        body: String,
    ) = ChromePhotosFixtureResponse(
        resourceId = "chrome-visual-shield-${path.substringAfterLast('/')}",
        contentType = "text/html; charset=utf-8",
        originalBytes = body.toByteArray(Charsets.UTF_8),
    )

    private fun canvasResponse(
        path: String,
        sample: ChromeVisualShieldFixtureSample,
    ): ChromePhotosFixtureResponse = pageResponse(path, canvasPage(sample))

    private fun payloadResponse(
        path: String,
        sample: ChromeVisualShieldFixtureSample,
    ): ChromePhotosFixtureResponse {
        val bytes = ChromeVisualShieldFixtureSampleStore.payload(sample)
        val body =
            if (bytes == null) {
                """{"ready":false,"sample":"${sample.wireName}"}"""
            } else {
                try {
                    val encoded = Base64.getEncoder().encodeToString(bytes)
                    """{"ready":true,"sample":"${sample.wireName}","sha256":"${sample.expectedSha256}","base64":"$encoded"}"""
                } finally {
                    bytes.fill(0)
                }
            }
        return ChromePhotosFixtureResponse(
            resourceId = "chrome-visual-shield-${path.substringAfterLast('/')}",
            contentType = "application/json; charset=utf-8",
            originalBytes = body.toByteArray(Charsets.UTF_8),
        )
    }

    private fun canvasPage(sample: ChromeVisualShieldFixtureSample): String {
        val payloadPath = payloadPath(sample)
        val renderedPath = renderedPath(sample)
        val left = ChromeVisualShieldLabControl.RegionLeftBasisPoints / 100.0
        val top = ChromeVisualShieldLabControl.RegionTopBasisPoints / 100.0
        val width =
            (
                ChromeVisualShieldLabControl.RegionRightBasisPoints -
                    ChromeVisualShieldLabControl.RegionLeftBasisPoints
            ) / 100.0
        val height =
            (
                ChromeVisualShieldLabControl.RegionBottomBasisPoints -
                    ChromeVisualShieldLabControl.RegionTopBasisPoints
            ) / 100.0
        return """
            <!doctype html>
            <html data-fixture-signature="${ChromeVisualShieldLabControl.FixtureSignature}"
                  data-expected-sha="${sample.expectedSha256}">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
              <title>Visual Shield canvas ${sample.wireName}</title>
              <style>
                html, body { margin: 0; min-height: 100vh; background: #111; }
                #fixture-canvas {
                  position: fixed;
                  left: ${left}vw;
                  top: ${top}vh;
                  width: ${width}vw;
                  height: ${height}vh;
                  background: ${ChromeVisualShieldContainContract.NeutralBackground};
                }
              </style>
            </head>
            <body data-carrier="canvas" data-sample="${sample.wireName}">
              <canvas id="fixture-canvas" data-carrier-visible="canvas"></canvas>
              <script>
                (async () => {
                  const canvas = document.getElementById('fixture-canvas');
                  const expectedSha = '${sample.expectedSha256}';
                  let requestedRevision = 0;
                  let rendering = false;

                  const nextFrame = () => new Promise(resolve => requestAnimationFrame(resolve));
                  const renderLatest = async () => {
                    if (rendering) return;
                    rendering = true;
                    try {
                      while (requestedRevision > 0) {
                        const revision = requestedRevision;
                        requestedRevision = 0;
                        document.documentElement.dataset.renderAttested = 'false';
                        const [identityResponse, payloadResponse] = await Promise.all([
                          fetch('${ChromeVisualShieldFixture.RenderIdentityPath}', { cache: 'no-store' }),
                          fetch('$payloadPath', { cache: 'no-store' })
                        ]);
                        const identityResult = await identityResponse.text();
                        if (!identityResult.startsWith('result=render_identity token=')) {
                          throw new Error('fixture render identity unavailable');
                        }
                        const renderIdentityToken = identityResult.substring('result=render_identity token='.length);
                        const payload = await payloadResponse.json();
                        if (!payload.ready || payload.sha256 !== expectedSha) {
                          throw new Error('fixture payload unavailable');
                        }
                        const binary = atob(payload.base64);
                        const bytes = Uint8Array.from(binary, value => value.charCodeAt(0));
                        let bitmap = null;
                        try {
                          const digest = await crypto.subtle.digest('SHA-256', bytes);
                          const observedSha = Array.from(new Uint8Array(digest), value =>
                            value.toString(16).padStart(2, '0')).join('');
                          if (observedSha !== expectedSha) throw new Error('fixture sha mismatch');
                          bitmap = await createImageBitmap(new Blob([bytes], { type: 'application/octet-stream' }));
                          const sourceWidth = bitmap.width;
                          const sourceHeight = bitmap.height;
                          const canvasRect = canvas.getBoundingClientRect();
                          const dpr = window.devicePixelRatio || 1;
                          canvas.width = Math.max(1, Math.round(canvasRect.width * dpr));
                          canvas.height = Math.max(1, Math.round(canvasRect.height * dpr));
                          const context = canvas.getContext('2d', { alpha: false });
                          context.fillStyle = '${ChromeVisualShieldContainContract.NeutralBackground}';
                          context.fillRect(0, 0, canvas.width, canvas.height);
                          const containScale = Math.min(canvas.width / sourceWidth, canvas.height / sourceHeight);
                          const drawWidth = sourceWidth * containScale;
                          const drawHeight = sourceHeight * containScale;
                          const drawX = (canvas.width - drawWidth) / 2;
                          const drawY = (canvas.height - drawHeight) / 2;
                          context.drawImage(bitmap, drawX, drawY, drawWidth, drawHeight);
                          document.documentElement.dataset.carrierVisible = 'canvas';
                          document.documentElement.dataset.observedSha = observedSha;
                          document.documentElement.dataset.renderContract = '${ChromeVisualShieldContainContract.Version}';
                          document.documentElement.dataset.sourceSize = sourceWidth + 'x' + sourceHeight;
                          document.documentElement.dataset.canvasSize = canvas.width + 'x' + canvas.height;
                          document.documentElement.dataset.drawRect = [drawX, drawY, drawWidth, drawHeight].join(',');
                          await nextFrame();
                          if (requestedRevision !== 0) continue;
                          const visualViewport = window.visualViewport;
                          const viewportLeft = visualViewport ? visualViewport.offsetLeft : 0;
                          const viewportTop = visualViewport ? visualViewport.offsetTop : 0;
                          const viewportWidth = visualViewport ? visualViewport.width : window.innerWidth;
                          const viewportHeight = visualViewport ? visualViewport.height : window.innerHeight;
                          const viewportScale = visualViewport ? visualViewport.scale : 1;
                          const attestation = [observedSha, '${ChromeVisualShieldContainContract.Version}',
                            renderIdentityToken, sourceWidth, sourceHeight, canvas.width, canvas.height,
                            drawX, drawY, drawWidth, drawHeight,
                            canvasRect.left, canvasRect.top, canvasRect.width, canvasRect.height,
                            viewportLeft, viewportTop, viewportWidth, viewportHeight, viewportScale, dpr].join('|');
                          const attested = await fetch('$renderedPath', {
                            method: 'POST', headers: { 'Content-Type': 'text/plain' }, body: attestation
                          });
                          if (!attested.ok || !(await attested.text()).startsWith('result=render_attested')) {
                            requestedRevision += 1;
                            await nextFrame();
                            continue;
                          }
                          document.documentElement.dataset.renderAttested = 'true';
                          document.documentElement.dataset.renderIdentity = renderIdentityToken;
                          console.info('carrierVisible=canvas sample=${sample.wireName} sha=' + observedSha +
                            ' renderContract=${ChromeVisualShieldContainContract.Version} canvas=' +
                            canvas.width + 'x' + canvas.height + ' draw=' +
                            [drawX, drawY, drawWidth, drawHeight].join(','));
                        } finally {
                          if (bitmap) bitmap.close();
                          bytes.fill(0);
                        }
                      }
                    } finally {
                      rendering = false;
                      if (requestedRevision !== 0) void renderLatest();
                    }
                  }

                  const requestRender = () => {
                    requestedRevision += 1;
                    document.documentElement.dataset.renderAttested = 'false';
                    void renderLatest();
                  };
                  window.addEventListener('resize', requestRender);
                  window.addEventListener('orientationchange', requestRender);
                  if (window.visualViewport) window.visualViewport.addEventListener('resize', requestRender);
                  requestRender();
                })().catch(error => {
                  document.documentElement.dataset.fixtureError = error.message;
                  console.error('carrierVisible=canvas fixtureError=' + error.message);
                });
              </script>
            </body>
            </html>
            """.trimIndent()
    }

    private fun page(
        name: String,
        sentinel: Boolean,
        delayed: Boolean,
    ): String {
        val left = ChromeVisualShieldLabControl.RegionLeftBasisPoints / 100.0
        val top = ChromeVisualShieldLabControl.RegionTopBasisPoints / 100.0
        val width =
            (
                ChromeVisualShieldLabControl.RegionRightBasisPoints -
                    ChromeVisualShieldLabControl.RegionLeftBasisPoints
            ) / 100.0
        val height =
            (
                ChromeVisualShieldLabControl.RegionBottomBasisPoints -
                    ChromeVisualShieldLabControl.RegionTopBasisPoints
            ) / 100.0
        val sentinelMarkup = if (sentinel) SentinelMarkup else ""
        val delayedScript =
            if (delayed) {
                """
                <script>
                setTimeout(() => {
                  const region = document.createElement('div');
                  region.id = 'shield-sentinel';
                  region.setAttribute('aria-hidden', 'true');
                  document.body.appendChild(region);
                  document.documentElement.dataset.delayedReady = 'true';
                }, 1200);
                </script>
                """.trimIndent()
            } else {
                ""
            }
        return """
            <!doctype html>
            <html data-fixture-signature="${ChromeVisualShieldLabControl.FixtureSignature}">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
              <title>Visual Shield lab $name</title>
              <style>
                html, body { margin: 0; min-height: 400vh; background: #e8edf3; color: #102030; }
                #safe-label { position: fixed; left: 4vw; top: 8vh; font: 700 20px sans-serif; }
                #shield-sentinel {
                  position: fixed;
                  left: ${left}vw;
                  top: ${top}vh;
                  width: ${width}vw;
                  height: ${height}vh;
                  background: linear-gradient(90deg, rgb(220,20,48) 0 50%, rgb(0,0,0) 50% 100%);
                  outline: 8px solid rgb(255,238,0);
                }
              </style>
            </head>
            <body data-fixture="chrome-visual-shield-13b-r" data-stage="$name"
                  data-region-id="${ChromeVisualShieldLabControl.RegionId}">
              <div id="safe-label">GLOSH 13B-R $name</div>
              $sentinelMarkup
              $delayedScript
            </body>
            </html>
            """.trimIndent()
    }

    private const val SentinelMarkup =
        "<div id=\"shield-sentinel\" aria-hidden=\"true\"></div>"
    private const val MaxRenderAttestationBytes = 1_024
}
