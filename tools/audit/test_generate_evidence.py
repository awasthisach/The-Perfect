from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from generate_evidence import main, merge_entries


class EvidenceLedgerMergeTest(unittest.TestCase):
    def test_preserves_distinct_tests_for_same_control_and_replaces_same_test(self):
        existing = [
            {
                "id": "EVID-001",
                "control_id": "DB-INV-001",
                "test_id": "DB-TEST-001",
                "ci_run_id": "33790505509",
                "result": "PASS",
            },
            {
                "id": "EVID-002",
                "control_id": "RESTORE-INV-001",
                "test_id": "RESTORE-TEST-001",
                "ci_run_id": "33790505509",
                "result": "PASS",
            },
            {
                "id": "EVID-003",
                "control_id": "RESTORE-INV-001",
                "test_id": "RESTORE-TEST-002",
                "ci_run_id": "33790505509",
                "result": "PASS",
            },
        ]
        incoming = [
            {
                "id": "EVID-005",
                "control_id": "DB-INV-001",
                "test_id": "DB-TEST-001",
                "ci_run_id": "40365692135",
                "result": "PASS",
            }
        ]

        merged = merge_entries(existing, incoming)

        self.assertEqual(3, len(merged))
        keys = {(entry["control_id"], entry["test_id"]) for entry in merged}
        self.assertEqual(
            {
                ("DB-INV-001", "DB-TEST-001"),
                ("RESTORE-INV-001", "RESTORE-TEST-001"),
                ("RESTORE-INV-001", "RESTORE-TEST-002"),
            },
            keys,
        )
        db_entry = next(entry for entry in merged if entry["control_id"] == "DB-INV-001")
        self.assertEqual("40365692135", db_entry["ci_run_id"])
        self.assertEqual(["EVID-001", "EVID-002", "EVID-003"], [entry["id"] for entry in merged])

    def test_main_merges_ledger_and_keeps_distinct_tests(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            assurance = root / "docs" / "assurance"
            assurance.mkdir(parents=True)
            ledger_path = assurance / "evidence-ledger.json"
            ledger_path.write_text(
                json.dumps(
                    {
                        "schema_version": "1.0",
                        "entries": [
                            {
                                "id": "EVID-001",
                                "control_id": "RESTORE-INV-001",
                                "test_id": "RESTORE-TEST-001",
                                "ci_run_id": "33790505509",
                                "result": "PASS",
                            },
                            {
                                "id": "EVID-002",
                                "control_id": "RESTORE-INV-001",
                                "test_id": "RESTORE-TEST-002",
                                "ci_run_id": "33790505509",
                                "result": "PASS",
                            },
                        ],
                        "policy": {},
                    }
                ),
                encoding="utf-8",
            )
            argv = [
                "generate_evidence.py",
                "--root", str(root),
                "--commit-sha", "a" * 40,
                "--run-id", "40365692135",
                "--job-id", "test_and_quality",
                "--result", "PASS",
                "--environment", "github-actions-API35-emulator",
                "--include-db",
                "--merge-ledger",
            ]
            with patch.object(sys, "argv", argv):
                self.assertEqual(0, main())

            merged = json.loads(ledger_path.read_text(encoding="utf-8"))["entries"]
            restore_tests = {
                entry["test_id"]
                for entry in merged
                if entry["control_id"] == "RESTORE-INV-001"
            }
            self.assertEqual({"RESTORE-TEST-001", "RESTORE-TEST-002"}, restore_tests)
            self.assertTrue(any(entry["control_id"] == "DB-INV-001" for entry in merged))


if __name__ == "__main__":
    unittest.main()
