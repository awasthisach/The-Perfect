#!/usr/bin/env python3
"""Stage SHA-bound SQLCipher instrumentation evidence from a successful GitHub run."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import random
import re
import shutil
import stat
import subprocess
import sys
import tempfile
import time
import zipfile
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath
from typing import Any, Callable
from urllib.parse import quote

from validate_instrumented_reports import EvidenceError, validate_reports

SHA_RE = re.compile(r"^[0-9a-f]{40}$")
DIGEST_RE = re.compile(r"^sha256:([0-9a-f]{64})$")
ANSI_ESCAPE_RE = re.compile(r"\x1b\[[0-?]*[ -/]*[@-~]")
ALLOWED_EVENTS = {"pull_request", "push", "workflow_dispatch", "schedule"}
TRANSIENT_MARKERS = ("502", "503", "504", "429", "rate limit", "timed out", "temporarily unavailable")
EXPECTED_JOB = "SQLCipher Instrumented (hard)"
EXPECTED_MODULES = ("app", "core/database", "core/database-compat")
MAX_ARCHIVE_FILES = 10_000
MAX_ARCHIVE_UNCOMPRESSED_BYTES = 100 * 1024 * 1024


class ApiError(RuntimeError):
    def __init__(self, message: str, *, transient: bool):
        super().__init__(message)
        self.transient = transient


class StagingError(RuntimeError):
    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code


def is_transient_api_error(message: str) -> bool:
    lowered = message.lower()
    return any(marker in lowered for marker in TRANSIENT_MARKERS)


def retry_api_call(
    operation: Callable[[], Any],
    *,
    max_attempts: int = 5,
    base_delay_seconds: float = 1.0,
    max_delay_seconds: float = 16.0,
    sleeper: Callable[[float], None] = time.sleep,
    jitter: Callable[[], float] = random.random,
) -> Any:
    """Retry transient API failures with bounded exponential backoff and jitter."""
    for attempt in range(max_attempts):
        try:
            return operation()
        except ApiError as error:
            if not error.transient or attempt + 1 >= max_attempts:
                raise
            delay = min(max_delay_seconds, base_delay_seconds * (2**attempt))
            sleeper(delay * (0.75 + 0.5 * jitter()))
    raise AssertionError("retry loop exited unexpectedly")


def belongs_to_candidate(run: dict[str, Any], candidate_sha: str, event: str) -> bool:
    if run.get("event") != event:
        return False
    if run.get("head_sha") == candidate_sha:
        return True
    if event != "pull_request":
        return False
    pull_requests = run.get("pull_requests")
    return isinstance(pull_requests, list) and any(
        isinstance(pr, dict)
        and isinstance(pr.get("head"), dict)
        and pr["head"].get("sha") == candidate_sha
        for pr in pull_requests
    )


def select_latest_candidate_run(
    runs: list[dict[str, Any]], candidate_sha: str, event: str
) -> dict[str, Any] | None:
    if not SHA_RE.fullmatch(candidate_sha):
        raise ValueError("candidate SHA must be a full lowercase 40-character hexadecimal commit")
    if event not in ALLOWED_EVENTS:
        raise ValueError(f"unsupported workflow event: {event}")
    matches = [run for run in runs if belongs_to_candidate(run, candidate_sha, event)]
    if not matches:
        return None
    return max(matches, key=lambda run: (str(run.get("created_at", "")), int(run.get("id", 0))))


def run_outcome(run: dict[str, Any]) -> tuple[str, str]:
    """Return a precise status and explanation; never infer cancellation from pending state."""
    status = str(run.get("status") or "unknown")
    if status != "completed":
        return "EVIDENCE_PENDING", f"matching run is {status}; no terminal conclusion is available"
    conclusion = str(run.get("conclusion") or "unknown")
    if conclusion == "success":
        return "PASS", "matching instrumented workflow completed successfully"
    return "TEST_FAILED", f"matching instrumented workflow completed with conclusion={conclusion}"


def validate_artifact_metadata(
    artifact: dict[str, Any], *, expected_name: str, expected_run_id: str
) -> str:
    if artifact.get("name") != expected_name:
        raise StagingError("EVIDENCE_ARTIFACT_MISMATCH", "artifact name does not match the expected exact-SHA artifact")
    if artifact.get("expired") is not False:
        raise StagingError("EVIDENCE_ARTIFACT_EXPIRED", "artifact metadata does not confirm the selected artifact is retained")
    try:
        artifact_id = int(artifact["id"])
    except (KeyError, TypeError, ValueError) as exc:
        raise StagingError("EVIDENCE_ARTIFACT_MISMATCH", "artifact metadata has no valid artifact ID") from exc
    if artifact_id <= 0:
        raise StagingError("EVIDENCE_ARTIFACT_MISMATCH", "artifact ID must be positive")
    workflow_run = artifact.get("workflow_run")
    if not isinstance(workflow_run, dict) or workflow_run.get("id") is None:
        raise StagingError("EVIDENCE_RUN_MISMATCH", "artifact metadata does not identify its producer run")
    if str(workflow_run.get("id")) != str(expected_run_id):
        raise StagingError("EVIDENCE_RUN_MISMATCH", "artifact producer run ID does not match selected run")
    digest = artifact.get("digest")
    if not isinstance(digest, str) or not DIGEST_RE.fullmatch(digest):
        raise StagingError("ARTIFACT_DIGEST_MISSING", "GitHub did not provide a valid SHA-256 artifact digest")
    return digest


def verify_archive_digest(archive_path: Path, expected_digest: str) -> None:
    match = DIGEST_RE.fullmatch(expected_digest)
    if not match:
        raise StagingError("ARTIFACT_DIGEST_MISSING", "expected artifact digest is missing or malformed")
    hasher = hashlib.sha256()
    with archive_path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            hasher.update(block)
    if hasher.hexdigest() != match.group(1):
        raise StagingError("ARTIFACT_DIGEST_MISMATCH", "downloaded artifact archive SHA-256 does not match GitHub metadata")


def safe_extract_zip(archive_path: Path, destination: Path) -> None:
    """Extract a bounded ZIP without path traversal, symlinks, duplicate paths, or zip bombs."""
    destination.mkdir(parents=True, exist_ok=True)
    root = destination.resolve()
    seen: set[str] = set()
    total_size = 0
    try:
        with zipfile.ZipFile(archive_path) as archive:
            members = archive.infolist()
            if len(members) > MAX_ARCHIVE_FILES:
                raise StagingError("EVIDENCE_ARCHIVE_INVALID", "artifact archive contains too many entries")
            total_size = sum(member.file_size for member in members)
            if total_size > MAX_ARCHIVE_UNCOMPRESSED_BYTES:
                raise StagingError("EVIDENCE_ARCHIVE_INVALID", "artifact archive exceeds the uncompressed size limit")
            for member in members:
                name = member.filename
                posix_path = PurePosixPath(name)
                if not name or "\\" in name or posix_path.is_absolute() or any(part in {"..", ""} for part in posix_path.parts):
                    raise StagingError("EVIDENCE_ARCHIVE_INVALID", "artifact archive contains an unsafe path")
                normalized = posix_path.as_posix().rstrip("/")
                if normalized in seen:
                    raise StagingError("EVIDENCE_ARCHIVE_INVALID", "artifact archive contains duplicate paths")
                seen.add(normalized)
                mode = member.external_attr >> 16
                if stat.S_ISLNK(mode):
                    raise StagingError("EVIDENCE_ARCHIVE_INVALID", "artifact archive contains a symbolic link")
                target = (root / Path(*posix_path.parts)).resolve()
                if target != root and root not in target.parents:
                    raise StagingError("EVIDENCE_ARCHIVE_INVALID", "artifact path escapes the extraction directory")
                if member.is_dir():
                    target.mkdir(parents=True, exist_ok=True)
                    continue
                target.parent.mkdir(parents=True, exist_ok=True)
                with archive.open(member) as source, target.open("xb") as output:
                    shutil.copyfileobj(source, output)
    except StagingError:
        raise
    except (OSError, zipfile.BadZipFile, RuntimeError) as exc:
        raise StagingError("EVIDENCE_ARCHIVE_INVALID", f"artifact ZIP could not be safely extracted: {exc}") from exc


def _gh_json(endpoint: str) -> Any:
    env = os.environ.copy()
    env["NO_COLOR"] = "1"
    env.pop("CLICOLOR_FORCE", None)
    env.pop("FORCE_COLOR", None)
    env.pop("GH_FORCE_TTY", None)
    completed = subprocess.run(["gh", "api", endpoint], capture_output=True, text=True, check=False, env=env)
    if completed.returncode != 0:
        detail = ANSI_ESCAPE_RE.sub("", completed.stderr or completed.stdout or "GitHub API command failed").strip()
        raise ApiError(detail[:500], transient=is_transient_api_error(detail))
    try:
        return json.loads(ANSI_ESCAPE_RE.sub("", completed.stdout).strip())
    except json.JSONDecodeError as exc:
        raise ApiError("GitHub API returned invalid JSON", transient=False) from exc


def _gh_download_artifact(repo: str, artifact_id: int, archive_path: Path) -> None:
    endpoint = f"repos/{repo}/actions/artifacts/{artifact_id}/zip"
    with archive_path.open("wb") as output:
        completed = subprocess.run(["gh", "api", endpoint], stdout=output, stderr=subprocess.PIPE, check=False)
    if completed.returncode != 0:
        raw_detail = (completed.stderr or b"GitHub artifact download failed").decode("utf-8", errors="replace")
        detail = ANSI_ESCAPE_RE.sub("", raw_detail).strip()
        raise ApiError(detail[:500], transient=is_transient_api_error(detail))


def _list_candidate_runs(repo: str, event: str) -> list[dict[str, Any]]:
    found: list[dict[str, Any]] = []
    page = 1
    while page <= 20:
        endpoint = (
            f"repos/{repo}/actions/workflows/weekly-instrumented.yml/runs"
            f"?event={quote(event)}&per_page=100&page={page}"
        )
        payload = retry_api_call(lambda: _gh_json(endpoint))
        runs = payload.get("workflow_runs") if isinstance(payload, dict) else None
        if not isinstance(runs, list):
            raise StagingError("EVIDENCE_API_ERROR", "workflow runs response has no workflow_runs array")
        found.extend(run for run in runs if isinstance(run, dict))
        if len(runs) < 100:
            break
        page += 1
    if page > 20:
        raise StagingError("EVIDENCE_API_ERROR", "workflow run pagination exceeded the safe search bound")
    return found


def _get_json(repo: str, endpoint: str) -> Any:
    return retry_api_call(lambda: _gh_json(f"repos/{repo}/{endpoint}"))


def _wait_for_success(repo: str, candidate_sha: str, event: str, timeout_seconds: int, poll_interval: int) -> dict[str, Any]:
    started = time.monotonic()
    last_outcome = "EVIDENCE_MISSING"
    last_detail = "no run associated with the exact candidate SHA has been found"
    while True:
        try:
            runs = _list_candidate_runs(repo, event)
        except ApiError as exc:
            raise StagingError("EVIDENCE_API_ERROR", f"GitHub run-list API failed after retries: {exc}") from exc
        selected = select_latest_candidate_run(runs, candidate_sha, event)
        if selected is not None:
            run_id = selected.get("id")
            if run_id is None:
                raise StagingError("EVIDENCE_API_ERROR", "matching workflow run has no run ID")
            try:
                run = _get_json(repo, f"actions/runs/{int(run_id)}")
            except (ApiError, ValueError) as exc:
                raise StagingError("EVIDENCE_API_ERROR", f"GitHub run-detail API failed after retries: {exc}") from exc
            if not isinstance(run, dict) or not belongs_to_candidate(run, candidate_sha, event):
                raise StagingError("EVIDENCE_SHA_MISMATCH", "selected run detail no longer matches candidate SHA and event")
            last_outcome, last_detail = run_outcome(run)
            if last_outcome == "TEST_FAILED":
                raise StagingError(last_outcome, last_detail)
            if last_outcome == "PASS":
                return run
        if time.monotonic() - started >= timeout_seconds:
            if last_outcome == "EVIDENCE_MISSING":
                raise StagingError("EVIDENCE_MISSING", last_detail)
            raise StagingError("EVIDENCE_PENDING", last_detail)
        time.sleep(poll_interval)


def _get_successful_job(repo: str, run_id: str) -> dict[str, Any]:
    try:
        payload = _get_json(repo, f"actions/runs/{run_id}/jobs?per_page=100")
    except ApiError as exc:
        raise StagingError("EVIDENCE_API_ERROR", f"GitHub jobs API failed after retries: {exc}") from exc
    jobs = payload.get("jobs") if isinstance(payload, dict) else None
    if not isinstance(jobs, list):
        raise StagingError("EVIDENCE_API_ERROR", "jobs response has no jobs array")
    matches = [job for job in jobs if isinstance(job, dict) and job.get("name") == EXPECTED_JOB]
    if not matches:
        raise StagingError("EVIDENCE_JOB_MISSING", f"run {run_id} has no {EXPECTED_JOB!r} job")
    job = max(matches, key=lambda value: int(value.get("id", 0)))
    if not str(job.get("id") or "").isdigit() or int(job["id"]) <= 0:
        raise StagingError("EVIDENCE_JOB_MISSING", f"run {run_id} has no valid hard-gate job ID")
    if job.get("conclusion") != "success":
        raise StagingError("TEST_FAILED", f"instrumented hard-gate job conclusion is {job.get('conclusion')!r}")
    return job


def _get_artifact(repo: str, run_id: str, candidate_sha: str) -> tuple[dict[str, Any], str]:
    try:
        payload = _get_json(repo, f"actions/runs/{run_id}/artifacts?per_page=100")
    except ApiError as exc:
        raise StagingError("EVIDENCE_API_ERROR", f"GitHub artifact-list API failed after retries: {exc}") from exc
    artifacts = payload.get("artifacts") if isinstance(payload, dict) else None
    if not isinstance(artifacts, list):
        raise StagingError("EVIDENCE_API_ERROR", "artifact-list response has no artifacts array")
    expected_name = f"sqlcipher-instrumented-{candidate_sha}"
    matches = [artifact for artifact in artifacts if isinstance(artifact, dict) and artifact.get("name") == expected_name]
    if not matches:
        raise StagingError("EVIDENCE_ARTIFACT_MISSING", f"run {run_id} has no artifact named {expected_name}")
    artifact = max(matches, key=lambda value: str(value.get("created_at", "")))
    digest = validate_artifact_metadata(artifact, expected_name=expected_name, expected_run_id=run_id)
    return artifact, digest


def stage_evidence(
    *,
    repo: str,
    event: str,
    candidate_sha: str,
    output_dir: Path,
    status_path: Path,
    timeout_seconds: int = 40 * 60,
    poll_interval: int = 60,
) -> dict[str, Any]:
    if not SHA_RE.fullmatch(candidate_sha):
        raise StagingError("EVIDENCE_SHA_MISMATCH", "candidate SHA must be a full lowercase 40-character hexadecimal commit")
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repo):
        raise StagingError("EVIDENCE_API_ERROR", "repository must use owner/name format")
    if event not in ALLOWED_EVENTS:
        raise StagingError("EVIDENCE_API_ERROR", f"unsupported GitHub event: {event}")
    if timeout_seconds <= 0 or poll_interval <= 0:
        raise StagingError("EVIDENCE_API_ERROR", "timeout and poll interval must be positive")

    run = _wait_for_success(repo, candidate_sha, event, timeout_seconds, poll_interval)
    run_id = str(run["id"])
    job = _get_successful_job(repo, run_id)
    artifact, digest = _get_artifact(repo, run_id, candidate_sha)
    artifact_id = int(artifact["id"])
    status_path.parent.mkdir(parents=True, exist_ok=True)
    output_dir.parent.mkdir(parents=True, exist_ok=True)

    with tempfile.TemporaryDirectory(prefix="cpas-instrumented-") as temporary:
        temp_root = Path(temporary)
        archive_path = temp_root / "artifact.zip"
        try:
            retry_api_call(lambda: _gh_download_artifact(repo, artifact_id, archive_path))
        except ApiError as exc:
            raise StagingError("EVIDENCE_API_ERROR", f"GitHub artifact download failed after retries: {exc}") from exc
        if not archive_path.is_file() or archive_path.stat().st_size == 0:
            raise StagingError("EVIDENCE_ARTIFACT_INVALID", "downloaded artifact archive is empty")
        verify_archive_digest(archive_path, digest)
        extracted = temp_root / "extracted"
        safe_extract_zip(archive_path, extracted)
        try:
            modules = validate_reports(extracted, candidate_sha, run_id)
        except EvidenceError as exc:
            raise StagingError("EVIDENCE_INVALID", str(exc)) from exc
        if set(modules) != set(EXPECTED_MODULES):
            raise StagingError("EVIDENCE_INVALID", "validated module set differs from the required SQLCipher suites")

        if output_dir.exists():
            shutil.rmtree(output_dir)
        shutil.copytree(extracted, output_dir)

    return {
        "status": "PASS",
        "candidate_sha": candidate_sha,
        "event": event,
        "run_id": run_id,
        "job_id": str(job.get("id", "")),
        "job_name": EXPECTED_JOB,
        "artifact_id": artifact_id,
        "artifact_name": artifact.get("name"),
        "artifact_digest": digest,
        "modules": modules,
        "staged_at": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
    }


def write_status(path: Path, status: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    temporary.write_text(json.dumps(status, indent=2) + "\n", encoding="utf-8")
    os.replace(temporary, path)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", required=True)
    parser.add_argument("--event", required=True)
    parser.add_argument("--candidate-sha", required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--status-file", type=Path, required=True)
    parser.add_argument("--timeout-seconds", type=int, default=40 * 60)
    parser.add_argument("--poll-interval", type=int, default=60)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        status = stage_evidence(
            repo=args.repo,
            event=args.event,
            candidate_sha=args.candidate_sha,
            output_dir=args.output_dir,
            status_path=args.status_file,
            timeout_seconds=args.timeout_seconds,
            poll_interval=args.poll_interval,
        )
    except StagingError as exc:
        status = {
            "status": exc.code,
            "candidate_sha": args.candidate_sha,
            "event": args.event,
            "detail": str(exc),
            "staged_at": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        }
        write_status(args.status_file, status)
        print(json.dumps(status, indent=2), file=sys.stderr)
        return 1
    except Exception as exc:  # Fail closed, while preserving a machine-readable cause.
        status = {
            "status": "STAGING_ERROR",
            "candidate_sha": args.candidate_sha,
            "event": args.event,
            "detail": f"unexpected staging error: {type(exc).__name__}: {exc}",
            "staged_at": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        }
        write_status(args.status_file, status)
        print(json.dumps(status, indent=2), file=sys.stderr)
        return 1
    write_status(args.status_file, status)
    print(json.dumps(status, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
