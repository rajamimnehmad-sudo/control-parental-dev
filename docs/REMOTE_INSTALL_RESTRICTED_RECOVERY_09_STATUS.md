# REMOTE-INSTALL-RESTRICTED-RECOVERY-09

Updated: 2026-08-25 ART

Physical S22 evidence showed that the proactive App Info route was invalid: the Samsung/Android 16 App Info overflow menu (`⋮ > Permitir configuración restringida`) is not guaranteed to exist before Android has actually rejected an Accessibility enable attempt.

The corrected flow is reactive:

1. Open Glosh Remote Accessibility normally first.
2. Customer attempts to enable the service.
3. Actual enabled Accessibility state is the only success authority.
4. Only if the customer returns with Accessibility still disabled does Glosh offer the Android restricted-settings recovery.
5. Recovery copy is conditional: if the overflow exists, use `Permitir configuración restringida`; if it does not exist, return to Accessibility and trigger the blocked enable attempt before retrying recovery.
6. No local confirmation bit can bypass or substitute the actual system state.

Exact product-code checkpoint under automated rerun:

`690dac8ca7d2a9537316987d46cad728d83454a9`

First gate attempt:
- architecture guard PASS;
- Python suite reached 13/14 with one timing failure in the unchanged Mac heartbeat recovery test (`register_calls` stayed 1 instead of reaching 2 before assertion);
- Android/lint/assemble were not reached in that attempt;
- no APK was authorized from the failed attempt.

The heartbeat implementation and Mac connection stack were untouched by this recovery batch and had already passed the same 14/14 suite on the prior candidate. The exact failed job is being rerun without product-code changes to distinguish timing flakiness from a real regression.

Connection base remains `PASS FINAL DEV / CLOSED`. No Supabase, broker, relay, crypto, Chrome, DAG, App Usuario/Admin or Device Owner behavior was changed.
