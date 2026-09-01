#!/usr/bin/env python3
"""Executable CPAS assurance verifier.

This verifier is intentionally dependency-free and fail-closed.  Markdown is
checked only for document integrity; machine-readable assurance files are the
source of computed status.  A structurally valid but empty evidence ledger is
NOT a production PASS.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ASSURANCE = ROOT / "docs" / "assurance"
SHEET = ASSURANCE / "FINAL_CPAS_SHEET.md"
INVARIANTS = ASSURANCE / "05-security-reliability-invariants.yaml"
TEST_MATRIX = ASSURANCE / "07-test-matrix.yaml"
RISK = ASSURANCE / "08-risk-register.yaml"
LEDGER = ASSURANCE / "evidence-ledger.json"
OUT = ROOT / "cpas-status.json"

REQUIRED = [SHEET, INVARIANTS, TEST_MATRIX, RISK, LEDGER]
VALID_STATUS = {"pending", "verified", "failed"}
VALID_SEVERITY = {"critical", "high", "medium", "low"}
SHA256_RE = re.compile(r"^[0-9a-fA-F]{64}$")
COMMIT_RE = re.compile(r"^[0-9a-fA-F]{40}$")


def check(name: str, passed: bool, detail: str, *, blocking: bool = True) -> dict:
    return {
        "name": name,
        "status": "PASS" if passed else "BLOCKED",
        "blocking": blocking,
        "detail": detail,
    }


def yaml_scalar(text: str, key: str) -> str | None:
    match = re.match(rf"^\s*{re.escape(key)}:\s*([^#\n]+?)\s*$", text)
    return match.group(1).strip().strip("\"'") if match else None


def parse_id_blocks(text: str, key: str = "id:") -> list[dict[str, str]]:
    blocks: list[dict[str, str]] = []
    current: dict[str, str] | None = None
    for line in text.splitlines():
        if re.match(r"^\s*-\s*id:\s*\S+", line):
            if current:
                blocks.append(current)
            value = line.split("id:", 1)[1].strip().strip("\"'")
            current = {"id": value}
            continue
        if current:
            for field in ("severity", "status", "closure_requires", "required_tests", "required_sequence"):
                value = yaml_scalar(line, field)
                if value is not None:
                    current[field] = value
    if current:
        blocks.append(current)
    return blocks


def parse_list(value: str | None) -> list[str]:
    if not value:
        return []
    value = value.strip()
    if value.startswith("[") and value.endswith("]"):
        value = value[1:-1]
    return [item.strip().strip("\"'") for item in value.split(",") if item.strip()]


def validate_required_files() -> tuple[bool, str]:
    missing = [str(path.relative_to(ROOT)) for path in REQUIRED if not path.is_file()]
    return (not missing, "all required assurance files present" if not missing else "missing: " + ", ".join(missing))


def validate_sheet() -> tuple[bool, str]:
    if not SHEET.is_file():
        return False, "FINAL_CPAS_SHEET.md missing"
    text = SHEET.read_text(encoding="utf-8")
    headings = [int(x) for x in re.findall(r"^#\s+(\d+)\.\s+", text, re.MULTILINE)]
    return headings == list(range(1, 50)), f"found {len(headings)} numbered top-level sections; expected 1..49"


def validate_invariants() -> tuple[bool, str, list[str]]:
    if not INVARIANTS.is_file():
        return False, "invariant registry missing", []
    text = INVARIANTS.read_text(encoding="utf-8")
    blocks = parse_id_blocks(text)
    ids = [b["id"] for b in blocks]
    duplicates = sorted({item for item in ids if ids.count(item) > 1})
    malformed = [b["id"] for b in blocks if not b.get("required_tests") and not b.get("required_sequence")]
    if duplicates:
        return False, "duplicate invariant IDs: " + ", ".join(duplicates), ids
    if malformed:
        return False, "invariants missing executable requirements: " + ", ".join(malformed), ids
    if not ids:
        return False, "no invariants declared", ids
    return True, f"{len(ids)} unique invariants with executable test/sequence requirements", ids


def validate_test_matrix(invariant_ids: list[str]) -> tuple[bool, str]:
    if not TEST_MATRIX.is_file():
        return False, "test matrix missing"
    text = TEST_MATRIX.read_text(encoding="utf-8")
    if not re.search(r"^schema_version:\s*\d+", text, re.MULTILINE):
        return False, "test matrix schema_version missing"
    if not re.search(r"^test_levels:\s*$", text, re.MULTILINE):
        return False, "test matrix test_levels missing"
    if not re.search(r"^minimum_traceability:\s*$", text, re.MULTILINE):
        return False, "test matrix minimum_traceability missing"
    if "critical_invariant:" not in text or "positive_and_negative_tests" not in text:
        return False, "critical invariant traceability policy missing"
    return True, f"test matrix contract present; {len(invariant_ids)} invariants are registered for traceability"


def validate_risks() -> tuple[bool, str, list[str]]:
    if not RISK.is_file():
        return False, "risk register missing", []
    text = RISK.read_text(encoding="utf-8")
    blocks = parse_id_blocks(text)
    ids = [b["id"] for b in blocks]
    duplicates = sorted({item for item in ids if ids.count(item) > 1})
    invalid = [b["id"] for b in blocks if b.get("severity") not in VALID_SEVERITY or not b.get("status")]
    blockers = []
    missing_closure = []
    for block in blocks:
        if block.get("status") != "closed" and block.get("severity") in {"critical", "high"}:
            blockers.append(block["id"])
            if not parse_list(block.get("closure_requires")):
                missing_closure.append(block["id"])
    if duplicates:
        return False, "duplicate risk IDs: " + ", ".join(duplicates), blockers
    if invalid:
        return False, "risk entries missing/invalid severity or status: " + ", ".join(invalid), blockers
    if missing_closure:
        return False, "open critical/high findings missing closure requirements: " + ", ".join(missing_closure), blockers
    return not blockers, ("no open critical/high findings" if not blockers else "open blockers: " + ", ".join(blockers)), blockers


def validate_ledger() -> tuple[bool, str, list[str]]:
    if not LEDGER.is_file():
        return False, "evidence-ledger.json missing", []
    try:
        data = json.loads(LEDGER.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError) as exc:
        return False, f"invalid JSON: {exc}", []
    if not isinstance(data, dict) or data.get("schema_version") != 1:
        return False, "ledger schema_version must be 1", []
    entries = data.get("entries")
    if not isinstance(entries, list):
        return False, "entries must be a list", []
    if not entries:
        return False, "evidence ledger is empty; production PASS requires reproducible evidence", []

    ids: list[str] = []
    errors: list[str] = []
    for index, entry in enumerate(entries):
        prefix = f"entry[{index}]"
        if not isinstance(entry, dict):
            errors.append(f"{prefix} must be an object")
            continue
        entry_id = entry.get("id")
        if not isinstance(entry_id, str) or not entry_id:
            errors.append(f"{prefix}.id missing")
        else:
            ids.append(entry_id)
        status = entry.get("status")
        if status not in VALID_STATUS:
            errors.append(f"{prefix}.status invalid")
        if status == "verified":
            source_commit = entry.get("source_commit")
            if not isinstance(source_commit, str) or not COMMIT_RE.fullmatch(source_commit):
                errors.append(f"{prefix}.source_commit must be a 40-hex commit SHA")
            digest = entry.get("artifact_digest")
            if not isinstance(digest, str) or not SHA256_RE.fullmatch(digest.removeprefix("sha256:")):
                errors.append(f"{prefix}.artifact_digest must be a SHA-256 digest")
            if not entry.get("test"):
                errors.append(f"{prefix}.test missing")
            if not entry.get("workflow_run_id"):
                errors.append(f"{prefix}.workflow_run_id missing")
            if not entry.get("timestamp"):
                errors.append(f"{prefix}.timestamp missing")
    duplicates = sorted({item for item in ids if ids.count(item) > 1})
    if duplicates:
        errors.append("duplicate evidence IDs: " + ", ".join(duplicates))
    return (not errors, "ledger valid with reproducible evidence" if not errors else "; ".join(errors), ids)


def self_test() -> int:
    assert parse_list("[a, b]") == ["a", "b"]
    assert SHA256_RE.fullmatch("0" * 64)
    assert not SHA256_RE.fullmatch("0" * 63)
    assert COMMIT_RE.fullmatch("a" * 40)
    assert not COMMIT_RE.fullmatch("a" * 39)
    assert parse_id_blocks("  - id: X\n    severity: critical\n    required_tests: [a, b]\n") == [
        {"id": "X", "severity": "critical", "required_tests": "[a, b]"}
    ]
    print("CPAS verifier self-tests: PASS")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Fail-closed CPAS verifier")
    parser.add_argument("--self-test", action="store_true", help="run verifier self-tests only")
    args = parser.parse_args()
    if args.self_test:
        return self_test()

    checks: list[dict] = []
    ok_files, detail_files = validate_required_files()
    checks.append(check("required_assurance_files", ok_files, detail_files))

    ok_sheet, detail_sheet = validate_sheet()
    checks.append(check("cpas_49_point_integrity", ok_sheet, detail_sheet))

    ok_inv, detail_inv, invariant_ids = validate_invariants()
    checks.append(check("invariant_contract", ok_inv, detail_inv))

    ok_matrix, detail_matrix = validate_test_matrix(invariant_ids)
    checks.append(check("test_matrix_contract", ok_matrix, detail_matrix))

    ok_risk, detail_risk, blockers = validate_risks()
    checks.append(check("risk_register_gate", ok_risk, detail_risk))

    ok_ledger, detail_ledger, evidence_ids = validate_ledger()
    checks.append(check("evidence_ledger_gate", ok_ledger, detail_ledger))

    blocked = [c for c in checks if c["status"] == "BLOCKED" and c.get("blocking", True)]
    status = "PASS" if not blocked else "BLOCKED"
    result = {
        "schema_version": 2,
        "production_status": {"value": status, "computed": True},
        "computed_at": datetime.now(timezone.utc).isoformat(),
        "checks": checks,
        "summary": {"total": len(checks), "passed": len(checks) - len(blocked), "blocked": len(blocked)},
        "blocking_findings": blockers,
        "inventory": {
            "invariants": len(invariant_ids),
            "evidence_entries": len(evidence_ids),
        },
    }
    OUT.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2))
    return 0 if status == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
