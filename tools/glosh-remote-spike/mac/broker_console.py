"""Async operator-console helpers for broker-backed Glosh Remote sessions."""

from __future__ import annotations

import asyncio
import sys

from broker_client import BrokerOperatorClient


async def print_pending_requests(broker: BrokerOperatorClient) -> None:
    try:
        requests = await asyncio.get_running_loop().run_in_executor(None, broker.pending)
    except Exception as exc:
        print(f"[broker] no se pudieron consultar solicitudes: {exc}")
        return
    if not requests:
        print("Sin solicitudes pendientes.")
        return
    for request in requests:
        print(
            f"Solicitud pendiente: {request.manufacturer} {request.model} · "
            f"Android {request.android} · {request.request_id}"
        )


async def accept_request(
    broker: BrokerOperatorClient,
    request_id: str,
    descriptor: str,
    session=None,
) -> str:
    if session is not None and getattr(session, "support_slot_claimed", False):
        return "Esta sesión ya aceptó un cliente."
    requests = await asyncio.get_running_loop().run_in_executor(None, broker.pending)
    request = next((item for item in requests if item.request_id == request_id), None)
    if request is None:
        return "Solicitud pendiente no encontrada o expirada."
    await asyncio.get_running_loop().run_in_executor(None, broker.accept, request, descriptor)
    if session is not None:
        session.support_slot_claimed = True
        session.accepted_request_id = request.request_id
    return f"[broker] solicitud aceptada: {request.request_id}"


async def maintain_operator_presence(
    session,
    broker: BrokerOperatorClient,
    interval_seconds: float = 60.0,
) -> None:
    """Renew the support-ready lease while the technician is explicitly waiting."""
    while not session.stop_event.is_set():
        try:
            await asyncio.wait_for(session.stop_event.wait(), timeout=interval_seconds)
            return
        except asyncio.TimeoutError:
            pass
        try:
            await asyncio.get_running_loop().run_in_executor(None, broker.register, 0)
        except Exception as exc:
            if not session.stop_event.is_set():
                print(f"[broker] heartbeat temporal falló: {exc}", file=sys.stderr)


async def announce_pending_requests(
    session,
    broker: BrokerOperatorClient,
    descriptor: str | None = None,
    poll_interval_seconds: float = 2.0,
) -> None:
    announced = set()
    while not session.stop_event.is_set():
        try:
            requests = await asyncio.get_running_loop().run_in_executor(None, broker.pending)
            current = {request.request_id for request in requests}
            slot_claimed = getattr(session, "support_slot_claimed", False)

            if (
                descriptor
                and not slot_claimed
                and session.agent is None
                and len(requests) == 1
            ):
                request = requests[0]
                await asyncio.get_running_loop().run_in_executor(
                    None, broker.accept, request, descriptor
                )
                session.support_slot_claimed = True
                session.accepted_request_id = request.request_id
                print(
                    f"\n[broker] cliente único aceptado automáticamente: "
                    f"{request.manufacturer} {request.model}",
                    flush=True,
                )
                current.discard(request.request_id)
                requests = []
            elif len(requests) > 1 and not slot_claimed:
                print(
                    "\n[broker] hay varias solicitudes pendientes; "
                    "se requiere accept <request-id> manual.",
                    flush=True,
                )

            for request in requests:
                if request.request_id not in announced:
                    print(
                        f"\nSolicitud pendiente: {request.manufacturer} {request.model} · "
                        f"Android {request.android}\n"
                        f"Para aceptar: accept {request.request_id}",
                        flush=True,
                    )
            announced.intersection_update(current)
            announced.update(current)
        except Exception as exc:
            if not session.stop_event.is_set():
                print(f"[broker] consulta temporal falló: {exc}", file=sys.stderr)
        await asyncio.sleep(poll_interval_seconds)
