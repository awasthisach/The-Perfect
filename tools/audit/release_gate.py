#!/usr/bin/env python3
"""Fail-closed release promotion gate.

Requires:
  - cpas-status.json production_status == PASS
  - foss-status.json status == PASS (when present; missing is BLOCKED)
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
    parser = argparse.ArgumentParser(description="CPAS + FOSSA release gate")
    parser.add_argument("--cpas-status", type=Path, default=Path("cpas-status.json"))
    parser.add_argument("--foss-status", type=Path, default=Path("foss-status.json"))
    parser.add_argument("--apk", type=Path, default=None)
    parser.add_argument("--require-fossa", action="store_true", default=True)
    parser.add_argument("--allow-missing-fossa", action="store_true")
    args = parser.parse_args()

    failures: list[str] = []

    try:
        cpas = load_json(args.cpas_status)
        status = cpas.get("production_status", {})
        value = status.get("value") if isinstance(status, dict) else None
        if value != "PASS":
            failures.append(f"CPAS production_status is {value!r}, expected PASS")
        else:
            print("CPAS: PASS")
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        failures.append(f"CPAS status unreadable: {exc}")

    require_fossa = args.require_fossa and not args.allow_missing_fossa
    if require_fossa:
        try:
            foss = load_json(args.foss_status)
            if foss.get("status") != "PASS":
                failures.append(
                    f"FOSSA status is {foss.get('status')!r}, expected PASS "
                    f"(reason={foss.get('reason')!r})"
                )
            else:
                print("FOSSA: PASS")
        except (OSError, ValueError, json.JSONDecodeError) as exc:
            failures.append(f"FOSSA status unreadable/missing: {exc}")
    else:
        print("FOSSA: skipped (--allow-missing-fossa)")

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
