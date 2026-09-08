# DEV463 — original HTTP error UI through existing shield

Status: 429 tests/build/lint PASS; APK installed and SHA matched (apk-463.json). Controlled403/429 each pass16/16 checks. Functional SHA 7f676f522de07a82b63116fecba8b433c100e7e6.

Cause: document admission rejected every status other than 200, replacing complete 403/429 HTML with a local fallback. This suppressed original recovery UI regardless of whether the existing parser could protect it. A23 Google returned 429; H&M previously returned 403. Preserving the original error does not resolve the upstream refusal.

Change: complete HTTP 400–599 HTML enters the same MIME/encoding/size/parser/bootstrap path as 200. The protected transformed response retains upstream status and reason. Partial, bodyless, redirect, cache-only, unsupported MIME/charset and ambiguous intent remain rejected. No domain-specific condition, unfiltered HTML, model, release registry, SELF_READY or parser changes. Gzip retains existing bounded decoding and checks.

Tests retain negative rejected-document coverage for 204/205/206/304 and add error-status MIME/charset checks, shield ordering, identity binding and status preservation. Controlled /svg06a/http403 and /svg06a/http429 reuse the same 16 original-UI and unsafe-media checks as /svg06a.

User direction: reduce interference with ordinary UI globally; no allow-all protected mode. Ads remain paused, GIF/video excluded. Cache/revisit latency is a separate unresolved issue. This change does not claim whole-browser normality or user acceptance.

Physical: Google Images camisa mujer returned200 and25 decoded images; TTFB1011ms/DCL1881ms/load3816ms in one run. Upstream recovery is not attributed to463. H&M still fails: phone document_insertion_unsafe; independent Mac403 error starts <HTML><HEAD> without doctype. Parser unchanged. No claim H&M store compatibility.
