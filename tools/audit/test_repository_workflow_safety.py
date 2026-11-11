from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]


class RepositoryWorkflowSafetyTest(unittest.TestCase):
    def test_ci_does_not_cancel_in_flight_candidate_builds(self):
        workflow = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")
        self.assertIn("group: ci-${{ github.workflow }}-${{ github.ref }}", workflow)
        self.assertIn("cancel-in-progress: false", workflow)

    def test_sqlcipher_fixture_installs_only_missing_dependencies_with_bounded_retries(self):
        workflow = (ROOT / ".github/workflows/ci.yml").read_text(encoding="utf-8")
        fixture_step = workflow.split(
            "- name: Generate SQLCipher 4.5.4 compatibility fixture", 1
        )[1].split("- name:", 1)[0]
        self.assertIn("missing_packages=()", fixture_step)
        self.assertIn("All SQLCipher build dependencies are already available; skipping apt.", fixture_step)
        self.assertIn("for attempt in 1 2 3", fixture_step)
        self.assertIn("Acquire::Retries=1", fixture_step)
        self.assertIn("Acquire::http::Timeout=15", fixture_step)
        self.assertIn("Acquire::https::Timeout=15", fixture_step)
        self.assertIn('if [ "$dependencies_ready" != true ]; then', fixture_step)
        self.assertIn('echo "::error::Unable to install required SQLCipher build dependencies after 3 attempts."', fixture_step)
        self.assertIn("command -v cc", fixture_step)
        self.assertIn("command -v tclsh", fixture_step)
        self.assertIn("missing_packages+=(tcl)", fixture_step)
        self.assertNotIn("tcl-dev", fixture_step)
        self.assertNotIn("/usr/include/tcl", fixture_step)
        self.assertIn("test -f /usr/include/openssl/evp.h", fixture_step)
        self.assertIn("test -f /usr/include/zlib.h", fixture_step)
        self.assertIn("curl -fsSL https://github.com/sqlcipher/sqlcipher/archive/refs/tags/v4.5.4.tar.gz", fixture_step)

    def test_ort_runs_nightly_and_for_exact_candidate_pull_request_heads(self):
        workflow = (ROOT / ".github/workflows/nightly-ort.yml").read_text(encoding="utf-8")
        self.assertIn("  schedule:", workflow)
        self.assertIn("  pull_request:", workflow)
        self.assertIn("branches: [main]", workflow)
        self.assertIn("types: [opened, synchronize, reopened, ready_for_review]", workflow)
        self.assertIn("  workflow_dispatch:", workflow)
        self.assertNotRegex(workflow, r"(?m)^  push:")
        self.assertIn("timeout-minutes: 360", workflow)
        self.assertIn("ref: ${{ github.event.pull_request.head.sha || github.sha }}", workflow)
        self.assertIn("sw-version: ${{ github.event.pull_request.head.sha || github.sha }}", workflow)

    def test_ort_manual_dispatch_checks_selected_ref_checkout_and_scan_revision(self):
        workflow = (ROOT / ".github/workflows/nightly-ort.yml").read_text(encoding="utf-8")
        self.assertIn("ref: ${{ github.event.pull_request.head.sha || github.sha }}", workflow)
        self.assertIn('actual_sha="$(git rev-parse HEAD)"', workflow)
        self.assertIn('CHECKOUT_SHA: ${{ steps.verify_checkout.outputs.checkout_sha }}', workflow)
        self.assertIn('"checkout_revision": checkout_sha', workflow)
        self.assertIn('"scan_revision": scan_sha', workflow)
        self.assertIn('"workflow_run_id": int(os.environ["WORKFLOW_RUN_ID"])', workflow)

    def test_ort_excludes_only_test_scopes_from_source_scanning(self):
        project_config = (ROOT / ".ort.yml").read_text(encoding="utf-8")
        self.assertIn('pattern: "(?i).*test.*"', project_config)
        self.assertIn('reason: "TEST_DEPENDENCY_OF"', project_config)
        self.assertIn("Production compile/runtime scopes remain included", project_config)

    def test_ort_scanner_skips_excluded_scopes_without_dropping_analyzer_metadata(self):
        workflow = (ROOT / ".github/workflows/nightly-ort.yml").read_text(encoding="utf-8")
        self.assertIn("ort-cli-scan-args: -P ort.scanner.skipExcluded=true", workflow)
        self.assertIn("Retain excluded test dependencies in analyzer/SBOM metadata", workflow)

    def test_package_curations_are_installed_in_analyzer_config_directory(self):
        workflow = (ROOT / ".github/workflows/nightly-ort.yml").read_text(encoding="utf-8")
        self.assertIn("Prepare pinned ORT config with project package curations", workflow)
        self.assertIn('git -C "$config_dir" fetch --depth 1 origin 6cb99721f7e536361ca2b20d8bcdec9c3125236b', workflow)
        self.assertIn('install -m 0644 ort-curations.yml "$config_dir/curations.yml"', workflow)
        self.assertNotIn("ort-cli-analyze-args:", workflow)
        self.assertNotIn("--package-curations-dir", workflow)
        self.assertIn("ort-cli-evaluate-args:", workflow)
        self.assertIn("--package-curations-file", workflow)

    def test_ort_curates_every_unresolvable_vcs_revision_from_candidate_scan(self):
        curations = (ROOT / "ort-curations.yml").read_text(encoding="utf-8")
        for package_id in (
            "Maven:commons-logging:commons-logging:1.2",
            "Maven:org.apache.httpcomponents:httpclient:4.5.6",
            "Maven:org.apache.httpcomponents:httpcore:4.4.16",
            "Maven:org.apache.httpcomponents:httpmime:4.5.6",
        ):
            with self.subTest(package_id=package_id):
                self.assertIn(f'- id: "{package_id}"', curations)
                package_block = curations.split(f'- id: "{package_id}"', 1)[1].split("\n- id:", 1)[0]
                self.assertIn("source_artifact:", package_block)
                self.assertIn("source_code_origins: [ARTIFACT]", package_block)

    def test_cpas_selects_exact_sha_and_fails_closed_on_bad_ort_provenance(self):
        workflow = (ROOT / ".github/workflows/cpas-gate.yml").read_text(encoding="utf-8")
        self.assertIn("tools/audit/select_ort_run.py --candidate-sha", workflow)
        self.assertIn("timeout-minutes: 350", workflow)
        self.assertIn('elif [ "$attempt" -ge 15 ]; then', workflow)
        self.assertIn("after 15 minutes", workflow)
        self.assertIn('"$run_conclusion" != "success"', workflow)
        self.assertIn('status.get("workflow_run_id")', workflow)
        self.assertIn('"checkout_revision": expected_sha', workflow)
        self.assertIn('"scan_revision": expected_sha', workflow)
        self.assertIn("for field, value in expected.items():", workflow)
        self.assertIn("Enforce fail-closed result after preserving status artifact", workflow)
        self.assertIn('if [ "$ORT_STAGE_OUTCOME" != "success" ]; then', workflow)

    def test_branch_cleanup_workflow_is_read_only_and_never_deletes(self):
        workflow = (ROOT / ".github/workflows/cleanup-stale-branches.yml").read_text(encoding="utf-8")
        self.assertIn("contents: read", workflow)
        self.assertNotIn("contents: write", workflow)
        self.assertNotIn("--method DELETE", workflow)
        self.assertIn("no branches were deleted", workflow.lower())

    def test_release_sbom_action_is_pinned_to_an_exact_commit(self):
        workflow = (ROOT / ".github/workflows/release.yml").read_text(encoding="utf-8")
        self.assertIn("anchore/sbom-action@2ed2d2006fea5d582c257dbad4532b1e4cbc8b83", workflow)
        self.assertNotIn("anchore/sbom-action@v0", workflow)


if __name__ == "__main__":
    unittest.main()
