# Engineering third-party notice — 09A feasibility

This is an engineering inventory, not legal advice.

| Component | Version / commit | License | Included role |
| --- | --- | --- | --- |
| HEV Socks5 Tunnel | 2.17.1 / `9a06bc6e7989da54e3d32ff701ef7a7ce4995d3a` | MIT | Shared transport library |
| hev-socks5-core | 1.6.4 / `162dd996299fc2d2bff2dd63728f8a2cd71ed31a` | MIT | Statically linked SOCKS client core |
| hev-task-system | 5.10.3 / `328f35d903221b51811b3d02b277d665dfbdc75f` | MIT | Statically linked task runtime |
| lwIP fork | 2.2.1.7 / `2a11c14c7a32887af25a034e82ef18b0b12076ac` | BSD-3-Clause plus upstream notices | Statically linked TCP/IP stack |
| yaml | 0.2.5.2 / `efa36117a8646d26d12b58e05bac472d7854a70d` | MIT | Statically linked config parser |

Source: <https://github.com/heiher/hev-socks5-tunnel> and its pinned submodules.
The Android build excludes the standalone executable and does not package
Windows/Wintun artifacts. Binary redistribution, modification, and commercial
use are permitted by the reviewed MIT/BSD terms subject to preserving their
copyright and license notices. The complete upstream license texts must remain
part of any product distribution review before Production.
