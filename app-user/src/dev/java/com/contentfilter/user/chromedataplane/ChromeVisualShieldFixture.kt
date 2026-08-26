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
    const val SafeCanvasPath = "${CanvasPrefix}safe"
    const val BlockCanvasPath = "${CanvasPrefix}block"
    const val SafePayloadPath = "${PayloadPrefix}safe"
    const val BlockPayloadPath = "${PayloadPrefix}block"

    fun responseFor(request: ChromePhotosProxyRequest): ChromePhotosFixtureResponse? {
        val path = request.target.substringBefore('?').substringBefore('#')
        val response =
            when (path) {
                ControlPath -> pageResponse(path, page("control", sentinel = false, delayed = false))
                SentinelPath -> pageResponse(path, page("sentinel", sentinel = true, delayed = false))
                SecondPath -> pageResponse(path, page("second-document", sentinel = true, delayed = false))
                DelayedPath -> pageResponse(path, page("delayed", sentinel = false, delayed = true))
                else -> dynamicSampleResponse(path) ?: return null
            }
        return response
    }

    fun canvasPath(sample: ChromeVisualShieldFixtureSample): String = CanvasPrefix + sample.wireName

    fun payloadPath(sample: ChromeVisualShieldFixtureSample): String = PayloadPrefix + sample.wireName

    private fun dynamicSampleResponse(path: String): ChromePhotosFixtureResponse? {
        val sampleName =
            when {
                path.startsWith(CanvasPrefix) -> path.removePrefix(CanvasPrefix)
                path.startsWith(PayloadPrefix) -> path.removePrefix(PayloadPrefix)
                else -> return null
            }
        val sample = ChromeVisualShieldFixtureSample.fromWireName(sampleName) ?: return null
        return if (path.startsWith(CanvasPrefix)) canvasResponse(path, sample) else payloadResponse(path, sample)
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
                  const expectedSha = '${sample.expectedSha256}';
                  const response = await fetch('$payloadPath', { cache: 'no-store' });
                  const payload = await response.json();
                  if (!payload.ready || payload.sha256 !== expectedSha) throw new Error('fixture payload unavailable');
                  const binary = atob(payload.base64);
                  const bytes = Uint8Array.from(binary, value => value.charCodeAt(0));
                  const digest = await crypto.subtle.digest('SHA-256', bytes);
                  const observedSha = Array.from(new Uint8Array(digest), value => value.toString(16).padStart(2, '0')).join('');
                  if (observedSha !== expectedSha) throw new Error('fixture sha mismatch');
                  const bitmap = await createImageBitmap(new Blob([bytes], { type: 'application/octet-stream' }));
                  const sourceWidth = bitmap.width;
                  const sourceHeight = bitmap.height;
                  const canvas = document.getElementById('fixture-canvas');
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
                  bitmap.close();
                  bytes.fill(0);
                  document.documentElement.dataset.carrierVisible = 'canvas';
                  document.documentElement.dataset.observedSha = observedSha;
                  document.documentElement.dataset.renderContract = '${ChromeVisualShieldContainContract.Version}';
                  document.documentElement.dataset.sourceSize = sourceWidth + 'x' + sourceHeight;
                  document.documentElement.dataset.canvasSize = canvas.width + 'x' + canvas.height;
                  document.documentElement.dataset.drawRect = [drawX, drawY, drawWidth, drawHeight].join(',');
                  console.info('carrierVisible=canvas sample=${sample.wireName} sha=' + observedSha +
                    ' renderContract=${ChromeVisualShieldContainContract.Version} canvas=' + canvas.width + 'x' + canvas.height +
                    ' draw=' + [drawX, drawY, drawWidth, drawHeight].join(','));
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
}
