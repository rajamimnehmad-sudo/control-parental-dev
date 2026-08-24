# Glosh Remote Live Guide V2 — Gate device route

Updated: 2026-08-24 18:32 ART

## Decision

User changed the physical validation route for `REMOTE-INSTALL-LIVE-GUIDE-V2-04`:

- Primary development/physical **Gate A** device is now the Samsung A23 (`SM-A235M`, Android 14 / API 34).
- USB/ADB is allowed only as lab instrumentation for installing the candidate APK, collecting logs/evidence and driving repeated guide-only tests.
- The final Glosh Remote product architecture remains wireless/remote and must not depend on USB or a customer PC.
- Samsung S22 remains a later spot-check / compatibility verification device after Gate A is stable on A23; an A23 PASS must not be described as universal Samsung or S22 physical PASS.

## Gate A scope on A23

No broker. No relay. No Operator. No full remote session.

Validate guide-only behavior:
- About phone → Software information;
- Software information → Build number;
- Developer options → Wireless debugging;
- `MOSTRARME` required before Glosh-initiated movement;
- max three reveal scroll actions;
- human scroll cancels reveal and applies cooldown;
- `ME PERDÍ` recovery;
- wrong-screen fail closed;
- overlay/IME excluded from scan authority;
- no stale highlight after rotation/window changes;
- zero crash/ANR.

Each target should be repeated enough to detect nondeterminism; any wrong highlight is a failure.

## Next route

1. Implement V2 against the current Remote worktree/HEAD.
2. Run technical gates.
3. Run Gate A on A23 via USB/ADB instrumentation.
4. If A23 Gate A passes, perform a shorter S22 compatibility spot-check before Gate B/C closure claims.
5. Gate B and C remain blocked until guide-only behavior is stable.

No Chrome, GloshIA, DAG, App Usuario/Admin, Supabase or production Device Owner logic may be touched by this device-route change.
