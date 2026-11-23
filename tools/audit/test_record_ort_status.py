"""Tests for exact-SHA ORT status recording."""
from __future__ import annotations
import importlib.util
from pathlib import Path
import unittest
SCRIPT = Path(__file__).with_name("record_ort_status.py")
SPEC = importlib.util.spec_from_file_location("record_ort_status", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)
SHA = "0123456789abcdef0123456789abcdef01234567"

def status(**overrides):
    values = {
        "ort_outcome": "success", "report_validation_outcome": "success",
        "expected_sha": SHA, "checkout_sha": SHA, "scan_sha": SHA,
        "cache_save_outcome": "success", "cache_validation_outcome": "success",
        "cache_status": "PASS", "cache_file_count": 25,
        "cache_scan_result_count": 3, "workflow_run_id": 123,
        "workflow_ref": "refs/heads/audit/fixes-2026-11",
    }
    values.update(overrides)
    return MODULE.build_status(**values)

class RecordOrtStatusTests(unittest.TestCase):
    def test_all_required_evidence_passes(self):
        result = status()
        self.assertEqual("PASS", result["status"])
        self.assertEqual([], result["blocking_reasons"])
        self.assertEqual("success", result["cache_save_step_outcome"])
        self.assertEqual("success", result["cache_validation_outcome"])

    def test_cancelled_scan_and_failed_validation_do_not_mislabel_cache_save(self):
        result = status(ort_outcome="cancelled", report_validation_outcome="failure",
                        cache_validation_outcome="failure", cache_status="BLOCKED",
                        cache_file_count=544, cache_scan_result_count=0)
        self.assertEqual("BLOCKED", result["status"])
        self.assertEqual("success", result["cache_save_step_outcome"])
        self.assertEqual("failure", result["cache_validation_outcome"])
        self.assertTrue(any("ORT action outcome is 'cancelled'" in reason for reason in result["blocking_reasons"]))
        self.assertTrue(any("No non-empty" in reason for reason in result["blocking_reasons"]))

    def test_sha_mismatch_blocks(self):
        result = status(scan_sha="f" * 40)
        self.assertEqual("BLOCKED", result["status"])
        self.assertTrue(any("SHA provenance mismatch" in reason for reason in result["blocking_reasons"]))

    def test_cache_save_failure_blocks_even_when_results_exist(self):
        result = status(cache_save_outcome="failure")
        self.assertEqual("BLOCKED", result["status"])
        self.assertTrue(any("cache save outcome" in reason for reason in result["blocking_reasons"]))

    def test_empty_scan_result_inventory_blocks(self):
        result = status(cache_scan_result_count=0)
        self.assertEqual("BLOCKED", result["status"])
        self.assertTrue(any("No non-empty" in reason for reason in result["blocking_reasons"]))

    def test_unknown_outcomes_fail_closed(self):
        result = status(ort_outcome="unknown", cache_status="unknown")
        self.assertEqual("BLOCKED", result["status"])
        self.assertGreaterEqual(len(result["blocking_reasons"]), 2)

if __name__ == "__main__":
    unittest.main()
