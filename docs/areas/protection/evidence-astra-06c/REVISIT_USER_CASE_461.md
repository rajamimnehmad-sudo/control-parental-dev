# DEV461: user rejects cached-page fluency

The user specifically reports Google “camisa mujer” does not feel fluid. No user acceptance or normality PASS. Public query physically reproduced in official Chrome on A23 with unchanged DEV461 and protection active. Chrome data/cache were not cleared or disabled.

| navigation load event (ms) | first visit | reload |
| --- | ---: | ---: |
| controlled multi-origin |3492.4|1238.2|
| Mimo homepage |5602.0|5285.0|
| Google Images, camisa mujer |4133.1|3410.2|
| Google web, camisa mujer |9136.5|3400.7|

Controlled back/forward reloaded the document (navigation type back_forward), load1540.4ms,12 decoded images, zero incomplete eager images. This does not establish BFCache restoration.

Single runs, different public content and network conditions. The Images reload restored scroll near1000px, so its visible-image set differs from the first top-of-page run. Load is not paint or above-fold completion. Google web reload still had visible small-image resources finishing around3955ms even after the load event. First web result product-photo resources completed around1850–3661ms; a later visible24px icon completed9976ms. Neither total-load timing nor the improved mixed-cache benchmark establishes subjective fluidity.

Code findings: the current Glosh cache retains decisions keyed by exact bytes/MIME/engine generation. Real-upstream OkHttp has no HTTP response cache configured. Protected images/documents use no-store downstream and strip conditional requests; revisit therefore still requires bytes and fresh document identity. Do not relax these rules or release cached originals without preserving the existing authority/Byte Gate contract.

Native samples also include a late cache hit after processing admission waiting3790.69ms: an initial cache miss may become a cache hit while waiting for a processing permit. This is a scheduling investigation lead, not yet an attributed explanation for the entire user case. No polling, larger queues, URL-only SAFE cache or host workaround was added.

The ad-removal request is paused behind normal navigation and revisit latency. The device remains available for user trial; final long-run/memory acceptance is still pending. See revisit-user-case-461.json for raw bounded DOM measurements.
