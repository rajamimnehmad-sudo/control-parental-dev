from __future__ import annotations
from dataclasses import dataclass
from enum import Enum
from typing import Optional, Tuple

class Confidence(str, Enum):
    LOW='LOW'; MEDIUM='MEDIUM'; HIGH='HIGH'

class Screen(str, Enum):
    UNKNOWN='UNKNOWN'; APP='APP'; SETTINGS_HOME='SETTINGS_HOME'; ABOUT_PHONE='ABOUT_PHONE'; SOFTWARE_INFO='SOFTWARE_INFO'; DEVELOPER_OPTIONS='DEVELOPER_OPTIONS'; WIRELESS_DEBUGGING='WIRELESS_DEBUGGING'; NETWORK_CONFIRMATION='NETWORK_CONFIRMATION'; PAIRING_DIALOG='PAIRING_DIALOG'; CREDENTIAL_PROMPT='CREDENTIAL_PROMPT'

class Action(str, Enum):
    WAIT_STABLE='WAIT_STABLE'; ASK_ALLOW_RESTRICTED_SETTINGS='ASK_ALLOW_RESTRICTED_SETTINGS'; ASK_ENABLE_ACCESSIBILITY='ASK_ENABLE_ACCESSIBILITY'; ASK_CONNECT_WIFI='ASK_CONNECT_WIFI'; TRY_ADB_RECONNECT='TRY_ADB_RECONNECT'; POLICY_BLOCKED='POLICY_BLOCKED'; UNSUPPORTED_ANDROID='UNSUPPORTED_ANDROID'; OPEN_DEVELOPER_SETTINGS='OPEN_DEVELOPER_SETTINGS'; OPEN_DEVICE_INFO_SETTINGS='OPEN_DEVICE_INFO_SETTINGS'; CLICK_SOFTWARE_INFO='CLICK_SOFTWARE_INFO'; CLICK_BUILD_NUMBER='CLICK_BUILD_NUMBER'; WAIT_USER_CREDENTIAL='WAIT_USER_CREDENTIAL'; CLICK_WIRELESS_DEBUGGING='CLICK_WIRELESS_DEBUGGING'; ENABLE_WIRELESS_DEBUGGING='ENABLE_WIRELESS_DEBUGGING'; ACCEPT_NETWORK_CONFIRMATION='ACCEPT_NETWORK_CONFIRMATION'; CLICK_PAIR_WITH_CODE='CLICK_PAIR_WITH_CODE'; AUTO_PAIR_WITH_CODE='AUTO_PAIR_WITH_CODE'; SHOW_MANUAL_PAIR_CODE='SHOW_MANUAL_PAIR_CODE'; CONNECT_SUPPORT='CONNECT_SUPPORT'; FALLBACK_GUIDE='FALLBACK_GUIDE'; DONE='DONE'

@dataclass(frozen=True)
class SnapshotAuthority:
    trusted_settings_window: bool
    stable_snapshots: int
    generation_current: bool
    window_id_current: bool
    fingerprint_current: bool
    ambiguous_window: bool=False
    def safe(self):
        return self.trusted_settings_window and self.stable_snapshots>=2 and self.generation_current and self.window_id_current and self.fingerprint_current and not self.ambiguous_window

@dataclass(frozen=True)
class Candidate:
    key: str
    confidence: Confidence
    clickable: bool
    unique: bool
    margin_ok: bool
    fresh_reacquired: bool
    def safe_to_click(self):
        return self.confidence==Confidence.HIGH and self.clickable and self.unique and self.margin_ok and self.fresh_reacquired

@dataclass(frozen=True)
class Observation:
    android_api: int
    oem: str
    accessibility_enabled: bool
    adb_connected: bool=False
    support_connected: bool=False
    restricted_settings_required: bool=False
    wifi_ready: bool=True
    wireless_policy_blocked: bool=False
    previous_pairing_known: bool=False
    reconnect_attempted: bool=False
    screen: Screen=Screen.UNKNOWN
    authority: Optional[SnapshotAuthority]=None
    candidate: Optional[Candidate]=None
    wireless_enabled: Optional[bool]=None
    build_taps_done: int=0
    pair_code_candidates: Tuple[str,...]=()
    pairing_context_high: bool=False
    request_active: bool=True
    direct_dev_probe_attempted: bool=False
    direct_dev_screen_recognized: bool=False

@dataclass(frozen=True)
class Decision:
    action: Action
    target: Optional[str]=None
    reason: str=''

