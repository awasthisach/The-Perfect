from __future__ import annotations

import json
import re
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
VERIFY = ROOT / "tools" / "audit" / "cpas_verify.py"
ASSURANCE_FILES = [
    "00-cpas-constitution.md",
    "03-evidence-policy.md",
    "04-technology-registry.yaml",
    "05-security-reliability-invariants.yaml",
    "06-requirements-traceability.yaml",
    "07-test-matrix.yaml",
    "08-risk-register.yaml",
    "09-gate-catalog.yaml",
    "10-remediation-policy.md",
    "evidence-ledger.schema.json",
    "evidence-ledger.json",
    "FINAL_CPAS_SHEET.md",
]


class CpasVerifierTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        assurance = self.root / "docs" / "assurance"
        assurance.mkdir(parents=True)
        for name in ASSURANCE_FILES:
            shutil.copy(ROOT / "docs" / "assurance" / name, assurance / name)
        for path in [
            "core/data/src/main", "core/data/src/test", "core/database/src/main",
            "core/database/src/androidTest", "core/domain/src/main", "core/domain/src/test",
            "core/cloud-gdrive/src/main", "core/cloud-gdrive/src/test", "core/background/src/main",
            "core/background/src/test",
        ]:
            (self.root / path).mkdir(parents=True)

    def tearDown(self) -> None:
        self.temp.cleanup()

    def run_verifier(self) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [sys.executable, str(VERIFY), "--root", str(self.root)],
            text=True,
            capture_output=True,
            check=False,
        )

    def test_empty_ledger_blocks(self) -> None:
        result = self.run_verifier()
        self.assertEqual(result.returncode, 1)
        self.assertIn("BLOCKED", result.stdout)
        status = json.loads((self.root / "cpas-status.json").read_text())
        self.assertEqual(status["production_status"]["value"], "BLOCKED")

    def test_unknown_traceability_control_blocks(self) -> None:
        path = self.root / "docs" / "assurance" / "06-requirements-traceability.yaml"
        path.write_text(path.read_text().replace("[STORAGE-INV-001]", "[FAKE-INV-999]"))
        result = self.run_verifier()
        self.assertEqual(result.returncode, 1)
        self.assertIn("unknown controls", result.stdout)

    def test_malformed_evidence_blocks(self) -> None:
        path = self.root / "docs" / "assurance" / "evidence-ledger.json"
        path.write_text(json.dumps({"schema_version": "1.0", "entries": [{"id": "bad"}], "policy": {}}))
        result = self.run_verifier()
        self.assertEqual(result.returncode, 1)
        self.assertIn("ledger contract", result.stdout)

    def test_valid_synthetic_evidence_passes_structural_checks(self) -> None:
        ledger = {
            "schema_version": "1.0",
            "entries": [{
                "id": "EVID-001",
                "control_id": "STORAGE-INV-001",
                "test_id": "STORAGE-TEST-001",
                "source_commit": "a" * 40,
                "ci_run_id": "local-test",
                "ci_job_id": "cpas-verify",
                "executed_at": "2099-01-01T00:00:00Z",
                "result": "PASS",
                "environment": "synthetic-fixture",
                "expires_at": "2099-02-01T00:00:00Z",
            }],
            "policy": {
                "status_values": ["pending", "verified", "failed"],
                "production_rule": "synthetic test only",
            },
        }
        (self.root / "docs" / "assurance" / "evidence-ledger.json").write_text(json.dumps(ledger))
        risk = self.root / "docs" / "assurance" / "08-risk-register.yaml"
        # Close every finding status so the synthetic fixture isolates ledger/schema checks.
        # Statuses evolve (triaged, repair_implemented, tested, ...); the test must not
        # depend on a single lifecycle string.
        risk_text = risk.read_text()
        risk_text = re.sub(r"(?m)^(\s*status:\s*)\S+", r"\1closed", risk_text)
        risk.write_text(risk_text)
        result = self.run_verifier()
        self.assertEqual(result.returncode, 0, result.stdout)
        status = json.loads((self.root / "cpas-status.json").read_text())
        self.assertEqual(status["production_status"]["value"], "PASS")


if __name__ == "__main__":
    unittest.main()
