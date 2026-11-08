from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]


class RepositoryWorkflowSafetyTest(unittest.TestCase):
    def test_ort_runs_only_on_schedule_or_manual_candidate_dispatch(self):
        workflow = (ROOT / ".github/workflows/nightly-ort.yml").read_text(encoding="utf-8")
        self.assertIn("  schedule:", workflow)
        self.assertIn("  workflow_dispatch:", workflow)
        self.assertNotRegex(workflow, r"(?m)^  (?:push|pull_request):")
        self.assertIn("timeout-minutes: 300", workflow)
        self.assertIn("ref: ${{ github.sha }}", workflow)

    def test_ort_manual_dispatch_checks_selected_ref_checkout_and_scan_revision(self):
        workflow = (ROOT / ".github/workflows/nightly-ort.yml").read_text(encoding="utf-8")
        self.assertIn("ref: ${{ github.sha }}", workflow)
        self.assertIn('actual_sha="$(git rev-parse HEAD)"', workflow)
        self.assertIn('CHECKOUT_SHA: ${{ steps.verify_checkout.outputs.checkout_sha }}', workflow)
        self.assertIn('"checkout_revision": checkout_sha', workflow)
        self.assertIn('"scan_revision": scan_sha', workflow)
        self.assertIn('"workflow_run_id": int(os.environ["WORKFLOW_RUN_ID"])', workflow)

    def test_package_curations_use_supported_analyzer_directory_option(self):
        workflow = (ROOT / ".github/workflows/nightly-ort.yml").read_text(encoding="utf-8")
        self.assertIn("Prepare project package curations", workflow)
        self.assertIn("cp ort-curations.yml .ort-project-curations/curations.yml", workflow)
        self.assertIn("ort-cli-analyze-args:", workflow)
        self.assertIn("--package-curations-dir", workflow)
        self.assertNotIn("--package-curations-file", workflow.split("ort-cli-analyze-args:", 1)[1].split("ort-cli-evaluate-args:", 1)[0])
        self.assertIn("ort-cli-evaluate-args:", workflow)
        self.assertIn("--package-curations-file", workflow)

    def test_cpas_selects_exact_sha_and_fails_closed_on_bad_ort_provenance(self):
        workflow = (ROOT / ".github/workflows/cpas-gate.yml").read_text(encoding="utf-8")
        self.assertIn("tools/audit/select_ort_run.py --candidate-sha", workflow)
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
