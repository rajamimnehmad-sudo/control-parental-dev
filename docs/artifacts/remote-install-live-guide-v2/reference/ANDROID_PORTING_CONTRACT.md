# Glosh Remote Live Guide V2 — Android porting contract

This document is intentionally implementation-oriented. Codex should map these pure prototype concepts to the existing Android code instead of redesigning them.

## 1. Window authority

Authoritative source: `AccessibilityService.getWindows()` only when interactive windows are enabled.

Select one window only when:
- `type == AccessibilityWindowInfo.TYPE_APPLICATION`;
- package belongs to the dynamically resolved Settings package set;
- exactly one plausible Settings window is focused/active, or there is only one Settings application window.

Never match against:
- `TYPE_ACCESSIBILITY_OVERLAY`;
- `TYPE_INPUT_METHOD`;
- Glosh coach/highlight windows;
- non-Settings packages.

Coach/highlight views should also use `importantForAccessibility=NO` where applicable.

## 2. Event serialization

Use a single serialized reducer/actor for the live guide. A Kotlin `Channel`/actor or equivalent single-consumer pipeline is appropriate.

Every relevant event increments `generation`.

A scan produces a token:
- windowId;
- generation;
- logical fingerprint.

Before applying matcher, scroll or overlay output, compare the token to current state. If different, drop the result silently.

No delayed callback may mutate highlight/rescue state after a newer generation exists.

## 3. Stable snapshot

A window is matchable only after:
- trusted Settings application window selected;
- no relevant event for 350–500 ms;
- two equivalent logical fingerprints;
- fresh root reacquired after the latest event.

Do not retain `AccessibilityNodeInfo` across asynchronous waits. Convert needed fields to immutable snapshot data, recycle/allow framework lifecycle as appropriate, and reacquire before performing an action.

## 4. Matcher

Port the prototype's deterministic score model, but tune constants only with physical evidence.

Minimum target evidence:
- exact localized alias or exact contentDescription alias;
- correct screen context;
- expected role/clickability;
- optional resource id/context boost.

High-confidence target must beat runner-up by a margin. Ambiguity => no action.

Do not fuzzy-match arbitrary Settings text.

## 5. Reveal / scroll

Hidden target never moves the screen by itself.

Only the coach action `MOSTRARME` arms a bounded reveal sequence.

Sequence:
1. reacquire stable trusted window;
2. rematch target HIGH;
3. if the real node is available and supports `ACTION_SHOW_ON_SCREEN`, call once;
4. otherwise scroll the nearest valid scrollable ancestor/container;
5. maximum 3 scroll actions;
6. after every action wait for a new event, reacquire root and rematch;
7. stop on target visible, ambiguity, no progress, changed package/window/screen, timeout or unsupported action.

A human scroll immediately cancels/disarms reveal and starts a >=1400 ms cooldown.

## 6. Overlay

Two windows are allowed, but neither is scan authority:

Highlight:
- `TYPE_ACCESSIBILITY_OVERLAY`;
- not touchable;
- not focusable;
- transparent except target outline;
- clear immediately when generation/window changes.

Coach bar:
- thin bottom or top bar;
- `TYPE_ACCESSIBILITY_OVERLAY`;
- non-focusable;
- only small local controls (`MOSTRARME`, `ME PERDÍ`, close/minimize);
- `importantForAccessibility=NO` unless a deliberate accessible UI design is later implemented separately.

Never render a stale target rectangle.

## 7. Fast path

Before teaching Build Number:
- `discover` support only;
- open the best resolvable Developer Settings route;
- if `Depuración inalámbrica` is found HIGH in stable trusted Settings, mark developer stage complete;
- otherwise route to OEM developer-options recipe.

No broker request/RSA/nonce before developer stage is ready.

## 8. Rescue

`ME PERDÍ` is explicit user action.

It:
- reacquires and stabilizes current Settings window;
- classifies only known screens;
- aligns the UX step when confidence is HIGH;
- otherwise clears highlight and offers `ABRIR AJUSTES CORRECTOS`.

Target absence during a transition is not rescue.

## 9. Pairing

Guaranteed path remains in-app six digit input + notification `RemoteInput`.

Accessibility PIN read is optional and only when:
- exact expected pairing state;
- trusted Settings application window;
- exactly one 6 digit candidate;
- strong pairing semantics nearby.

All paths call one pairing entrypoint and one submit guard.

## 10. Lifecycle/security

When onboarding is inactive, scanner and overlays are inert.

On CONNECTED or cancel:
- remove overlays;
- clear guide state/code references;
- stop package/event interest;
- `disableSelf()` when the product flow requires the temporary guide to turn off.

Never store node text, screenshots, PIN, nonce, descriptor, session key or account identity in pilot telemetry.
