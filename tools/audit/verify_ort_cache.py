#!/usr/bin/env python3
"""Fail-closed validation of persisted ORT ScanCode result files."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

SCAN_RESULT_NAME = "scan-results.yml"
SCAN_RESULT_STORAGE_ROOTS = {"artifact", "repository"}


def find_scan_result_files(cache_dir: Path) -> list[Path]:
    """Find ORT's actual provenance-based scan-result records, not metadata files."""
    found: list[Path] = []
    if not cache_dir.is_dir():
        return found

    for path in cache_dir.rglob(SCAN_RESULT_NAME):
        if not path.is_file() or path.stat().st_size <= 0:
            continue
        relative = path.relative_to(cache_dir)
        # ORT stores scan results below ~/.ort/scanner/results/{artifact|repository}/.
        if len(relative.parts) >= 3 and relative.parts[0] == "results":
            if relative.parts[1] in SCAN_RESULT_STORAGE_ROOTS:
                found.append(path)
    return sorted(found)


def build_manifest(cache_dir: Path, cache_save_outcome: str) -> dict[str, Any]:
    files = sorted(path for path in cache_dir.rglob("*") if path.is_file()) if cache_dir.is_dir() else []
    scan_results = find_scan_result_files(cache_dir)
    status = "PASS" if cache_save_outcome == "success" and scan_results else "BLOCKED"
    return {
        "gate_id": "GATE-ORT-CACHE-001",
        "status": status,
        "cache_save_step_outcome": cache_save_outcome,
        "cache_directory": str(cache_dir),
        "file_count": len(files),
        "total_bytes": sum(path.stat().st_size for path in files),
        "cache_scan_result_file_count": len(scan_results),
        "scan_result_inventory": [
            {"path": str(path.relative_to(cache_dir)), "size_bytes": path.stat().st_size}
            for path in scan_results
        ],
        "file_inventory": [
            {"path": str(path.relative_to(cache_dir)), "size_bytes": path.stat().st_size}
            for path in files
        ],
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--cache-dir", required=True, type=Path)
    parser.add_argument("--cache-save-outcome", required=True)
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--github-output", type=Path)
    args = parser.parse_args()

    manifest = build_manifest(args.cache_dir, args.cache_save_outcome)
    args.manifest.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    if args.github_output:
        with args.github_output.open("a", encoding="utf-8") as output:
            output.write(f"cache_status={manifest['status']}\n")
            output.write(f"cache_file_count={manifest['file_count']}\n")
            output.write(f"cache_scan_result_file_count={manifest['cache_scan_result_file_count']}\n")

    print(json.dumps(manifest))
    if manifest["status"] != "PASS":
        print("::error::ORT cache save failed or no non-empty provenance-based scan-results.yml files were persisted.")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
