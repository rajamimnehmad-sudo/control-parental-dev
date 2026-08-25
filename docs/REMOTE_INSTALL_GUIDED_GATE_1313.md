# REMOTE-INSTALL-GUIDED-ASSISTANT-08 — immutable automated gate

Created: 2026-08-25 ART

The repeated BLOCKED report was produced from the superseded SHA:

`6945d9728dc72a78c87fee334a078db5145eb080`

It is not evidence against the corrected code.

## Only authorized automated rerun

Immutable gate branch:

`gate/remote-guided-1313eea`

Exact SHA:

`1313eea1324903348c6e375b3ce9327120b31ff9`

The mutable implementation branch also points to that SHA at creation time:

`work/remote-install-guided-assistant-08-chatgpt`

Codex must create a brand-new detached worktree from the immutable gate branch and verify `git rev-parse HEAD` before running any command. If HEAD is not exactly `1313eea…`, it must stop before tests.

Run only:

```bash
ANDROID_HOME=/Users/yejielnehmad/Library/Android/sdk \
  bash tools/glosh-remote-spike/verify_guided_assistant.sh
```

No phone and no physical gate until this automated run completes.

Expected corrected properties:
- `SamsungSettingsClassifier` contains no Android `Rect` geometry call;
- default pairing action uses `Cue.TAP`;
- code-reading/manual-entry states use `Cue.CODE`;
- detected/waiting code uses `Cue.WAIT`.

No code modification, push, PR, merge, Supabase, Production or other project area is authorized during the gate.
