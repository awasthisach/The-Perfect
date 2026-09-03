#!/usr/bin/env python3
"""Self-tests for CPAS verifier fail-closed behavior.

Run: python3 -m unittest tools.audit.test_cpas_verify -v
or:  python3 -m unittest discover -s tools/audit -p 'test_*.py' -v
"""
from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
VERIFY = ROOT / "tools" / "audit" / "cpas_verify.py"


def write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


class CpasVerifySelfTests(unittest.TestCase):
    def test_empty_ledger_blocks_production(self) -> None:
        """Empty evidence ledger must not produce production PASS."""
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            assurance = root / "docs" / "assurance"
            # Minimal skeleton so required-file check is not the only failure
            for name in (
                "00-cpas-constitution.md",
                "03-evidence-policy.md",
                "10-remediation-policy.md",
                "FINAL_CPAS_SHEET.md",
            ):
                body = "# placeholder\n" if name != "FINAL_CPAS_SHEET.md" else "\n".join(
                    f"# {i}. Section {i}" for i in range(1, 50)
                ) + "\n"
                write(assurance / name, body)
            write(assurance / "04-technology-registry.yaml", "fts_module: FTS5\nmin_sdk: 24\n")
            write(
                assurance / "05-security-reliability-invariants.yaml",
                "invariants:\n  - id: STORAGE-INV-001\n",
            )
            write(
                assurance / "06-requirements-traceability.yaml",
                "requirements:\n  - id: REQ-X\n    controls: [STORAGE-INV-001]\n"
                "    implementation_refs: [core]\n    test_refs: [core]\n",
            )
            (root / "core").mkdir()
            write(assurance / "07-test-matrix.yaml", "matrix: []\n")
            write(
                assurance / "08-risk-register.yaml",
                "open_findings: []\nclosed_findings: []\n",
            )
            write(
                assurance / "09-gate-catalog.yaml",
                "gates:\n  - id: GATE-CPAS-001\n",
            )
            write(assurance / "evidence-ledger.schema.json", "{}\n")
            write(
                assurance / "evidence-ledger.json",
                json.dumps({"schema_version": "1.0", "entries": [], "policy": {}}),
            )

            proc = subprocess.run(
                [sys.executable, str(VERIFY), "--root", str(root)],
                capture_output=True,
                text=True,
            )
            self.assertNotEqual(proc.returncode, 0, msg=proc.stdout + proc.stderr)
            status = json.loads((root / "cpas-status.json").read_text(encoding="utf-8"))
            self.assertEqual(status["production_status"]["value"], "BLOCKED")

    def test_invalid_risk_id_fails_grammar_check(self) -> None:
        self.assertTrue(VERIFY.is_file())
        # Live repo should reject non-PROD risk IDs if introduced; smoke the regex path
        import importlib.util

        spec = importlib.util.spec_from_file_location("cpas_verify", VERIFY)
        mod = importlib.util.module_from_spec(spec)
        assert spec.loader is not None
        spec.loader.exec_module(mod)
        self.assertTrue(mod.RISK_ID_RE.fullmatch("PROD-002"))
        self.assertFalse(mod.RISK_ID_RE.fullmatch("RISK-002"))
        self.assertTrue(mod.GATE_ID_RE.fullmatch("GATE-LICENSE-001"))
        self.assertFalse(mod.GATE_ID_RE.fullmatch("LICENSE-001"))


if __name__ == "__main__":
    unittest.main()
