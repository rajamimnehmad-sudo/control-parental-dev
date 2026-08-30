"""ADB ownership, preflight, orientation, and exact navigation for H19."""

from __future__ import annotations

import hashlib
import os
import re
import shlex
import shutil
import subprocess
import time
from pathlib import Path
from typing import Any

from h19_plan import HarnessError


APP_PACKAGE = "com.contentfilter.user.dev"
CHROME_PACKAGE = "com.android.chrome"
RECEIVER = f"{APP_PACKAGE}/com.contentfilter.user.chromedataplane.ChromePhotosDataPlaneLabReceiver"
CONTROLLED_URL = "https://glosh-photos.test/web19/controlled"
CHROME_POLICY_URL = "chrome://policy"


class Adb:
    def __init__(self, binary: str, serial: str) -> None:
        self.prefix = [binary, "-s", serial]

    def run(
        self,
        *args: str,
        check: bool = True,
        timeout: int = 60,
        text: bool = True,
    ) -> subprocess.CompletedProcess[Any]:
        return subprocess.run(
            [*self.prefix, *args],
            check=check,
            capture_output=True,
            text=text,
            timeout=timeout,
        )

    def shell(self, *args: str, check: bool = True, timeout: int = 60) -> str:
        command = " ".join(shlex.quote(value) for value in args)
        return self.run("shell", command, check=check, timeout=timeout).stdout.replace("\r", "")

    def broadcast(self, action: str, extras: list[tuple[str, str, str]] | None = None) -> str:
        args = ["am", "broadcast", "--receiver-foreground", "-a", action, "-n", RECEIVER]
        for kind, key, value in extras or []:
            args.extend([kind, key, value])
        return self.shell(*args, timeout=30)

    def path_exists(self, path: str) -> bool:
        return self.run("shell", f"test -e {shlex.quote(path)}", check=False).returncode == 0


def sha256_text(value: str) -> str:
    return hashlib.sha256(value.encode()).hexdigest()


def locate_adb() -> str:
    candidates = [
        os.environ.get("ADB"),
        str(Path(os.environ.get("ANDROID_HOME", "")) / "platform-tools/adb") if os.environ.get("ANDROID_HOME") else None,
        shutil.which("adb"),
        str(Path.home() / "Library/Android/sdk/platform-tools/adb"),
    ]
    for candidate in candidates:
        if candidate and Path(candidate).is_file() and os.access(candidate, os.X_OK):
            return candidate
    raise HarnessError("adb not found; set ADB or ANDROID_HOME")


def setting(adb: Adb, name: str) -> str | None:
    value = adb.shell("settings", "get", "system", name).strip()
    return None if value in {"", "null"} else value


def restore_setting(adb: Adb, name: str, value: str | None) -> None:
    if value is None:
        adb.shell("settings", "delete", "system", name, check=False)
    else:
        adb.shell("settings", "put", "system", name, value, check=False)


def namespaced_setting(adb: Adb, namespace: str, name: str) -> str | None:
    if namespace not in {"system", "secure", "global"}:
        raise HarnessError(f"unsupported settings namespace: {namespace}")
    value = adb.shell("settings", "get", namespace, name).strip()
    return None if value in {"", "null"} else value


def restore_namespaced_setting(adb: Adb, namespace: str, name: str, value: str | None) -> None:
    if namespace not in {"system", "secure", "global"}:
        raise HarnessError(f"unsupported settings namespace: {namespace}")
    if value is None:
        adb.shell("settings", "delete", namespace, name, check=False)
    else:
        adb.shell("settings", "put", namespace, name, value, check=False)


def interactive_display_snapshot(adb: Adb) -> dict[str, Any]:
    power = adb.shell("dumpsys", "power", check=False, timeout=30)
    policy = adb.shell("dumpsys", "window", "policy", check=False, timeout=30)
    wakefulness = re.search(r"\bmWakefulness=([A-Za-z]+)", power)
    interactive = re.search(r"\binteractiveState=([A-Z_]+)", policy)
    keyguard = re.search(r"\bmIsShowing=(true|false)\b", policy)
    return {
        "wakefulness": wakefulness.group(1) if wakefulness else "unknown",
        "interactiveState": interactive.group(1) if interactive else "unknown",
        "keyguardShowing": keyguard.group(1) == "true" if keyguard else None,
    }


