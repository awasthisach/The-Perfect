#!/usr/bin/env python3
"""Record exact-SHA ORT gate status with separate cache-save and cache-validation outcomes."""
from __future__ import annotations
import argparse
import json
import os
from pathlib import Path
from typing import Any

def build_status(*, ort_outcome: str, report_validation_outcome: str, expected_sha: str,
                 checkout_sha: str, scan_sha: str, cache_save_outcome: str,
                 cache_validation_outcome: str, cache_status: str, cache_file_count: int,
                 cache_scan_result_count: int, workflow_run_id: int, workflow_ref: str,
                 validation_problems: list[str] | None = None) -> dict[str, Any]:
    reasons: list[str] = []
    if ort_outcome != "success":
        reasons.append(f"ORT action outcome is {ort_outcome!r}, expected 'success'")
    if report_validation_outcome != "success":
        reasons.append(f"Required ORT report validation outcome is {report_validation_outcome!r}, expected 'success'")
    if not expected_sha or checkout_sha != expected_sha or scan_sha != expected_sha:
        reasons.append(f"SHA provenance mismatch: workflow={expected_sha!r}, checkout={checkout_sha!r}, scan={scan_sha!r}")
    if cache_save_outcome != "success":
        reasons.append(f"ORT scan-result cache save outcome is {cache_save_outcome!r}, expected 'success'")
    if cache_validation_outcome != "success":
        reasons.append(f"ORT scan-result cache validation outcome is {cache_validation_outcome!r}, expected 'success'")
    if cache_status != "PASS":
        reasons.append(f"ORT scan-result cache validation status is {cache_status!r}, expected 'PASS'")
    if cache_scan_result_count <= 0:
        reasons.append("No non-empty provenance-based ScanCode scan-results.yml files were persisted")
    if validation_problems:
        reasons.extend(f"ORT output validation: {problem}" for problem in validation_problems)
    status = "PASS" if not reasons else "BLOCKED"
    return {
        "gate_id": "GATE-LICENSE-001",
        "scanner": "OSS Review Toolkit (ORT)",
        "status": status,
        "workflow_run_id": workflow_run_id,
        "workflow_ref": workflow_ref,
        "workflow_sha": expected_sha,
        "checkout_revision": checkout_sha,
        "scan_revision": scan_sha,
        "ort_action_outcome": ort_outcome,
        "report_validation_outcome": report_validation_outcome,
        "cache_save_step_outcome": cache_save_outcome,
        "cache_validation_outcome": cache_validation_outcome,
        "cache_validation_status": cache_status,
        "cache_file_count": cache_file_count,
        "cache_scan_result_file_count": cache_scan_result_count,
        "validation_problems": validation_problems or [],
        "blocking_reasons": reasons,
        "reason": "ORT stages, SHA provenance, required reports, cache save, cache validation and persisted ScanCode results passed" if status == "PASS" else "; ".join(reasons),
    }

def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=Path("ort-status.json"))
    args = parser.parse_args()
    def env(name: str, default: str = "unknown") -> str:
        return os.environ.get(name, default).strip() or default
    try:
        cache_file_count = int(env("CACHE_FILE_COUNT", "0"))
        cache_scan_result_count = int(env("CACHE_SCAN_RESULT_COUNT", "0"))
        workflow_run_id = int(env("WORKFLOW_RUN_ID", "0"))
    except ValueError as exc:
        raise SystemExit(f"Invalid numeric ORT status environment value: {exc}") from exc
    validation_problems: list[str] = []
    validation_report_path = env("VALIDATION_REPORT_PATH", "")
    if validation_report_path:
        try:
            validation_report = json.loads(Path(validation_report_path).read_text(encoding="utf-8"))
            raw_problems = validation_report.get("validation_problems") if isinstance(validation_report, dict) else None
            if isinstance(raw_problems, list) and all(isinstance(item, str) for item in raw_problems):
                validation_problems = raw_problems
            else:
                validation_problems = ["validation report is malformed or lacks a string validation_problems list"]
        except (OSError, UnicodeError, json.JSONDecodeError) as exc:
            validation_problems = [f"validation report could not be read: {exc}"]

    result = build_status(
        ort_outcome=env("ORT_OUTCOME"),
        report_validation_outcome=env("VERIFY_OUTCOME"),
        expected_sha=env("WORKFLOW_SHA", ""),
        checkout_sha=env("CHECKOUT_SHA", ""),
        scan_sha=env("ORT_SCAN_SHA", ""),
        cache_save_outcome=env("CACHE_SAVE_OUTCOME"),
        cache_validation_outcome=env("CACHE_VALIDATION_OUTCOME"),
        cache_status=env("CACHE_STATUS"),
        cache_file_count=cache_file_count,
        cache_scan_result_count=cache_scan_result_count,
        workflow_run_id=workflow_run_id,
        workflow_ref=env("WORKFLOW_REF", ""),
        validation_problems=validation_problems,
    )
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2))
    return 0 if result["status"] == "PASS" else 1

if __name__ == "__main__":
    raise SystemExit(main())
