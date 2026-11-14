import json
import tempfile
import unittest
from pathlib import Path

from validate_instrumented_reports import EvidenceError, validate_reports

SHA = "a" * 40
RUN_ID = 12345
MODULES = ("app", "core/database", "core/database-compat")


class ValidateInstrumentedReportsTest(unittest.TestCase):
    def create_evidence(self, root: Path, xml: str = '<testsuite><testcase classname="db" name="works"/></testsuite>'):
        (root / "sqlcipher-instrumented-manifest.json").write_text(
            json.dumps({"tested_sha": SHA, "run_id": RUN_ID, "event": "workflow_dispatch"}),
            encoding="utf-8",
        )
        for module in MODULES:
            report = root / module / "build/outputs/androidTest-results/connected/debug/TEST-db.xml"
            report.parent.mkdir(parents=True, exist_ok=True)
            report.write_text(xml, encoding="utf-8")

    def test_accepts_complete_zero_skip_sha_and_run_bound_reports(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.create_evidence(root)
            result = validate_reports(root, SHA, str(RUN_ID))
            self.assertEqual(set(MODULES), set(result))
            self.assertEqual(1, result["core/database"]["tests"])

    def test_rejects_missing_module_report(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.create_evidence(root)
            (root / "core/database-compat").rename(root / "compat-removed")
            with self.assertRaisesRegex(EvidenceError, "core/database-compat"):
                validate_reports(root, SHA)

    def test_rejects_skipped_instrumentation(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.create_evidence(root, '<testsuite><testcase><skipped/></testcase></testsuite>')
            with self.assertRaisesRegex(EvidenceError, "skipped"):
                validate_reports(root, SHA)

    def test_rejects_failure_and_error(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.create_evidence(root, '<testsuite><testcase><failure/></testcase></testsuite>')
            with self.assertRaisesRegex(EvidenceError, "failures=1"):
                validate_reports(root, SHA)

    def test_rejects_wrong_candidate_sha(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.create_evidence(root)
            with self.assertRaisesRegex(EvidenceError, "does not match"):
                validate_reports(root, "b" * 40)

    def test_rejects_wrong_workflow_run_id(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self.create_evidence(root)
            with self.assertRaisesRegex(EvidenceError, "run id"):
                validate_reports(root, SHA, "99999")


if __name__ == "__main__":
    unittest.main()
