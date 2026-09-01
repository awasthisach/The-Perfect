#!/usr/bin/env python3
"""Executable CPAS production gate.

Computes assurance status from repository evidence. It deliberately fails closed:
missing controls, malformed evidence, or open critical/high risks keep production
status BLOCKED. No production PASS is hard-coded.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[2]
ASSURANCE = ROOT / "docs" / "assurance"
SHEET = ASSURANCE / "FINAL_CPAS_SHEET.md"
RISK = ASSURANCE / "08-risk-register.yaml"
OUT = ROOT / "cpas-status.json"

REQUIRED = [
    ASSURANCE / "README.md",
    ASSURANCE / "04-technology-registry.yaml",
    ASSURANCE / "05-security-reliability-invariants.yaml",
    ASSURANCE / "07-test-matrix.yaml",
    RISK,
    SHEET,
]


def check(name: str, passed: bool, detail: str) -> dict:
    return {"name": name, "status": "PASS" if passed else "BLOCKED", "detail": detail}


def main() -> int:
    checks: list[dict] = []

    missing = [str(p.relative_to(ROOT)) for p in REQUIRED if not p.is_file()]
    checks.append(check("required_assurance_files", not missing,
                        "all required files present" if not missing else "missing: " + ", ".join(missing)))

    headings = []
    if SHEET.is_file():
        text = SHEET.read_text(encoding="utf-8")
        headings = [int(x) for x in re.findall(r"^#\s+(\d+)\.\s+", text, re.MULTILINE)]
    expected = list(range(1, 50))
    checks.append(check("cpas_49_point_integrity", headings == expected,
                        f"found {len(headings)} numbered top-level sections; expected 1..49"))

    risk_text = RISK.read_text(encoding="utf-8") if RISK.is_file() else ""
    findings = []
    current = None
    for line in risk_text.splitlines():
        m = re.match(r"\s*- id:\s*(\S+)", line)
        if m:
            current = {"id": m.group(1)}
            findings.append(current)
            continue
        if current:
            m = re.match(r"\s*severity:\s*(\S+)", line)
            if m:
                current["severity"] = m.group(1)
            m = re.match(r"\s*status:\s*(\S+)", line)
            if m:
                current["status"] = m.group(1)

    open_blockers = [f for f in findings if f.get("status") != "closed" and f.get("severity") in {"critical", "high"}]
    checks.append(check("risk_register_gate", not open_blockers,
                        "no open critical/high findings" if not open_blockers else
                        "open blockers: " + ", ".join(f["id"] for f in open_blockers)))

    ledger = ASSURANCE / "evidence-ledger.json"
    ledger_ok = False
    ledger_detail = "missing evidence-ledger.json"
    if ledger.is_file():
        try:
            data = json.loads(ledger.read_text(encoding="utf-8"))
            entries = data.get("entries")
            ledger_ok = isinstance(entries, list) and all(
                isinstance(e, dict) and e.get("id") and e.get("status") in {"pending", "verified", "failed"}
                for e in entries
            )
            ledger_detail = f"{len(entries)} evidence entries" if isinstance(entries, list) else "entries must be a list"
        except (json.JSONDecodeError, OSError) as exc:
            ledger_detail = f"invalid JSON: {exc}"
    checks.append(check("evidence_ledger_integrity", ledger_ok, ledger_detail))

    passed = sum(c["status"] == "PASS" for c in checks)
    blocked = len(checks) - passed
    status = "PASS" if blocked == 0 else "BLOCKED"
    result = {
        "schema_version": 1,
        "production_status": {"value": status, "computed": True},
        "computed_at": datetime.now(timezone.utc).isoformat(),
        "checks": checks,
        "summary": {"total": len(checks), "passed": passed, "blocked": blocked},
        "blocking_findings": [f["id"] for f in open_blockers],
    }
    OUT.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2))
    return 0 if status == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
