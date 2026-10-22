from pathlib import Path
import unittest


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

    def test_ci_and_release_keep_advisor_findings_fail_closed(self):
        for workflow_name in ("ci.yml", "release.yml"):
            workflow = (
                ROOT / ".github" / "workflows" / workflow_name
            ).read_text(encoding="utf-8")
            with self.subTest(workflow=workflow_name):
                self.assertIn("uses: ./.github/actions/ort-ci", workflow)
                self.assertIn("fail-on: 'violations,issues'", workflow)
                self.assertIn("ORT_ADVISOR_EXIT_CODE", workflow)
                self.assertIn('status = "PASS"', workflow)


if __name__ == "__main__":
    unittest.main()
