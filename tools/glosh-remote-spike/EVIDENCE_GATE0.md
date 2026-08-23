# REMOTE-INSTALL-CONNECTION-00 — BUILD + GATE 0

Date: 2026-08-23

Result: **PASS BUILD/GATE0**

## Source and isolation

- Task ID: `REMOTE-INSTALL-CONNECTION-00`
- Original spike base: `885663fdd9d03542db154650e7abcacec11523d8`
- Expected implementation HEAD: `e10fda2063af0561ebd8691aa1d2af42b044bf64`
- Remote implementation branch checked: `origin/work/remote-install-connection-00`
- Gate branch: `gate/remote-install-connection-00`
- Isolated worktree: `/private/tmp/glosh-remote-install-connection-00-gate`
- Initial HEAD: `e10fda2063af0561ebd8691aa1d2af42b044bf64`
- Final tested code HEAD: `75e2a21b77c90d95ebf14c418dfcb1363281dac4`
- The remote branch still resolved exactly to the expected implementation HEAD before work began.
- The implementation diff from the original spike base was limited to `tools/glosh-remote-spike/**`.
- No registered worktree had uncommitted changes in that path, and the Chrome worktree was not modified.

## Changes made

- `MainActivity.onRequestPermissionsResult(...)` was changed from `protected` to `public`.
- Root cause: `android.app.Activity` exposes this callback as `public`; Java rejected the weaker access level during `compileDebugJavaWithJavac`.
- Commit: `75e2a21b77c90d95ebf14c418dfcb1363281dac4` (`fix(remote): expose notification permission callback`).
- No architecture, allowlist, persistence, production infrastructure, root project Gradle, Android product app, Device Owner, Chrome, DAG, Supabase, or Glosh Central files were changed.

## Static validation

- No references to the removed `PairingCoordinator` were found.
- Source manifest is valid and registers non-exported `RemotePairingService` with `dataSync` foreground service type.
- Target 35 permissions include `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, and `POST_NOTIFICATIONS`.
- The RemoteInput reply `PendingIntent` is the only mutable pending intent; activity and stop intents are immutable.
- Notification RemoteInput is structured as a six-digit reply and is compatible with the min SDK 30 path.
- Pairing discovery owns a non-reference-counted `MulticastLock` and releases it when discovery stops or the service is destroyed.
- `AdbConnectionManager` generates RSA key/certificate material in memory and closes/drops the singleton on revocation.
- Relay callbacks and command work are generation-scoped; encrypted inbound frames reject repeated/decreasing sequence numbers.
- Session keys/descriptors are zeroed or destroyed on terminal paths; no SharedPreferences, database, file, or Android backup persistence was found.
- Android action mapping remains fixed to `whoami`, `device`, `owners`, `users`, and `battery`; `ping` is app-local. No arbitrary remote shell input exists.
- Lint reported no `NewApi` errors for min SDK 30.

## Toolchain

- macOS: Apple Silicon (`aarch64`)
- Java: Homebrew OpenJDK `17.0.19`
- Gradle: `8.10.2`
- Android Gradle Plugin: `8.7.3`
- Android SDK: compile/target `35`, min `30`; platform 35 and build-tools 35.0.0 installed
- AAPT2: `2.19-11948202`
- Python: `3.9.6`
- cloudflared: `2026.8.2` installed locally from the free Homebrew formula; no service, account, plan, or paid resource was created

## Dependencies

Android:

- `com.github.MuntashirAkon:libadb-android:3.1.1`
- `com.github.MuntashirAkon:sun-security-android:1.1`
- `org.conscrypt:conscrypt-android:2.5.3`
- `com.squareup.okhttp3:okhttp:4.12.0`
- `junit:junit:4.13.2`

Python virtual environment (`mac/.venv`, gitignored):

- `websockets==14.2`
- `cryptography==46.0.7`
- `cffi==2.0.0`
- `pycparser==2.23`
- `typing_extensions==4.16.0`

## Python protocol test

Command:

```text
tools/glosh-remote-spike/mac/.venv/bin/python -m unittest test_protocol.py
```

Result: **PASS**, 1 test, validating the Java/Python AES-GCM known vector.

## Android build gates

Command from repository root:

```text
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/Users/yejielnehmad/Library/Android/sdk \
./gradlew -p tools/glosh-remote-spike \
  :app:testDebugUnitTest \
  :app:lintDebug \
  :app:assembleDebug
