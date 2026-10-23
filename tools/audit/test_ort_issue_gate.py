"""Execute the vendored action's final issue gate; no ORT reports are synthesized."""
import os
from pathlib import Path
import subprocess
import textwrap
import unittest

ROOT = Path(__file__).resolve().parents[2]

class OrtIssueGateTest(unittest.TestCase):
    def test_analyzer_and_advisor_fail_independently(self):
        action = (ROOT / '.github/actions/ort-ci/action.yml').read_text()
        step = action.split('    - name: Fail on Analyzer or Advisor issues\n')[1]
        self.assertIn("if: contains(inputs.fail-on, 'issues')", step)
        script = textwrap.dedent(step.split('      run: |\n')[1])
        for analyzer, advisor, expected in [('0', '0', 0), ('2', '0', 2), ('0', '2', 2), ('2', '2', 2)]:
            with self.subTest(analyzer=analyzer, advisor=advisor):
                result = subprocess.run(['bash', '-eu', '-c', script], capture_output=True,
                    env={**os.environ, 'ANALYZER_EXIT_CODE': analyzer, 'ADVISOR_EXIT_CODE': advisor})
                self.assertEqual(expected, result.returncode)
