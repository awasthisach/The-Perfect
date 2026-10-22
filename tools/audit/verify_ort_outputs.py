#!/usr/bin/env python3
"""Fail closed when a successful ORT action did not produce its core results and SBOMs."""
from __future__ import annotations

import argparse
import json
from pathlib import Path

REQUIRED_OUTPUTS = (
    "analyzer-result.json",
    "scan-result.json",
    "evaluation-result.json",
    "advisor-result.json",
    "bom.cyclonedx.json",
    "bom.cyclonedx.xml",
    "bom.spdx.json",
    "bom.spdx.yml",
)


def missing_outputs(results_dir: Path) -> list[str]:
    """Return required ORT output names that are missing or empty."""
    return [
        name for name in REQUIRED_OUTPUTS
        if not (results_dir / name).is_file() or (results_dir / name).stat().st_size == 0
    ]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--results-dir", type=Path, required=True)
    parser.add_argument("--revision", required=True)
    args = parser.parse_args()

    missing = missing_outputs(args.results_dir)
    report = {
        "gate_id": "GATE-LICENSE-001",
        "revision": args.revision,
        "results_dir": str(args.results_dir),
        "required_outputs": list(REQUIRED_OUTPUTS),
        "missing_or_empty_outputs": missing,
        "status": "PASS" if not missing else "BLOCKED",
    }
    print(json.dumps(report, indent=2))
    if missing:
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
