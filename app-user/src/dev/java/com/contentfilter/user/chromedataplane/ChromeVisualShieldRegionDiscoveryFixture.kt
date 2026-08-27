package com.contentfilter.user.chromedataplane

import android.util.Log
import com.contentfilter.feature.accessibility.chromevisual.ChromeVisualShieldLabControl
import com.contentfilter.feature.accessibility.chromevisual.ChromeVisualShieldRegionDiscoveryGenerationOutcome
import com.contentfilter.feature.accessibility.chromevisual.ChromeVisualShieldRegionDiscoveryRenderBinding

internal enum class ChromeVisualShieldRegionDiscoveryScenario(
    val wireName: String,
    val samples: List<ChromeVisualShieldFixtureSample>,
    val layout: ChromeVisualShieldRegionDiscoveryLayout,
    val expectComplete: Boolean = true,
) {
    CenteredSafe(
        "centered-safe",
        listOf(ChromeVisualShieldFixtureSample.Safe),
        ChromeVisualShieldRegionDiscoveryLayout.Centered,
    ),
    CenteredBlock(
        "centered-block",
        listOf(ChromeVisualShieldFixtureSample.Block),
        ChromeVisualShieldRegionDiscoveryLayout.Centered,
    ),
    OffLeftBlock(
        "off-left-block",
        listOf(ChromeVisualShieldFixtureSample.Block),
        ChromeVisualShieldRegionDiscoveryLayout.Left,
    ),
    OffRightBlock(
        "off-right-block",
        listOf(ChromeVisualShieldFixtureSample.Block),
        ChromeVisualShieldRegionDiscoveryLayout.Right,
    ),
    MultiSafeBlock(
        "multi-safe-block",
        listOf(ChromeVisualShieldFixtureSample.Safe, ChromeVisualShieldFixtureSample.Block),
        ChromeVisualShieldRegionDiscoveryLayout.Multi,
    ),
    Ambiguous(
        "ambiguous",
        listOf(ChromeVisualShieldFixtureSample.Block),
        ChromeVisualShieldRegionDiscoveryLayout.Centered,
        expectComplete = false,
    ),
    ;

    companion object {
        fun fromWireName(value: String?): ChromeVisualShieldRegionDiscoveryScenario? =
            entries.firstOrNull { it.wireName == value }
    }
}

internal enum class ChromeVisualShieldRegionDiscoveryLayout {
    Centered,
    Left,
    Right,
    Multi,
}

internal data class ChromeVisualShieldRegionDiscoveryDraw(
    val sample: ChromeVisualShieldFixtureSample,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val geometry: ChromeVisualShieldContainGeometry,
)

internal object ChromeVisualShieldRegionDiscoveryLayoutContract {
    const val Version = "canvas-content-islands-v1"
    const val NeutralBackground = "#202428"
    const val CardColor = "#f5f5f5"
    const val CardPaddingPx = 3.0

    fun geometry(
        scenario: ChromeVisualShieldRegionDiscoveryScenario,
        sources: List<Pair<Int, Int>>,
        canvasWidth: Int,
        canvasHeight: Int,
    ): List<ChromeVisualShieldContainGeometry>? {
        if (
            sources.size != scenario.samples.size ||
            sources.any { it.first <= 0 || it.second <= 0 } ||
            canvasWidth <= 0 ||
            canvasHeight <= 0
        ) {
            return null
        }
        return when (scenario.layout) {
            ChromeVisualShieldRegionDiscoveryLayout.Centered,
            ChromeVisualShieldRegionDiscoveryLayout.Left,
            ChromeVisualShieldRegionDiscoveryLayout.Right,
            -> {
                val source = sources.single()
                val contained = contain(source.first, source.second, canvasWidth * 0.72, canvasHeight * 0.82)
                val left =
                    when (scenario.layout) {
                        ChromeVisualShieldRegionDiscoveryLayout.Left -> canvasWidth * 0.05
                        ChromeVisualShieldRegionDiscoveryLayout.Right -> canvasWidth * 0.95 - contained.width
                        else -> (canvasWidth - contained.width) / 2.0
                    }
                listOf(contained.copy(left = left, top = (canvasHeight - contained.height) / 2.0))
            }
            ChromeVisualShieldRegionDiscoveryLayout.Multi ->
                sources.mapIndexed { index, source ->
                    val contained = contain(source.first, source.second, canvasWidth * 0.38, canvasHeight * 0.78)
                    val center = if (index == 0) canvasWidth * 0.25 else canvasWidth * 0.75
                    contained.copy(
                        left = center - contained.width / 2.0,
                        top = (canvasHeight - contained.height) / 2.0,
                    )
                }
        }
    }

