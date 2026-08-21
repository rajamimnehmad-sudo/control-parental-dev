# Physical gate helper

This helper is for `CHROME-PHOTOS-PROTECTED-SURFACE-00` only.

- `sentinel-fixture.html` deliberately renders raw magenta/lime sentinel pixels beneath Chrome.
- The DEV protected surface draws a small cyan compositor marker on every overlay frame.
- `detect_sentinel_frames.py` checks a recording after `phase=armed` and FAILs if:
  - magenta/lime raw sentinel pixels appear; or
  - the cyan protected-surface marker disappears.

Suggested local flow for Codex:

1. Serve this directory from the Mac (`python3 -m http.server 8765`).
2. `adb reverse tcp:8765 tcp:8765`.
3. Open `http://127.0.0.1:8765/sentinel-fixture.html` in Chrome.
4. Wait for `ChromePhotosSurfaceProbe phase=armed` and first `result=staged`.
5. Start the highest-fidelity available recording (prefer scrcpy/device capture at 60 fps).
6. Run slow scroll, flings, reverse, lazy section, keyboard and rotation.
7. Analyze: `python3 detect_sentinel_frames.py recording.mp4 --samples-dir failures`.

A screen recording is useful automation evidence but does not replace the project's required human physical check. If the recorder does not capture the cyan marker reliably, mark the recording gate BLOCKED rather than inferring safety.
