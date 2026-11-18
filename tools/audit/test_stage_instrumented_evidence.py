from __future__ import annotations

import hashlib
import tempfile
import unittest
import zipfile
from pathlib import Path

from stage_instrumented_evidence import (
    ANSI_ESCAPE_RE,
    ApiError,
    StagingError,
    belongs_to_candidate,
    retry_api_call,
    run_outcome,
    safe_extract_zip,
    select_latest_candidate_run,
    validate_artifact_metadata,
    verify_archive_digest,
)

SHA = "a" * 40
OTHER_SHA = "b" * 40
DIGEST = "sha256:" + "c" * 64


class StageInstrumentedEvidenceTests(unittest.TestCase):
    def test_ansi_color_codes_are_removed_before_json_parsing(self) -> None:
        import json

        colored = "\x1b[1;37m{\x1b[m\n  \x1b[1;34m\"status\"\x1b[m: \x1b[32m\"ok\"\x1b[m\n}"
        self.assertEqual({"status": "ok"}, json.loads(ANSI_ESCAPE_RE.sub("", colored)))

    def test_pull_request_selection_requires_same_event_and_exact_pr_head(self) -> None:
        runs = [
            {"id": 1, "event": "pull_request", "head_sha": "f" * 40,
             "pull_requests": [{"head": {"sha": SHA}}], "created_at": "2026-01-01T00:00:00Z"},
            {"id": 2, "event": "push", "head_sha": "f" * 40,
             "pull_requests": [{"head": {"sha": SHA}}], "created_at": "2026-01-03T00:00:00Z"},
            {"id": 3, "event": "pull_request", "head_sha": OTHER_SHA,
             "pull_requests": [{"head": {"sha": OTHER_SHA}}], "created_at": "2026-01-04T00:00:00Z"},
            {"id": 4, "event": "pull_request", "head_sha": SHA,
             "created_at": "2026-01-02T00:00:00Z"},
        ]
        selected = select_latest_candidate_run(runs, SHA, "pull_request")
        self.assertEqual(4, selected["id"])
        self.assertFalse(belongs_to_candidate(runs[1], SHA, "pull_request"))
        self.assertIsNone(select_latest_candidate_run(runs, SHA, "push"))

    def test_newer_pending_run_is_not_misclassified_as_cancelled_or_replaced_by_old_pass(self) -> None:
        runs = [
            {"id": 8, "event": "push", "head_sha": SHA, "created_at": "2026-01-01T00:00:00Z",
             "status": "completed", "conclusion": "success"},
            {"id": 9, "event": "push", "head_sha": SHA, "created_at": "2026-01-02T00:00:00Z",
             "status": "in_progress", "conclusion": None},
        ]
        selected = select_latest_candidate_run(runs, SHA, "push")
        self.assertEqual(9, selected["id"])
        self.assertEqual(("EVIDENCE_PENDING", "matching run is in_progress; no terminal conclusion is available"), run_outcome(selected))
        self.assertEqual(("PASS", "matching instrumented workflow completed successfully"), run_outcome(runs[0]))
        self.assertEqual(
            ("TEST_FAILED", "matching instrumented workflow completed with conclusion=cancelled"),
            run_outcome({"status": "completed", "conclusion": "cancelled"}),
        )

    def test_transient_api_errors_retry_with_bounded_exponential_backoff(self) -> None:
        calls = 0
        sleeps: list[float] = []

        def operation() -> str:
            nonlocal calls
            calls += 1
            if calls < 3:
                raise ApiError("HTTP 502 from GitHub", transient=True)
            return "ok"

        result = retry_api_call(
            operation,
            max_attempts=4,
            base_delay_seconds=1,
            max_delay_seconds=4,
            sleeper=sleeps.append,
            jitter=lambda: 0.5,
        )
        self.assertEqual("ok", result)
        self.assertEqual(3, calls)
        self.assertEqual([1.0, 2.0], sleeps)

    def test_nontransient_api_error_is_not_retried(self) -> None:
        calls = 0

        def operation() -> None:
            nonlocal calls
            calls += 1
            raise ApiError("HTTP 403 forbidden", transient=False)

        with self.assertRaises(ApiError):
            retry_api_call(operation, max_attempts=5, sleeper=lambda _: None)
        self.assertEqual(1, calls)

    def test_artifact_metadata_must_bind_name_id_run_and_sha256(self) -> None:
        artifact = {
            "id": 123,
            "name": f"sqlcipher-instrumented-{SHA}",
            "expired": False,
            "digest": DIGEST,
            "workflow_run": {"id": 456},
        }
        self.assertEqual(
            DIGEST,
            validate_artifact_metadata(artifact, expected_name=f"sqlcipher-instrumented-{SHA}", expected_run_id="456"),
        )
        with self.assertRaisesRegex(StagingError, "name"):
            validate_artifact_metadata(artifact, expected_name="wrong", expected_run_id="456")
        with self.assertRaisesRegex(StagingError, "run ID"):
            validate_artifact_metadata(artifact, expected_name=f"sqlcipher-instrumented-{SHA}", expected_run_id="999")
        with self.assertRaisesRegex(StagingError, "digest"):
            validate_artifact_metadata({**artifact, "digest": None}, expected_name=f"sqlcipher-instrumented-{SHA}", expected_run_id="456")

    def test_downloaded_archive_digest_is_checked(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            archive = Path(temporary) / "evidence.zip"
            archive.write_bytes(b"zip-payload")
            digest = hashlib.sha256(archive.read_bytes()).hexdigest()
            verify_archive_digest(archive, "sha256:" + digest)
            with self.assertRaisesRegex(StagingError, "does not match"):
                verify_archive_digest(archive, DIGEST)

    def test_zip_extraction_rejects_path_traversal(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            archive = root / "malicious.zip"
            with zipfile.ZipFile(archive, "w") as zipped:
                zipped.writestr("../escape.txt", "not allowed")
            with self.assertRaisesRegex(StagingError, "unsafe path"):
                safe_extract_zip(archive, root / "out")
            self.assertFalse((root / "escape.txt").exists())

    def test_zip_extraction_accepts_normal_evidence_paths(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            archive = root / "evidence.zip"
            with zipfile.ZipFile(archive, "w") as zipped:
                zipped.writestr("sqlcipher-instrumented-manifest.json", "{}\n")
            destination = root / "out"
            safe_extract_zip(archive, destination)
            self.assertEqual("{}\n", (destination / "sqlcipher-instrumented-manifest.json").read_text())


if __name__ == "__main__":
    unittest.main()
