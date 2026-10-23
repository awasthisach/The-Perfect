#!/usr/bin/env python3
"""Fail-closed release promotion gate.

Requires:
  - cpas-status.json production_status == PASS
  - ort-status.json status == PASS (missing or malformed is BLOCKED)
  - optional APK path exists and is non-empty

Exit 0 only when all required checks pass. Intended for release.yml before
shipping signed artifacts.
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path


def load_json(path: Path) -> dict:
    if not path.is_file():
        raise FileNotFoundError(str(path))
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise ValueError(f"{path} is not a JSON object")
    return data


def main() -> int:
    parser = argparse.ArgumentParser(description="CPAS + ORT + APK release gate")
    parser.add_argument("--cpas-status", type=Path, default=Path("cpas-status.json"))
    parser.add_argument("--ort-status", type=Path, default=Path("ort-status.json"))
    parser.add_argument("--revision", required=True)
    parser.add_argument("--apk", type=Path, default=None)
    args = parser.parse_args()

    failures: list[str] = []

    try:
        cpas = load_json(args.cpas_status)
        if cpas.get("source_commit") != args.revision:
            failures.append("CPAS revision mismatch")
        status = cpas.get("production_status", {})
        value = status.get("value") if isinstance(status, dict) else None
        if value != "PASS":
            failures.append(f"CPAS production_status is {value!r}, expected PASS")
        else:
            print("CPAS: PASS")
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        failures.append(f"CPAS status unreadable: {exc}")

    try:
        ort = load_json(args.ort_status)
        if ort.get("scan_revision") != args.revision:
            failures.append("ORT revision mismatch")
        if ort.get("status") != "PASS":
            failures.append(
                f"ORT status is {ort.get('status')!r}, expected PASS "
                f"(reason={ort.get('reason')!r})"
            )
        else:
            print("ORT: PASS")
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        failures.append(f"ORT status unreadable/missing: {exc}")

    if args.apk is not None:
        if not args.apk.is_file() or args.apk.stat().st_size <= 0:
            failures.append(f"APK missing or empty: {args.apk}")
        else:
            print(f"APK: present ({args.apk.stat().st_size} bytes)")

    if failures:
        for item in failures:
            print(f"BLOCKED: {item}", file=sys.stderr)
        return 1

    print("RELEASE_GATE: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
