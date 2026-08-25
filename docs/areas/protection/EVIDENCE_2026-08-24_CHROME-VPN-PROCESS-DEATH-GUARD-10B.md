# CHROME-VPN-PROCESS-DEATH-GUARD-10B — Evidence

## Result

**PASS DEV.** This ticket adds an independent fail-closed Chrome guard and closes the normal main-process-death window demonstrated on the A23. It does not claim Production readiness, general web semantics, image provenance, or an absolute guarantee after every Android package-wide force-stop implementation.

## Coordination

- Base: `c58d32b1608fabdbd6399e00a8f2e30f3374081e` (10A final evidence).
- 10A functional ancestor: `e00681811dc5b9115df989a3177866074f296dfb`.
- Functional 10B commit: `1a9852c867a97c24c142b2f8f478299f5d916fab`.
- Final evidence commit: the commit containing this document; exact SHA is reported in the final handoff.
- Branch: `work/chrome-vpn-process-death-guard-10b`.
- Worktree: `/Users/yejielnehmad/Developer/glosh-chrome-vpn-process-death-guard-10b`.
- Owner: Proteccion Android / Codex.
- Glosh Remote remained paused. No S22 use. No Glosh Central write.
- The unrelated dirty `work/chrome-visual` files, including both `VpnDomainPolicyEvaluator*` paths, were not read for implementation, edited, cleaned, stashed, or reset.

## Observable security contract

`NO CURRENT PROTECTION LEASE => CHROME SUSPENDED`.

The main process no longer calls the Chrome release mutation. It may only open a guard session and publish a short healthy lease. The separate `:chrome_guard` process is the sole final release authority. Unknown state, boot, package replacement, invalid caller, stale/replayed identity, bad health, expiry, Binder death, release-verification failure, and explicit revocation all converge on verified Device Owner suspension.

## Architecture

```text
main process
  existing bootstrap + VPN/transport/proxy/policy/GloshIA/A11y health
      |
      | private explicit Messenger/Binder IPC
      | session + nonce + generation + heartbeat lease
      v
:chrome_guard
  LeaseVerifier -> GuardCoordinator -> ChromeSuspensionAuthority
      |                                      |
      | device-protected minimal state       | DevicePolicyManager
      v                                      v
  generation / reason / expected state   com.android.chrome suspend/release
```

### Process isolation

- Service process: `android:process=":chrome_guard"`, `exported=false`, Direct-Boot aware.
- Physical healthy baseline used different PIDs for main and guard.
- The guard process returns before VPN, sync, Room, or remote coordinators are started. Hilt/Application construction still contributes substantial PSS; see residual risks.
- The guard does not run GloshIA, proxy, HEV, DNS, image processing, datasets, or UI.

### Lease and IPC

- IPC: explicit private bound service using `Messenger`; Binder `sendingUid` must equal the application UID and resolve back to the same package.
- Heartbeat interval: 500 ms.
- Lease TTL: 1,500 ms.
- IPC/open-session timeout: 5,000 ms.
- Time authority: `SystemClock.elapsedRealtime()`.
- Identity: schema version, boot marker, monotonically persisted protection generation, session ID, per-main-process random nonce, bootstrap generation, heartbeat sequence, and runtime generation fields.
- Rejected: no session, wrong caller, old boot, old generation/session/nonce/bootstrap, duplicate or reordered heartbeat, future issue time, expired/oversized TTL, invalid runtime generation, or any unhealthy dependency.
- Binder death provides immediate revocation; the TTL remains the independent bounded fallback if heartbeats stop without a Binder callback.

### Device-protected storage

Only the schema, monotonic generation, expected suspended state, bounded reason, boot marker, and guard restart count are stored in Device Protected Storage. No CA keys, remote tokens, images, models, datasets, or user content are stored there. A schema mismatch resets to expected-suspended.

### Suspension authority

