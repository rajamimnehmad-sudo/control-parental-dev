# REMOTE-INSTALL-LIVE-GUIDE-V2-04 — GitHub handoff

Authoritative branch:
`coordination/remote-install-live-guide-v2`

Binary bundle:
`docs/artifacts/remote-install-live-guide-v2/glosh_remote_live_guide_v2_prototype.zip`

Expected SHA-256:
`cdff82c2bcd3c15d8ead1286d545f38cb473dfa2473bb1d505e07894d58a8418`

Expected size:
`36217` bytes

## Safe retrieval from an existing local worktree

Do not checkout this coordination branch over a working code worktree. Fetch it and extract only the artifact:

```bash
git fetch origin coordination/remote-install-live-guide-v2
git show origin/coordination/remote-install-live-guide-v2:docs/artifacts/remote-install-live-guide-v2/glosh_remote_live_guide_v2_prototype.zip > /tmp/glosh_remote_live_guide_v2_prototype.zip
shasum -a 256 /tmp/glosh_remote_live_guide_v2_prototype.zip
unzip -l /tmp/glosh_remote_live_guide_v2_prototype.zip
```

Only proceed when SHA-256 exactly matches the expected value.

The bundle contains the audit, Android porting contract, acceptance gates, handoff manifest, Samsung S22 Spanish fixture, prototype engine/tests and interactive mock.

Do not merge or checkout the coordination branch merely to read the handoff. Do not touch unrelated worktrees.
