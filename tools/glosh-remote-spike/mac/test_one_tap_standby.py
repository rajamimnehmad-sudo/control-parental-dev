from __future__ import annotations

import asyncio
import json
import time
import unittest

from broker_client import PendingRequest
from broker_console import accept_request, announce_pending_requests, maintain_operator_presence
from glosh_remote_relay import RemoteSession, hmac_b64


class FakeSession:
    def __init__(self) -> None:
        self.stop_event = asyncio.Event()
        self.agent = None
        self.support_slot_claimed = False
        self.accepted_request_id = None
        self.accepted_client_fingerprint = None


class FakeBroker:
    def __init__(self, session: FakeSession, requests, stop_after_pending: int = 3) -> None:
        self.session = session
        self.requests = list(requests)
        self.stop_after_pending = stop_after_pending
        self.pending_calls = 0
        self.accepted = []
        self.register_calls = 0
        self.fail_first_register = False

    def pending(self):
        self.pending_calls += 1
        if self.pending_calls >= self.stop_after_pending:
            self.session.stop_event.set()
        return list(self.requests)

    def accept(self, request, descriptor: str) -> None:
        self.accepted.append((request.request_id, descriptor))

    def register(self, ttl_seconds: int = 0) -> None:
        del ttl_seconds
        self.register_calls += 1
        if self.fail_first_register and self.register_calls == 1:
            raise OSError("temporary heartbeat failure")


class FakeWebSocket:
    def __init__(self, session: RemoteSession) -> None:
        self.session = session
        self.sent = []
        self.closed = []

    async def send(self, raw: str) -> None:
        self.sent.append(raw)

    async def recv(self) -> str:
        challenge = json.loads(self.sent[-1])["nonce"]
        return json.dumps(
            {
                "type": "auth",
                "proof": hmac_b64(
                    self.session.key,
                    f"agent-auth:{self.session.sid}:{challenge}",
                ),
                "device": {"manufacturer": "Samsung", "model": "test"},
            }
        )

    async def close(self, code: int, reason: str) -> None:
        self.closed.append((code, reason))

    def __aiter__(self):
        return self

    async def __anext__(self):
        raise StopAsyncIteration


class OneTapStandbyTest(unittest.IsolatedAsyncioTestCase):
    async def test_operator_heartbeat_recovers_after_temporary_failure(self) -> None:
        session = FakeSession()
        broker = FakeBroker(session, [], stop_after_pending=999)
        broker.fail_first_register = True

        task = asyncio.create_task(
            maintain_operator_presence(session, broker, interval_seconds=0.005)
        )
        await asyncio.sleep(0.025)
        session.stop_event.set()
        await asyncio.wait_for(task, timeout=0.2)

        self.assertGreaterEqual(broker.register_calls, 2)

    async def test_zero_requests_never_autoaccepts(self) -> None:
        session = FakeSession()
        broker = FakeBroker(session, [], stop_after_pending=2)
        await announce_pending_requests(
            session, broker, "gloshremote://secret", poll_interval_seconds=0.001
        )
        self.assertEqual([], broker.accepted)

    async def test_exactly_one_request_autoaccepts_once_and_claims_slot(self) -> None:
        session = FakeSession()
        request = PendingRequest("request-one", "client-key-a", "", "Samsung", "S22", "16", 60)
        broker = FakeBroker(session, [request], stop_after_pending=4)
        await announce_pending_requests(
            session, broker, "gloshremote://secret", poll_interval_seconds=0.001
        )
        self.assertEqual([("request-one", "gloshremote://secret")], broker.accepted)
        self.assertTrue(session.support_slot_claimed)
        self.assertEqual("request-one", session.accepted_request_id)

    async def test_multiple_requests_fail_closed_without_autoaccept(self) -> None:
        session = FakeSession()
        requests = [
            PendingRequest("request-one", "", "", "Samsung", "S22", "16", 60),
            PendingRequest("request-two", "", "", "Samsung", "A23", "14", 60),
        ]
        broker = FakeBroker(session, requests, stop_after_pending=2)
        await announce_pending_requests(
            session, broker, "gloshremote://secret", poll_interval_seconds=0.001
        )
        self.assertEqual([], broker.accepted)
        self.assertFalse(session.support_slot_claimed)

    async def test_manual_accept_claims_slot_and_blocks_second_customer(self) -> None:
        session = FakeSession()
        first = PendingRequest("request-one", "", "", "Samsung", "S22", "16", 60)
        second = PendingRequest("request-two", "", "", "Samsung", "A23", "14", 60)
        broker = FakeBroker(session, [first, second], stop_after_pending=999)

        result = await accept_request(
            broker, "request-one", "gloshremote://secret", session
        )
        self.assertIn("solicitud aceptada", result)
        self.assertTrue(session.support_slot_claimed)

        broker.requests = [second]
        blocked = await accept_request(
            broker, "request-two", "gloshremote://secret", session
        )
        self.assertEqual("Esta sesión ya aceptó un cliente.", blocked)
        self.assertEqual([("request-one", "gloshremote://secret")], broker.accepted)

    async def test_claimed_slot_accepts_renewal_from_same_ephemeral_identity(self) -> None:
        session = FakeSession()
        session.support_slot_claimed = True
        session.accepted_request_id = "already-accepted"
        from broker_console import client_fingerprint

        original = PendingRequest(
            "request-one", "client-key-a", "", "Samsung", "S22", "16", 60
        )
        session.accepted_client_fingerprint = client_fingerprint(original)
        request = PendingRequest(
            "request-two", "client-key-a", "", "Samsung", "S22", "16", 60
        )
        broker = FakeBroker(session, [request], stop_after_pending=2)

        await announce_pending_requests(
            session, broker, "gloshremote://secret", poll_interval_seconds=0.001
        )
        self.assertEqual([("request-two", "gloshremote://secret")], broker.accepted)
        self.assertEqual("request-two", session.accepted_request_id)

    async def test_claimed_slot_blocks_request_from_different_identity(self) -> None:
        session = FakeSession()
        session.support_slot_claimed = True
        session.accepted_request_id = "already-accepted"
        from broker_console import client_fingerprint

        original = PendingRequest(
            "request-one", "client-key-a", "", "Samsung", "S22", "16", 60
        )
        session.accepted_client_fingerprint = client_fingerprint(original)
        attacker = PendingRequest(
            "request-two", "client-key-b", "", "Samsung", "S22", "16", 60
        )
        broker = FakeBroker(session, [attacker], stop_after_pending=2)

        await announce_pending_requests(
            session, broker, "gloshremote://secret", poll_interval_seconds=0.001
        )
        self.assertEqual([], broker.accepted)

    async def test_session_ttl_does_not_start_while_waiting(self) -> None:
        session = RemoteSession(30)
        self.assertIsNone(session.expires_at)
        await asyncio.sleep(0.01)
        self.assertIsNone(session.expires_at)

    async def test_first_authenticated_agent_starts_session_ttl(self) -> None:
        session = RemoteSession(30)
        websocket = FakeWebSocket(session)
        before = time.monotonic()

        await session.handler(websocket, f"/agent?sid={session.sid}")

        self.assertIsNotNone(session.expires_at)
        assert session.expires_at is not None
        self.assertGreaterEqual(session.expires_at, before + 29 * 60)
        self.assertEqual([], websocket.closed)


if __name__ == "__main__":
    unittest.main()