def prepare_interactive_display(adb: Adb, timeout_seconds: int = 10) -> dict[str, Any]:
    """Acquire a reversible, bounded display lease for a physical Chrome gate."""

    before = interactive_display_snapshot(adb)
    previous_stay_awake = namespaced_setting(adb, "global", "stay_on_while_plugged_in")
    try:
        adb.shell("settings", "put", "global", "stay_on_while_plugged_in", "3")
        adb.shell("input", "keyevent", "224")
        adb.shell("wm", "dismiss-keyguard", check=False)
        deadline = time.monotonic() + timeout_seconds
        current = interactive_display_snapshot(adb)
        while time.monotonic() < deadline:
            if (
                current["wakefulness"] == "Awake"
                and current["interactiveState"] == "INTERACTIVE_STATE_AWAKE"
                and current["keyguardShowing"] is False
            ):
                return {
                    "before": before,
                    "current": current,
                    "previousStayOnWhilePluggedIn": previous_stay_awake,
                    "currentStayOnWhilePluggedIn": namespaced_setting(
                        adb,
                        "global",
                        "stay_on_while_plugged_in",
                    ),
                    "restoreSleep": before["wakefulness"] != "Awake",
                }
            time.sleep(0.2)
            current = interactive_display_snapshot(adb)
    except BaseException:
        restore_namespaced_setting(adb, "global", "stay_on_while_plugged_in", previous_stay_awake)
        raise
    restore_namespaced_setting(adb, "global", "stay_on_while_plugged_in", previous_stay_awake)
    raise HarnessError(f"A23 display did not become interactive and unlocked: {current}")


def restore_interactive_display(adb: Adb, lease: dict[str, Any]) -> dict[str, Any]:
    restore_namespaced_setting(
        adb,
        "global",
        "stay_on_while_plugged_in",
        lease.get("previousStayOnWhilePluggedIn"),
    )
    if lease.get("restoreSleep") is True:
        adb.shell("input", "keyevent", "223", check=False)
    return {
        "display": interactive_display_snapshot(adb),
        "stayOnWhilePluggedIn": namespaced_setting(adb, "global", "stay_on_while_plugged_in"),
    }


def package_info(adb: Adb, package_name: str) -> dict[str, str]:
    output = adb.shell("dumpsys", "package", package_name)
    version_code = re.search(r"\bversionCode=(\d+)", output)
    version_name = re.search(r"\bversionName=([^\s]+)", output)
    return {
        "installed": str(bool(adb.shell("pm", "path", package_name, check=False).strip())).lower(),
        "versionCode": version_code.group(1) if version_code else "",
        "versionName": version_name.group(1) if version_name else "",
        "dumpsysSha256": sha256_text(output),
    }


def ce_data_inode_from_package_dump(value: str) -> str:
    match = re.search(r"\bUser\s+0:\s+ceDataInode=(\d+)\b", value)
    if not match:
        raise HarnessError("cannot establish the app ceDataInode from PackageManager")
    return match.group(1)


def ce_data_inode(adb: Adb) -> str:
    return ce_data_inode_from_package_dump(adb.shell("dumpsys", "package", APP_PACKAGE))


def filtered_device_policy(value: str) -> dict[str, Any]:
    selected = []
    for line in value.splitlines():
        lowered = line.lower()
        if any(token in lowered for token in ("owner", "affiliation", "com.android.chrome", "contentfilter")):
            selected.append(line.strip()[:500])
    return {"sha256": sha256_text(value), "selectedLines": selected[:120]}


def exit_info(adb: Adb, package_name: str) -> dict[str, Any]:
    output = adb.shell("dumpsys", "activity", "exit-info", package_name, check=False, timeout=60)
    reasons = {
        "crash": len(re.findall(r"REASON_CRASH(?:_NATIVE)?", output)),
        "anr": len(re.findall(r"REASON_ANR", output)),
        "lowMemory": len(re.findall(r"REASON_LOW_MEMORY", output)),
    }
    return {"sha256": sha256_text(output), "reasons": reasons}


def exit_info_delta(before: dict[str, Any], after: dict[str, Any]) -> dict[str, int]:
    before_reasons = before.get("reasons", {})
    after_reasons = after.get("reasons", {})
    return {
        reason: max(0, int(after_reasons.get(reason, 0)) - int(before_reasons.get(reason, 0)))
        for reason in ("crash", "anr", "lowMemory")
    }


def collect_preflight(adb: Adb, expected_model: str, expected_sdk: int) -> dict[str, Any]:
    if adb.run("get-state").stdout.strip() != "device":
        raise HarnessError("ADB target is not in device state")
    model = adb.shell("getprop", "ro.product.model").strip()
    sdk_value = adb.shell("getprop", "ro.build.version.sdk").strip()
    if model != expected_model or not sdk_value.isdigit() or int(sdk_value) != expected_sdk:
        raise HarnessError(f"unexpected device {model}/API{sdk_value}; expected {expected_model}/API{expected_sdk}")
    owners = adb.shell("dpm", "list-owners", check=False)
    accessibility = adb.shell("settings", "get", "secure", "enabled_accessibility_services", check=False).strip()
    accessibility_dump = adb.shell("dumpsys", "accessibility", check=False, timeout=60)
    policy = adb.shell("dumpsys", "device_policy", check=False, timeout=90)
    inode = ce_data_inode(adb)
    service_dump = adb.shell("dumpsys", "activity", "services", APP_PACKAGE, check=False)
    if "ChromePhotosDataPlaneLabService" in service_dump:
        raise HarnessError("H19 service is already active; refuse to overwrite an existing lab session")
    owner_text = (owners + policy).lower()
    if APP_PACKAGE not in owner_text or "deviceowner" not in owner_text.replace(" ", ""):
        raise HarnessError("Glosh is not the current Device Owner")
    affiliated = "Affiliated" in owners or bool(re.search(r"mAffiliationIds=\[[^\]]+\]", policy))
    if not affiliated:
        raise HarnessError("A23 is not demonstrably affiliated")
    if "ProtectorAccessibilityService" not in accessibility or "ProtectorAccessibilityService" not in accessibility_dump:
        raise HarnessError("Glosh Accessibility is not enabled and bound")
    return {
        "model": model,
        "sdk": int(sdk_value),
        "android": adb.shell("getprop", "ro.build.version.release").strip(),
        "buildFingerprintSha256": sha256_text(adb.shell("getprop", "ro.build.fingerprint").strip()),
        "app": package_info(adb, APP_PACKAGE),
        "chrome": package_info(adb, CHROME_PACKAGE),
        "owners": [line.strip() for line in owners.splitlines() if line.strip()],
        "affiliatedObserved": True,
        "accessibilityIncludesGlosh": "contentfilter" in accessibility.lower(),
        "accessibilityBound": True,
        "enabledAccessibilitySha256": sha256_text(accessibility),
        "ceDataInode": inode,
        "devicePolicy": filtered_device_policy(policy),
        "exitInfo": {"app": exit_info(adb, APP_PACKAGE), "chrome": exit_info(adb, CHROME_PACKAGE)},
        "wmSize": adb.shell("wm", "size").strip(),
        "wmDensity": adb.shell("wm", "density").strip(),
    }


