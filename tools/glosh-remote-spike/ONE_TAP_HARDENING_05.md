# REMOTE-INSTALL-ONE-TAP-HARDENING-05

Status: **CODE COMPLETE / PENDING REAL GRADLE GATE**

Base: `eaa44f1ba5da204d66a720ae6f5f805699ee22ee`

Implementation branch: `work/remote-install-one-tap-05-chatgpt`

## Scope implemented

- Customer normal path reduced to one initial CTA: `CONECTAR CON SOPORTE`.
- Normal automated states are passive progress + `CANCELAR`; legacy guide controls remain only behind fail-closed manual fallback.
- First-time Accessibility bootstrap is a single explicit `ACTIVAR AUTOMATIZACIÓN` action; no `CONTINUAR SIN GUÍA` bypass in the normal product flow.
- Connected terminal state is `Conectado con soporte` + `FINALIZAR CONEXIÓN`.
- Contextual six-digit pairing remains automatic when unique/high-confidence; ambiguous pairing explicitly switches to `AUTOPILOT_FALLBACK` and exposes the existing six-box manual input.
- Mac operator presence now renews automatically every 60 s while the technician is explicitly waiting.
- A single pending customer request is autoaccepted for that one-use RemoteSession; zero requests do nothing and 2+ requests fail closed to manual `accept <request-id>`.
- RemoteSession TTL no longer starts when the Mac console opens. It starts only after the first authenticated Android agent connects and is not extended by reconnects.
- Android broker request renewal is bounded by a 30-minute overall wait window instead of five short-lived requests.
- Transient request/poll failures use bounded 500 ms → 1 s → 2 s → 4 s → 5 s backoff with six consecutive failures max; a successful broker response resets the failure budget.
- Ambiguous claim transport failure remains fail-closed and is intentionally not retried because claim consumes the one-time ciphertext.

## Preserved security architecture

Unchanged:

- broker rendezvous contract;
- RSA-OAEP sealed descriptor;
- WSS relay;
- mutual HMAC authentication;
- AES-256-GCM payload protection;
- sequence/replay protection;
- fixed command allowlist;
- ephemeral ADB identity;
- no public ADB and no `adb tcpip 5555`;
- trusted Settings-window / immutable snapshot / generation guards.

## Validation already performed by ChatGPT

Independent local smoke checks outside the Android repository runtime:

- `BrokerWaitPolicy` compiled with JDK and passed >5 renewals, 30-minute cutoff and retry-backoff/reset cases.
- operator heartbeat survived a simulated first transient failure and continued renewing;
- zero pending requests produced zero accepts;
- exactly one request produced one autoaccept only;
- multiple simultaneous requests produced zero autoaccepts;
- standby/TTL contract verified conceptually: no expiry before authenticated client, single expiry deadline after first authentication.

Repository diff from base is intentionally limited to `tools/glosh-remote-spike/**`.

## Required closure gate

Do not declare PASS until the real standalone Android project runs:

```bash
./gradlew -p tools/glosh-remote-spike :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

and Python tests including:

```bash
cd tools/glosh-remote-spike/mac
python -m unittest test_protocol.py test_broker.py test_one_tap_standby.py
```

Expected APK after PASS:

`tools/glosh-remote-spike/app/build/outputs/apk/debug/app-debug.apk`

No physical A23/S22 gate is required for this build-only closure; the S22 cable-free UX gate comes only after ChatGPT reviews the Gradle/test result and exact APK hash.

No merge, PR, Production, Supabase mutation or deployment is authorized by this task.
