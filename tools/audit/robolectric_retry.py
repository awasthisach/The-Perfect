#!/usr/bin/env python3
"""Classify a failed Gradle unit-test run for a transient Robolectric artifact fetch.

The classifier is deliberately fail-closed: a Robolectric artifact-fetch signature
AND a transient transport/status failure must occur in the same log/report file.
"""
from __future__ import annotations

import argparse
from pathlib import Path
import re
import sys

ROBOLECTRIC_FETCH = re.compile(
    r"(?:Failed to fetch maven artifact org\.robolectric:"
    r"|Could not (?:GET|HEAD) ['"]?https?://[^\s'"]+/org/robolectric/)",
    re.IGNORECASE,
)
TRANSIENT_FAILURE = re.compile(
    r"(?:HTTP response code:\s*(?:429|5\d\d)"
    r"|Received status code:\s*(?:429|5\d\d)"
    r"|\b(?:SocketTimeoutException|ConnectException|UnknownHostException)\b"
    r"|\bConnection reset\b"
    r"|\b(?:Read|Connect) timed out\b"
    r"|Temporary failure in name resolution)",
    re.IGNORECASE,
)


def report_files(root: Path):
    """Yield Gradle test XML and HTML reports that can contain the test cause."""
    patterns = (
        "**/build/test-results/testDebugUnitTest/**/*.xml",
        "**/build/test-results/testDebugUnitTest/*.xml",
        "**/build/reports/tests/testDebugUnitTest/**/*.html",
    )
    seen: set[Path] = set()
    for pattern in patterns:
        for path in root.glob(pattern):
            if path.is_file() and path not in seen:
                seen.add(path)
                yield path


def is_transient_robolectric_fetch(log_text: str, reports: list[tuple[str, str]]) -> tuple[bool, str]:
    """Return true only when one source contains both the Robolectric and transient signatures."""
    sources = [("Gradle log", log_text), *reports]
    for name, text in sources:
        if ROBOLECTRIC_FETCH.search(text) and TRANSIENT_FAILURE.search(text):
            return True, name
    return False, ""


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--log", required=True, type=Path)
    parser.add_argument("--root", type=Path, default=Path("."))
    args = parser.parse_args()

    try:
        log_text = args.log.read_text(encoding="utf-8", errors="replace")
        reports = [
            (str(path), path.read_text(encoding="utf-8", errors="replace"))
            for path in report_files(args.root)
        ]
    except OSError as exc:
        print(f"Retry classifier could not read evidence: {exc}", file=sys.stderr)
        return 2

    matched, source = is_transient_robolectric_fetch(log_text, reports)
    if matched:
        print(f"Transient Robolectric artifact-fetch failure confirmed in {source}.")
        return 0

    print("No confirmed transient Robolectric artifact-fetch failure; do not retry.")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