    private fun contain(
        sourceWidth: Int,
        sourceHeight: Int,
        maximumWidth: Double,
        maximumHeight: Double,
    ): ChromeVisualShieldContainGeometry {
        val scale = minOf(maximumWidth / sourceWidth, maximumHeight / sourceHeight)
        return ChromeVisualShieldContainGeometry(0.0, 0.0, sourceWidth * scale, sourceHeight * scale)
    }
}

internal object ChromeVisualShieldRegionDiscoveryFixture {
    const val PagePrefix = "/web13br/discovery/"
    const val RenderIdentityPrefix = "/web13br/discovery-render-identity/"
    const val RenderedPrefix = "/web13br/discovery-rendered/"

    fun responseFor(
        request: ChromePhotosProxyRequest,
        path: String,
    ): ChromePhotosFixtureResponse? {
        val scenarioName =
            when {
                path.startsWith(PagePrefix) -> path.removePrefix(PagePrefix)
                path.startsWith(RenderIdentityPrefix) -> path.removePrefix(RenderIdentityPrefix)
                path.startsWith(RenderedPrefix) -> path.removePrefix(RenderedPrefix)
                else -> return null
            }
        val scenario = ChromeVisualShieldRegionDiscoveryScenario.fromWireName(scenarioName) ?: return null
        return when {
            path.startsWith(PagePrefix) -> response(path, "text/html; charset=utf-8", page(scenario))
            path.startsWith(RenderIdentityPrefix) -> renderIdentityResponse(request, path, scenario)
            else -> renderedResponse(request, path, scenario)
        }
    }

    fun pagePath(scenario: ChromeVisualShieldRegionDiscoveryScenario): String = PagePrefix + scenario.wireName

    private fun renderIdentityResponse(
        request: ChromePhotosProxyRequest,
        path: String,
        scenario: ChromeVisualShieldRegionDiscoveryScenario,
    ): ChromePhotosFixtureResponse {
        val nativeGeneration = ChromeVisualShieldLabControl.currentRegionDiscoveryGeneration()
        val key =
            request.body
                .takeIf { request.method == "POST" && it.size <= MaxRenderKeyBytes }
                ?.toString(Charsets.UTF_8)
                ?.let { ChromeVisualShieldRegionDiscoveryRenderGeometryKey.parse(scenario, it) }
        val result =
            if (
                nativeGeneration == null ||
                key == null ||
                scenario.samples.any { !ChromeVisualShieldFixtureSampleStore.isReady(it) }
            ) {
                "result=region_render_identity_request_invalid scenario=${scenario.wireName}"
            } else {
                ChromeVisualShieldRegionDiscoveryHandshakeStore.request(nativeGeneration, key) {
                    ChromeVisualShieldLabControl.beginRegionDiscoveryFixtureRender(key.digest)
                }.responseText(scenario)
            }
        Log.i(LogTag, "phase=render_identity $result")
        return response(path, "text/plain; charset=utf-8", result)
    }

