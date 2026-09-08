# Global blank-page investigation — candidate DEV460

Status: IN_PROGRESS; no integral normality PASS.

Base: 76f285be92945857e5de89423f5df7edf54fc7a9. Governance: 9323b126dde3b91e21e2136a11d087fa3c1f0ca1.

On DEV457, Google /sorry returned upstream HTTP429 on three requests at12:18:00–12:18:07. Existing document admission replaced it with41bytes of empty HTML. CDP confirmed an empty body, Chrome awake/foreground, active/ready, suspended=false, reset3. No proof yet whether the trigger is shared-network rate limiting, test traffic, or another transport factor. Automated Google burst tests paused. Google's documentation describes unusual-traffic challenges but does not establish the cause for this device: https://support.google.com/websearch/answer/86640?hl=es

Separately, Mimo homepage loaded with11 decoded images. Clicking the actual Abrigos link navigated to /todoslosproductos and produced an empty page. At12:24:15 native document admission recorded document_encoding_unsupported. A separate anonymous curl request to the same public category URL with Accept-Encoding: identity returned HTTP200 text/html and Content-Encoding: gzip. This is a global unsupported-content-encoding compatibility case, not a host-specific rule.

DEV459@77faba4c adds a local, text-only explanation for rejected top-level documents. No original markup is released, no automatic retries, no new external requests. Subdocuments retain the existing empty fail-close surface. Build459 was intentionally cancelled during R8 to combine the newly demonstrated gzip fix; no459 APK installed.

DEV460@cdcbcd00 adds bounded gzip decoding only when existing document admission rejects exactly that encoding. Both compressed input and decoded output retain the existing4MiB document limit. Invalid gzip, checksum errors, overflow, ambiguous or stacked encodings remain fail-close. Decoded content re-enters the same MIME/charset/parser/SELF_READY/Byte Gate path; HTTP errors are not made successful admissions. No domain matching, library dependency, model or release-authority change. Java GZIPInputStream documentation: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/GZIPInputStream.html

Tests cover protected bootstrap ordering for compressed HTML, bad CRC, expansion overflow, unsupported MIME, duplicate/stacked encodings, rejected HTTP status, and absence of upstream markup/authority in local error responses. Build and physical verification pending at this checkpoint.

Frávega remains a separate unclassified application-level404 after successful HTTP200 and JSON responses; not explained by this gzip reproduction. No workaround adopted. Google429 and Frávega must not be reported resolved by merely displaying an error message.

Additional DEV457 controls: Mercado Libre homepage47 decoded images; celulares category23 decoded images and product text; actual product link redirected to visible /captcha/wall with a manual verification screen. No challenge solved or bypassed. Mimo homepage continued working while gzip category failed. Anonymous desktop curl Google search returnedHTTP200; this is a different client/network route and does not establish phone OFF equivalence or disprove the phone429.
