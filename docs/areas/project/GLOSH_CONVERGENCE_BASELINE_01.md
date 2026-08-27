# GLOSH-CONVERGENCE-BASELINE-01

Status: **TECHNICAL BASELINE ASSEMBLED — PENDING CHATGPT FINAL REVIEW**

- Base: `origin/main` at `7269636f3c916bf92cd93947bf2595db330836dd`.
- Functional baseline: `de6f105d87d94144c5ab6b438f832b6210047c8d`.
- Assembly branch: `review/glosh-convergence-baseline-01`.
- This is a review baseline, not a merge, deploy, Production change, cleanup, or
  declaration of final canonical status.

## Included mechanisms

| Mechanism | Source | Baseline commit | Result |
| --- | --- | --- | --- |
| Chrome 10A, 10B, 11A, 11B, 12, 12A, 12B, 13A and 13B-R | final passing tree `review/chrome-visual-shield-13b-r-dev358-final` at `5c31b948a61edd21a600a14a6303531924df2356` | `91f6d403` | Integrated by selected product paths; R1 and unrelated branch history excluded |
| GloshIA Visual R3.1 shared runtime and Chrome wiring | same passing tree | `91f6d403` | Integrated with the reviewed model and policy |
| Exact technical-host allowlist | source `0100cf9e` | `46be3f69` | Integrated as an independent P0 patch |
| Atomic policy revision sync | source `5aa1f8cf` | `de6244a7` | Integrated as an independent P0 patch |
| Device-token write scope | source `76bbf975` | `de6f105d` | Integrated; its required SQL function already existed in main |

The Chrome source branch was not merged or cherry-picked wholesale: it contains
157 commits outside main after its merge base, including unrelated DAG/video
history. The baseline reconstructs only the final reviewed product paths:
`app-user`, Chrome domain contracts, `feature-accessibility`, `feature-vpn`,
`gloshia-visual-core`, HEV, and the required Gradle wiring.

## Canonical version / evidence manifest

| Area | Exact baseline version | State |
| --- | --- | --- |
| App User | main DEV311 plus reviewed Chrome mechanisms through 13B-R | INCLUDED; no convergence version bump |
| App Admin | main DEV293 | INCLUDED unchanged |
| Super Admin | main, PR #100/#101 (`6e9ba072`, `885663fd`) | INCLUDED unchanged |
| Protection | main plus Chrome 10A/10B and Accessibility 12/12A/12B | INCLUDED |
| Chrome | passing foundation `5c31b948`; R1 excluded | INCLUDED through 13B-R semantics |
| GloshIA | R3.1 model SHA `c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48` | INCLUDED; single runtime |
| DAG Browser | main DEV211 | INCLUDED unchanged; RESERVE |
| Backend / Sync | exact hosts, atomic sync, device-token scope | PARTIAL P0 INCLUDED; no live migration |
| Installer / Remote | main only | Glosh Remote PAUSED; preserved installer is historical lab work |

## Excluded mechanisms

| Classification | Mechanisms | Reason |
| --- | --- | --- |
| EXCLUDE BLOCKED | Chrome R1 `001be18d`, R1 diagnostic `88804188`, Chrome R2A/video, pairing hardening `bb148ea2`/`bf37b77e` | R1 remains blocked; pairing still lacks E2E/Auth closure; dependent chains were not promoted |
| EXCLUDE PAUSED | UX V4 User/Admin, post-main DAG, video/GIF, Glosh Remote | No reviewed technical closure; existence of code does not make a route active |
| EXCLUDE CANDIDATE | GloshIA R4 and experimental models/calibrations | Nothing displaces R3.1 |
| EXCLUDE HISTORICAL | Device Owner installer local candidate and superseded Chrome routes | Preserved for recovery/evidence, not canonical product |

No mechanism remains `UNKNOWN` in this assembly.

## Equivalence and gates

- The Chrome selected tree at `91f6d403` is byte-equivalent to `5c31b948`
  except `app-user/build.gradle.kts`: DEV remains 311 instead of the historical
  DEV358 gate number.
- `app-dag-browser`, `web-super-admin`, governance, workflow and architecture
  guard paths have no delta from `origin/main`.
- Stable patch IDs match their reviewed P0 sources for all three P0 commits.
- The R3.1 ONNX file hashes exactly to
  `c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48`.
- Focused unit run passed: GloshIA, VPN, Accessibility/Chrome, policy, sync,
  App User DEV and App Admin DEV; 646 tests, 0 failures, 0 errors, 0 skipped.
- `lintDevDebug` and `assembleDevDebug` passed for App User and App Admin (893
  tasks); both DEV APKs were produced locally and were not installed.
- `git diff --check` passed.
- Web was not rebuilt because its tree is identical to main.
- No ADB, physical gate, live Supabase operation, schema application, deploy, or
  Android version increment was performed. Functional equivalence avoids
  repeating previously closed physical gates.

### Visible quality note

The optional repository-wide `ktlintCheck` is not green. Its findings are
pre-existing/inherited rather than introduced by convergence: App User has
existing main and reviewed Chrome 11A/11B/guard formatting debt, and
`ChromeVisualShieldController.kt` has one indentation finding already present
byte-for-byte in `5c31b948`. Targeted ktlint is green for `core-policy`,
`core-sync`, `feature-vpn`, `gloshia-visual-core`, and `core-domain`; it stops on
that inherited Accessibility finding. This baseline intentionally does not
reformat reviewed mechanisms. The debt remains visible and separate from the
passing functional, lint, assemble, hash, and equivalence gates.

## Pending real decisions

1. ChatGPT must review this assembly before it can be named the canonical
   convergence baseline.
2. Pairing E2E/Auth closure requires a separate P0 ticket; no blocked dependency
   was forced into this baseline.
3. Chrome R1 needs an architectural decision; R2A stays behind it.
4. DAG post-main, UX V4, Remote, video/GIF, and experimental GloshIA remain
   paused/reserve until explicitly prioritized and gated.

## Next priority

ChatGPT final review of this branch. After approval, make the baseline decision
explicit in coordination and plan the smallest separate P0 pairing/Auth closure;
do not reactivate a product front merely because its candidate code was
preserved.