    private fun renderedResponse(
        request: ChromePhotosProxyRequest,
        path: String,
        scenario: ChromeVisualShieldRegionDiscoveryScenario,
    ): ChromePhotosFixtureResponse {
        val fields = request.body.toString(Charsets.UTF_8).split('|')
        val claimedBinding = fields.toRegionDiscoveryBinding()
        val nativeGeneration = ChromeVisualShieldLabControl.currentRegionDiscoveryGeneration()
        val claim =
            if (
                request.method == "POST" &&
                request.body.size <= MaxAttestationBytes &&
                nativeGeneration != null &&
                claimedBinding != null
            ) {
                ChromeVisualShieldRegionDiscoveryHandshakeStore.claimAttestation(
                    nativeGeneration = nativeGeneration,
                    claimedBinding = claimedBinding,
                )
            } else {
                null
            }
        val result =
            if (
                claim == null ||
                scenario.samples.any { !ChromeVisualShieldFixtureSampleStore.isReady(it) }
            ) {
                "result=region_attestation_stale_or_invalid scenario=${scenario.wireName}"
            } else {
                val recorded =
                    ChromeVisualShieldRegionDiscoveryAttestationStore.record(
                        scenario = scenario,
                        body = request.body.toString(Charsets.UTF_8),
                        expectedBinding = claim.binding,
                    )
                val attestation =
                    ChromeVisualShieldRegionDiscoveryAttestationStore.peek(
                        scenario,
                        claim.binding,
                    )
                val accepted =
                    ChromeVisualShieldRegionDiscoveryHandshakeStore.executeAttestation(
                        claim,
                        ChromeVisualShieldLabControl.currentRegionDiscoveryGeneration(),
                    ) {
                        if (!recorded.startsWith("result=region_render_attested") || attestation == null) {
                            false
                        } else {
                            ChromeVisualShieldLabControl.renderAttested(
                                renderIdentityToken = claim.binding.renderIdentityToken,
                                regionDiscoveryOracle = attestation.oracle(),
                                regionDiscoveryBinding = claim.binding,
                            ) == "result=render_identity_attested"
                        }
                    }
                when {
                    accepted == null ->
                        "result=region_attestation_stale scenario=${scenario.wireName}"
                    accepted -> generationResult(scenario, claim.binding, recorded)
                    else -> {
                        ChromeVisualShieldRegionDiscoveryAttestationStore.clear(scenario)
                        "result=region_attestation_rejected scenario=${scenario.wireName} detail=$recorded"
                    }
                }
            }
        Log.i(
            LogTag,
            "phase=render_attestation $result ${ChromeVisualShieldRegionDiscoveryHandshakeStore.metrics().logText()}",
        )
        return response(path, "text/plain; charset=utf-8", result)
    }

    private fun generationResult(
        scenario: ChromeVisualShieldRegionDiscoveryScenario,
        binding: ChromeVisualShieldRegionDiscoveryRenderBinding,
        recorded: String,
    ): String =
        when (
            ChromeVisualShieldLabControl.awaitRegionDiscoveryGeneration(
                binding,
                GenerationOutcomeTimeoutMillis,
            )
        ) {
            ChromeVisualShieldRegionDiscoveryGenerationOutcome.Completed -> recorded
            ChromeVisualShieldRegionDiscoveryGenerationOutcome.Invalidated -> {
                ChromeVisualShieldRegionDiscoveryAttestationStore.clear(scenario)
                "result=region_generation_invalidated scenario=${scenario.wireName} " +
                    "contentEpoch=${binding.contentEpoch} regionSequence=${binding.regionSequence}"
            }
            ChromeVisualShieldRegionDiscoveryGenerationOutcome.Stopped ->
                "result=region_generation_stopped scenario=${scenario.wireName}"
            ChromeVisualShieldRegionDiscoveryGenerationOutcome.TimedOut ->
                "result=region_generation_timeout scenario=${scenario.wireName}"
        }

