#!/usr/bin/env python3
"""Release preflight: actual jobs/steps for the exact SHA, never status-name inference."""
import argparse
import re
from evidence_contract import gh_json

REQUIRED = {
    'ci.yml': ('Build, Test, Lint & Quality', ['Run unit tests', 'Run Android lint', 'Assemble debug APK']),
    'weekly-instrumented.yml': ('SQLCipher Instrumented (hard)', ['SQLCipher instrumentation (HARD)']),
    'check-embedding-worker.yml': ('worker-build', ['Run Worker authentication regression tests', 'Bundle and validate Worker without deploying', 'Verify production embedding is disabled by default']),
}

def validate_run(run, jobs, revision, workflow, job_name, steps):
    if (run.get('head_sha') != revision or run.get('status') != 'completed' or run.get('conclusion') != 'success'
        or run.get('path') != '.github/workflows/'+workflow):
        return False
    matching = [j for j in jobs if j.get('name') == job_name and j.get('status') == 'completed' and j.get('conclusion') == 'success']
    return len(matching) == 1 and all(any(s.get('name') == required and s.get('status') == 'completed' and s.get('conclusion') == 'success' for s in matching[0].get('steps', [])) for required in steps)

def main():
    p=argparse.ArgumentParser();p.add_argument('--revision',required=True);a=p.parse_args()
    if not re.fullmatch('[0-9a-f]{40}', a.revision): raise SystemExit('Invalid candidate revision')
    for workflow,(job,steps) in REQUIRED.items():
        runs=gh_json(f'actions/workflows/{workflow}/runs?head_sha={a.revision}&per_page=100')['workflow_runs']
        passed=False
        for run in runs:
            if run.get('head_sha') != a.revision or run.get('conclusion') != 'success': continue
            jobs=gh_json(f'actions/runs/{run["id"]}/jobs?per_page=100')['jobs']
            if validate_run(run,jobs,a.revision,workflow,job,steps):
                print(f'{workflow}: verified run {run["id"]} for {a.revision}');passed=True;break
        if not passed: raise SystemExit(f'BLOCKED: no complete successful {workflow} evidence for exact candidate')
if __name__ == '__main__': main()
