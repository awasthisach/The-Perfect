#!/usr/bin/env python3
"""Select the newest ORT workflow run associated with one exact candidate SHA."""
from __future__ import annotations

import argparse
import json
import sys
from typing import Any


def workflow_runs(payload: Any) -> list[dict[str, Any]]:
    """Normalize the array-of-pages returned by gh api --paginate --slurp."""
    pages = payload if isinstance(payload, list) else [payload]
    found: list[dict[str, Any]] = []
    for page in pages:
        if not isinstance(page, dict):
            continue
        runs = page.get("workflow_runs")
        if isinstance(runs, list):
            found.extend(run for run in runs if isinstance(run, dict))
    return found


def belongs_to_candidate(run: dict[str, Any], candidate_sha: str) -> bool:
    if run.get("head_sha") == candidate_sha:
        return True
    pull_requests = run.get("pull_requests")
    if not isinstance(pull_requests, list):
        return False
    return any(
        isinstance(pr, dict)
        and isinstance(pr.get("head"), dict)
        and pr["head"].get("sha") == candidate_sha
        for pr in pull_requests
    )


def select_exact_candidate_run(payload: Any, candidate_sha: str) -> dict[str, Any] | None:
    if len(candidate_sha) != 40 or any(ch not in "0123456789abcdef" for ch in candidate_sha.lower()):
        raise ValueError("candidate SHA must be a full 40-character hexadecimal commit")
    matches = [
        run for run in workflow_runs(payload)
        if belongs_to_candidate(run, candidate_sha)
    ]
    if not matches:
        return None
    # ISO-8601 created_at strings sort chronologically; run id is a stable tie-breaker.
    return max(matches, key=lambda run: (str(run.get("created_at", "")), int(run.get("id", 0))))


def is_completed_success(run: dict[str, Any]) -> bool:
    return run.get("status") == "completed" and run.get("conclusion") == "success"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--candidate-sha", required=True)
    args = parser.parse_args()
    try:
        payload = json.load(sys.stdin)
        selected = select_exact_candidate_run(payload, args.candidate_sha)
    except (json.JSONDecodeError, ValueError) as error:
        print(f"invalid ORT run selection input: {error}", file=sys.stderr)
        return 2
    if selected is None:
        return 0
    print(selected.get("id", ""))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
