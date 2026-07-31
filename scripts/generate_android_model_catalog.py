#!/usr/bin/env python3
"""Generate the Android model storefront from Handy's desktop catalog.

The desktop catalog describes GGUF artifacts and therefore cannot describe the
legacy `.bin` files currently downloadable by Android. The small override file
contains only that Android-specific compatibility bridge; all storefront model
metadata comes from catalog.json.

Usage:
    python3 scripts/generate_android_model_catalog.py
    python3 scripts/generate_android_model_catalog.py --check
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any, NoReturn

ROOT = Path(__file__).resolve().parents[1]
CATALOG_PATH = ROOT / "src-tauri" / "src" / "catalog" / "catalog.json"
OVERRIDES_PATH = ROOT / "scripts" / "android_model_catalog_overrides.json"
OUTPUT_PATH = ROOT / "app" / "src" / "main" / "java" / "com" / "handy" / "android" / "ModelCatalog.kt"
MAX_PARAMETERS = 1_200_000_000
PARAMETER_PATTERN = re.compile(r"^\s*([0-9]+(?:\.[0-9]+)?)\s*([BM])\s*$", re.IGNORECASE)
SHA256_PATTERN = re.compile(r"^[0-9a-f]{64}$")


def fail(message: str) -> NoReturn:
    raise ValueError(message)


def kotlin_string(value: str) -> str:
    return json.dumps(value, ensure_ascii=False)


def parse_parameters(value: Any, model_id: str) -> int:
    match = PARAMETER_PATTERN.fullmatch(str(value or ""))
    if not match:
        fail(f"{model_id}: parameters must be a number followed by M or B, got {value!r}")
    number = float(match.group(1))
    multiplier = 1_000_000_000 if match.group(2).upper() == "B" else 1_000_000
    return int(number * multiplier)


def require_string(model: dict[str, Any], field: str) -> str:
    value = model.get(field)
    if not isinstance(value, str) or not value.strip():
        fail(f"{model.get('id', '<unknown>')}: missing non-empty {field}")
    return value


def default_file(model: dict[str, Any]) -> dict[str, Any]:
    files = model.get("files")
    if not isinstance(files, list) or not files:
        fail(f"{model.get('id', '<unknown>')}: files must be a non-empty list")
    default_quant = model.get("default_quant")
    selected = next((item for item in files if item.get("quant") == default_quant), files[0])
    if not isinstance(selected.get("size_bytes"), int) or selected["size_bytes"] <= 0:
        fail(f"{model.get('id', '<unknown>')}: default file has invalid size_bytes")
    return selected


def load_inputs() -> tuple[dict[str, Any], dict[str, dict[str, Any]]]:
    catalog = json.loads(CATALOG_PATH.read_text(encoding="utf-8"))
    overrides = json.loads(OVERRIDES_PATH.read_text(encoding="utf-8"))
    if not isinstance(catalog.get("models"), list):
        fail("catalog.json: models must be a list")
    if not isinstance(overrides, dict):
        fail("android_model_catalog_overrides.json: root must be an object")

    seen: set[str] = set()
    for model in catalog["models"]:
        model_id = require_string(model, "id")
        if model_id in seen:
            fail(f"catalog.json: duplicate model id {model_id}")
        seen.add(model_id)

    unknown_overrides = set(overrides) - seen
    if unknown_overrides:
        fail(f"Android overrides refer to unknown catalog ids: {sorted(unknown_overrides)}")
    return catalog, overrides


def validate_override(model_id: str, override: dict[str, Any], model: dict[str, Any]) -> None:
    if model.get("architecture") != "whisper":
        fail(f"{model_id}: Android legacy overrides are only supported for Whisper catalog entries")
    for field in ("id", "fileName", "url", "sha256"):
        if not isinstance(override.get(field), str) or not override[field].strip():
            fail(f"{model_id}: Android override missing {field}")
    if not override["url"].lower().startswith("https://"):
        fail(f"{model_id}: Android override URL must use HTTPS")
    if not override["fileName"].lower().endswith(".bin"):
        fail(f"{model_id}: Android override fileName must end in .bin")
    if not SHA256_PATTERN.fullmatch(override["sha256"].lower()):
        fail(f"{model_id}: Android override sha256 must be 64 lowercase hex characters")
    if not isinstance(override.get("sizeBytes"), int) or override["sizeBytes"] <= 0:
        fail(f"{model_id}: Android override sizeBytes must be positive")


def selected_models(catalog: dict[str, Any]) -> list[dict[str, Any]]:
    result = []
    for model in catalog["models"]:
        model_id = model["id"]
        parameter_count = parse_parameters(model.get("parameters"), model_id)
        if parameter_count > MAX_PARAMETERS:
            continue
        if not isinstance(model.get("language_count"), int) or model["language_count"] < 1:
            fail(f"{model_id}: language_count must be a positive integer")
        architecture = require_string(model, "architecture")
        name = require_string(model, "name")
        description = require_string(model, "description")
        file = default_file(model)
        result.append(
            {
                "id": model_id,
                "name": name,
                "parameters": str(model["parameters"]),
                "parameter_count": parameter_count,
                "description": description,
                "language_count": model["language_count"],
                "architecture": architecture,
                "size_bytes": file["size_bytes"],
            }
        )
    return result


def render(catalog: dict[str, Any], overrides: dict[str, dict[str, Any]]) -> str:
    models = selected_models(catalog)
    selected_ids = {model["id"] for model in models}
    excluded_overrides = set(overrides) - selected_ids
    if excluded_overrides:
        fail(
            "Android overrides refer to models above the mobile parameter limit "
            f"or otherwise excluded: {sorted(excluded_overrides)}"
        )
    models_by_id = {model["id"]: model for model in catalog["models"]}
    for model_id, override in overrides.items():
        validate_override(model_id, override, models_by_id[model_id])

    lines = [
        "package com.handy.android",
        "",
        "// GENERATED FILE - do not edit manually.",
        "// Source: src-tauri/src/catalog/catalog.json",
        "// Generator: scripts/generate_android_model_catalog.py",
        "",
        "/** The verified Android download bridge for a catalog entry. */",
        "data class AndroidDownloadSpec(",
        "    val id: String,",
        "    val fileName: String,",
        "    val url: String,",
        "    val sha256: String,",
        "    val sizeBytes: Long,",
        ")",
        "",
        "/** A mobile-sized model from the desktop catalog and its Android status. */",
        "data class ModelCatalogEntry(",
        "    val id: String,",
        "    val name: String,",
        "    val parameters: String,",
        "    val parameterCount: Long,",
        "    val description: String,",
        "    val languageCount: Int,",
        "    val architecture: String,",
        "    /** Size of the selected artifact shown by the storefront. */",
        "    val downloadSizeBytes: Long,",
        "    val androidDownload: AndroidDownloadSpec? = null,",
        ") {",
        "    val isAvailableOnAndroid: Boolean",
        "        get() = androidDownload != null",
        "}",
        "",
        "object ModelCatalog {",
        "    /** Source metadata copied from the desktop catalog snapshot. */",
        f"    const val SOURCE_CATALOG_VERSION = {int(catalog.get('catalog_version', 0))}",
        f"    const val SOURCE_CATALOG_GENERATED_AT = {kotlin_string(str(catalog.get('generated_at', '')))}",
        f"    const val MAX_PARAMETERS = {MAX_PARAMETERS}L",
        "",
        "    /** All catalog entries at or below the Android mobile parameter limit. */",
        "    val models: List<ModelCatalogEntry> = listOf(",
    ]

    for model in models:
        override = overrides.get(model["id"])
        size_bytes = override["sizeBytes"] if override else model["size_bytes"]
        args = [
            kotlin_string(model["id"]),
            kotlin_string(model["name"]),
            kotlin_string(model["parameters"]),
            f"{model['parameter_count']}L",
            kotlin_string(model["description"]),
            str(model["language_count"]),
            kotlin_string(model["architecture"]),
            f"{size_bytes}L",
        ]
        lines.append(f"        ModelCatalogEntry({', '.join(args)}" + ("," if override else "),"))
        if override:
            lines.extend(
                [
                    "            AndroidDownloadSpec(",
                    f"                id = {kotlin_string(override['id'])},",
                    f"                fileName = {kotlin_string(override['fileName'])},",
                    f"                url = {kotlin_string(override['url'])},",
                    f"                sha256 = {kotlin_string(override['sha256'].lower())},",
                    f"                sizeBytes = {override['sizeBytes']}L,",
                    "            ),",
                    "        ),",
                ]
            )

    lines.extend(
        [
            "    ).filter { it.parameterCount <= MAX_PARAMETERS }",
            "",
            "    val downloadableModels: List<ModelCatalogEntry>",
            "        get() = models.filter { it.isAvailableOnAndroid }.distinctBy { it.androidDownload?.id }",
            "",
            "    fun find(id: String): ModelCatalogEntry? = models.firstOrNull { it.id == id }",
            "}",
            "",
            "fun formatModelSize(bytes: Long): String {",
            "    val megabytes = bytes / (1024.0 * 1024.0)",
            "    return if (megabytes >= 1024.0) {",
            '        "%.1f GB".format(java.util.Locale.US, megabytes / 1024.0)',
            "    } else {",
            '        "%.0f MB".format(java.util.Locale.US, megabytes)',
            "    }",
            "}",
            "",
        ]
    )
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="verify the checked-in file without writing it")
    args = parser.parse_args()
    try:
        catalog, overrides = load_inputs()
        generated = render(catalog, overrides)
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"android model catalog generation failed: {error}", file=sys.stderr)
        return 1

    if args.check:
        current = OUTPUT_PATH.read_text(encoding="utf-8") if OUTPUT_PATH.exists() else None
        if current != generated:
            print(f"{OUTPUT_PATH} is out of date; run the generateModelCatalog Gradle task", file=sys.stderr)
            return 1
        print(f"Android model catalog is up to date ({len(selected_models(catalog))} models)")
        return 0

    OUTPUT_PATH.write_text(generated, encoding="utf-8")
    print(f"Generated {OUTPUT_PATH} ({len(selected_models(catalog))} models)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
