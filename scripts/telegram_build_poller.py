#!/usr/bin/env python3
"""Poll Telegram and dispatch one authorized Android debug build.

This script intentionally accepts no user-controlled ref, Gradle task, or file.
It only dispatches the fixed workflow on the repository's main branch.
"""

from __future__ import annotations

import json
import os
import sys
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen


def required_env(name: str) -> str:
    value = os.environ.get(name)
    if not value:
        raise RuntimeError(f"Missing required environment variable: {name}")
    return value


BOT_TOKEN = required_env("TELEGRAM_BOT_TOKEN")
ALLOWED_CHAT_ID = required_env("TELEGRAM_ALLOWED_CHAT_ID")
GITHUB_TOKEN = required_env("GITHUB_TOKEN")
GITHUB_REPOSITORY = required_env("GITHUB_REPOSITORY")
WORKFLOW_FILE = os.environ.get("TELEGRAM_BUILD_WORKFLOW", "telegram-build.yml")
BUILD_REF = "main"


def request_json(request: Request) -> dict[str, Any]:
    try:
        with urlopen(request, timeout=30) as response:
            payload = json.load(response)
    except (HTTPError, URLError) as error:
        raise RuntimeError(f"HTTP request failed: {error}") from error

    if not isinstance(payload, dict):
        raise RuntimeError("API returned an invalid response")
    return payload


def telegram(method: str, payload: dict[str, Any]) -> dict[str, Any]:
    body = urlencode(
        {
            key: json.dumps(value) if isinstance(value, (list, dict)) else value
            for key, value in payload.items()
        }
    ).encode()
    request = Request(
        f"https://api.telegram.org/bot{BOT_TOKEN}/{method}",
        data=body,
        headers={"Content-Type": "application/x-www-form-urlencoded"},
    )
    result = request_json(request)
    if not result.get("ok"):
        raise RuntimeError(f"Telegram API rejected {method}")
    return result


def github_request(path: str, method: str = "GET", body: bytes | None = None) -> dict[str, Any]:
    request = Request(
        f"https://api.github.com{path}",
        data=body,
        method=method,
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {GITHUB_TOKEN}",
            "Content-Type": "application/json",
            "User-Agent": "handy-telegram-build-bot",
            "X-GitHub-Api-Version": "2022-11-28",
        },
    )
    try:
        with urlopen(request, timeout=30) as response:
            if response.status not in (200, 201, 204):
                raise RuntimeError(f"GitHub returned HTTP {response.status}")
            if response.status == 204:
                return {}
            payload = json.load(response)
    except (HTTPError, URLError) as error:
        raise RuntimeError(f"GitHub request failed: {error}") from error

    if not isinstance(payload, dict):
        raise RuntimeError("GitHub API returned an invalid response")
    return payload


def github_build_is_active() -> bool:
    path = (
        f"/repos/{GITHUB_REPOSITORY}/actions/workflows/{WORKFLOW_FILE}/runs"
        "?branch=main&event=workflow_dispatch&per_page=20"
    )
    result = github_request(path)
    runs = result.get("workflow_runs", [])
    if not isinstance(runs, list):
        return False
    return any(
        isinstance(run, dict)
        and run.get("status") in {"queued", "in_progress", "waiting", "pending"}
        for run in runs
    )


def github_dispatch(chat_id: str) -> None:
    body = json.dumps(
        {
            "ref": BUILD_REF,
            "inputs": {"chat_id": chat_id},
        }
    ).encode()
    github_request(
        f"/repos/{GITHUB_REPOSITORY}/actions/workflows/{WORKFLOW_FILE}/dispatches",
        method="POST",
        body=body,
    )


def message_is_build_request(message: dict[str, Any]) -> bool:
    chat = message.get("chat")
    if not isinstance(chat, dict):
        return False

    # A private chat's chat ID is the user's ID. Reject groups/channels even if
    # their ID happens to be configured accidentally.
    if chat.get("type") != "private" or str(chat.get("id")) != ALLOWED_CHAT_ID:
        return False

    text = message.get("text")
    if not isinstance(text, str) or not text.strip():
        return False

    command = text.strip().split(maxsplit=1)[0].split("@", maxsplit=1)[0]
    return command.lower() == "/build"


def main() -> int:
    updates_response = telegram(
        "getUpdates",
        {
            "limit": 100,
            "timeout": 0,
            "allowed_updates": ["message"],
        },
    )
    updates = updates_response.get("result", [])
    if not isinstance(updates, list) or not updates:
        return 0

    # Process the oldest authorized request first. Confirming only through that
    # update leaves newer requests for the next scheduled poll.
    selected_update: dict[str, Any] | None = None
    selected_chat_id: str | None = None
    highest_seen_update_id: int | None = None

    for update in updates:
        if not isinstance(update, dict):
            continue
        update_id = update.get("update_id")
        if isinstance(update_id, int):
            highest_seen_update_id = update_id
        message = update.get("message")
        if (
            selected_update is None
            and isinstance(message, dict)
            and message_is_build_request(message)
        ):
            chat = message["chat"]
            selected_update = update
            selected_chat_id = str(chat["id"])

    if selected_update is not None and selected_chat_id is not None:
        confirmation_id = selected_update.get("update_id")
        if not isinstance(confirmation_id, int):
            raise RuntimeError("Authorized update has no valid update_id")

        # Confirm before dispatching. If GitHub is temporarily unavailable,
        # the user can resend /build; if Telegram fails after dispatch, the
        # same request cannot start a duplicate build on the next poll.
        telegram("getUpdates", {"offset": confirmation_id + 1, "limit": 1})
        try:
            if github_build_is_active():
                telegram(
                    "sendMessage",
                    {
                        "chat_id": selected_chat_id,
                        "text": "ℹ️ Ya hay una compilación debug en curso. Espera a que termine antes de enviar otro /build.",
                    },
                )
                return 0
            github_dispatch(selected_chat_id)
        except RuntimeError:
            telegram(
                "sendMessage",
                {
                    "chat_id": selected_chat_id,
                    "text": "❌ No se pudo iniciar la compilación. Reenvía /build para intentarlo de nuevo.",
                },
            )
            raise

        telegram(
            "sendMessage",
            {
                "chat_id": selected_chat_id,
                "text": "✅ Compilación debug iniciada. Te enviaré el APK cuando termine.",
            },
        )
    else:
        # No authorized command: discard old, irrelevant messages so they do
        # not accumulate in Telegram's update queue.
        if isinstance(highest_seen_update_id, int):
            telegram("getUpdates", {"offset": highest_seen_update_id + 1, "limit": 1})
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except RuntimeError as error:
        print(f"::error::{error}", file=sys.stderr)
        raise SystemExit(1)