```

Results after the scoped fix:

- `:app:testDebugUnitTest`: **PASS**, 7 tests, 0 failures, 0 errors
- `:app:lintDebug`: **PASS**, 0 errors, 14 warnings
- `:app:assembleDebug`: **PASS**
- Overall Gradle result: `BUILD SUCCESSFUL`

Relevant lint notes:

- Two trust-manager warnings originate in the bundled `libadb-android:3.1.1` artifact. This is a known risk of the unaudited bootstrap dependency and is confined to local ADB in this V0.
- Other warnings are non-blocking spike debt: target 35 is behind the locally known latest API, backup-rule deprecation, missing icon, redundant SDK checks, and hard-coded Spanish UI strings.
- The merged APK manifest also lists `WRITE_EXTERNAL_STORAGE`, `READ_EXTERNAL_STORAGE`, and `READ_PHONE_STATE`, although they are absent from the source manifest and are not requested by the app flow. Review/removal should be considered before a productized build; it does not affect Gate 0 execution.

## APK

- Absolute path: `/private/tmp/glosh-remote-install-connection-00-gate/tools/glosh-remote-spike/app/build/outputs/apk/debug/app-debug.apk`
- File name: `app-debug.apk`
- Size: `18,990,083` bytes (`18M` by `du -h`)
- SHA-256: `b957a505ffb729d1daed7d78890b046ce8cb019cf4ad951048fefdbf9f3e5ed0`
- The APK remains an ignored build artifact and was not added to Git.

## Gate 0 — Mac relay + Quick Tunnel + mock agent

- Relay bound to `ws://127.0.0.1:8765`.
- `cloudflared` created an outbound Quick Tunnel at `https://indices-built-settlement-keep.trycloudflare.com`.
- Mock connected through the corresponding public WSS URL.
- Mutual HMAC authentication: **PASS** (`[mock] mutually authenticated`).
- AES-256-GCM command/result framing with directional AAD and sequence counters: **PASS**.
- `ping`: **PASS**, result `pong`.
- `whoami`: **PASS**, result `mock:whoami`.
- `device`: **PASS**, result `mock:device`.
- `status`: **PASS**, reported `Glosh MockAgent`, device `mock`, SDK `0`.
- Non-allowlisted action check: **PASS**; `uname` was rejected locally with `Comando no permitido. Usá help.` and was not sent to the mock.
- Unexpected crypto, replay, or stale-callback errors: none observed.
- Revocation: **PASS**. `quit` exited the relay with code 0 and the mock disconnected with code 0. Reusing the same descriptor after shutdown failed before authentication with HTTP 530, so no further command could execute.

## Limitations and pending gate

- Gate 0 validates relay, Quick Tunnel, public WSS, mutual authentication, AES-GCM, sequence/correlation behavior, command results, allowlist rejection, and revocation without Android.
- It does not validate same-device mDNS, notification RemoteInput behavior, libadb pairing/TLS, OEM Developer Options, or real Android service lifecycle.
- The Gradle run emitted an SDK XML-version compatibility warning between installed command-line tooling components; build, tests, lint, and APK packaging still completed successfully.
- Quick Tunnel is DEV/laboratory-only and was terminated after the gate.
- Gate 1 with an Android 11+ device is pending and was intentionally not run.
- No Glosh install, Device Owner, VPN, Accessibility, Chrome, account, data reset, production deploy, push, PR, merge, or publication was performed.
