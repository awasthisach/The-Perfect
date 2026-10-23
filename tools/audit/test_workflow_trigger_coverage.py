"""Required exact-SHA evidence must be scheduled for every automatic gate event."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2] / '.github/workflows'


def event_block(text, event):
    match = re.search(r'^  '+event+r':\n((?:    .*\n)*)', text, re.M)
    if match is None:
        raise AssertionError(f'Missing {event} trigger')
    return match.group(1)


def branches(block):
    match = re.search(r'branches: \[([^\]]+)\]', block)
    if match:
        return {value.strip().strip('"\'') for value in match.group(1).split(',')}
    if 'branches:' in block:
        raise AssertionError('Update coverage parser for non-inline branch list')
    return None  # no branch restriction


class WorkflowTriggerCoverageTest(unittest.TestCase):
    def test_producers_cover_all_automatic_candidate_events(self):
        gate = (ROOT / 'cpas-gate.yml').read_text()
        for name in ['ci.yml', 'weekly-instrumented.yml', 'check-embedding-worker.yml']:
            producer = (ROOT / name).read_text()
            self.assertIn('  workflow_dispatch:', producer)
            for event in ['push', 'pull_request']:
                with self.subTest(workflow=name, event=event):
                    expected = branches(event_block(gate, event))
                    block = event_block(producer, event)
                    for restriction in ['paths:', 'paths-ignore:', 'branches-ignore:', 'types:']:
                        self.assertNotIn(restriction, block, 'Required exact-candidate evidence cannot be omitted')
                    actual = branches(block)
                    if expected is None:
                        self.assertIsNone(actual)
                    elif actual is not None:
                        self.assertTrue(expected <= actual, f'Missing branches: {expected-actual}')
        self.assertIn('  schedule:', (ROOT / 'weekly-instrumented.yml').read_text())
