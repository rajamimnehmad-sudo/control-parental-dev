# CHROME-H20-ORIGINAL-UI-SVG-AUTHORITY-06A

## Result

`BLOCKED` for the mandatory battery A/B comparison. The SVG authority, Chrome compatibility fixes, physical SVG gate, lifecycle checks, security absolutes, and 47-minute final long-run passed and are preserved at the functional SHA recorded below.

- Base SHA: `8abd7762bc18261cff9c7512f9821da7b233a4b7`
- Governance SHA: `9323b126dde3b91e21e2136a11d087fa3c1f0ca1`
- Functional SHA before this evidence commit: `76f285be92945857e5de89423f5df7edf54fc7a9`
- Device: Samsung A23, Android 14, Chrome official
- APK: DEV423
- APK SHA-256: `6388dd7904c94f76f65533200ccf17590c964c2651c619abcfe30727ef85024b`
- Update-in-place; Chrome data/cache were not cleared; bootstrap reset count remained `3`.

## Original UI SVG authority

The implementation adds a separate fail-closed authority for bounded, self-contained UI SVG without enabling global `data:` or `blob:` image sources.

- Native validator enforces exact SVG MIME, bounded bytes/tree/path complexity, safe XML parsing, explicit SVG vocabulary/attributes, internal-only references, coherent IDs, and rejects active, raster, external, nested data/blob, malformed, oversized, or pathological content.
- Content-addressed, generation/capability-scoped registry serves exact validated bytes from a dedicated internal HTTPS endpoint. GET/HEAD only; wrong capability/digest, stale generation, unregistered asset, or capacity exhaustion fail closed.
- CSP rewriting adds only the exact internal origin to each effective image policy, including header/default fallback and meta CSP paths. `data:` and `blob:` remain blocked.
- Tokenized CSS rewriting covers external stylesheets, initial style tags/attributes, data SVG image/icon contexts, and dynamic CSS/markup/Shadow DOM paths. Ordinary strings and raster data URLs are not promoted.
- Network `image/svg+xml` is structurally validated and delivered byte-for-byte only on pass; it is never sent to R3.1.
- Accepted inline SVG retains original nodes, attributes, geometry, fill/stroke, CSS, interaction, and responsive behavior. The prior geometry/color reconstruction behavior was removed for admitted vectors.

The controlled physical fixture passed all 12 cases on the final APK:

`INLINE_ORIGINAL`, `INLINE_CLICK`, `STATIC_CSS`, `EXTERNAL_CSS`, `STYLE_ATTRIBUTE`, `DATA_IMAGE`, `FAVICON`, `NETWORK_SVG`, `UNSAFE_NETWORK_FAIL_CLOSED`, `DYNAMIC_CSS`, `RASTER_FAIL_CLOSED`, and `UNSAFE_INLINE_FAIL_CLOSED`.

Final authority counters included 52 registered UI SVG assets / 22,419 bytes, 270 CSS rewrites, 140 accepted network SVGs, 9 rejected network SVGs, 15 internal assets served, and 0 internal asset rejects.

## Compatibility and UI fidelity

Real Chrome pages covered traditional HTML, large documents, search, editorial, reference/documentation, SPA/package UI, ecommerce, image-heavy/lazy media, responsive rotation, multiple origins/CDNs, menus, forms, buttons, links, and SVG-heavy UI.

- Frávega: original logo, hamburger/search UI, buttons, modal, category/product imagery, cards, pricing and lazy-load were present. The generic `iframe.style` read compatibility defect was fixed while mutation paths remain guarded.
- Wikipedia Argentina: original navbar, icons, SVG UI, text and images rendered. Its 2.745 MiB document exposed a generic 2 MiB admission ceiling; the bounded document limit is now 4 MiB with tests.
- Google search: results, controls, navigation and dynamic scroll rendered normally.
- MDN `mask-image`: CSS/SVG-heavy reference UI survived reload and portrait/landscape/portrait.
- npm React: SPA/package UI and navigation remained intact.
- Reuters: original logo/navbar, headlines, SAFE photos and layout rendered; filtered media used localized placeholders.
- Wikimedia Commons: image-heavy/lazy scrolling and reload remained stable.
- Android reference: menus, reference content, scroll, reload and responsive relayout remained stable.
- Mercado Libre: original logo, search, hamburger, cart, badges and banner rendered; BLOCK media used localized placeholders without replacing UI icons.

Other generic fixes in this batch add backpressure for burst image admission, permit read-only protected UI style inspection, and preserve real-web readiness when a controlled fixture lease expires.

The DAG browser was inspected only as a reference. Its older safe-vector policy supports the same separation of vector UI from raster media but is Gecko/DAG-specific and less complete; no DAG code was copied into Chrome.

## Lifecycle and physical gates

