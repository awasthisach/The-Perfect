#!/usr/bin/env python3
"""Regression tests for exact-SHA ORT workflow-run selection."""
from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("select_ort_run.py")
SPEC = importlib.util.spec_from_file_location("select_ort_run", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)

CANDIDATE = "a" * 40
OTHER = "b" * 40


class SelectOrtRunTests(unittest.TestCase):
    def test_manual_dispatch_selected_ref_head_sha_is_selected(self):
        payload = [{"workflow_runs": [{
            "id": 101, "event": "workflow_dispatch", "head_sha": CANDIDATE,
            "status": "completed", "conclusion": "success", "created_at": "2026-11-06T01:00:00Z"
        }]}]
        selected = MODULE.select_exact_candidate_run(payload, CANDIDATE)
        self.assertEqual(101, selected["id"])
        self.assertTrue(MODULE.is_completed_success(selected))

    def test_pr_synthetic_sha_can_match_exact_associated_head_sha(self):
        payload = [{"workflow_runs": [{
            "id": 102, "head_sha": OTHER, "event": "pull_request",
            "pull_requests": [{"head": {"sha": CANDIDATE}}],
            "created_at": "2026-11-06T02:00:00Z"
        }]}]
        self.assertEqual(102, MODULE.select_exact_candidate_run(payload, CANDIDATE)["id"])

    def test_wrong_sha_is_never_selected(self):
        payload = [{"workflow_runs": [{
            "id": 103, "head_sha": OTHER, "pull_requests": [],
            "created_at": "2026-11-06T03:00:00Z"
        }]}]
        self.assertIsNone(MODULE.select_exact_candidate_run(payload, CANDIDATE))

    def test_newest_exact_sha_run_wins_without_accepting_other_sha(self):
        payload = [
            {"workflow_runs": [
                {"id": 104, "head_sha": CANDIDATE, "created_at": "2026-11-06T01:00:00Z"},
                {"id": 105, "head_sha": OTHER, "created_at": "2026-11-06T04:00:00Z"},
            ]},
            {"workflow_runs": [
                {"id": 106, "head_sha": CANDIDATE, "created_at": "2026-11-06T02:00:00Z"},
            ]},
        ]
        self.assertEqual(106, MODULE.select_exact_candidate_run(payload, CANDIDATE)["id"])

    def test_failed_or_incomplete_exact_sha_run_is_not_acceptable_evidence(self):
        failed = {"head_sha": CANDIDATE, "status": "completed", "conclusion": "failure"}
        incomplete = {"head_sha": CANDIDATE, "status": "in_progress", "conclusion": None}
        self.assertFalse(MODULE.is_completed_success(failed))
        self.assertFalse(MODULE.is_completed_success(incomplete))

    def test_invalid_or_short_candidate_sha_is_rejected(self):
        with self.assertRaises(ValueError):
            MODULE.select_exact_candidate_run([], "abc")


if __name__ == "__main__":
    unittest.main()
