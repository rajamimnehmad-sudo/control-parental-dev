# Glosh Remote — Operator prototype status

Updated: 2026-08-24 12:52 ART

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
- bounded chunk iterator (max 192 KiB) with per-chunk SHA-256;
- full-file SHA-256 helper;
- manifest validation for package/size/file SHA/signing-cert SHA;
- local audit event filtering that drops pairing codes, operator keys, session keys, nonces, descriptors and ciphertext.

Local prototype test result:
- 9/9 Python unit tests PASS.
- Tests cover one-active-session enforcement, READY precheck, account USER_ACTION classification, Device Owner blocker, signing mismatch blocker, installation stage transitions, transfer completeness, chunk-size bound, full-file hashing and secret stripping from audit events.

Artifact SHA-256:
`ccaf03ca08f2e5fc4506c1ea79724473849f5b91406d64ca942e3f14f801382b`

## Product decisions locked
- Mac Operator is a GUI over the existing relay/broker engine, not a replacement security protocol.
- Initial product model: many requests may wait, but only one accepted/active support session per operator.
- PRECHECK is strictly read-only and runs before account removal, APK install or Device Owner changes.
- Installation is staged: PRECHECK -> TRANSFER -> VERIFY -> INSTALL -> VERIFY INSTALL -> optional Device Owner phase.
- No arbitrary shell exposed in normal UI; advanced DEV console remains hidden/separate.
- APK transfer should use the existing end-to-end encrypted session, chunked and hash-verified, rather than a public APK URL.
- No local-head integration should be attempted from stale GitHub code.

## Next integration when Mac/local runner is available
1. Read the actual local Glosh Remote HEAD/worktree first.
2. Reuse the prototype domain/UI pieces only where they fit the current implementation.
3. Implement an adapter over the current Supabase broker client + relay session.
4. Add read-only remote PRECHECK actions to the allowlist/agent contract.
5. Add chunked APK transfer/install actions with strict manifest and signature verification.
6. Package the Operator for macOS and run local/physical gates.
7. ChatGPT reviews the exact diff and evidence before closure.

## Coordination
- REMOTE-INSTALL-CONNECTION-00: PASS FINAL DEV / CLOSED.
- REMOTE-INSTALL-LIVE-GUIDE-03: design final; Android implementation/gate is local-runner work.
- REMOTE-INSTALL-MAC-OPERATOR-04: architecture + isolated prototype complete; integration pending current local HEAD.
- REMOTE-INSTALL-PRECHECK-05: contract/decision model defined; remote command implementation pending current local HEAD.
- REMOTE-INSTALL-PIPELINE-06: staged transfer/install model defined; remote command implementation pending current local HEAD.
- REMOTE-ADAPTIVE-INSTALL-PILOT-01 remains after Live Guide + Operator/Precheck integration.

No Chrome, GloshIA, DAG, App Usuario/Admin, Supabase backend, production Device Owner logic or current local Glosh Remote worktree was modified by this prototype work.
