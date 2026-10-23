from pathlib import Path
import json
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

    def test_ort_runs_only_in_nightly_workflow_and_release_fails_closed(self):
        ci = (ROOT / ".github" / "workflows" / "ci.yml").read_text(encoding="utf-8")
        release = (ROOT / ".github" / "workflows" / "release.yml").read_text(encoding="utf-8")
        nightly = (ROOT / ".github" / "workflows" / "nightly-ort.yml").read_text(encoding="utf-8")

        # Daily PR CI builds and tests the app, but does not run the expensive scanner.
        self.assertNotIn("uses: ./.github/actions/ort-ci", ci)
        self.assertNotIn("oss-review-toolkit/ort-ci-github-action", ci)
        self.assertIn("Run unit tests", ci)
        self.assertIn("Run Android lint", ci)
        self.assertIn("Assemble debug APK", ci)

        # The nightly workflow runs the vendored, patched ORT action and fails closed.
        self.assertIn("uses: ./.github/actions/ort-ci", nightly)
        self.assertIn("fail-on: 'violations,issues'", nightly)
        # Duplicate YAML mapping keys can make GitHub create a failed workflow run
        # with zero jobs. Guard against recurrence of the duplicate fail-on input.
        self.assertEqual(
            nightly.count("          fail-on:"),
            1,
            "nightly-ort.yml must define the ORT fail-on input exactly once",
        )
        self.assertFalse(
            (ROOT / ".fossa.yml").exists(),
            "The retired FOSSA configuration must stay deleted",
        )
        self.assertNotRegex(ci, r"(?i)fossa|foss-status")
        self.assertNotRegex(release, r"(?i)fossa|foss-status")
        self.assertIn("cache-scan-results", nightly)
        self.assertIn("tools/audit/verify_ort_outputs.py", nightly)
        self.assertIn('status = "PASS" if outcome == "success" and verified == "success" else "BLOCKED"', nightly)
        self.assertIn("ort-results-evidence", nightly)

        # Release must use evidence for the exact commit and cannot run ORT as a soft step.
        self.assertIn("nightly-ort.yml/runs?head_sha=", release)
        self.assertIn('status.get("scan_revision") != os.environ["GITHUB_SHA"]', release)
        self.assertIn("tools/audit/verify_ort_outputs.py", release)
        self.assertIn("release_gate.py", release)
        self.assertNotIn("uses: ./.github/actions/ort-ci", release)

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



    def test_ort_cache_is_versioned_and_restores_legacy_cache(self):
        action = self.action
        nightly = (ROOT / ".github" / "workflows" / "nightly-ort.yml").read_text(encoding="utf-8")
        ci = (ROOT / ".github" / "workflows" / "ci.yml").read_text(encoding="utf-8")

        self.assertIn("id: cache-scan-results", action)
        cache_step = action.split("id: cache-scan-results", 1)[1].split(
            "id: project-sources", 1
        )[0]
        self.assertIn("ort-scan-results-v2-", cache_step)
        self.assertIn("restore-keys: |", cache_step)
        self.assertIn("ort-scan-results-cache", cache_step)
        self.assertLess(
            cache_step.index("ort-scan-results-v2-"),
            cache_step.index("ort-scan-results-cache"),
            "The current commit-scoped cache must be preferred before legacy cache fallback",
        )
        self.assertIn("cache-scan-results", nightly)
        self.assertNotIn("uses: ./.github/actions/ort-ci", ci)


if __name__ == "__main__":
    unittest.main()