    private fun page(scenario: ChromeVisualShieldRegionDiscoveryScenario): String {
        val left = ChromeVisualShieldLabControl.RegionLeftBasisPoints / 100.0
        val top = ChromeVisualShieldLabControl.RegionTopBasisPoints / 100.0
        val width =
            (ChromeVisualShieldLabControl.RegionRightBasisPoints - ChromeVisualShieldLabControl.RegionLeftBasisPoints) /
                100.0
        val height =
            (ChromeVisualShieldLabControl.RegionBottomBasisPoints - ChromeVisualShieldLabControl.RegionTopBasisPoints) /
                100.0
        val samples = scenario.samples.joinToString(",") { "'${it.wireName}'" }
        val shas = scenario.samples.joinToString(",") { "'${it.expectedSha256}'" }
        val payloads = scenario.samples.joinToString(",") { "'${ChromeVisualShieldFixture.payloadPath(it)}'" }
        return """
            <!doctype html>
            <html data-fixture-signature="${ChromeVisualShieldLabControl.FixtureSignature}"
                  data-discovery-scenario="${scenario.wireName}">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
              <title>Visual Shield discovery ${scenario.wireName}</title>
              <style>
                html, body { margin: 0; min-height: 100vh; background: #111; }
                #discovery-canvas { position: fixed; left: ${left}vw; top: ${top}vh;
                  width: ${width}vw; height: ${height}vh;
                  background: ${ChromeVisualShieldRegionDiscoveryLayoutContract.NeutralBackground}; }
              </style>
            </head>
            <body data-carrier="canvas" data-never-release="true">
              <canvas id="discovery-canvas" data-carrier-visible="canvas"></canvas>
              <script>
                (async () => {
                  const canvas = document.getElementById('discovery-canvas');
                  const sampleIds = [$samples];
                  const expectedShas = [$shas];
                  const payloadPaths = [$payloads];
                  let renderRequested = false;
                  let rendering = false;
                  let lastSubmittedGeometryKey = null;
                  const frame = () => new Promise(resolve => requestAnimationFrame(resolve));
                  const sha256 = async bytes => Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256', bytes)),
                    value => value.toString(16).padStart(2, '0')).join('');
                  const contain = (sw, sh, mw, mh) => {
                    const scale = Math.min(mw / sw, mh / sh);
                    return { width: sw * scale, height: sh * scale };
                  };
                  const layout = (sources, width, height) => {
                    if ('${scenario.layout.name}' === 'Multi') return sources.map((source, index) => {
                      const size = contain(source.width, source.height, width * 0.38, height * 0.78);
                      const center = index === 0 ? width * 0.25 : width * 0.75;
                      return { x: center - size.width / 2, y: (height - size.height) / 2, ...size };
                    });
                    const source = sources[0];
                    const size = contain(source.width, source.height, width * 0.72, height * 0.82);
                    let x = (width - size.width) / 2;
                    if ('${scenario.layout.name}' === 'Left') x = width * 0.05;
                    if ('${scenario.layout.name}' === 'Right') x = width * 0.95 - size.width;
                    return [{ x, y: (height - size.height) / 2, ...size }];
                  };
                  const geometrySnapshot = () => {
                    const vv = window.visualViewport;
                    const viewportWidth = vv ? vv.width : innerWidth;
                    const viewportHeight = vv ? vv.height : innerHeight;
                    const browserControlsHeight = Math.max(0, screen.height - viewportHeight);
                    canvas.style.left = (viewportWidth * 0.15) + 'px';
                    canvas.style.top = Math.max(0, screen.height * 0.25 - browserControlsHeight) + 'px';
                    canvas.style.width = (viewportWidth * 0.70) + 'px';
                    canvas.style.height = (screen.height * 0.30) + 'px';
                    const rect = canvas.getBoundingClientRect();
                    const dpr = window.devicePixelRatio || 1;
                    const backingWidth = Math.max(1, Math.round(rect.width * dpr));
                    const backingHeight = Math.max(1, Math.round(rect.height * dpr));
                    const orientation = screen.orientation && screen.orientation.type
                      ? screen.orientation.type
                      : (viewportWidth >= viewportHeight ? 'landscape' : 'portrait');
                    const viewport = {
                      left: vv ? vv.offsetLeft : 0, top: vv ? vv.offsetTop : 0,
                      width: vv ? vv.width : innerWidth, height: vv ? vv.height : innerHeight,
                      scale: vv ? vv.scale : 1
                    };
                    const keyFields = ['${scenario.wireName}',
                      '${ChromeVisualShieldRegionDiscoveryLayoutContract.Version}', expectedShas.join(','), orientation,
                      viewport.left, viewport.top, viewport.width, viewport.height, viewport.scale, dpr,
                      rect.left, rect.top, rect.width, rect.height, backingWidth, backingHeight];
                    return { vv, viewport, rect, dpr, backingWidth, backingHeight, keyBody:keyFields.join('|') };
                  };
                  const responseFields = text => Object.fromEntries(text.trim().split(/\s+/).map(field => {
                    const separator = field.indexOf('=');
                    return separator < 0 ? [field, ''] : [field.substring(0, separator), field.substring(separator + 1)];
                  }));
                  const renderLatest = async () => {
                    if (rendering) return;
                    rendering = true;
                    try {
                      while (renderRequested) {
                        renderRequested = false;
                        const geometry = geometrySnapshot();
                        if (geometry.keyBody === lastSubmittedGeometryKey) continue;
                        lastSubmittedGeometryKey = geometry.keyBody;
                        const identityResponse = await fetch('${RenderIdentityPrefix}${scenario.wireName}', {
                          method:'POST', headers:{'Content-Type':'text/plain'}, cache:'no-store', body:geometry.keyBody
                        });
                        const identityText = await identityResponse.text();
                        const identity = responseFields(identityText);
                        if (identity.result === 'region_render_reuse') continue;
                        if (identity.result !== 'region_render_new' || !identity.token || !identity.renderKey ||
                            !identity.protectionSessionId || !identity.windowId || !identity.contentEpoch ||
                            !identity.viewportEpoch || !identity.regionSequence) {
                          throw new Error('identity unavailable');
                        }
                        const token = identity.token;
                        const renderKey = identity.renderKey;
                        const payloads = await Promise.all(payloadPaths.map(path => fetch(path, {cache:'no-store'}).then(r => r.json())));
                        const sources = [];
                        try {
                          for (let index = 0; index < payloads.length; index += 1) {
                            const payload = payloads[index];
                            if (!payload.ready || payload.sha256 !== expectedShas[index]) throw new Error('payload unavailable');
                            const bytes = Uint8Array.from(atob(payload.base64), value => value.charCodeAt(0));
                            try {
                              if (await sha256(bytes) !== expectedShas[index]) throw new Error('sha mismatch');
                              sources.push(await createImageBitmap(new Blob([bytes], {type:'application/octet-stream'})));
                            } finally { bytes.fill(0); }
                          }
                          if (geometrySnapshot().keyBody !== geometry.keyBody) {
                            renderRequested = true;
                            continue;
                          }
                          canvas.width = geometry.backingWidth;
                          canvas.height = geometry.backingHeight;
                          const context = canvas.getContext('2d', {alpha:false});
                          context.fillStyle = '${ChromeVisualShieldRegionDiscoveryLayoutContract.NeutralBackground}';
                          context.fillRect(0, 0, canvas.width, canvas.height);
                          if (${scenario == ChromeVisualShieldRegionDiscoveryScenario.Ambiguous}) {
                            const gradient = context.createLinearGradient(0, 0, canvas.width, 0);
                            gradient.addColorStop(0, '#202428'); gradient.addColorStop(1, '#d4d7da');
                            context.fillStyle = gradient; context.fillRect(0, 0, canvas.width, canvas.height);
                          }
                          const draws = layout(sources, canvas.width, canvas.height);
                          draws.forEach((draw, index) => {
                            const pad = ${ChromeVisualShieldRegionDiscoveryLayoutContract.CardPaddingPx};
                            context.fillStyle = '${ChromeVisualShieldRegionDiscoveryLayoutContract.CardColor}';
                            context.fillRect(draw.x - pad, draw.y - pad, draw.width + pad * 2, draw.height + pad * 2);
                            context.drawImage(sources[index], draw.x, draw.y, draw.width, draw.height);
                          });
                          await frame();
                          if (geometrySnapshot().keyBody !== geometry.keyBody) {
                            renderRequested = true;
                            continue;
                          }
                          const fields = ['${scenario.wireName}', '${ChromeVisualShieldRegionDiscoveryLayoutContract.Version}',
                            token, renderKey, identity.protectionSessionId, identity.windowId, identity.contentEpoch,
                            identity.viewportEpoch, identity.regionSequence, canvas.width, canvas.height,
                            geometry.rect.left, geometry.rect.top,
                            geometry.rect.width, geometry.rect.height, geometry.viewport.left, geometry.viewport.top,
                            geometry.viewport.width, geometry.viewport.height, geometry.viewport.scale, geometry.dpr,
                            ${scenario.expectComplete},
                            draws.map((draw, index) => [sampleIds[index], expectedShas[index], sources[index].width,
                              sources[index].height, draw.x, draw.y, draw.width, draw.height].join(',')).join(';')];
                          const attested = await fetch('${RenderedPrefix}${scenario.wireName}', {
                            method:'POST', headers:{'Content-Type':'text/plain'}, body:fields.join('|')
                          });
                          const attestationResult = await attested.text();
                          if (attestationResult.startsWith('result=region_generation_invalidated')) {
                            lastSubmittedGeometryKey = null;
                            renderRequested = true;
                            continue;
                          }
                          if (!attested.ok || !attestationResult.startsWith('result=region_render_attested')) {
                            document.documentElement.dataset.fixtureError = 'attestation rejected';
                            continue;
                          }
                        } finally {
                          sources.forEach(bitmap => bitmap.close());
                        }
                      }
                    } finally { rendering = false; if (renderRequested) void renderLatest(); }
                  };
                  const requestRender = () => { renderRequested = true; void renderLatest(); };
                  addEventListener('resize', requestRender); addEventListener('orientationchange', requestRender);
                  if (visualViewport) visualViewport.addEventListener('resize', requestRender);
                  requestRender();
                })().catch(error => { document.documentElement.dataset.fixtureError = error.message; });
              </script>
            </body>
            </html>
            """.trimIndent()
    }

