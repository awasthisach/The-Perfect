from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]


class RepositoryWorkflowSafetyTest(unittest.TestCase):
    def test_ort_runs_only_on_schedule_or_manual_candidate_dispatch(self):
        workflow = (ROOT / ".github/workflows/nightly-ort.yml").read_text(encoding="utf-8")
        self.assertIn("  schedule:", workflow)
        self.assertIn("  workflow_dispatch:", workflow)
        self.assertNotRegex(workflow, r"(?m)^  (?:push|pull_request):")
        self.assertIn("timeout-minutes: 90", workflow)
        self.assertIn("ref: ${{ github.sha }}", workflow)

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
