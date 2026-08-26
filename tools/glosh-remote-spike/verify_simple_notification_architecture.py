#!/usr/bin/env python3
"""Static product guard for the notification-only support entry point."""

from pathlib import Path


ROOT = Path(__file__).resolve().parent
APP = ROOT / "app" / "src" / "main"


def require(value: bool, message: str) -> None:
    if not value:
        raise SystemExit(f"ARCHITECTURE_GUARD_FAIL: {message}")


manifest = (APP / "AndroidManifest.xml").read_text()
activity = (
    APP / "java" / "com" / "glosh" / "remote" / "spike" / "MainActivity.java"
).read_text()
service = (
    APP / "java" / "com" / "glosh" / "remote" / "spike" / "RemotePairingService.java"
).read_text()
broker_client = (
    APP
    / "java"
    / "com"
    / "glosh"
    / "remote"
    / "spike"
    / "broker"
    / "SupportSessionBrokerClient.java"
).read_text()
guide_sources = list((APP / "java" / "com" / "glosh" / "remote" / "spike" / "guide").rglob("*.java"))

require("BIND_ACCESSIBILITY_SERVICE" not in manifest, "Accessibility must not be registered")
require("LiveGuideAccessibilityService" not in manifest, "guide service must not be exposed")
require(not guide_sources, "legacy guide and overlay code must be absent from the APK sources")
require("SYSTEM_ALERT_WINDOW" not in manifest, "overlay permission is forbidden")
require("android.permission.WAKE_LOCK" in manifest, "scoped screen wake lock is required")
require("ACTION_MANAGE_OVERLAY_PERMISSION" not in activity, "overlay settings are forbidden")
require("guide.accessibility" not in activity, "activity must not depend on Accessibility")
require("LiveGuideRuntime" not in activity, "activity must not activate the legacy guide")
require("PairingCodeInputView" not in activity, "PIN entry inside the app is forbidden")
require("showPairingInput" not in activity, "PIN entry inside the app is forbidden")
require("RemoteInput.Builder" in service, "notification RemoteInput is required")
require('"Ingresar 6 dígitos"' in service, "the notification PIN action is required")
require("openWirelessDebugging(this)" in activity, "official Wireless Debugging route is required")
require("takeDescriptor()" in activity, "one-time descriptor consumption is required")
require(
    "screenAwakeLease.acquireForSessionMinutes(SUPPORT_SESSION_MINUTES)" in service,
    "screen lease must start only after local ADB authentication",
)
require(
    service.index("screenAwakeLease.acquireForSessionMinutes(SUPPORT_SESSION_MINUTES)")
    > service.index('shell.execute("whoami")'),
    "screen lease must start after the authenticated ADB canary",
)
require("currentScreenLease.release()" in service, "screen lease must be released on cleanup")
require(
    "if (identity == null)" in broker_client,
    "request renewal must reuse the original ephemeral identity",
)
renewal_body = broker_client.split("private void renewExpiredRequest", 1)[1].split(
    "private synchronized boolean shouldRenew", 1
)[0]
require(
    "destroyIdentity()" not in renewal_body,
    "request renewal must not destroy the accepted client identity",
)

print("ARCHITECTURE_GUARD_PASS")
