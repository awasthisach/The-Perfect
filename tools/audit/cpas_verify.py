#!/usr/bin/env python3
"""Deterministic CPAS assurance verifier.

The verifier fails closed. It validates the repository's assurance contracts and
emits cpas-status.json. It intentionally does not treat prose, skipped tests,
or an empty evidence ledger as production evidence.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from datetime import datetime, timezone
from pathlib import Path

ID_RE = re.compile(r"^[A-Z][A-Z0-9]*(?:-[A-Z0-9]+)+$")
EVIDENCE_ID_RE = re.compile(r"^EVID-[0-9]{3,}$")
SHA_RE = re.compile(r"^[0-9a-f]{40}$")
DATETIME_RE = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z$")


def check(name: str, passed: bool, detail: str) -> dict[str, str]:
    return {"name": name, "status": "PASS" if passed else "BLOCKED", "detail": detail}


def ids_from_yaml(path: Path) -> list[str]:
    ids: list[str] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        match = re.match(r"\s*-\s+id:\s*([A-Za-z0-9_-]+)\s*$", line)
        if match:
            ids.append(match.group(1))
    return ids


def controls_from_traceability(path: Path) -> list[str]:
    controls: list[str] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        match = re.match(r"\s*controls:\s*\[([^]]*)\]", line)
        if match:
            controls.extend(item.strip() for item in match.group(1).split(",") if item.strip())
    return controls


def required_ledger_fields(entry: object) -> bool:
    if not isinstance(entry, dict):
        return False
    required = {
        "id", "control_id", "test_id", "source_commit", "ci_run_id",
        "ci_job_id", "executed_at", "result", "environment", "expires_at",
    }
    if not required.issubset(entry):
        return False
    if not EVIDENCE_ID_RE.fullmatch(str(entry["id"])):
        return False
    if not ID_RE.fullmatch(str(entry["control_id"])) or not ID_RE.fullmatch(str(entry["test_id"])):
        return False
    if not SHA_RE.fullmatch(str(entry["source_commit"])):
        return False
    if entry["result"] not in {"PASS", "FAIL"}:
        return False
    if not DATETIME_RE.fullmatch(str(entry["executed_at"])) or not DATETIME_RE.fullmatch(str(entry["expires_at"])):
        return False
    return True


def parse_risks(path: Path) -> list[dict[str, str]]:
    findings: list[dict[str, str]] = []
    current: dict[str, str] | None = None
    for line in path.read_text(encoding="utf-8").splitlines():
        match = re.match(r"\s*- id:\s*(\S+)", line)
        if match:
            current = {"id": match.group(1)}
            findings.append(current)
            continue
        if current:
            for field in ("severity", "status"):
                match = re.match(rf"\s*{field}:\s*(\S+)", line)
                if match:
                    current[field] = match.group(1)
    return findings


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Verify CPAS assurance contracts")
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    return parser.parse_args()


def main() -> int:
    root = parse_args().root.resolve()
    assurance = root / "docs" / "assurance"
    sheet = assurance / "FINAL_CPAS_SHEET.md"
    risk_path = assurance / "08-risk-register.yaml"
    invariant_path = assurance / "05-security-reliability-invariants.yaml"
    traceability_path = assurance / "06-requirements-traceability.yaml"
    ledger_path = assurance / "evidence-ledger.json"
    output_path = root / "cpas-status.json"

    required = [
        assurance / "00-cpas-constitution.md",
        assurance / "03-evidence-policy.md",
        assurance / "04-technology-registry.yaml",
        invariant_path,
        traceability_path,
        assurance / "07-test-matrix.yaml",
        risk_path,
        assurance / "09-gate-catalog.yaml",
        assurance / "10-remediation-policy.md",
        assurance / "evidence-ledger.schema.json",
        ledger_path,
        sheet,
    ]
    checks: list[dict[str, str]] = []

    missing = [str(path.relative_to(root)) for path in required if not path.is_file()]
    checks.append(check("required_assurance_files", not missing,
                        "all required files present" if not missing else "missing: " + ", ".join(missing)))

    headings: list[int] = []
    if sheet.is_file():
        headings = [int(value) for value in re.findall(r"^#\s+(\d+)\.\s+", sheet.read_text(encoding="utf-8"), re.MULTILINE)]
    checks.append(check("cpas_49_point_integrity", headings == list(range(1, 50)),
                        f"found {len(headings)} numbered top-level sections; expected 1..49"))

    invariant_ids = ids_from_yaml(invariant_path) if invariant_path.is_file() else []
    duplicate_invariants = sorted({item for item in invariant_ids if invariant_ids.count(item) > 1})
    invalid_invariants = sorted(item for item in invariant_ids if not ID_RE.fullmatch(item))
    checks.append(check("canonical_invariant_ids", bool(invariant_ids) and not duplicate_invariants and not invalid_invariants,
                        "canonical invariant IDs are unique and valid" if invariant_ids else "no invariant IDs found"))

    trace_controls = controls_from_traceability(traceability_path) if traceability_path.is_file() else []
    orphan_controls = sorted(set(invariant_ids) - set(trace_controls))
    unknown_controls = sorted(set(trace_controls) - set(invariant_ids))
    checks.append(check("traceability_control_refs", not orphan_controls and not unknown_controls,
                        "all invariant IDs are bidirectionally referenced" if not orphan_controls and not unknown_controls else
                        f"orphan controls: {orphan_controls}; unknown controls: {unknown_controls}"))

    if traceability_path.is_file():
        trace_text = traceability_path.read_text(encoding="utf-8")
        ref_paths = re.findall(r"(?:implementation_refs|test_refs):\s*\[([^]]*)\]", trace_text)
        flattened_refs = [item.strip() for group in ref_paths for item in group.split(",") if item.strip()]
        missing_refs = sorted(ref for ref in flattened_refs if not (root / ref).is_dir())
    else:
        missing_refs = ["traceability file missing"]
    checks.append(check("traceability_paths", not missing_refs,
                        "implementation and test reference paths exist" if not missing_refs else "missing paths: " + ", ".join(missing_refs)))

    findings = parse_risks(risk_path) if risk_path.is_file() else []
    open_blockers = [item for item in findings if item.get("status") != "closed" and item.get("severity") in {"critical", "high"}]
    checks.append(check("risk_register_gate", not open_blockers,
                        "no open critical/high findings" if not open_blockers else
                        "open blockers: " + ", ".join(item["id"] for item in open_blockers)))

    ledger_ok = False
    ledger_detail = "missing evidence-ledger.json"
    entries: object = None
    if ledger_path.is_file():
        try:
            data = json.loads(ledger_path.read_text(encoding="utf-8"))
            entries = data.get("entries")
            policy = data.get("policy")
            shape_ok = isinstance(entries, list) and isinstance(policy, dict)
            entry_shape_ok = shape_ok and all(required_ledger_fields(item) for item in entries)
            duplicate_evidence = len({item.get("id") for item in entries if isinstance(item, dict)}) != len(entries) if isinstance(entries, list) else True
            ledger_ok = bool(shape_ok and entry_shape_ok and not duplicate_evidence and entries)
            if not entries:
                ledger_detail = "evidence ledger is empty; production status is BLOCKED"
            elif not entry_shape_ok:
                ledger_detail = "one or more evidence entries violate the ledger contract"
            elif duplicate_evidence:
                ledger_detail = "duplicate evidence IDs"
            else:
                ledger_detail = f"{len(entries)} schema-valid evidence entries"
        except (json.JSONDecodeError, OSError) as exc:
            ledger_detail = f"invalid JSON: {exc}"
    checks.append(check("evidence_ledger_integrity", ledger_ok, ledger_detail))

    stale_count = 0
    if isinstance(entries, list):
        now = datetime.now(timezone.utc)
        for entry in entries:
            if isinstance(entry, dict):
                try:
                    expiry = datetime.fromisoformat(str(entry["expires_at"]).replace("Z", "+00:00"))
                    if expiry <= now:
                        stale_count += 1
                except (KeyError, ValueError):
                    stale_count += 1
    checks.append(check("evidence_freshness", stale_count == 0,
                        "all evidence is within expiry" if stale_count == 0 else f"expired or malformed evidence entries: {stale_count}"))

    passed = sum(item["status"] == "PASS" for item in checks)
    blocked = len(checks) - passed
    status = "PASS" if blocked == 0 else "BLOCKED"
    result = {
        "schema_version": "1.0",
        "production_status": {"value": status, "computed": True},
        "computed_at": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "checks": checks,
        "summary": {"total": len(checks), "passed": passed, "blocked": blocked},
        "blocking_findings": [item["id"] for item in open_blockers],
    }
    output_path.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2))
    return 0 if status == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
