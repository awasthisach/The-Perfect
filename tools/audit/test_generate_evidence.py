#!/usr/bin/env python3
"""Tests that CPAS evidence generation includes only controls exercised by a job."""
from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("generate_evidence.py")
SPEC = importlib.util.spec_from_file_location("generate_evidence", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class EvidenceControlSelectionTests(unittest.TestCase):
    def test_db_only_run_does_not_claim_unit_test_controls(self) -> None:
        self.assertEqual([MODULE.DB_CONTROL], MODULE.selected_controls(True, True))

    def test_full_ci_run_keeps_unit_and_optional_db_controls(self) -> None:
        self.assertEqual(
            MODULE.UNIT_TEST_CONTROLS,
            MODULE.selected_controls(False, False),
        )
        self.assertEqual(
            MODULE.UNIT_TEST_CONTROLS + [MODULE.DB_CONTROL],
            MODULE.selected_controls(True, False),
        )

    def test_db_only_requires_db_evidence_flag(self) -> None:
        with self.assertRaises(ValueError):
            MODULE.selected_controls(False, True)


if __name__ == "__main__":
    unittest.main()