    private fun response(
        path: String,
        contentType: String,
        body: String,
    ) = ChromePhotosFixtureResponse(
        resourceId = "chrome-visual-shield-discovery-${path.substringAfterLast('/')}",
        contentType = contentType,
        originalBytes = body.toByteArray(Charsets.UTF_8),
    )

    private const val MaxAttestationBytes = 4_096
    private const val MaxRenderKeyBytes = 2_048
    private const val GenerationOutcomeTimeoutMillis = 20_000L
    private const val LogTag = "GloshR2AHandshake"
}

private fun List<String>.toRegionDiscoveryBinding(): ChromeVisualShieldRegionDiscoveryRenderBinding? {
    if (size < 9) return null
    return ChromeVisualShieldRegionDiscoveryRenderBinding(
        protectionSessionId = get(4).toLongOrNull() ?: return null,
        windowId = get(5).toIntOrNull() ?: return null,
        contentEpoch = get(6).toLongOrNull() ?: return null,
        viewportEpoch = get(7).toLongOrNull() ?: return null,
        regionSequence = get(8).toLongOrNull() ?: return null,
        renderIdentityToken = get(2),
        renderGeometryKeyDigest = get(3),
    ).takeIf { it.isStructurallyValid() }
}

private fun ChromeVisualShieldRegionDiscoveryHandshakeRequestResult.responseText(
    scenario: ChromeVisualShieldRegionDiscoveryScenario,
): String {
    val prefix =
        when (this) {
            is ChromeVisualShieldRegionDiscoveryHandshakeRequestResult.NewRenderRequired ->
                "result=region_render_new scenario=${scenario.wireName} " +
                    "token=${binding.renderIdentityToken} renderKey=$renderKeyDigest " +
                    "protectionSessionId=${binding.protectionSessionId} windowId=${binding.windowId} " +
                    "contentEpoch=${binding.contentEpoch} viewportEpoch=${binding.viewportEpoch} " +
                    "regionSequence=${binding.regionSequence}"
            is ChromeVisualShieldRegionDiscoveryHandshakeRequestResult.Reuse ->
                "result=region_render_reuse scenario=${scenario.wireName} renderKey=$renderKeyDigest phase=${phase.name.lowercase()}"
            is ChromeVisualShieldRegionDiscoveryHandshakeRequestResult.Rejected ->
                "result=region_render_rejected scenario=${scenario.wireName} renderKey=$renderKeyDigest reason=$reason"
        }
    return "$prefix ${metrics.logText()}"
}

private fun ChromeVisualShieldRegionDiscoveryHandshakeMetrics.logText(): String =
    "identityRequests=$requests beginFixtureRenderCount=$beginFixtureRenderCount reused=$reused " +
        "attestationClaims=$attestationClaims attestationAccepted=$attestationAccepted " +
        "attestationRejected=$attestationRejected staleAttestationDropped=$staleAttestationDropped " +
        "generationReplacements=$generationReplacements"
