# Chrome Stock Media Authority

## Scope

Glosh filters photos and ordinary web media delivered through their normal network or raster APIs while the user keeps the official stock Chrome application.

The security authority is content identity, not screen geometry:

`network body digest + canonical format + GloshIA model/policy epoch`.

Scroll position, viewport, element bounds and orientation are not part of a photo's identity and do not grant or revoke a verdict.

## Authoritative outcomes

- `SAFE / model_allow`: Chrome receives the exact inspected bytes.
- `BLOCK / model_filter`: Chrome receives a Glosh placeholder; original bytes delivered to Chrome must remain zero.
- `UNKNOWN`, engine/decode error, unavailable or unsupported media: Chrome receives a placeholder or the sink remains hidden; original bytes delivered to Chrome must remain zero.
- Renderer-local photo/media sinks that cannot establish byte authority (`data:`, `blob:`, Canvas, OffscreenCanvas, ImageBitmap, WebGL/WebGPU media, media SVG and equivalent ordinary sinks) remain fail-close.

The document surface may become visible only after the current foreground Chrome window exposes the exact ready token issued for the transformed top-level document and current policy epoch. Screenshots and crops are evidence tools only; they never authorize presentation.

## Included media paths

- `img`, `picture`, `source/srcset` and HTTP(S) CSS images;
- static JPEG, PNG, WebP and AVIF;
- CDN/cross-origin responses, redirects, lazy loading, infinite scroll and SPA source replacement;
- local image/data/blob and normal raster APIs listed above;
- external SVG and conservative inline SVG handling;
- shadow roots and transformed HTTP(S) subdocuments;
- cache, Service Worker and managed Chrome surfaces to the extent closed by the H19 reset, response policy, document shield and supported Android enterprise policies.

Animated images, video, PDF, unsupported containers and ambiguous/partial entities initially remain fail-close.

## Explicit non-goal

H19 does **not** claim control of every possible pixel. A deliberately hostile page may synthesize a photographic-looking raster solely from generic HTML/CSS primitives such as thousands of `div`/`span` nodes, glyphs, borders, shadows, gradients or solid colors without using a media resource, Canvas or another raster API.

That H18 negative control is outside the photo/media filtering contract. Ordinary CSS and JavaScript must not be disabled merely to hide it.

## Product-status boundary

A technical H19 PASS demonstrates the scoped DEV architecture on the validated A23/Chrome build. It does not by itself establish Production readiness, video/GIF/DRM coverage, battery certification or multi-OEM support.
