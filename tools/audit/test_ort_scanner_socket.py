"""Exercise the scanner shell's Docker arguments; no Docker or ORT scan is run."""
import json
import os
from pathlib import Path
import shlex
import subprocess
import sys
import tempfile
import unittest

ACTION_FILE = Path(__file__).resolve().parents[2] / ".github/actions/ort/action.yml"


def scanner_script():
    lines = ACTION_FILE.read_text().splitlines()
    step = next(i for i, line in enumerate(lines) if line.strip() == "- name: Run ORT Scanner")
    start = next(i for i in range(step, len(lines)) if lines[i].strip() == "run: |") + 1
    script = []
    for line in lines[start:]:
        if line and not line.startswith("        "):
            break
        script.append(line[8:] if line.startswith("        ") else "")
    return "\n".join(script) + "\n"


class OrtScannerSocketTest(unittest.TestCase):
    def docker_arguments(self, spaced_socket=False):
        with tempfile.TemporaryDirectory(prefix="ort-scanner-test-") as tmp:
            root = Path(tmp)
            home = root / "home"
            home.mkdir()
            binary = root / "bin"
            binary.mkdir()
            socket = root / ("ssh agent" if spaced_socket else "ssh-agent") / "agent.sock"
            socket.parent.mkdir()
            socket.touch()
            agent = binary / "ssh-agent"
            assignment = "SSH_AUTH_SOCK=" + shlex.quote(str(socket)) + "; export SSH_AUTH_SOCK;"
            agent.write_text("#!/bin/sh\nprintf '%s\\n' " + shlex.quote(assignment) + "\n")
            agent.chmod(0o755)
            capture = root / "docker-arguments.json"
            docker = binary / "docker"
            docker.write_text(
                "#!" + sys.executable + "\nimport json,os,sys\n"
                "with open(os.environ['DOCKER_CAPTURE_FILE'],'w') as f: json.dump(sys.argv[1:],f)\n"
            )
            docker.chmod(0o755)
            env = {
                "PATH": str(binary) + os.pathsep + os.defpath,
                "HOME": str(home), "USER": "runner", "DOCKER_CAPTURE_FILE": str(capture),
                "ORT_HOME_PATH": ".ort", "ORT_DOCKER_IMAGE": "example.invalid/ort:test",
                "ORT_LOG_LEVEL": "warn", "ORT_JDK_JAVA_OPTIONS": "",
                "ORT_DOCKER_CLI_ARGS": "", "ORT_CLI_ARGS": "", "ORT_CLI_SCAN_ARGS": "",
                "ORT_RESULTS_CURRENT_PATH": str(home / "current.json"),
                "ORT_RESULTS_PATH": str(home / "results"),
                "ORT_RESULTS_SCANNER_PATH": str(home / "results/scan-result.json"),
            }
            result = subprocess.run(["bash", "-c", scanner_script()], env=env, text=True, capture_output=True, timeout=10)
            self.assertEqual(0, result.returncode, result.stderr)
            return json.loads(capture.read_text()), str(socket)

    def test_container_socket_environment_matches_mount_destination(self):
        args, host_socket = self.docker_arguments()
        self.assertIn(host_socket + ":/ssh.socket", args)
        self.assertIn("SSH_AUTH_SOCK=/ssh.socket", args)
        self.assertNotIn("SSH_AUTH_SOCK=" + host_socket, args)
        self.assertIn("type=tmpfs,target=/tmp", args)

    def test_host_socket_with_spaces_is_one_volume_argument(self):
        args, host_socket = self.docker_arguments(spaced_socket=True)
        self.assertIn(host_socket + ":/ssh.socket", args)
        self.assertIn("SSH_AUTH_SOCK=/ssh.socket", args)


if __name__ == "__main__":
    unittest.main()
