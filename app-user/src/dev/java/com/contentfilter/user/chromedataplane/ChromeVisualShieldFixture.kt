package com.contentfilter.user.chromedataplane

import com.contentfilter.feature.accessibility.chromevisual.ChromeVisualShieldLabControl

/** Controlled DEV origin for transport/identity/cleanup validation. It grants no production authority. */
internal object ChromeVisualShieldFixture {
    const val ControlPath = "/web13br/control"
    const val SentinelPath = "/web13br/sentinel"
    const val SecondPath = "/web13br/second"
    const val DelayedPath = "/web13br/delayed"

    fun responseFor(request: ChromePhotosProxyRequest): ChromePhotosFixtureResponse? {
        val path = request.target.substringBefore('?').substringBefore('#')
        val body =
            when (path) {
                ControlPath -> page("control", sentinel = false, delayed = false)
                SentinelPath -> page("sentinel", sentinel = true, delayed = false)
                SecondPath -> page("second-document", sentinel = true, delayed = false)
                DelayedPath -> page("delayed", sentinel = false, delayed = true)
                else -> return null
            }
        return ChromePhotosFixtureResponse(
            resourceId = "chrome-visual-shield-${path.substringAfterLast('/')}",
            contentType = "text/html; charset=utf-8",
            originalBytes = body.toByteArray(Charsets.UTF_8),
        )
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