- Reuses the existing Device Owner admin and `DevicePolicyManager.setPackagesSuspended()`.
- Effective state is verified with `PackageManager.isPackageSuspended()`.
- Mutations are idempotent and use at most three attempts with two bounded 50 ms gaps.
- Release verification failure invalidates the session and immediately attempts verified suspension. If fallback suspension also fails, state is `Unverified`; no lease remains capable of release.
- Main-process fail-close also invokes the same shared authority, so loss of the guard does not leave Chrome open while Android restarts the guard.

### Boot and update

- `LOCKED_BOOT_COMPLETED`, `BOOT_COMPLETED`, and `MY_PACKAGE_REPLACED` are handled in `:chrome_guard`.
- Default after each is suspended; old sessions are not persisted.
- New release requires a new boot-valid session/generation and full health.
- Chrome reset generation is not changed and full reset is not repeated.

### Always-on / lockdown

The pre-gate values were `always_on_vpn_app=null` and `always_on_vpn_lockdown=null`. The experiment did not mutate them because verified Device Owner package suspension was effective and the A23 rejected package-wide force-stop of its protected Device Owner package. The exact prior values therefore remained unchanged without a rollback mutation.

## Automated gates

Final nominal command:

```text
:feature-vpn:testDebugUnitTest
:feature-vpn:compileDebugKotlin
:feature-vpn:ktlintCheck
:feature-vpn:lintDebug
:app-user:testDevDebugUnitTest
:app-user:compileDevDebugKotlin
:app-user:lintDevDebug
:app-user:assembleDevDebug
git diff --check
```

Result: **PASS** (`BUILD SUCCESSFUL`; `git diff --check` empty).

Covered deterministically:

- no lease defaults to suspension;
- current lease releases and expiry suspends;
- stale generation/session/nonce/boot/bootstrap and replayed heartbeat reject;
- wrong caller rejects;
- every required health bit rejects with a stable reason;
- suspension failure prevents release;
- release verification failure re-suspends and invalidates the lease;
- release plus fallback-suspension failure remains `Unverified`;
- recovery requires a new generation;
- boot/package replacement invalidates old lease;
- repeated suspension is idempotent;
- storage migration, timer, IPC, retry, heartbeat, and TTL bounds.

One earlier parallel lint attempt encountered an Android Lint/FIR internal analyzer exception while resolving unrelated `UserHomeGreetingTest.kt`, with no lint finding. A serial retry and the final nominal matrix above both passed; this was tooling instability, not a touched-scope failure.

## APK

- Package: `com.contentfilter.user.dev`.
- Version: `344` / `1.0.1-dev`.
- File: `app-user/build/outputs/apk/dev/debug/app-user-dev-debug.apk`.
- Size: 158,827,845 bytes.
- SHA-256: `1e630305034383014ae9175e7259065353bea03b44efca7db6e60dfe002d890a`.
- Install: `adb install -r`, success; no uninstall or data clear.
- Signer: `C=US, O=Android, CN=Android Debug`.
- Signer SHA-256: `d51bc0dabd280ce1b0f098ae168eb57758faeba301156cde835737835f8a8832`.

## Physical device

- Serial: `R58T34V31AE`.
- Model: Samsung `SM-A235M`.
- Android: 14 / API 34.
- Chrome: `151.0.7922.173`.
- App User CE inode before/after/final: `1239519`.
- Chrome bootstrap reset count: exactly `1` throughout.
- Device Owner: `com.contentfilter.user.dev/...ProtectionDeviceAdminReceiver`.
- Affiliated: yes.
- Accessibility component: `...ProtectorAccessibilityService`.
- Product VPN: active before and after; `bypassable=false`.

## Physical failure matrix

### Healthy baseline

Full protection started with a fresh session, guard generation, proxy/CA, GloshIA R3.1, VPN attestation, and A11y. The guard released Chrome only after the first all-ready lease. Main and guard PIDs were distinct. Full-tunnel and transport behavior reused the reviewed 10A implementation unchanged.

