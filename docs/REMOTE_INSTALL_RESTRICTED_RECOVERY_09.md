# REMOTE-INSTALL-RESTRICTED-RECOVERY-09

Status: IMPLEMENTING

Physical S22 evidence: the proactive restricted-settings App Info step is not reliable because Samsung/Android does not always expose the App Info overflow menu before Android has actually blocked the Accessibility activation attempt.

Correct route:

1. Open Glosh Remote Accessibility first.
2. Customer attempts to enable Glosh Remote.
3. If Accessibility becomes enabled, continue automatically; this is the only authoritative success signal.
4. If customer returns with Accessibility still disabled, show a recovery screen explaining the Android denial case.
5. Recovery opens Glosh Remote App Info and instructs the customer to use `More > Allow restricted settings` only after Android has actually produced the denial.
6. If the overflow is still absent, instruct the customer to return to Accessibility, trigger the denial once, then retry recovery. Never claim the menu must already exist.
7. No local `confirmed` bit may substitute for the actual Accessibility-service enabled state.

No Chrome, DAG, Apps, Supabase, broker, relay, crypto or Device Owner code is in scope.
