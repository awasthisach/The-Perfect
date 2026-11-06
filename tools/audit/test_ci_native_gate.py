from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]

class NativeGatePermissionsTest(unittest.TestCase):
    def setUp(self):
        self.text = (ROOT / ".github/workflows/weekly-instrumented.yml").read_text()
        self.native = self.text.split("  instrumented-hard:\n", 1)[1].split("  publish-evidence:\n", 1)[0]
        self.publisher = self.text.split("  publish-evidence:\n", 1)[1]

    def test_pr_native_job_keeps_read_only_token(self):
        global_settings = self.text.split("jobs:", 1)[0]
        self.assertIn("permissions:\n  contents: read", global_settings)
        self.assertNotIn("contents: write", global_settings)
        self.assertNotIn("pull-requests: write", global_settings)
        self.assertNotIn("contents: write", self.native)
        self.assertNotIn("git push", self.native)

    def test_all_native_suites_remain_hard(self):
        for task in [":app:connectedDebugAndroidTest", ":core:database:connectedDebugAndroidTest", ":core:database-compat:connectedDebugAndroidTest"]:
            self.assertIn(task, self.native)
        self.assertNotRegex(self.native, r"(?m)^\s*continue-on-error:\s*true")

    def test_publisher_is_separate_and_not_a_pr_job(self):
        self.assertIn("needs: instrumented-hard", self.publisher)
        self.assertIn("github.event_name == 'schedule'", self.publisher)
        self.assertIn("github.event_name == 'workflow_dispatch'", self.publisher)
        self.assertIn("contents: write", self.publisher)
        self.assertIn("pull-requests: write", self.publisher)

    def test_native_evidence_attests_only_db_and_checked_out_sha(self):
        self.assertIn("--db-only", self.publisher)
        self.assertIn('--commit-sha "$(git rev-parse HEAD)"', self.publisher)

    def test_evidence_update_does_not_claim_production_pass(self):
        self.assertNotIn("CPAS gate should PASS after merge", self.publisher)
        self.assertIn("open risks still determine release readiness", self.publisher)

if __name__ == "__main__":
    unittest.main()