### Main PID kill

- Main PID was killed while Chrome was released under a valid lease.
- The guard PID survived.
- Command observed: 23:11:40.847.
- `main_binder_died`: 23:11:41.011.
- Verified Chrome suspension: 23:11:41.112.
- Command-to-suspension: about 265 ms; Binder-death-to-suspension: about 101 ms.
- Android restarted the main process because Accessibility remained bound, but Chrome stayed suspended.
- Opening Chrome produced `ActionDisabledByAdminDialog`.
- Recovery required STOP/START, a new session and generation; reset count stayed 1.

### Deliberate Java fatal crash

- Expected DEV fatal: `chrome_guard_dev_main_crash` at 23:14:04.690.
- Binder death observed: 23:14:05.074.
- Suspension verified: 23:14:05.235.
- Fatal-to-suspension: about 545 ms.
- `ApplicationExitInfo` classified the deliberate main-process event as an app crash/exception.
- Guard survived; recovery required a new generation.

### Transport/native loss

A controlled full-tunnel transport stop revoked VPN attestation at 23:15:18.723. Main fail-close and guard invalidation/suspension were observed by 23:15:18.886, about 163 ms. Restarting only the transport did not reopen Chrome; full session reaccreditation was required. No unsafe native undefined-behavior crash hook was introduced.

### Guard process death

An early DEV342 hook killed synchronously during `onStartCommand` and Android redelivered that destructive start, creating a diagnostic-only guard crash loop. The hook was corrected to return `START_NOT_STICKY` before an asynchronous self-kill.

DEV343 then demonstrated one kill, immediate main fail-close, one guard restart in suspended state, and no redelivery loop.

Final DEV344 smoke validated the final client-binding behavior:

- old guard PID: 19745;
- DEV kill logged: 23:42:42.975;
- main verified `guard_lost` suspension: 23:42:43.084 (about 109 ms);
- main PID remained 19797;
- new guard PID: 20773;
- new guard created suspended at 23:42:44.580;
- old lease/session was not reused and Chrome remained suspended.

### Process eviction approximation

Android later evicted an idle/stopped guard process (`Killing ... :chrome_guard ... empty #21`). Chrome had already been suspended and the lab stopped. The next receiver-driven start initialized the guard suspended. This does not claim Android will preserve an idle process indefinitely; the persistent safe state is DPM suspension, not process residency.

### Package update

DEV344 was installed in place over DEV343. Package replacement started the guard suspended with reason `package_replaced_guard`, then opened generation 10 only from the new main runtime. Immediately after install, the secure Accessibility enable flag was transiently 0; Chrome remained suspended. Accessibility subsequently returned enabled/bound, health reaccredited, and the first valid lease released Chrome. Reset was skipped (`generation=1`, `resetCount=1`), and CE inode remained 1239519.

### Reboot / Direct Boot

Before reboot Chrome was suspended. After reboot (`boot_count=77`), the independent guard started for locked boot and recorded `boot_guard`; Chrome remained suspended. The main locked-boot guard also reported blocked. After unlock, the main runtime and Accessibility returned, a new generation/session was created, reset was skipped, and release happened only after full health. The pre-reboot lease did not release Chrome.

### Accessibility loss

The original secure settings were captured, the Glosh Accessibility component was removed for the gate, and then the exact values were restored. Loss was detected at 23:26:04.659; main DPM suspension was verified at 23:26:04.753 (about 94 ms) and guard invalidation at 23:26:04.772 (about 113 ms). Restoration did not resurrect the old lease; a new STOP/START session was required.

### Force-stop platform behavior

The test was performed only after STOP with Chrome already suspended. On this Samsung Device Owner configuration, Android rejected `am force-stop com.contentfilter.user.dev` and logged that the protected package request was ignored. Main and guard processes remained; Chrome remained suspended and opening it produced `ActionDisabledByAdminDialog`. Always-on/lockdown remained null. Therefore this device did not expose the package-wide process-death case; no claim is made that a same-package guard survives a force-stop on platforms that permit one.

