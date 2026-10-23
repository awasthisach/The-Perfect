"""Regression tests for the fail-closed CPAS + ORT release gate."""
from __future__ import annotations

import json
import os
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from io import StringIO
from pathlib import Path
from unittest.mock import patch

from release_gate import main


class ReleaseGateTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.previous_cwd = os.getcwd()
        os.chdir(self.temp_dir.name)
        Path("cpas-status.json").write_text(
            json.dumps({"source_commit": "a" * 40, "production_status": {"value": "PASS"}}), encoding="utf-8"
        )

    def tearDown(self) -> None:
        os.chdir(self.previous_cwd)
        self.temp_dir.cleanup()

    def write_ort(self, status: str = "PASS") -> None:
        Path("ort-status.json").write_text(
            json.dumps({"status": status, "scan_revision": "a" * 40, "reason": "test fixture"}), encoding="utf-8"
        )

    def run_gate(self) -> tuple[int, str]:
        output = StringIO()
        with patch("sys.argv", ["release_gate.py", "--revision", "a" * 40]), redirect_stdout(output), redirect_stderr(output):
            code = main()
        return code, output.getvalue()

    def test_passes_only_when_cpas_and_ort_pass(self) -> None:
        self.write_ort("PASS")
        code, output = self.run_gate()
        self.assertEqual(code, 0, output)
        self.assertIn("CPAS: PASS", output)
        self.assertIn("ORT: PASS", output)
        self.assertIn("RELEASE_GATE: PASS", output)

    def test_wrong_revision_blocks_release(self):
        self.write_ort("PASS")
        Path("cpas-status.json").write_text(json.dumps({"source_commit": "b"*40, "production_status": {"value": "PASS"}}))
        self.assertEqual(1, self.run_gate()[0])

    def test_missing_ort_status_blocks_release(self) -> None:
        code, output = self.run_gate()
        self.assertEqual(code, 1)
        self.assertIn("ORT status unreadable/missing", output)

    def test_nonpassing_ort_status_blocks_release(self) -> None:
        self.write_ort("BLOCKED")
        code, output = self.run_gate()
        self.assertEqual(code, 1)
        self.assertIn("ORT status is 'BLOCKED'", output)

    def test_nonpassing_cpas_status_blocks_release_even_if_ort_passes(self) -> None:
        Path("cpas-status.json").write_text(
            json.dumps({"production_status": {"value": "BLOCKED"}}), encoding="utf-8"
        )
        self.write_ort("PASS")
        code, output = self.run_gate()
        self.assertEqual(code, 1)
        self.assertIn("CPAS production_status is 'BLOCKED'", output)


if __name__ == "__main__":
    unittest.main()
