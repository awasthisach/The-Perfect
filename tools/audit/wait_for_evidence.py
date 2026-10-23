#!/usr/bin/env python3
"""Wait for exact-candidate evidence producers; failures and timeouts stay blocked."""
import argparse
import re
import time
from evidence_contract import SOURCES, latest_candidate_run, workflow_path_matches


def evidence_ready(revision):
    ready = True
    for workflow, *_ in SOURCES.values():
        try:
            run = latest_candidate_run(workflow, revision)
        except ValueError:
            ready = False
            continue
        if not workflow_path_matches(run.get('path'), workflow):
            raise ValueError(f'{workflow}: unexpected workflow path')
        if run.get('status') == 'completed':
            if run.get('conclusion') != 'success':
                raise ValueError(f'{workflow}: latest candidate run did not succeed')
        else:
            ready = False
    return ready


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--revision', required=True)
    parser.add_argument('--timeout-seconds', type=int, default=2700)
    args = parser.parse_args()
    if not re.fullmatch('[0-9a-f]{40}', args.revision) or args.timeout_seconds < 0:
        raise SystemExit('Invalid candidate or timeout')
    deadline = time.monotonic() + args.timeout_seconds
    while True:
        try:
            if evidence_ready(args.revision):
                return
        except ValueError as error:
            raise SystemExit(f'BLOCKED: {error}')
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise SystemExit('BLOCKED: required candidate workflows missing or still running')
        print('Waiting for exact-candidate unit and native evidence', flush=True)
        time.sleep(min(30, remaining))


if __name__ == '__main__':
    main()
