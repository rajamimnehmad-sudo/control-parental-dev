# Live Guide V2 — acceptance gates

No gate can be skipped because a later gate passes.

## Gate 0 — pure logic (already local prototype)

Required:
- overlay window never selected;
- untrusted app never selected;
- ambiguous target rejected;
- stale generation rejected;
- no reveal before `MOSTRARME`;
- user scroll cancels reveal;
- scroll bounded;
- state machine fails closed;
- pairing submit has one authority;
- telemetry strips sensitive fields.

## Gate A — Samsung guide only, OFFLINE

No broker, relay or support session.

Pass criteria:
- S22 recognizes Samsung route;
- Settings package resolution correct;
- About phone → Software information target correct;
- Software information → Build number target correct;
- old false-rescue case cannot reproduce;
- coach's own `Número de compilación` text never appears as matcher candidate;
- hidden target does not scroll until `MOSTRARME`;
- after `MOSTRARME`, maximum 3 moves;
- human scroll stops guide movement for >=1.4 s;
- wrong screen produces no incorrect highlight;
- `ME PERDÍ` can recover known screen;
- rotation clears stale rectangle and reacquires bounds;
- zero crash/ANR.

Repeat each target 10 times. Any wrong highlight = FAIL.

## Gate B — pairing UX

Broker/relay may be active, but focus is pairing UX.

Pass criteria:
- no broker request during learning stage;
- request begins only after developer stage complete;
- Wireless Debugging and Pair with code targets correct;
- in-app six boxes always work;
- sixth digit submits once;
- notification path uses same submit guard;
- optional accessibility auto-code, when available, cannot race a manual submit;
- expired/failed code returns to a clean retry state;
- zero code persistence/logging.

## Gate C — full remote session

Pass criteria:
- cross-network connection;
- explicit operator accept;
- WSS/HMAC/AES authenticated;
- status authenticated;
- ping=pong;
- whoami reports shell uid;
- device correct;
- non-allowlisted action rejected;
- CONNECTED UX correct;
- overlays removed;
- guide service disabled/cleaned per flow;
- cancel/revoke closes agent;
- status final has no agent;
- relay/tunnel/support window closed;
- zero crash/ANR.

## Gate D — OEM compatibility

Samsung physical PASS does not imply Motorola/Xiaomi PASS.

For each new OEM/major skin:
1. capture only non-sensitive structural evidence needed to author aliases;
2. implement bounded recipe;
3. unit test;
4. physical Gate A;
5. then Gate B/C if necessary.

No broad fuzzy matcher is allowed as a substitute for OEM evidence.
