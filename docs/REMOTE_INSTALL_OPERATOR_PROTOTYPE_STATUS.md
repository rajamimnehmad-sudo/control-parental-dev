# Glosh Remote — Operator prototype status

Updated: 2026-08-24

## Scope
This is a coordination/status document only. No production/local installer code was modified by ChatGPT because the current Glosh Remote implementation HEAD remains local on the user's Mac and newer than the stale GitHub remote branch.

## Prototype completed outside repo
ChatGPT prepared an isolated prototype of the future Mac Operator so Codex can later bind it to the real local HEAD without designing the UI/domain from zero.

Prototype contents:
- domain model for support requests and session state;
- explicit one-active-session safety rule;
- pending-request queue model;
- PRECHECK evaluator with READY / USER_ACTION / BLOCKED / INFO outcomes;
- secret-safe redaction/audit helpers;
- PySide6 UI shell with Open Support, Close Support, Accept, Reject, PRECHECK and Disconnect;
- backend adapter interface plus mock backend;
- APK installation state machine;
- bounded chunk iterator (max 96 KiB raw) with per-chunk SHA-256;
- full-file SHA-256 helper;
- manifest validation for package/size/file SHA/signing-cert SHA;
- local audit event filtering that drops pairing codes, operator keys, session keys, nonces, descriptors and ciphertext;
- explicit wire-contract models for `precheck_v1` and staged install actions;
- PRECHECK wire parser rejects unexpected identity fields such as account email addresses and accepts coarse counts only;
- chunk wire-size validation accounts for base64 inside encrypted payload plus outer ciphertext/base64 envelope;
- Device Owner commit state machine;
- Device Owner assessment for foreign owner, Profile Owner, extra users/profiles, any account presence, Glosh installation/signing/admin receiver/self-test and ADB readiness;
- fresh PRECHECK SHA-256 fingerprint binding;
- short-lived Android-side user commit grant model;
- independent Mac operator confirmation;
- fixed `device_owner_activate_v1` contract with no arbitrary shell field;
- post-activation VERIFYING/ACTIVE/RECOVERY states and explicit prohibition on automatic Device Owner rollback.

Why 96 KiB chunks:
- the current relay caps a WebSocket message at 256 KiB;
- APK bytes require base64 in the command payload;
- the encrypted payload is then base64-encoded again in the outer envelope;
- 96 KiB leaves conservative room for both expansions plus JSON/AES overhead.

Local prototype test result:
- 18/18 Python unit tests PASS.
- Coverage includes one-active-session enforcement, READY precheck, account USER_ACTION classification, Device Owner blockers, signing mismatch blocker, installation stage transitions, transfer completeness, chunk-size bound, full-file hashing, secret stripping, rejection of identity-bearing PRECHECK fields, wire chunk bounds, dual-consent Device Owner commit, fresh-precheck invalidation and recovery-without-auto-rollback.

Current artifact SHA-256:
`0eab46a459df25c96fa89b177d7276e650848dcb663c0b623a4e00cdb3820b50`

## Product decisions locked
- Mac Operator is a GUI over the existing relay/broker engine, not a replacement security protocol.
- Initial product model: many requests may wait, but only one accepted/active support session per operator.
- PRECHECK is strictly read-only and runs before account removal, APK install or Device Owner changes.
- Installation is staged: PRECHECK -> TRANSFER -> VERIFY -> INSTALL -> VERIFY INSTALL -> Device Owner commit -> VERIFY OWNER.
- No arbitrary shell exposed in normal UI; advanced DEV console remains hidden/separate.
- APK transfer uses the existing end-to-end encrypted session, chunked and hash-verified, rather than a public APK URL.
- PRECHECK returns coarse account/provider counts and blocker classes, not account identifiers/emails.
- For the ADB Device Owner route, total Android account count must reach zero and extra users/profiles must be cleared explicitly before commit.
- Device Owner is an asymmetric commit point: all reversible validation happens first; after commit, failures enter repair/recovery rather than automatic owner removal.
- Device Owner activation requires a short-lived Android user grant bound to the PRECHECK fingerprint plus an independent Mac operator confirmation.
- No local-head integration should be attempted from stale GitHub code.

## Next integration when Mac/local runner is available
1. Read the actual local Glosh Remote HEAD/worktree first.
2. Reuse the prototype domain/UI pieces only where they fit the current implementation.
3. Implement an adapter over the current Supabase broker client + relay session.
4. Add read-only remote PRECHECK actions to the allowlist/agent contract.
5. Add chunked APK transfer/install actions with strict manifest and signature verification.
6. Integrate Android Live Guide account/profile cleanup without exposing identities to Mac.
7. Add Android-minted short-lived user Device Owner grant.
8. Add fixed Device Owner activation action bound to fresh precheck fingerprint + verified APK/component.
9. Verify Device Owner through both system state and Glosh's own DevicePolicyManager view.
10. Keep message sizing consistent with the live relay envelope limit; do not raise WebSocket limits merely to move larger chunks.
11. Package the Operator for macOS and run local/physical gates.
12. ChatGPT reviews the exact diff and evidence before closure.

## Coordination
- REMOTE-INSTALL-CONNECTION-00: PASS FINAL DEV / CLOSED.
- REMOTE-INSTALL-LIVE-GUIDE-03: design final; Android implementation/gate is local-runner work.
- REMOTE-INSTALL-MAC-OPERATOR-04: architecture + isolated prototype complete; integration pending current local HEAD.
- REMOTE-INSTALL-PRECHECK-05: contract/decision model defined; remote command implementation pending current local HEAD.
- REMOTE-INSTALL-PIPELINE-06: staged transfer/install model defined; remote command implementation pending current local HEAD.
- REMOTE-INSTALL-DEVICE-OWNER-COMMIT-07: design final + isolated state machine/tests PASS; local integration pending.
- REMOTE-ADAPTIVE-INSTALL-PILOT-01 remains after Live Guide + Operator/Precheck/Device Owner integration.

No Chrome, GloshIA, DAG, App Usuario/Admin, Supabase backend, production Device Owner logic or current local Glosh Remote worktree was modified by this prototype work.
