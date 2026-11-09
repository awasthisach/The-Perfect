#!/usr/bin/env python3
"""Fail-closed validation for SHA-bound Android SQLCipher instrumentation reports."""
from __future__ import annotations
import argparse
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

EXPECTED_MODULES = ("app", "core/database", "core/database-compat")
REPORT_RELATIVE_PARTS = ("build", "outputs", "androidTest-results", "connected", "debug")


class EvidenceError(ValueError):
    """Raised when retained instrumentation evidence is incomplete or inconsistent."""


def validate_reports(reports_dir: Path, expected_sha: str, expected_run_id: str | None = None) -> dict[str, dict[str, int]]:
    manifest_path = reports_dir / "sqlcipher-instrumented-manifest.json"
    if not manifest_path.is_file():
        raise EvidenceError(f"Missing SHA/run manifest: {manifest_path}")
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise EvidenceError(f"Invalid SQLCipher manifest: {exc}") from exc
    if manifest.get("tested_sha") != expected_sha:
        raise EvidenceError(f"Report SHA {manifest.get('tested_sha')!r} does not match expected {expected_sha!r}")
    if expected_run_id is not None and str(manifest.get("run_id")) != str(expected_run_id):
        raise EvidenceError(f"Report run id {manifest.get('run_id')!r} does not match selected run {expected_run_id!r}")

    summary: dict[str, dict[str, int]] = {}
    for module in EXPECTED_MODULES:
        module_root = reports_dir / module
        reports = [
            path for path in module_root.rglob("TEST-*.xml")
            if tuple(path.parts[-len(REPORT_RELATIVE_PARTS)-1:-1]) == REPORT_RELATIVE_PARTS
        ] if module_root.is_dir() else []
        if not reports:
            raise EvidenceError(f"No connectedDebugAndroidTest JUnit XML found for module {module}")
        counts = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0, "reports": len(reports)}
        for report in reports:
            try:
                root = ET.parse(report).getroot()
            except (OSError, ET.ParseError) as exc:
                raise EvidenceError(f"Invalid JUnit XML {report}: {exc}") from exc
            counts["tests"] += sum(1 for _ in root.iter("testcase"))
            counts["failures"] += sum(1 for _ in root.iter("failure"))
            counts["errors"] += sum(1 for _ in root.iter("error"))
            counts["skipped"] += sum(1 for _ in root.iter("skipped"))
        if counts["tests"] == 0:
            raise EvidenceError(f"JUnit XML for {module} contains zero test cases")
        if counts["failures"] or counts["errors"]:
            raise EvidenceError(f"JUnit XML for {module} contains failures={counts['failures']} errors={counts['errors']}")
        if counts["skipped"]:
            raise EvidenceError(f"JUnit XML for {module} contains skipped tests={counts['skipped']}")
        summary[module] = counts
    return summary


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reports-dir", type=Path, required=True)
    parser.add_argument("--expected-sha", required=True)
    parser.add_argument("--expected-run-id")
    args = parser.parse_args()
    try:
        summary = validate_reports(args.reports_dir, args.expected_sha, args.expected_run_id)
    except EvidenceError as exc:
        print(f"INSTRUMENTED_EVIDENCE_BLOCKED: {exc}", file=sys.stderr)
        return 1
    print(json.dumps({"status": "PASS", "tested_sha": args.expected_sha, "modules": summary}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
