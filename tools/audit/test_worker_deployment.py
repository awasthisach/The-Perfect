"""Execute the deployment guard against real TOML, without deploying anything."""
import os
from pathlib import Path
import subprocess
import tempfile
import textwrap
import unittest

ROOT = Path(__file__).resolve().parents[2]

class WorkerDeploymentTest(unittest.TestCase):
    def test_guard_reads_configuration_and_rejects_mismatched_confirmation(self):
        workflow = (ROOT / '.github/workflows/deploy-embedding-worker.yml').read_text()
        step = workflow.split('      - name: Guard production embedding enablement\n')[1].split('      - name:')[0]
        script = textwrap.dedent(step.split('        run: |\n')[1])
        for flag, confirmation, expected in [('false','no',0), ('true','yes',0), ('false','yes',1), ('true','no',1), ('invalid','no',1)]:
            with self.subTest(flag=flag, confirmation=confirmation), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                config = root / 'cloudflare/drive-semantic-embed/wrangler.toml'
                config.parent.mkdir(parents=True)
                config.write_text(f'[vars]\nGEMINI_PRODUCTION_APPROVED = "{flag}"\n')
                output = root / 'github-env'
                result = subprocess.run(['bash','-c',script], cwd=root, env={**os.environ, 'GITHUB_ENV':str(output), 'CONFIRM_GEMINI_DATA_HANDLING':confirmation}, capture_output=True, text=True)
                self.assertEqual(expected, result.returncode, result.stderr)
                if expected == 0:
                    self.assertEqual(f'GEMINI_APPROVED={flag}\n', output.read_text())
        smoke = workflow.split('      - name: Verify deployed health contract')[1]
        self.assertNotIn('github.event.inputs.confirm_gemini_data_handling', smoke)
