#!/usr/bin/env python3
"""Release preflight: actual jobs/steps for the exact SHA, never status-name inference."""
import argparse
import re
from evidence_contract import gh_json, latest_candidate_run

REQUIRED = {
    'nightly-ort.yml': ('ORT Analyzer, Scanner, Evaluator, Advisor & SBOM (fail-closed)', ['Run ORT compliance pipeline', 'Verify required ORT reports and SBOMs', 'Record ORT status']),
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
        try:
            run = latest_candidate_run(workflow, a.revision)
            jobs = gh_json(f'actions/runs/{run["id"]}/jobs?per_page=100')['jobs']
        except (ValueError, KeyError, TypeError):
            raise SystemExit(f'BLOCKED: no valid {workflow} evidence for exact candidate')
        if not validate_run(run, jobs, a.revision, workflow, job, steps):
            raise SystemExit(f'BLOCKED: latest {workflow} run did not complete every required check')
        print(f'{workflow}: verified latest run {run["id"]} for {a.revision}')
if __name__ == '__main__': main()
