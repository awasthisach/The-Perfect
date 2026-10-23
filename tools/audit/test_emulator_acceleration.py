"""Run the actual device-access script with a private fixture path and sudo stub."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]

class EmulatorAccelerationTest(unittest.TestCase):
    def test_missing_device_failed_grant_and_runner_only_grant(self):
        original = (ROOT / 'tools/audit/emulator_acceleration.sh').read_text()
        for present, grant, expected in [(False, True, 'off'), (True, False, 'off'), (True, True, 'on')]:
            with self.subTest(present=present, grant=grant), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                device = root / 'kvm'
                if present:
                    device.touch()
                log = root / 'sudo.log'
                stub = root / 'sudo'
                stub.write_text('#!/bin/sh\nprintf "%s\\n" "$*" >> "$TEST_SUDO_LOG"\nexit "$TEST_SUDO_EXIT"\n')
                stub.chmod(0o700)
                # Only the fixed device location changes. No real device or sudo is used.
                script = original.replace('/dev/kvm', str(device))
                result = subprocess.run(['bash', '-c', script], capture_output=True, text=True,
                    env={**os.environ, 'PATH':str(root)+':'+os.environ['PATH'],
                         'TEST_SUDO_LOG':str(log), 'TEST_SUDO_EXIT':'0' if grant else '1'})
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertEqual(expected, result.stdout.strip())
                calls = log.read_text() if log.exists() else ''
                if not present:
                    self.assertEqual('', calls)
                elif grant:
                    self.assertIn(f'-n chown {os.getuid()}:{os.getgid()} {device}', calls)
                    self.assertIn(f'-n chmod 600 {device}', calls)
                    self.assertNotIn('666', calls)
                else:
                    self.assertNotIn('chmod', calls)

    def test_ci_uses_selected_mode_without_weakening_native_gate(self):
        workflow = (ROOT / '.github/workflows/ci.yml').read_text()
        self.assertIn('acceleration="$(bash tools/audit/emulator_acceleration.sh)"', workflow)
        self.assertIn('-accel "$acceleration"', workflow)
        self.assertIn("if: ${{ steps.emulator.outputs.booted == 'true' }}", workflow)
