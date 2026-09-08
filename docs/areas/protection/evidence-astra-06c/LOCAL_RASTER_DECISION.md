# Decision needed: static inline raster transport

Task06C, functional DEV452 d88853be, integral base76f285, governance9323b126.

## Observed gap

On official Chrome/A23, controlled Google Images query `camisa mujer`, DEV451 recorded79 image elements,9 decoded images and65 shield-blocked elements. Six blocked elements intersected the viewport; each was28x28px and had its source removed. The page's scripts contained22 inline JPEG/PNG/WebP declarations. These six small suggestion images are a compatibility gap, not a measurement of all search-result photos or proof that local rasters explain every loading delay. See google-local-surface-451.json.

The current bootstrap explicitly strips data:/blob: image sources and srcset before presentation. Network images instead pass through the existing bounded image authority, unchanged R3.1 and verified decision delivery. No local raster submission/decision transport is present in this candidate. Unsupported fail-close remains secure but must not be called supported.

Network latency remains separate: the sampled Google navigation had10 engine image deliveries with p50/p95/p99=549.983/791.913/791.913ms (small sample, tail not stable). Existing concurrency2 did not improve the alternating fixture median consistently; default1 retained. No normal-OFF causal comparison or subjective acceptance is claimed.

## Concrete proposed extension — not implemented

Add a generic, bounded transport for static data: JPEG/PNG/WebP/AVIF through the existing image Byte Gate and model. A document may submit bounded bytes, receive only an opaque reference to an authorized result, and present originals only after SAFE; BLOCK remains a localized gray replacement and UNKNOWN/unsupported remains closed. Bind submissions/results to the current protection session and document, cancel stale work after navigation/replacement, cap queued bytes/work/results, and prevent result references from bypassing decisions or crossing stale generations. Preserve original decoded bytes and element presentation when SAFE. Reuse content-hash decision caching only with exact byte identity.

This requires a new local-image transport and asynchronous element lifecycle. It is not a domain exception and must not weaken existing parser/SELF_READY/release checks. The implementation and security gate must cover static markup and dynamic src/srcset/picture, stale completion, abuse limits, cancellation, lifecycle, cache identity and zero raw BLOCK/UNKNOWN. The exact mechanism must be reviewed within authorized DEV paths before coding.

Scope of the requested decision: static data: raster photos only. No GIF, video, blob media, model/weights/labels/thresholds, guard/release authority, Production, deployment or external services. If any implementation requires another material path or authority change, stop again at that boundary.

The original ticket requires STOP for material architecture changes. No such transport has been implemented or enabled. Existing recovery and health validation can continue independently while this decision is pending.

## DEV454 follow-up

Physical Google Images screenshot at10:10 ART shows original Chrome/Google UI, gray suggestion chips and two blank sponsored product-photo areas. DOM measurement places two shield-blocked, source-stripped172x215px image nodes in those areas, plus six28px chips; scripts contain25 static inline raster declarations. This establishes a larger visible local-raster compatibility gap than the451 sample. It does not prove all missing photos share this cause or that network/model-replaced gray thumbnails are the same issue. Evidence: physical-google-454.png and google-local-surface-454.json. Transport authorization remains pending; no local raster bypass implemented.

Exact assignment confirmation: google-local-assignment-454.json matches the two visible blocked element IDs to their exact quoted `_setImagesSrc(ii,s)` script assignments; both use data:image/webp. No raw image payload or full script is recorded. The architecture question was surfaced again with this concrete454 evidence; response remains pending.
