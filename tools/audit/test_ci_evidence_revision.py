import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]

class CIEvidenceRevisionTest(unittest.TestCase):
    def test_both_evidence_commands_bind_to_actual_checkout(self):
        lines = [line for line in (ROOT / ".github/workflows/ci.yml").read_text().splitlines() if "generate_evidence.py" in line]
        self.assertEqual(2, len(lines))
        for line in lines:
            self.assertIn('--commit-sha "$(git rev-parse HEAD)"', line)

    def test_event_merge_sha_is_not_reported_as_tested_checkout(self):
        text = (ROOT / ".github/workflows/ci.yml").read_text()
        self.assertNotIn('--commit-sha "${{ github.sha }}"', text)

    def test_checkout_selection_is_preserved(self):
        text = (ROOT / ".github/workflows/ci.yml").read_text()
        self.assertIn('ref: ${{ github.event.pull_request.head.sha || github.sha }}', text)

    def test_shell_revision_ignores_different_event_sha(self):
        with tempfile.TemporaryDirectory() as directory:
            def git(*args):
                return subprocess.run(["git", *args], cwd=directory, check=True, capture_output=True, text=True, timeout=10).stdout.strip()
            git("init", "-q")
            git("-c", "user.name=Local regression fixture", "-c", "user.email=fixture@example.invalid", "commit", "--allow-empty", "-qm", "local fixture")
            actual = git("rev-parse", "HEAD")
            env = dict(os.environ, GITHUB_SHA="f" * 40)
            reported = subprocess.run(["bash", "-c", 'printf "%s" "$(git rev-parse HEAD)"'], cwd=directory, env=env, check=True, capture_output=True, text=True, timeout=10).stdout
            self.assertEqual(actual, reported)
            self.assertNotEqual(env["GITHUB_SHA"], reported)

if __name__ == "__main__":
    unittest.main()
