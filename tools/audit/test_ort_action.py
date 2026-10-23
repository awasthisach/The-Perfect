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
                "spdxVersion: SPDX-2.3\n", encoding="utf-8"
            )
            self.assertEqual([], validate_outputs(results_dir))

            (results_dir / "bom.spdx.json").write_text(
                '{"spdxVersion": "not-an-spdx-version"}', encoding="utf-8"
            )
            self.assertTrue(
                any("bom.spdx.json: missing valid spdxVersion" in issue
                    for issue in validate_outputs(results_dir))
            )

    def test_codeql_uses_default_not_advanced_workflow(self):
        """Rely on GitHub default Code Scanning; advanced workflow must stay absent.

        GitHub docs: use either the default CodeQL setup OR a single advanced
        workflow — never both. The advanced codeql-analysis.yml was intentionally
        removed to eliminate dual-setup SARIF conflicts.
        """
        workflow_path = ROOT / ".github" / "workflows" / "codeql-analysis.yml"
        self.assertFalse(
            workflow_path.exists(),
            "Advanced codeql-analysis.yml must remain removed to avoid dual CodeQL conflict",
        )

    def test_release_signing_is_conditional_for_generic_assemble_but_fail_closed_for_release_tasks(self):
        gradle = (ROOT / "app" / "build.gradle.kts").read_text(encoding="utf-8")
        self.assertIn("val releaseSigningConfigured = releaseSigningVariables.all", gradle)
        self.assertIn("if (releaseSigningConfigured) {\n      create(\"release\")", gradle)
        self.assertIn("if (releaseSigningConfigured) {\n        signingConfig = signingConfigs.getByName(\"release\")", gradle)
        self.assertIn('Regex(\n    """(?i)^(assemble|bundle|package|validateSigning|sign|publish)([A-Za-z0-9]*Release)([A-Za-z0-9]*)$"""', gradle)
        self.assertIn('"Release builds must never fall back to the Android debug keystore."', gradle)
        self.assertIn('require(file(releaseKeystore).isFile)', gradle)

    def test_ort_runs_only_in_release_workflow_and_fails_closed(self):
        ci = (ROOT / ".github" / "workflows" / "ci.yml").read_text(encoding="utf-8")
        release = (ROOT / ".github" / "workflows" / "release.yml").read_text(encoding="utf-8")

        # Daily PR CI must stay fast: it builds/tests/lints and assembles the debug APK,
        # but must not start the expensive ORT analyzer/scanner.
        self.assertNotIn("uses: ./.github/actions/ort-ci", ci)
        self.assertNotIn("Open Source Compliance (ORT)", ci)
        self.assertIn("Run unit tests", ci)
        self.assertIn("Run Android lint", ci)
        self.assertIn("Assemble debug APK", ci)

        # Release promotion remains gated by ORT, its reports, and explicit exit codes.
        self.assertIn("uses: ./.github/actions/ort-ci", release)
        self.assertIn("fail-on: 'violations,issues'", release)
        self.assertIn("ORT_ADVISOR_EXIT_CODE", release)
        self.assertIn("tools/audit/verify_ort_outputs.py", release)
        self.assertIn('status = "PASS" if outcome == "success" and evaluator == "0" and advisor == "0" else "BLOCKED"', release)
        self.assertIn("needs: open_source_compliance", release)
        self.assertIn("if status != \"PASS\":", release)

    def test_missing_or_invalid_ort_reports_never_validate_as_pass(self):
        with tempfile.TemporaryDirectory() as directory:
            results_dir = Path(directory)

            # No ORT output at all must produce missing-output errors.
            missing = validate_outputs(results_dir)
            self.assertTrue(missing, "An empty ORT results directory must fail closed")
            self.assertTrue(any("analyzer-result.json: missing" in item for item in missing))

            # A present but malformed JSON report must still fail closed.
            (results_dir / "analyzer-result.json").write_text("{broken", encoding="utf-8")
            malformed = validate_outputs(results_dir)
            self.assertTrue(any("analyzer-result.json: invalid JSON" in item for item in malformed))



    def test_fossa_is_not_a_repository_compliance_gate(self):
        """FOSSA is retired in-repo; external GitHub App statuses are owner-managed."""
        workflow_dir = ROOT / ".github" / "workflows"
        workflow_files = list(workflow_dir.glob("*.yml")) + list(workflow_dir.glob("*.yaml"))
        forbidden = ("fossa/fossa-action", "fossa test", "FOSSA_API_KEY", "foss-status.json")
        for workflow in workflow_files:
            content = workflow.read_text(encoding="utf-8")
            for token in forbidden:
                self.assertNotIn(
                    token.lower(),
                    content.lower(),
                    f"Retired FOSSA integration reference {token!r} remains in {workflow.relative_to(ROOT)}",
                )
        self.assertFalse(
            (ROOT / ".fossa.yml").exists(),
            "Retired .fossa.yml must not be restored; ORT is the active compliance gate",
        )


    def test_ort_cache_is_used_only_by_release_workflow(self):
        ci = (ROOT / ".github" / "workflows" / "ci.yml").read_text(encoding="utf-8")
        release = (ROOT / ".github" / "workflows" / "release.yml").read_text(encoding="utf-8")

        self.assertNotIn("ort-scan-results-v2-", ci)
        self.assertNotIn("ort-scan-results-v1-", ci)

        self.assertIn("restore-keys: |", release)
        restore_keys = release.split("restore-keys: |", 1)[1].split("\n\n", 1)[0]
        self.assertIn("ort-scan-results-v2-", restore_keys)
        self.assertIn("ort-scan-results-v1-", restore_keys)
        self.assertLess(
            restore_keys.index("ort-scan-results-v2-"),
            restore_keys.index("ort-scan-results-v1-"),
            "Current dependency-scoped cache must be preferred before legacy fallback",
        )
        self.assertIn(
            "Legacy cache fallback: prior runs saved scanner state under v1.",
            release,
        )


if __name__ == "__main__":
    unittest.main()
