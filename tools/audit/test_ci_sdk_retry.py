import os
from pathlib import Path
import subprocess
import tempfile
import textwrap
import unittest

ROOT = Path(__file__).resolve().parents[2]

class AndroidSdkRetryTest(unittest.TestCase):
    def _script(self):
        workflow = (ROOT / ".github/workflows/ci.yml").read_text()
        step = workflow.split("      - name: Install required Android SDK components\n", 1)[1].split("\n      - name:", 1)[0]
        if "        run: |\n" in step:
            return textwrap.dedent(step.split("        run: |\n", 1)[1])
        return step.split("        run: ", 1)[1].strip()

    def _run(self, succeed_at):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            command = path / "sdkmanager"
            command.write_text("#!/usr/bin/env python3\nimport os\nfrom pathlib import Path\np=Path(os.environ['SDK_COUNTER'])\nn=int(p.read_text())+1 if p.exists() else 1\np.write_text(str(n))\nthreshold=int(os.environ['SDK_SUCCEED_AT'])\nraise SystemExit(0 if threshold > 0 and n >= threshold else 1)\n")
            command.chmod(0o755)
            counter = path / "calls"
            env = dict(os.environ, PATH=str(path)+os.pathsep+os.environ["PATH"], SDK_COUNTER=str(counter), SDK_SUCCEED_AT=str(succeed_at))
            result = subprocess.run(["bash", "-e", "-c", self._script()], env=env, capture_output=True, text=True, timeout=10)
            return result.returncode, int(counter.read_text())

    def test_immediate_success_runs_once(self):
        self.assertEqual((0, 1), self._run(1))

    def test_transient_failure_succeeds_on_third_attempt(self):
        self.assertEqual((0, 3), self._run(3))

    def test_persistent_failure_remains_hard_failure(self):
        code, calls = self._run(0)
        self.assertNotEqual(0, code)
        self.assertEqual(3, calls)

    def test_required_sdk_packages_are_preserved(self):
        script = self._script()
        for package in ["platforms;android-36", "build-tools;36.0.0", "platform-tools", "emulator", "platforms;android-35", "system-images;android-35;google_apis;x86_64"]:
            self.assertIn(package, script)

if __name__ == "__main__":
    unittest.main()
