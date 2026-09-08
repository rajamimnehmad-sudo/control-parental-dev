# Local photo scheduling — DEV464 candidate

Cause: Google Images embedded-raster submissions execute FIFO. A 463 sample had20 serial submissions spanning1.36–8.13s; a viewport image resource finished8.15s. This does not show that all delay is scheduler overhead: each cold photo still needs model work.

Controlled reference uses exactly the same browser-side script on each candidate, on the controlled fixture origin only. It adds valid PNG tEXt chunks to the same SAFE pixels, yielding12 distinct cold hashes:8 offscreen jobs submitted before4 viewport jobs. The script changes no browser settings, interception, model or release mechanism. DEV463 all12 decoded in each of3 runs; viewport completion2259.5/2117.8/2055.3ms, median2117.8ms. These are load events, not paint timing or Chrome ON–OFF.

Proposed change: prefer first currently visible pending job, up to4 overtakes before serving FIFO head. One active submission,128 pending/8MiB and native inference/admission limits unchanged. Geometry changes scheduling only and failures fall back to FIFO. Picture source uses its image geometry. No extra queue or image bytes retained.

Node tests verify selection, bounded overtaking, FIFO among visible entries, cancellation, picture and geometry failure. Existing local-photo physical fixture will revalidate original bytes, stale assignment, removed source, srcset, picture and Shadow DOM. Keep this delta only after physical improvement and health checks. No parser/SELF_READY/ByteGate or model changes.
