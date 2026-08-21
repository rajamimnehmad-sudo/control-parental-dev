import hashlib
import os
import stat
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "glosh_device_owner_installer.sh"
EXPECTED_SHA = "ba612fe2f23c5633e7041bf6c233d1ed435db3bcc7f43e6d47dfb03d7b7cf14b"


class InstallerTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.calls = self.root / "calls.log"
        self.owner = self.root / "owner"
        self.adb = self.root / "adb"
        self.adb.write_text(
            textwrap.dedent(
                """\
                #!/usr/bin/env bash
                set -u
                printf '%s\\n' "$*" >>"$MOCK_CALLS"
                if [[ "$1" == "devices" ]]; then
                  printf 'List of devices attached\\n'
                  printf '%b' "${MOCK_DEVICES:-MOCK123\\tdevice\\n}"
                  exit 0
                fi
                [[ "$1" == "-s" ]] || exit 90
                shift 2
                if [[ "$1" == "install" ]]; then
                  printf '%s\\n' "${MOCK_INSTALL_OUTPUT:-Success}"
                  exit "${MOCK_INSTALL_EXIT:-0}"
                fi
                [[ "$1" == "shell" ]] || exit 91
                shift
                case "$*" in
                  "getprop ro.product.model") echo SM-A235M ;;
                  "getprop ro.build.version.release") echo 14 ;;
                  "getprop ro.build.version.sdk") echo 34 ;;
                  "getprop ro.kernel.qemu") echo 0 ;;
                  "settings get global device_provisioned") echo 1 ;;
                  "settings get secure user_setup_complete") echo 1 ;;
                  "settings get secure enabled_accessibility_services") echo com.contentfilter.user.dev/.ProtectionAccessibilityService ;;
                  "dpm list-owners")
                    if [[ -e "$MOCK_OWNER_FILE" ]]; then
                      echo '1 owner:'
                      echo 'User 0: admin=com.contentfilter.user.dev/com.contentfilter.feature.accessibility.service.ProtectionDeviceAdminReceiver,DeviceOwner'
                    elif [[ -n "${MOCK_OTHER_OWNER:-}" ]]; then
                      echo '1 owner:'
                      echo 'User 0: admin=other/.Admin,DeviceOwner'
                    else
                      echo 'no owners'
                    fi
                    ;;
                  "pm list users")
                    echo 'Users:'
                    echo ' UserInfo{0:Owner:4c13} running'
                    if [[ "${MOCK_EXTRA_USER:-0}" == 1 ]]; then
                      echo ' UserInfo{10:Work:20} running'
                    fi
                    ;;
                  "dumpsys account")
                    echo 'User UserInfo{0:Owner:4c13}:'
                    echo "  Accounts: ${MOCK_ACCOUNTS:-0}"
                    if [[ "${MOCK_ACCOUNTS:-0}" != 0 ]]; then
                      echo '    Account {name=secret@example.invalid, type=com.google}'
                    fi
                    echo
                    echo '  AccountId, Action_Type, timestamp'
                    ;;
                  "dumpsys device_policy")
                    echo 'Enabled Device Admins (User 0, provisioningState: 0):'
                    echo 'com.contentfilter.user.dev/com.contentfilter.feature.accessibility.service.ProtectionDeviceAdminReceiver:'
                    ;;
                  "pm list packages") echo package:android ; echo package:com.contentfilter.user.dev ;;
                  "pm list packages -3") echo package:com.contentfilter.user.dev ;;
                  "dumpsys package com.contentfilter.user.dev")
                    echo "versionCode=${MOCK_VERSION:-318} minSdk=29 targetSdk=36"
                    echo 'ceDataInode=12345 installed=true'
                    echo 'com.contentfilter.feature.accessibility.service.ProtectionDeviceAdminReceiver'
                    ;;
                  "dumpsys accessibility") echo com.contentfilter.user.dev ;;
                  "am start -a android.settings.SYNC_SETTINGS") echo Starting ;;
                  "dpm set-device-owner --user 0 com.contentfilter.user.dev/com.contentfilter.feature.accessibility.service.ProtectionDeviceAdminReceiver")
                    echo "${MOCK_DPM_OUTPUT:-Success: Device owner set}"
                    if [[ "${MOCK_DPM_EXIT:-0}" == 0 ]]; then
                      touch "$MOCK_OWNER_FILE"
                      exit 0
                    fi
                    exit "$MOCK_DPM_EXIT"
                    ;;
                  *) echo "unexpected: $*" >&2 ; exit 92 ;;
                esac
                """
            ),
            encoding="utf-8",
        )
        self.adb.chmod(self.adb.stat().st_mode | stat.S_IXUSR)
        self.env = os.environ.copy()
        self.env.update(
            {
                "ADB_BIN": str(self.adb),
                "MOCK_CALLS": str(self.calls),
                "MOCK_OWNER_FILE": str(self.owner),
                "DEVICE_OWNER_CHECKPOINT_DIR": str(self.root / "checkpoints"),
            }
        )

    def tearDown(self):
        self.temp.cleanup()

    def run_script(self, *args, input_text=None, env=None):
        return subprocess.run(
            ["bash", str(SCRIPT), *args],
            input=input_text,
            text=True,
            capture_output=True,
            env=env or self.env,
            check=False,
        )

    def test_preflight_blocks_accounts_and_redacts_identity(self):
        env = self.env | {"MOCK_ACCOUNTS": "1"}
        result = self.run_script("preflight", env=env)
        self.assertEqual(10, result.returncode)
        checkpoints = list((self.root / "checkpoints").glob("*preflight.txt"))
        self.assertEqual(1, len(checkpoints))
        text = checkpoints[0].read_text(encoding="utf-8")
        self.assertIn("Cuentas registradas: 1", text)
        self.assertIn("1 com.google", text)
        self.assertNotIn("secret@example.invalid", text)
        self.assertNotIn("set-device-owner", self.calls.read_text(encoding="utf-8"))

    def test_preflight_rejects_multiple_devices(self):
        env = self.env | {"MOCK_DEVICES": "ONE\\tdevice\\nTWO\\tdevice\\n"}
        result = self.run_script("preflight", env=env)
        self.assertEqual(3, result.returncode)

    def test_preflight_rejects_extra_user(self):
        env = self.env | {"MOCK_EXTRA_USER": "1"}
        result = self.run_script("preflight", env=env)
        self.assertEqual(12, result.returncode)

    def test_preflight_rejects_other_owner(self):
        env = self.env | {"MOCK_OTHER_OWNER": "1"}
        result = self.run_script("preflight", env=env)
        self.assertEqual(11, result.returncode)

    def test_hash_mismatch_stops_before_install(self):
        apk = self.root / "wrong.apk"
        apk.write_bytes(b"wrong")
        result = self.run_script("run", "--apk", str(apk), input_text="")
        self.assertEqual(21, result.returncode)
        calls = self.calls.read_text(encoding="utf-8")
        self.assertNotIn("install -r", calls)
        self.assertNotIn("set-device-owner", calls)

    def test_success_runs_set_device_owner_once(self):
        apk = self.root / "dev319.apk"
        apk.write_bytes(b"candidate")
        real_hash = hashlib.sha256(apk.read_bytes()).hexdigest()
        patched = SCRIPT.read_text(encoding="utf-8").replace(EXPECTED_SHA, real_hash)
        test_script = self.root / "installer.sh"
        test_script.write_text(patched, encoding="utf-8")
        env = self.env | {"MOCK_VERSION": "319"}
        result = subprocess.run(
            ["bash", str(test_script), "run", "--apk", str(apk)],
            input="DEVICE OWNER MOCK123\n",
            text=True,
            capture_output=True,
            env=env,
            check=False,
        )
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        calls = self.calls.read_text(encoding="utf-8")
        self.assertEqual(1, calls.count("dpm set-device-owner"))
        self.assertIn("Device Owner confirmado", result.stdout)

    def test_failed_owner_attempt_is_not_retried(self):
        apk = self.root / "dev319.apk"
        apk.write_bytes(b"candidate")
        real_hash = hashlib.sha256(apk.read_bytes()).hexdigest()
        patched = SCRIPT.read_text(encoding="utf-8").replace(EXPECTED_SHA, real_hash)
        test_script = self.root / "installer.sh"
        test_script.write_text(patched, encoding="utf-8")
        env = self.env | {"MOCK_VERSION": "319", "MOCK_DPM_EXIT": "1", "MOCK_DPM_OUTPUT": "rejected"}
        first = subprocess.run(
            ["bash", str(test_script), "run", "--apk", str(apk)],
            input="DEVICE OWNER MOCK123\n",
            text=True,
            capture_output=True,
            env=env,
            check=False,
        )
        self.assertEqual(30, first.returncode)
        second = subprocess.run(
            ["bash", str(test_script), "run", "--apk", str(apk)],
            input="DEVICE OWNER MOCK123\n",
            text=True,
            capture_output=True,
            env=env,
            check=False,
        )
        self.assertEqual(23, second.returncode)
        calls = self.calls.read_text(encoding="utf-8")
        self.assertEqual(1, calls.count("dpm set-device-owner"))


if __name__ == "__main__":
    unittest.main()
