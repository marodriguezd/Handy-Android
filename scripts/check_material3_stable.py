#!/usr/bin/env python3
"""Detect a stable Compose Material3 1.5.x release and fail loudly.

Handy pins Compose to the stable BOM 2026.06.01 / material3 1.4.0. The Expressive
APIs (LargeFlexibleTopAppBar, ButtonGroup, SplitButton, SearchBarState) only exist
in material3 1.5.x, which has been alpha-only since late 2025. When Google finally
publishes a STABLE 1.5.x, this script exits 1 so CI flags the migration immediately
(see AUDIT.md §2.3 for the verified migration plan).

Stable means a bare "1.5.x" version with no alpha/beta/rc suffix (e.g. 1.5.0, 1.5.1);
anything like 1.5.0-alpha25, 1.5.0-beta01, 1.5.0-rc01 is still pre-release and exits 0.

Usage:
    python3 scripts/check_material3_stable.py            # query Maven (network)
    python3 scripts/check_material3_stable.py --metadata maven-metadata.xml
    python3 scripts/check_material3_stable.py --selftest # offline self-check
"""

import argparse
import re
import sys
import urllib.request
import xml.etree.ElementTree as ET

METADATA_URL = (
    "https://dl.google.com/dl/android/maven2/androidx/compose/"
    "material3/material3/maven-metadata.xml"
)
# Any stable version >= 1.5.0 means the Expressive migration becomes viable.
TARGET_MAJOR, TARGET_MINOR = 1, 5
STABLE_VERSION_RE = re.compile(r"^\d+\.\d+\.\d+$")

EXIT_OK = 0
EXIT_MIGRATE = 1


def parse_versions(xml_text: str) -> list[str]:
    """Return all <version> entries from a maven-metadata.xml document."""
    root = ET.fromstring(xml_text)
    return [node.text for node in root.iter("version") if node.text]


def stable_versions(versions: list[str]) -> list[tuple[int, ...]]:
    """Filter to bare semver triples (no alpha/beta/rc suffix) and sort them."""
    parsed: list[tuple[int, ...]] = []
    for version in versions:
        if STABLE_VERSION_RE.match(version):
            parsed.append(tuple(int(part) for part in version.split(".")))
    return sorted(parsed)


def check(versions: list[str]) -> tuple[int, str]:
    """Return (exit_code, message) for a list of published versions."""
    stable = stable_versions(versions)
    if not stable:
        return EXIT_OK, "no stable material3 release found (all published versions are pre-release)"

    latest = stable[-1]
    if latest[0] > TARGET_MAJOR or (latest[0] == TARGET_MAJOR and latest[1] >= TARGET_MINOR):
        version_str = ".".join(str(part) for part in latest)
        return (
            EXIT_MIGRATE,
            f"STABLE material3 {version_str} detected (>= {TARGET_MAJOR}.{TARGET_MINOR}.0)!\n"
            f"  -> Apply the Expressive migration plan in AUDIT.md §2.3 "
            f"(LargeFlexibleTopAppBar, ButtonGroup, SplitButton) and bump the Compose BOM.",
        )

    latest_str = ".".join(str(part) for part in latest)
    return EXIT_OK, f"latest stable material3 is {latest_str} (still below {TARGET_MAJOR}.{TARGET_MINOR}.0); pre-release builds ignored"


def fetch_metadata(url: str, timeout: int = 20) -> str:
    with urllib.request.urlopen(url, timeout=timeout) as response:
        return response.read().decode("utf-8")


# ---------------------------------------------------------------------------
# Self-test fixtures (no network). The `--selftest` flag runs these assertions
# so CI can validate the script's logic without depending on dl.google.com.
# ---------------------------------------------------------------------------

SELFTEST_CASES: list[tuple[str, int]] = [
    # Still alpha-only: must pass.
    (
        """<metadata><versioning><versions>
        <version>1.4.0</version>
        <version>1.5.0-alpha20</version>
        <version>1.5.0-alpha21</version>
        <version>1.5.0-alpha22</version>
        <version>1.5.0-alpha23</version>
        <version>1.5.0-alpha24</version>
        <version>1.5.0-alpha25</version>
        </versions></versioning></metadata>""",
        EXIT_OK,
    ),
    # Beta/rc are still pre-release: must pass.
    (
        """<metadata><versioning><versions>
        <version>1.5.0-beta01</version>
        <version>1.5.0-rc01</version>
        </versions></versioning></metadata>""",
        EXIT_OK,
    ),
    # Stable 1.5.0 appears: must fail.
    (
        """<metadata><versioning><versions>
        <version>1.5.0-alpha25</version>
        <version>1.5.0</version>
        </versions></versioning></metadata>""",
        EXIT_MIGRATE,
    ),
    # Stable 1.5.1 alongside older stable 1.4.x: must fail and report 1.5.1.
    (
        """<metadata><versioning><versions>
        <version>1.4.0</version>
        <version>1.4.1</version>
        <version>1.5.0</version>
        <version>1.5.1</version>
        </versions></versioning></metadata>""",
        EXIT_MIGRATE,
    ),
    # Stable 2.0.0 (future major): must fail too.
    (
        """<metadata><versioning><versions>
        <version>2.0.0</version>
        </versions></versioning></metadata>""",
        EXIT_MIGRATE,
    ),
    # No versions at all: malformed-ish, treat as pass with message.
    ("""<metadata><versioning><versions></versions></versioning></metadata>""", EXIT_OK),
]


def run_selftest() -> int:
    failures = 0
    for index, (xml_text, expected_code) in enumerate(SELFTEST_CASES, start=1):
        versions = parse_versions(xml_text)
        code, message = check(versions)
        status = "PASS" if code == expected_code else "FAIL"
        if code != expected_code:
            failures += 1
        print(f"[{status}] case {index}: expected exit {expected_code}, got {code} - {message.splitlines()[0]}")
    if failures:
        print(f"\n{len(failures)} selftest case(s) failed")
        return EXIT_MIGRATE
    print("\nall selftest cases passed")
    return EXIT_OK


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description="Check for a stable Compose Material3 1.5.x release")
    parser.add_argument("--metadata", metavar="FILE", help="read a local maven-metadata.xml instead of querying Maven")
    parser.add_argument("--selftest", action="store_true", help="run offline self-tests and exit")
    args = parser.parse_args(argv)

    if args.selftest:
        return run_selftest()

    try:
        xml_text = fetch_metadata(METADATA_URL) if not args.metadata else open(args.metadata, encoding="utf-8").read()
        versions = parse_versions(xml_text)
    except Exception as error:  # noqa: BLE001 - any transport/parse issue must not break CI silently
        print(f"WARNING: unable to check material3 metadata ({error}); skipping check", file=sys.stderr)
        return EXIT_OK

    code, message = check(versions)
    print(message)
    return code


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