class AdaptiveAutopilot:
    MIN_WIRELESS_ADB_API=30
    def decide(self,o:Observation)->Decision:
        if o.support_connected: return Decision(Action.DONE, reason='support already connected')
        if o.adb_connected: return Decision(Action.CONNECT_SUPPORT, reason='ADB already valid; skip Settings/pairing')
        if o.android_api<self.MIN_WIRELESS_ADB_API: return Decision(Action.UNSUPPORTED_ANDROID, reason='Android <11 standard wireless path unsupported')
        if not o.accessibility_enabled and o.restricted_settings_required: return Decision(Action.ASK_ALLOW_RESTRICTED_SETTINGS, reason='sideloaded Accessibility is restricted by OS')
        if not o.accessibility_enabled: return Decision(Action.ASK_ENABLE_ACCESSIBILITY, reason='manual bootstrap prerequisite')
        if not o.wifi_ready: return Decision(Action.ASK_CONNECT_WIFI, reason='Wireless Debugging needs a usable Wi-Fi network')
        if o.wireless_policy_blocked: return Decision(Action.POLICY_BLOCKED, reason='Wireless Debugging disabled by device/admin policy')
        if o.previous_pairing_known and not o.reconnect_attempted: return Decision(Action.TRY_ADB_RECONNECT, reason='reuse previous pairing before opening Settings')
        if o.screen==Screen.CREDENTIAL_PROMPT: return Decision(Action.WAIT_USER_CREDENTIAL, reason='never automate device credential')
        if o.screen==Screen.PAIRING_DIALOG:
            if not self._safe_observation(o): return Decision(Action.WAIT_STABLE, reason='pairing dialog not stable/trusted')
            codes=tuple(c for c in o.pair_code_candidates if c.isdigit() and len(c)==6)
            if o.request_active and o.pairing_context_high and len(codes)==1: return Decision(Action.AUTO_PAIR_WITH_CODE,target=codes[0],reason='unique contextual code')
            return Decision(Action.SHOW_MANUAL_PAIR_CODE, reason='PIN not unambiguous')
        if o.screen==Screen.NETWORK_CONFIRMATION:
            return Decision(Action.ACCEPT_NETWORK_CONFIRMATION,target='network_confirm_positive') if self._safe_click(o,'network_confirm_positive') else Decision(Action.FALLBACK_GUIDE,reason='unsafe confirmation')
        if o.screen==Screen.WIRELESS_DEBUGGING:
            if o.wireless_enabled is False:
                return Decision(Action.ENABLE_WIRELESS_DEBUGGING,target='wireless_debugging_toggle') if self._safe_click(o,'wireless_debugging_toggle') else Decision(Action.FALLBACK_GUIDE,reason='unsafe toggle')
            if o.wireless_enabled is True:
                return Decision(Action.CLICK_PAIR_WITH_CODE,target='pair_with_code') if self._safe_click(o,'pair_with_code') else Decision(Action.FALLBACK_GUIDE,reason='unsafe pair target')
            return Decision(Action.WAIT_STABLE,reason='wireless state unknown')
        if o.screen==Screen.DEVELOPER_OPTIONS:
            return Decision(Action.CLICK_WIRELESS_DEBUGGING,target='wireless_debugging') if self._safe_click(o,'wireless_debugging') else Decision(Action.FALLBACK_GUIDE,reason='wireless row unsafe')
        if o.screen==Screen.SOFTWARE_INFO:
            if o.build_taps_done<7:
                return Decision(Action.CLICK_BUILD_NUMBER,target='build_number',reason=f'tap {o.build_taps_done+1}/7') if self._safe_click(o,'build_number') else Decision(Action.FALLBACK_GUIDE,reason='build number unsafe')
            return Decision(Action.OPEN_DEVELOPER_SETTINGS,reason='re-probe after seven taps')
        if o.screen==Screen.ABOUT_PHONE:
            return Decision(Action.CLICK_SOFTWARE_INFO,target='software_info') if self._safe_click(o,'software_info') else Decision(Action.FALLBACK_GUIDE,reason='software info unsafe')
        if not o.direct_dev_probe_attempted: return Decision(Action.OPEN_DEVELOPER_SETTINGS,reason='fast path')
        if o.direct_dev_probe_attempted and not o.direct_dev_screen_recognized:
            if o.oem.lower()=='samsung': return Decision(Action.OPEN_DEVICE_INFO_SETTINGS,reason='Samsung enable-development fallback')
            return Decision(Action.FALLBACK_GUIDE,reason='OEM recipe unavailable')
        return Decision(Action.FALLBACK_GUIDE,reason='unrecognized state')
    @staticmethod
    def _safe_observation(o): return o.authority is not None and o.authority.safe()
    def _safe_click(self,o,key): return self._safe_observation(o) and o.candidate is not None and o.candidate.key==key and o.candidate.safe_to_click()