## Chrome / GloshIA canaries

- BLOCK fresh request on DEV343: `farm6.staticflickr.com`, nonce `guard10b_block_343_1787624981`, cache miss, engine call 1, `model_filter`, 77,187 input bytes -> 6,303 placeholder bytes; original was not delivered.
- A new 10B SAFE request to the historical httpbingo origin was attempted, but current lab policy/origin availability produced `connect_not_allowed` and zero requests. It is **not** claimed as a fresh SAFE. The reviewed 10A SAFE regression remains historical evidence; 10B did not change GloshIA, proxy semantics, model, thresholds, preprocessing, or datapath.
- Protected-surface regression during the valid canary: raw presented 0, stale 0, grid 0, post-ready captures 0.

## Exposure and bypass result

- After main kill, Java crash, transport loss, A11y loss, guard loss, reboot guard, package replacement guard, and final STOP, Chrome was effectively suspended.
- Attempts to open Chrome after fail-close were blocked by the Device Admin dialog.
- No new raw/web delivery or direct network bypass was observed after the measured fail-close points.
- The product VPN remained non-bypassable and Chrome direct TCP/UDP policy from 10A was not relaxed.

## Resources, crashes, and wakeups

- Final transport status: inactive; `ownedFdResources=0`; `activeProtectedUdpSockets=0`; runtime ready; controlled/full-tunnel default routes removed.
- Final UDP associations and protected UDP resources: 0.
- Final guard service stopped; Chrome remains suspended and the product DNS VPN remains active.
- No unexpected SIGABRT, SIGSEGV, native crash, ANR, or OOM was attributed to DEV344.
- Expected events are distinguished: deliberate Java main crash and deliberate PID kills.
- Representative guard snapshot before final build: about 101,546 KB total PSS and 66,336 KB total RSS. Most cost is inherited app/Hilt initialization; this is a real optimization/audit residual, not hidden.
- Steady lease scheduling is 500 ms with a 1,500 ms deadline. No tens-of-times-per-second polling was added.

## Rollback and final state

- Lease revoked; Chrome suspended first.
- Guard DEV service stopped.
- Proxy/CA removed; experimental transport stopped.
- Full-tunnel routes removed.
- Product VPN/DNS rebuilt and active with only its bounded DNS routes.
- Owned resources, UDP associations, and protected UDP sockets: 0.
- Always-on/lockdown restored exactly by leaving their prior null values unchanged.
- `resetCount=1`; CE inode 1239519; Device Owner/Affiliated preserved; Accessibility enabled and bound.
- Fixture/data from other tickets was not deleted; no Chrome/App User clear, uninstall, or second reset occurred.

## Real limitations and residual risk

1. A same-package independent process is not guaranteed to survive an Android force-stop that kills the entire package. On this A23 the force-stop was rejected because the package is protected as Device Owner. Persistent DPM Chrome suspension was the effective barrier.
2. A pure physical TTL-only expiry could not be isolated because shell `SIGSTOP` was denied and the app is non-debuggable; physical main kill and Java crash exercised the stronger Binder-death path, while TTL/replay/expiry are deterministic unit gates.
3. The guard process PSS is high for a minimal watchdog because the Hilt Application is still constructed before the guard-process early return. This should be reduced before Production.
4. No fresh authoritative SAFE image was available during 10B; this is recorded as an environmental/policy limitation, not reported as PASS evidence.
5. Always-on/lockdown vendor behavior was read and preserved but not enabled; any future adoption needs its own reversible Device Owner gate.
6. 10B remains DEV-only. It does not close general web semantics, Service Worker/content provenance, 11A, 11B, or Production hardening.

## Repository actions

- Local commits only.
- No push, PR, merge, main change, deployment, Production action, or Glosh Central modification.
