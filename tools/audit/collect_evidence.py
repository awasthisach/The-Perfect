#!/usr/bin/env python3
"""Collect only successful exact-candidate evidence from authoritative Actions artifacts."""
import argparse
import json
from pathlib import Path
from evidence_contract import SOURCES, gh_json, remote_bundle, verify_entries, CONTROLS
p = argparse.ArgumentParser()
p.add_argument('--revision', required=True)
p.add_argument('--output', type=Path, required=True)
a = p.parse_args()
entries = []
for scope, (workflow, _, _, _) in SOURCES.items():
    runs = gh_json(f'actions/workflows/{workflow}/runs?head_sha={a.revision}&status=success&per_page=100')['workflow_runs']
    for run in runs:
        if run['head_sha'] == a.revision:
            bundle, _ = remote_bundle(str(run['id']), scope, a.revision)
            entries.extend(bundle['entries'])
            break
errors = verify_entries(entries, a.revision, CONTROLS)
if errors:
    raise SystemExit('; '.join(errors))
a.output.write_text(json.dumps({'schema_version': '1.0', 'policy': {'status_values': ['pending', 'verified', 'failed'], 'production_rule': 'Exact candidate, executed control reports and verified Actions provenance are required.'}, 'entries': entries}, indent=2)+'\n')
