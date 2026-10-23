#!/usr/bin/env python3
"""Bundle executed JUnit reports; never infer unrelated controls from job success."""
import argparse
import hashlib
import json
import os
import re
import shutil
from datetime import datetime, timedelta, timezone
from pathlib import Path
from evidence_contract import CONTROLS, SOURCES, report_classes

def main():
    p = argparse.ArgumentParser()
    p.add_argument('--root', type=Path, default=Path.cwd())
    p.add_argument('--commit-sha', default=os.environ.get('GITHUB_SHA', ''))
    p.add_argument('--run-id', default=os.environ.get('GITHUB_RUN_ID', ''))
    p.add_argument('--scope', choices=SOURCES, required=True)
    p.add_argument('--output', type=Path, required=True, help='Directory for bundle and reports')
    a = p.parse_args()
    if not re.fullmatch('[0-9a-f]{40}', a.commit_sha) or not a.run_id.isdigit():
        raise SystemExit('exact SHA and numeric Actions run ID required')
    if a.output.exists():
        raise SystemExit('output directory must be fresh')
    a.output.mkdir(parents=True)
    paths = list(a.root.glob('**/build/test-results/testDebugUnitTest/TEST-*.xml')) if a.scope == 'unit' else list(a.root.glob('core/database/build/outputs/androidTest-results/connected/**/*.xml'))
    available = []
    for path in paths:
        raw = path.read_bytes()
        available.append((path, report_classes(raw)))
    entries = []
    now = datetime.now(timezone.utc).replace(microsecond=0)
    for control, (test, classes) in CONTROLS.items():
        if (control == 'DB-INV-001') != (a.scope == 'database'):
            continue
        selected = [(path, found) for path, found in available if set(classes) & found]
        if not set(classes).issubset(set().union(*(found for _, found in selected))):
            raise SystemExit(f'missing executed tests for {control}')
        reports = []
        for path, _ in selected:
            raw = path.read_bytes(); digest = hashlib.sha256(raw).hexdigest()
            name = f'reports/{digest}.xml'
            (a.output/'reports').mkdir(exist_ok=True)
            (a.output/name).write_bytes(raw)
            reports.append({'path': name, 'sha256': digest})
        entries.append({'id': f'EVID-{list(CONTROLS).index(control)+1:03d}', 'control_id': control, 'test_id': test,
            'source_commit': a.commit_sha, 'ci_run_id': a.run_id, 'ci_job_id': SOURCES[a.scope][1],
            'executed_at': now.isoformat().replace('+00:00','Z'),
            'expires_at': (now+timedelta(days=14 if a.scope == 'database' else 30)).isoformat().replace('+00:00','Z'),
            'result': 'PASS', 'environment': 'github-actions-emulator' if a.scope == 'database' else 'github-actions-unit',
            'scope': a.scope, 'artifact': SOURCES[a.scope][3], 'reports': reports})
    (a.output/'bundle.json').write_text(json.dumps({'entries': entries}, indent=2)+'\n')
    print(f'Bundled {len(entries)} controls backed by executed reports')
if __name__ == '__main__':
    main()