def observed_display_rotation(adb: Adb) -> int | None:
    output = adb.shell("dumpsys", "input", check=False, timeout=30)
    match = re.search(r"SurfaceOrientation:\s*([0-3])", output)
    if match:
        return int(match.group(1))
    display = adb.shell("dumpsys", "display", check=False, timeout=30)
    for pattern in (r"\bmCurrentOrientation=([0-3])\b", r"\brotation\s+([0-3])\b"):
        match = re.search(pattern, display, flags=re.IGNORECASE)
        if match:
            return int(match.group(1))
    return None


def set_and_verify_orientation(adb: Adb, orientation: str, timeout_seconds: int = 10) -> dict[str, Any]:
    if orientation == "current":
        return {"requested": "current", "observedRotation": observed_display_rotation(adb), "verified": True}
    adb.shell("settings", "put", "system", "accelerometer_rotation", "0")
    adb.shell("settings", "put", "system", "user_rotation", "0" if orientation == "portrait" else "1")
    expected = 0 if orientation == "portrait" else 1
    deadline = time.monotonic() + timeout_seconds
    observed: int | None = None
    while time.monotonic() < deadline:
        observed = observed_display_rotation(adb)
        if observed == expected:
            return {"requested": orientation, "observedRotation": observed, "verified": True}
        time.sleep(0.2)
    raise HarnessError(f"display rotation did not reach {orientation}; observed={observed}")


def navigate(adb: Adb, state: dict[str, Any]) -> None:
    action = state.get("navigation", "url")
    if action in {"url", "controlled", "chrome-policy"}:
        target = state["url"] if action == "url" else CONTROLLED_URL if action == "controlled" else CHROME_POLICY_URL
        adb.shell(
            "am", "start", "-W", "-a", "android.intent.action.VIEW", "-d", target, "-p", CHROME_PACKAGE,
            timeout=45,
        )
    elif action == "back":
        adb.shell("input", "keyevent", "4")
    elif action == "forward":
        adb.shell("input", "keyevent", "125")
    elif action == "reload":
        adb.shell("input", "keyevent", "285")
    elif action == "foreground":
        adb.shell("monkey", "-p", CHROME_PACKAGE, "-c", "android.intent.category.LAUNCHER", "1")
    elif action == "background-foreground":
        adb.shell("input", "keyevent", "3")
        time.sleep(0.5)
        adb.shell("monkey", "-p", CHROME_PACKAGE, "-c", "android.intent.category.LAUNCHER", "1")
    elif action == "restart-chrome":
        adb.shell("am", "force-stop", CHROME_PACKAGE)
        adb.shell("monkey", "-p", CHROME_PACKAGE, "-c", "android.intent.category.LAUNCHER", "1")
    else:
        raise HarnessError(f"unsupported bounded navigation action: {action}")


def swipe_up(adb: Adb) -> None:
    size = adb.shell("wm", "size")
    match = re.search(r"Physical size:\s*(\d+)x(\d+)", size)
    if not match:
        raise HarnessError("cannot determine physical display size")
    width, height = int(match.group(1)), int(match.group(2))
    adb.shell("input", "swipe", str(width // 2), str(height * 4 // 5), str(width // 2), str(height // 4), "420")


def tap_normalized(adb: Adb, x_permille: int, y_permille: int) -> None:
    size = adb.shell("wm", "size")
    match = re.search(r"Physical size:\s*(\d+)x(\d+)", size)
    if not match:
        raise HarnessError("cannot determine physical display size")
    width, height = int(match.group(1)), int(match.group(2))
    x = min(width - 1, max(0, width * x_permille // 1000))
    y = min(height - 1, max(0, height * y_permille // 1000))
    adb.shell("input", "tap", str(x), str(y))
