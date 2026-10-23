import unittest
from pathlib import Path
from unittest.mock import patch
import wait_for_evidence as gate

SHA = 'a' * 40
class EvidenceWaitTest(unittest.TestCase):
    def test_requires_all_producers_and_rejects_terminal_failure(self):
        unit = dict(id=1, head_sha=SHA, path='.github/workflows/ci.yml@main', status='completed', conclusion='success')
        native = dict(unit, path='.github/workflows/weekly-instrumented.yml', id=2)
        with patch.object(gate, 'latest_candidate_run', side_effect=[unit, native]):
            self.assertTrue(gate.evidence_ready(SHA))
        for missing in [ValueError('not yet scheduled'), dict(native,status='in_progress',conclusion=None)]:
            with patch.object(gate,'latest_candidate_run',side_effect=[unit,missing]):
                self.assertFalse(gate.evidence_ready(SHA))
        for conclusion in ['failure','cancelled','skipped','neutral',None]:
            with patch.object(gate,'latest_candidate_run',side_effect=[unit,dict(native,conclusion=conclusion)]), self.assertRaises(ValueError):
                gate.evidence_ready(SHA)

    def test_timeout_fails_closed_without_claiming_completion(self):
        with patch('sys.argv',['wait','--revision',SHA,'--timeout-seconds','0']), patch.object(gate,'evidence_ready',return_value=False), self.assertRaisesRegex(SystemExit,'missing or still running'):
            gate.main()

    def test_candidate_code_never_runs_from_privileged_completion_event(self):
        workflow = (Path(__file__).resolve().parents[2] / '.github/workflows/cpas-gate.yml').read_text()
        self.assertNotIn('  workflow_run:', workflow)
        self.assertNotIn('  pull_request_target:', workflow)
        self.assertIn('CANDIDATE_SHA: ${{ github.event.pull_request.head.sha || github.sha }}', workflow)
        self.assertIn('python3 tools/audit/wait_for_evidence.py --revision "$CANDIDATE_SHA"', workflow)
