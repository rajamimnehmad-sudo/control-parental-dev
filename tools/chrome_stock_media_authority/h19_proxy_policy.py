"""Bounded DEV proof that the current stock-Chrome process consumed ProxySettings."""

from __future__ import annotations

import re
import select
import subprocess
import time
from typing import Any

from h19_device import CHROME_PACKAGE, Adb, navigate, sha256_text
from h19_plan import HarnessError


CHROME_PROXY_POLICY = (
    '{"ProxyMode":"fixed_servers","ProxyServer":"127.0.0.1:8877",'
    '"ProxyBypassList":"<-loopback>"}'
)
CHROME_POLICY_LOG_TAG = "cr_CombinedPProvider"
CHROME_POLICY_LINE = re.compile(
    r"^\S+\s+\S+\s+(?P<pid>\d+)\s+\d+\s+I\s+cr_CombinedPProvider:\s+(?P<message>.*)$"
)


def chrome_proxy_policy_transition(
    lines: list[str],
    browser_pid: int,
) -> dict[str, Any] | None:
    """Accept only ProxySettings followed by a flush from the current Chrome browser process."""

    set_observed = False
    for line in lines:
        match = CHROME_POLICY_LINE.match(line.strip())
        if match is None or int(match.group("pid")) != browser_pid:
            continue
        message = match.group("message")
        if message.startswith("#setPolicy() ProxySettings -> "):
            set_observed = message == f"#setPolicy() ProxySettings -> {CHROME_PROXY_POLICY}"
        elif set_observed and message == "#flushPolicies()":
            return {
                "pass": True,
                "browserPid": browser_pid,
                "source": "chrome_combined_policy_provider",
                "proxyPolicySha256": sha256_text(CHROME_PROXY_POLICY),
                "setObserved": True,
                "flushObservedAfterSet": True,
            }
    return None


def wait_for_chrome_proxy_policy_observed(
    adb: Adb,
    since: str,
    timeout_seconds: int = 12,
    launch_policy_page: bool = True,
) -> dict[str, Any]:
    """Observe Chrome consume the current policy; the fixture later proves network use."""

    process = subprocess.Popen(
        [
            *adb.prefix,
            "logcat",
            "-T",
            since,
            "-v",
            "threadtime",
            f"{CHROME_POLICY_LOG_TAG}:I",
            "*:S",
        ],
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        text=True,
        bufsize=1,
    )
    lines: list[str] = []
    deadline = time.monotonic() + timeout_seconds
    try:
        if launch_policy_page:
            navigate(adb, {"navigation": "chrome-policy"})
        pids = [value for value in adb.shell("pidof", CHROME_PACKAGE, check=False).split() if value.isdigit()]
        if len(pids) != 1:
            raise HarnessError(f"cannot bind Chrome policy activation to one browser process; pids={len(pids)}")
        browser_pid = int(pids[0])
        while time.monotonic() < deadline:
            evidence = chrome_proxy_policy_transition(lines, browser_pid)
            if evidence is not None:
                return evidence
            if process.stdout is None:
                break
            remaining = max(0.0, deadline - time.monotonic())
            readable, _, _ = select.select([process.stdout], [], [], remaining)
            if not readable:
                break
            line = process.stdout.readline()
            if not line:
                if process.poll() is not None:
                    break
                continue
            lines.append(line)
        raise HarnessError(
            "Chrome did not observe the current managed proxy policy before network navigation; "
            "classification=CHROME_EFFECTIVE_POLICY_COLD_START_RACE"
        )
    finally:
        if process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=3)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=3)
