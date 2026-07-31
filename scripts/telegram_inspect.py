#!/usr/bin/env python3
"""Inspect pending Telegram messages without storing or printing the bot token.

This is a local diagnostic helper. It does not send messages, trigger builds,
or change webhook configuration. The token is entered interactively and kept
only in memory for the duration of the request.
"""

from __future__ import annotations

import getpass
import json
import sys
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen


def telegram_get_updates(token: str) -> list[dict[str, Any]]:
    body = urlencode(
        {"limit": 100, "allowed_updates": json.dumps(["message"])}
    ).encode()
    request = Request(
        f"https://api.telegram.org/bot{token}/getUpdates",
        data=body,
        headers={
            "Content-Type": "application/x-www-form-urlencoded",
            "User-Agent": "handy-telegram-inspector",
        },
    )
    try:
        with urlopen(request, timeout=30) as response:
            payload = json.load(response)
    except HTTPError as error:
        if error.code == 409:
            raise RuntimeError(
                "Telegram devuelve 409 Conflict: hay un webhook activo. "
                "Desactívalo en BotFather o ejecuta deleteWebhook desde tu "
                "propio terminal antes de usar getUpdates."
            ) from error
        if error.code == 401:
            raise RuntimeError("Token inválido o revocado (HTTP 401).") from error
        raise RuntimeError(f"Telegram devolvió HTTP {error.code}.") from error
    except URLError as error:
        raise RuntimeError(f"No se pudo conectar con Telegram: {error.reason}") from error

    if not isinstance(payload, dict) or payload.get("ok") is not True:
        raise RuntimeError("Telegram devolvió una respuesta no válida.")

    updates = payload.get("result", [])
    if not isinstance(updates, list):
        raise RuntimeError("Telegram devolvió una lista de actualizaciones no válida.")
    return [update for update in updates if isinstance(update, dict)]


def print_update(update: dict[str, Any]) -> None:
    message = update.get("message")
    if not isinstance(message, dict):
        return

    chat = message.get("chat")
    if not isinstance(chat, dict):
        return

    chat_id = chat.get("id", "desconocido")
    chat_type = chat.get("type", "desconocido")
    sender = message.get("from")
    sender_name = "desconocido"
    if isinstance(sender, dict):
        sender_name = " ".join(
            part
            for part in (sender.get("first_name"), sender.get("last_name"))
            if isinstance(part, str) and part
        ) or str(sender.get("username", "desconocido"))

    text = message.get("text", "")
    if not isinstance(text, str):
        text = f"[{message.get('content_type', 'mensaje sin texto')}]"

    print(f"update_id: {update.get('update_id', 'desconocido')}")
    print(f"chat_id: {chat_id}")
    print(f"tipo: {chat_type}")
    print(f"remitente: {sender_name}")
    print(f"texto: {text}")
    print("---")


def main() -> int:
    print("El token se usa solo en memoria y no se guarda ni se muestra.")
    token = getpass.getpass("Token del bot: ").strip()
    if not token:
        print("No se proporcionó ningún token.", file=sys.stderr)
        return 2

    updates = telegram_get_updates(token)
    if not updates:
        print("No hay mensajes pendientes. Envía /start al bot y vuelve a intentarlo.")
        return 0

    print(f"Actualizaciones pendientes: {len(updates)}")
    for update in updates:
        print_update(update)
    print("Copia solo el chat_id numérico de tu chat privado a TELEGRAM_ALLOWED_CHAT_ID.")
    print("No compartas el token ni la salida completa si contiene datos personales.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except RuntimeError as error:
        print(f"Error: {error}", file=sys.stderr)
        raise SystemExit(1)
