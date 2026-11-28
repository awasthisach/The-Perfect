#!/usr/bin/env python3
"""Record exact-SHA ORT gate status with stage diagnostics and cache outcomes."""
from __future__ import annotations

import argparse
import json
import os
from collections import Counter
from pathlib import Path
from typing import Any


def _read_json(results_dir: Path, filename: str) -> dict[str, Any] | None:
    try:
        value = json.loads((results_dir / filename).read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError):
        return None
    return value if isinstance(value, dict) else None


def _walk(value: object):
    if isinstance(value, dict):
        yield value
        for child in value.values():
            yield from _walk(child)
    elif isinstance(value, list):
        for child in value:
            yield from _walk(child)


def _items_by_type(items: object) -> dict[str, int]:
    counts: Counter[str] = Counter()
    if not isinstance(items, list):
        return {}
    for item in items:
        identifier = ""
        if isinstance(item, dict):
            identifier = item.get("id", "") if isinstance(item.get("id"), str) else ""
            if not identifier and isinstance(item.get("package"), dict):
                package_id = item["package"].get("id")
                identifier = package_id if isinstance(package_id, str) else ""
            purl = item.get("purl")
            if not identifier and isinstance(purl, str) and purl.startswith("pkg:"):
                identifier = purl[4:].split("/", 1)[0]
        ecosystem = identifier.split(":", 1)[0] if identifier else "UNKNOWN"
        counts[ecosystem] += 1
    return dict(sorted(counts.items()))


def _severity_counts(value: object) -> dict[str, int]:
    counts: Counter[str] = Counter()
    for item in _walk(value):
        severity = item.get("severity")
        if isinstance(severity, str) and severity.strip():
            counts[severity.upper()] += 1
    return dict(sorted(counts.items()))


def _rule_counts(violations: object) -> dict[str, int]:
    counts: Counter[str] = Counter()
    if not isinstance(violations, list):
        return {}
    for violation in violations:
        rule = violation.get("rule") if isinstance(violation, dict) else None
        if isinstance(rule, dict):
            rule = rule.get("id") or rule.get("name")
        if not isinstance(rule, str) or not rule:
            rule = violation.get("rule_id") if isinstance(violation, dict) else None
        counts[rule if isinstance(rule, str) and rule else "UNKNOWN"] += 1
    return dict(sorted(counts.items()))


def _license_coverage(items: object, *, cyclonedx: bool = False) -> dict[str, int | float | None]:
    if not isinstance(items, list):
        return {"item_count": 0, "unasserted_count": 0, "unasserted_percent": None}
    def asserted(item: object) -> bool:
        if not isinstance(item, dict):
            return False
        if cyclonedx:
            licenses = item.get("licenses")
            if not isinstance(licenses, list):
                return False
            return any(
                isinstance(entry, dict)
                and (
                    isinstance(entry.get("expression"), str)
                    and bool(entry["expression"].strip())
                    and entry["expression"].strip().upper() != "NOASSERTION"
                    or isinstance(entry.get("license"), dict)
                    and any(
                        isinstance(entry["license"].get(field), str)
                        and bool(entry["license"][field].strip())
                        and entry["license"][field].strip().upper() != "NOASSERTION"
                        for field in ("id", "name", "url", "text")
                    )
                )
                for entry in licenses
            )
        return any(
            isinstance(item.get(field), str)
            and bool(item[field].strip())
            and item[field].strip().upper() != "NOASSERTION"
            for field in ("licenseDeclared", "licenseConcluded")
        )
    unasserted = sum(not asserted(item) for item in items)
    percent = 100.0 * unasserted / len(items) if items else None
    return {"item_count": len(items), "unasserted_count": unasserted, "unasserted_percent": percent}


def collect_diagnostics(results_dir: Path, advisor_provider: str = "unknown") -> dict[str, Any]:
    """Collect best-effort stage diagnostics; validation still decides whether files are valid."""
    analyzer = _read_json(results_dir, "analyzer-result.json") or {}
    analyzer_result = analyzer.get("analyzer", {}).get("result", {}) if isinstance(analyzer.get("analyzer"), dict) else {}
    projects = analyzer_result.get("projects") if isinstance(analyzer_result, dict) else None
    packages = analyzer_result.get("packages") if isinstance(analyzer_result, dict) else None
    analyzer_issues = analyzer_result.get("issues", {}) if isinstance(analyzer_result, dict) else {}

    scanner = _read_json(results_dir, "scan-result.json") or {}
    scan_results = scanner.get("scanner", {}).get("scan_results") if isinstance(scanner.get("scanner"), dict) else None
    if not isinstance(scan_results, list) and isinstance(scanner.get("scanner"), dict):
        scan_results = scanner["scanner"].get("scanResults")

    evaluation = _read_json(results_dir, "evaluation-result.json") or {}
    violations = evaluation.get("evaluator", {}).get("violations") if isinstance(evaluation.get("evaluator"), dict) else None

    advisor = _read_json(results_dir, "advisor-result.json") or {}
    advisor_results = advisor.get("advisor", {}).get("results") if isinstance(advisor.get("advisor"), dict) else None
    advisor_package_result_count = len(advisor_results) if isinstance(advisor_results, (list, dict)) else 0
    vulnerability_count = sum(
        len(item.get("vulnerabilities", []))
        for item in _walk(advisor_results)
        if isinstance(item.get("vulnerabilities"), list)
    )

    spdx = _read_json(results_dir, "bom.spdx.json") or {}
    cyclonedx = _read_json(results_dir, "bom.cyclonedx.json") or {}
    spdx_coverage = _license_coverage(spdx.get("packages"))
    cyclonedx_coverage = _license_coverage(cyclonedx.get("components"), cyclonedx=True)

    return {
        "analyzer": {
            "project_count": len(projects) if isinstance(projects, list) else None,
            "projects_by_type": _items_by_type(projects),
            "package_count": len(packages) if isinstance(packages, list) else None,
            "packages_by_type": _items_by_type(packages),
            "issues_by_severity": _severity_counts(analyzer_issues),
        },
        "scanner": {
            "package_scan_result_count": len(scan_results) if isinstance(scan_results, list) else None,
            "issues_by_severity": _severity_counts(scanner.get("scanner", {})),
        },
        "evaluator": {
            "violation_count": len(violations) if isinstance(violations, list) else None,
            "violations_by_rule": _rule_counts(violations),
        },
        "advisor": {
            "provider": advisor_provider or "unknown",
            "package_result_count": advisor_package_result_count,
            "vulnerability_count": vulnerability_count,
        },
        "sbom_license_coverage": {
            "spdx": spdx_coverage,
            "cyclonedx": cyclonedx_coverage,
        },
    }


def build_status(*, ort_outcome: str, report_validation_outcome: str, expected_sha: str,
                 checkout_sha: str, scan_sha: str, cache_save_outcome: str,
                 cache_validation_outcome: str, cache_status: str, cache_file_count: int,
                 cache_scan_result_count: int, workflow_run_id: int, workflow_ref: str,
                 validation_problems: list[str] | None = None,
                 diagnostics: dict[str, Any] | None = None) -> dict[str, Any]:
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
        "diagnostics": diagnostics or {},
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

    results_dir = Path(env("ORT_RESULTS_DIR", "")) if env("ORT_RESULTS_DIR", "") else Path(".")
    diagnostics = collect_diagnostics(results_dir, env("ORT_ADVISOR_PROVIDER"))
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
        diagnostics=diagnostics,
    )
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2))
    return 0 if result["status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
