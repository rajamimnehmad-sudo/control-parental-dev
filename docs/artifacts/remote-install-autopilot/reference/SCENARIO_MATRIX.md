# Adaptive Autopilot scenario matrix

| Scenario at INICIAR | Shortest action | User action expected |
|---|---|---|
| Support session already connected | DONE | none |
| Local ADB already valid | Skip Settings/pairing → connect support | none |
| Previous pairing reconnects | Reconnect → connect support | none |
| Android < 11/API 30 | Block standard Wireless Debugging route | alternative installation route |
| Accessibility OFF | Open bootstrap | enable Accessibility / Restricted Settings if OS requires |
| Accessibility revoked mid-run | Pause immediately | re-enable |
| Dev Options ON, Wireless Debugging ON | Open Developer Settings → Wireless Debugging → Pair with code | normally none |
| Dev Options ON, Wireless Debugging OFF | Open Developer Settings → enable wireless → pair | normally none |
| Network trust/enable confirmation | Exact positive click if classified HIGH | none |
| Dev Options OFF (Samsung) | About phone intent → Software info → Build number ×7 | none unless credential requested |
| Samsung credential prompt | Pause | user enters PIN/pattern/password |
| Pairing dialog already open | Read unique code + pair | none if code unambiguous |
| Pairing code ambiguous/unreadable | Six-box input | type 6 digits |
| Pairing code expired | Re-open pair dialog → new code | normally none |
| No Wi-Fi / wireless unavailable | Stop as prerequisite | connect Wi-Fi / resolve device state |
| Wireless Debugging disabled by policy | BLOCKED | admin/policy resolution |
| Wrong/unknown Settings screen | Stop clicks → reopen exact intent or guide | usually none; guide if still unknown |
| Settings overlay/IME is top window | Wait/select trusted Settings window | none |
| Rotation/layout change | Invalidate generation/bounds, rescan | none |
| Two plausible Settings windows | Fail closed | fallback guide |
| Unsupported OEM, Dev Options already ON and common screens recognized | Fast path may still work | none if HIGH |
| Unsupported OEM, Dev Options OFF | Guided fallback | manual OEM step until a common state is reached |

## Coverage meaning

"Universal" means the state engine and safety model cover every state above. It does **not** mean v1 knows the private menu recipe of every OEM. Samsung is fully automated first. Other OEM adapters are data/recipe additions later.
