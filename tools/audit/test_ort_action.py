from pathlib import Path
import tempfile
import unittest

from verify_ort_outputs import RESULT_JSON_FILES, validate_outputs


ROOT = Path(__file__).resolve().parents[2]
ACTION = ROOT / ".github" / "actions" / "ort-ci" / "action.yml"


class OrtActionRegressionTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.action = ACTION.read_text(encoding="utf-8")

    def test_vendored_action_records_the_advisor_exit_code(self):
        self.assertIn(
            "Vendored from oss-review-toolkit/ort-ci-github-action at\n"
            "# 70149d4579e446b0312f8b80fdd8d465a91e4391; local patch fixes Advisor exit-code output.",
            self.action,
        )
        advisor_step = self.action.split("id: ort-advisor", 1)[1].split(
            "id: ort-evaluator", 1
        )[0]
        self.assertIn(
            'echo "exit-code=${ORT_CLI_ADVISE_EXIT_CODE}" >> "$GITHUB_OUTPUT"',
            advisor_step,
        )
        self.assertNotIn(
            'echo "exit-code=${ORT_CLI_EVALUATE_EXIT_CODE}" >> "$GITHUB_OUTPUT"',
            advisor_step,
        )

    def test_html_report_output_uses_its_own_initialized_path(self):
        init_step = self.action.split("id: ort-init", 1)[1].split(
            "id: ort-config", 1
        )[0]
        self.assertIn(
            'export ORT_RESULTS_HTML_REPORT_PATH="${ORT_RESULTS_PATH}/scan-report.html"',
            init_step,
        )
        self.assertIn(
            'echo "results-html-report-path=${ORT_RESULTS_HTML_REPORT_PATH}" >> "$GITHUB_OUTPUT"',
            init_step,
        )
        self.assertNotIn(
            'echo "results-html-report-path=${ORT_RESULTS_SCANNER_PATH}"',
            init_step,
        )

    def test_ort_output_gate_validates_result_json_and_sbom_formats(self):
        with tempfile.TemporaryDirectory() as directory:
            results_dir = Path(directory)
            for name in RESULT_JSON_FILES:
                (results_dir / name).write_text('{"result": true}', encoding="utf-8")
            (results_dir / "bom.cyclonedx.json").write_text(
                '{"bomFormat": "CycloneDX", "components": []}', encoding="utf-8"
            )
            (results_dir / "bom.cyclonedx.xml").write_text(
                '<bom xmlns="http://cyclonedx.org/schema/bom/1.6" version="1"></bom>',
                encoding="utf-8",
            )
            (results_dir / "bom.spdx.json").write_text(
                '{"spdxVersion": "SPDX-2.3", "packages": []}', encoding="utf-8"
            )
            (results_dir / "bom.spdx.yml").write_text(
                "spdxVersion: SPDX-2.3\\n", encoding="utf-8"
            )
            self.assertEqual([], validate_outputs(results_dir))

            (results_dir / "bom.spdx.json").write_text(
                '{"spdxVersion": "not-an-spdx-version"}', encoding="utf-8"
            )
            self.assertTrue(
                any("bom.spdx.json: missing valid spdxVersion" in issue
                    for issue in validate_outputs(results_dir))
            )

    def test_codeql_workflow_is_pinned_and_scans_java_kotlin(self):
        workflow = (
            ROOT / ".github" / "workflows" / "codeql-analysis.yml"
        ).read_text(encoding="utf-8")
        self.assertIn("languages: java-kotlin", workflow)
        self.assertIn("security-events: write", workflow)
        self.assertIn(
            "github/codeql-action/init@6f2ac81f5cd81fe351a197ba0b00d91d03cd2aee",
            workflow,
        )
        self.assertIn(
            "github/codeql-action/analyze@6f2ac81f5cd81fe351a197ba0b00d91d03cd2aee",
            workflow,
        )

    def test_ci_and_release_keep_advisor_findings_fail_closed(self):
        for workflow_name in ("ci.yml", "release.yml"):
            workflow = (
                ROOT / ".github" / "workflows" / workflow_name
            ).read_text(encoding="utf-8")
            with self.subTest(workflow=workflow_name):
                self.assertIn("uses: ./.github/actions/ort-ci", workflow)
                self.assertIn("fail-on: 'violations,issues'", workflow)
                self.assertIn("ORT_ADVISOR_EXIT_CODE", workflow)
                self.assertIn("tools/audit/verify_ort_outputs.py", workflow)
                self.assertIn('status = "PASS"', workflow)


if __name__ == "__main__":
    unittest.main()