- Chrome force-stop/relaunch: new browser PID, protection recovered, controlled SVG fixture remained 12/12 PASS.
- Wi-Fi disconnect/reconnect: fail-close/recovery passed.
- Background/foreground, multiple tabs, reload, back/forward/warm state, scrolling and portrait/landscape/portrait were exercised without reset or data clearing.
- An update-time Accessibility rebind delay correctly triggered `accessibility_lost` fail-close and Chrome suspension. Once Accessibility was bound again, STOP/START recovered automatically without bootstrap reset.
- Only one ADB-visible physical device was available; no second provisioned Samsung was available for the optional smoke.

## Final long-run

Final APK ran for 2,828 seconds (`47m08s`) continuously across the real-page matrix, ending back on Frávega.

- Battery: 50% to 45%; charge counter 2,515,000 to 2,290,000 microampere-hours.
- Temperature: 25.5 to 26.9 degrees C; no thermal escalation.
- MemAvailable: minimum 516,788 KiB, maximum 1,052,216 KiB, final 598,684 KiB.
- Representative active renderer RSS remained below about 386 MiB; no growth toward the 650 MiB stop threshold.
- Renderer CPU spikes above 100% occurred only during explicit scroll/reload/navigation and returned to idle in the next representative samples. No sustained idle runaway was observed.
- Final state: `active=true`, `ready=true`, `chromeSuspended=false`.

Final network/security counters:

- requests 1,707; SAFE original 492; BLOCK replaced 4; UNKNOWN replaced 0; unsupported replaced 111.
- raw BLOCK/UNKNOWN `0/0`.
- proxy queue rejects `0`; decision queue rejects/timeouts `0/0`.
- protect success/failure `149/0`.
- QUIC/direct-TCP bypass `0/0`.
- document transform outstanding `0`; ready tokens were bounded and tied to transformed documents.
- controlled SVG matrix remained 12/12 PASS.
- bootstrap reset count `3`.

ApplicationExitInfo and logs after the final install showed no new crash, ANR, OOM, or LOW_MEMORY. The only Chrome main-process exit was the intentional lifecycle force-stop; renderer exits were normal `ISOLATED NOT NEEDED` recycling.

## Battery evidence and blocker

ARM B (H20 + Byte Gate + R3.1 selective ON) completed for 1,886 seconds (`31m26s`) on Frávega with repeated scrolling/reloads:

- charge counter 2,790,000 to 2,640,000 microampere-hours; battery 55% to 52%; temperature 27.1 to 27.0 degrees C;
- renderer RSS approximately 315 MiB initially and 289 MiB finally, with no growth trend;
- MemAvailable remained approximately 798-890 MiB;
- Chrome/protection security absolutes stayed at zero.

A 621-second screen-off/background observation used 25,000 microampere-hours while temperature fell from 27.0 to 25.0 degrees C. Chrome browser/renderers remained at 0% in representative samples and request/inference counters did not loop. The main Glosh process showed low persistent worker activity; a controlled cadence optimization did not yield a conclusive improvement and was fully reverted before the final APK.

ARM A cannot be executed on this provisioned Device Owner A23 without changing the release-authority contract. The STOP command correctly rolls back proxy/CA/VPN state and the independent guard suspends Chrome fail-closed. Android reports the suspension owner as the Device Owner, and `cmd package unsuspend` cannot override it. Consequently there is no authorized way in this ticket to run Chrome normal / protection OFF on the same device and workload. Removing that suspension or adding an OFF-mode release path would modify release authority, which the ticket explicitly forbids.

Because the mandatory comparable ARM A is unavailable, attributable A/B battery overhead cannot be established and the product candidate cannot be declared PASS. This is the terminal blocker; the valid SVG/compatibility implementation is preserved for review.

## Automated validation

- `:app-user:testDevDebugUnitTest`: PASS.
- `:app-user:compileDevDebugKotlin`: PASS.
- `:app-user:lintDevDebug`: PASS.
- `:app-user:assembleDevDebug`: PASS.
- Focused lifecycle, guard, SVG authority, CSS/CSP/document-transform, proxy, and image-authority tests: PASS.
- Changed-file Kotlin formatting checks: PASS. Aggregate repository formatting still reports only pre-existing violations in untouched files.

## Rollback target

Final postflight must STOP the Chrome lab, restore the original 600,000 ms screen timeout, disable stay-awake, restore automatic rotation, and verify proxy/CA/VPN cleanup plus reset count `3`. STOP intentionally leaves Chrome suspended fail-closed under the existing release-authority contract.

Postflight completed: the Chrome lab/proxy stopped, the ephemeral CA was removed, global proxy and always-on/lockdown VPN settings are null, timeout is 600,000 ms, automatic rotation is restored, Wi-Fi is connected, and reset count remains `3`. STOP restored the pre-existing Content Filter DNS VPN (narrow DNS routes only); the laboratory full-tunnel is not present. Chrome is suspended fail-closed by the Device Owner as designed.
