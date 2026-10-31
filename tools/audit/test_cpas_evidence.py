"""Isolated regression tests for CPAS evidence validation."""
import json
import subprocess
import sys
import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path

VERIFY = Path(__file__).resolve().parent / "cpas_verify.py"


class CpasEvidenceTests(unittest.TestCase):
    def run_case(self, **changes):
        now = datetime.now(timezone.utc)

        def stamp(value):
            return value.strftime("%Y-%m-%dT%H:%M:%SZ")

        with tempfile.TemporaryDirectory(prefix="cpas-regression-") as tmp:
            root = Path(tmp)

            def write(relative, text):
                path = root / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(text, encoding="utf-8")

            fixtures = {
                "00-cpas-constitution.md": "# Synthetic fixture\n",
                "03-evidence-policy.md": "# Synthetic fixture\n",
                "10-remediation-policy.md": "# Synthetic fixture\n",
                "FINAL_CPAS_SHEET.md":
                    "\n".join(f"# {i}. Section" for i in range(1, 50)),
                "04-technology-registry.yaml":
                    "fts_module: FTS5\nmin_sdk: 24\n",
                "05-security-reliability-invariants.yaml":
                    "invariants:\n  - id: STORAGE-INV-001\n",
                "06-requirements-traceability.yaml":
                    "requirements:\n  - id: REQ-001\n"
                    "    controls: [STORAGE-INV-001]\n"
                    "    implementation_refs: [core]\n"
                    "    test_refs: [core]\n",
                "07-test-matrix.yaml": "matrix: []\n",
                "08-risk-register.yaml":
                    "open_findings: []\nclosed_findings: []\n",
                "09-gate-catalog.yaml":
                    "gates:\n  - id: GATE-CPAS-001\n",
                "evidence-ledger.schema.json": "{}\n",
                "ort-bom.cyclonedx.json": "{}\n",
                "ort-bom.spdx.json": "{}\n",
                "ort-status.json": '{"status":"PASS"}\n',
            }
            for name, text in fixtures.items():
                write(f"docs/assurance/{name}", text)

            (root / "core").mkdir()
            # Synthetic markers isolate evidence checks, not Android behavior.
            write(
                "app/build.gradle.kts",
                'signingConfigs { create("release") }\n'
                'buildTypes { release { } }\n'
                '// firebase-appcheck-playintegrity\n',
            )
            write(
                "app/src/main/java/com/vvf/smartmanager/VVFApplication.kt",
                "// FirebaseAppCheck PlayIntegrityAppCheckProviderFactory\n",
            )

            entry = {
                "id": "EVID-001",
                "control_id": "STORAGE-INV-001",
                "test_id": "STORAGE-TEST-001",
                "source_commit": "a" * 40,
                "ci_run_id": "123456",
                "ci_job_id": "654321",
                "executed_at": stamp(now - timedelta(hours=1)),
                "expires_at": stamp(now + timedelta(days=7)),
                "result": "PASS",
                "environment": "github-actions-API35-emulator",
            }
            entry.update(changes)
            write(
                "docs/assurance/evidence-ledger.json",
                json.dumps({"policy": {}, "entries": [entry]}),
            )
            proc = subprocess.run(
                [sys.executable, str(VERIFY), "--root", str(root)],
                capture_output=True,
                text=True,
            )
            output = root / "cpas-status.json"
            self.assertTrue(
                output.is_file(), msg=proc.stdout + proc.stderr
            )
            report = json.loads(output.read_text(encoding="utf-8"))
            return proc.returncode, report

    def test_valid_evidence_passes(self):
        code, report = self.run_case()
        self.assertEqual(code, 0, msg=str(report))
        self.assertEqual(report["production_status"]["value"], "PASS")
        self.assertTrue(
            all(c["status"] == "PASS" for c in report["checks"]),
            msg=str(report),
        )

    def assert_only_blocker(self, changes, expected_check):
        code, report = self.run_case(**changes)
        self.assertEqual(code, 1, msg=str(report))
        self.assertEqual(report["production_status"]["value"], "BLOCKED")
        blockers = {
            c["name"] for c in report["checks"]
            if c["status"] != "PASS"
        }
        self.assertEqual(blockers, {expected_check}, msg=str(report))

    def test_failed_evidence_blocks(self):
        self.assert_only_blocker(
            {"result": "FAIL"}, "evidence_results_pass"
        )

    def test_unknown_control_blocks(self):
        self.assert_only_blocker(
            {"control_id": "UNKNOWN-INV-999"}, "evidence_control_refs"
        )


if __name__ == "__main__":
    unittest.main()
