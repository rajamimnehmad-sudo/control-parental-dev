# REMOTE-INSTALL-GUIDED-ASSISTANT-08

Status: **CODE COMPLETE BY CHATGPT / REAL ANDROID GATE PENDING**

Base connection stack: `REMOTE-INSTALL-CONNECTION-00` remains frozen and already proved no-link, cross-network support.

Base commit for this UI/flow redesign:

`b4a559b2707bd3040642208be643a8eefc6922ec`

Implementation branch:

`work/remote-install-guided-assistant-08-chatgpt`

## Product decision

The customer does not need a brittle robot that presses every Android control. The professional product is a four-step assistant:

1. Glosh opens the exact destination.
2. The customer performs the protected Android tap or switch.
3. Glosh observes the trusted Settings window and detects the real state change.
4. Glosh opens the next exact destination automatically.

Accessibility is used for:

- trusted Settings-window observation;
- compact floating guidance;
- highlighting a known label when a direct route is unavailable;
- contextual six-digit code reading;
- Samsung enable-development fallback.

Accessibility is **not** used in the normal path for:

- automatic Settings clicks;
- automatic Settings scrolling;
- blind coordinate gestures;
- toggling protected Android controls.

## Customer experience

### Before the four steps

The app checks that a support console is available. This is displayed as `Preparando`, not as a fake numbered step.

### Step 1 of 4 — Accessibility

Glosh first attempts the exact accessibility-details destination for `LiveGuideAccessibilityService`, with fallback to the general Accessibility screen.

Customer action:

`Activá Glosh Remote`.

As soon as the service is enabled, Glosh proceeds without a Continue button.

### Step 2 of 4 — Developer options

Glosh opens Developer options directly.

- If already available, Glosh immediately probes the direct Wireless Debugging route.
- If unavailable, Samsung fallback opens About phone and highlights:
  - Software information;
  - Build number ×7.
- If Android requests PIN/pattern/password, the customer enters it. Glosh never reads it.

### Step 3 of 4 — Wireless debugging

Glosh prioritizes:

`android.settings.WIRELESS_DEBUGGING_SETTINGS`

The customer activates Wireless debugging and confirms `Allow` if Android requests network approval.

Glosh does not search/click the Wireless Debugging row in Developer options. If the direct action is unavailable, it falls back to visual guidance only.

### Step 4 of 4 — Pairing code

Glosh highlights `Pair device with pairing code` and the customer taps it.

Then:

- a unique contextual six-digit code is read locally and submitted automatically;
- if the broker/ADB pairing endpoint is not ready yet, the code is retained until it is;
- if automatic reading is not safe, the existing six-box app input and notification `RemoteInput` remain available.

After local pairing, the already-proven ADB → encrypted relay stack continues unchanged.

## Visual system

The app, floating coach and notification share `GuidePresentation`:

- exactly four stable steps;
- one title and one instruction at a time;
- compact graphite card with lime progress;
- small native microanimation:
  - tap;
  - switch;
  - seven taps;
  - six code boxes;
  - waiting spinner;
  - success check;
- reduced-motion support through `ValueAnimator.areAnimatorsEnabled()`;
- notification persists when the floating card is hidden;
- floating `×` hides only the card and never cancels the guide.

## Safety properties

- No programmatic Settings scroll in the guided coordinator.
- No Settings `ACTION_CLICK` in the guided coordinator.
- No switch, row or button is treated as complete until the resulting trusted Settings state is observed.
- Wrong/unknown screen fails to an explanation and a reopen action, not to a blind click.
- Pairing code is never logged.
- Existing FLAG_SECURE, ephemeral ADB identity, broker sealing, WSS/HMAC/AES and allowlist remain unchanged.

## Verification

Single automated command:

```bash
ANDROID_HOME=/Users/yejielnehmad/Library/Android/sdk \
  bash tools/glosh-remote-spike/verify_guided_assistant.sh
```

The script runs:

- architecture source guard against click/scroll ownership;
- Python protocol/broker/standby tests;
- Android JVM tests;
- lint;
- assemble;
- exact APK copy/hash/report.

Expected artifact:

`GloshRemote-Guided-DEV.apk`

## Physical gate

After automated PASS, use A23 as Samsung laboratory and verify:

- zero automatic clicks;
- zero programmatic scrolls;
- exact Accessibility destination or safe fallback;
- direct Developer options probe;
- direct Wireless Debugging destination;
- user toggle detected;
- pair row highlighted clearly;
- unique six-digit code automatically submitted;
- manual notification input works as fallback;
- local ADB and Mac support connect;
- overlay clears on cancel/finish;
- Content Filter, animations and Device Owner are restored.

Only after A23 PASS should the same exact APK be tested cable-free on S22 as a real customer flow.
