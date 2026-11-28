"""Regression tests for real ORT scan-result cache validation."""
from __future__ import annotations

import importlib.util
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("verify_ort_cache.py")
SPEC = importlib.util.spec_from_file_location("verify_ort_cache", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class VerifyOrtCacheTests(unittest.TestCase):
    def write(self, root: Path, relative: str, content: bytes = b"scan-result") -> Path:
        path = root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(content)
        return path

    def test_provenance_and_archive_only_cache_is_blocked(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write(root, "package_provenance/Maven/example/library/1.0/resolved_provenance.yml")
            self.write(root, "archive/abcd/archive.zip.xz", b"archive")
            manifest = MODULE.build_manifest(root, "success")
            self.assertEqual("BLOCKED", manifest["status"])
            self.assertGreater(manifest["file_count"], 0)
            self.assertEqual(0, manifest["cache_scan_result_file_count"])

    def test_nonempty_artifact_scan_result_is_counted(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write(root, "results/artifact/source/scan-results.yml")
            manifest = MODULE.build_manifest(root, "success")
            self.assertEqual("PASS", manifest["status"])
            self.assertEqual(1, manifest["cache_scan_result_file_count"])

    def test_nonempty_repository_scan_result_is_counted(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write(root, "results/repository/Git/source/revision/scan-results.yml")
            manifest = MODULE.build_manifest(root, "success")
            self.assertEqual("PASS", manifest["status"])
            self.assertEqual(1, manifest["cache_scan_result_file_count"])

    def test_empty_scan_result_file_is_not_counted(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write(root, "results/artifact/source/scan-results.yml", b"")
            manifest = MODULE.build_manifest(root, "success")
            self.assertEqual("BLOCKED", manifest["status"])
            self.assertEqual(0, manifest["cache_scan_result_file_count"])

    def test_wrong_storage_category_is_not_counted(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write(root, "results/package_provenance/source/scan-results.yml")
            manifest = MODULE.build_manifest(root, "success")
            self.assertEqual("BLOCKED", manifest["status"])
            self.assertEqual(0, manifest["cache_scan_result_file_count"])

    def test_failed_cache_save_blocks_even_with_scan_results(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write(root, "results/artifact/source/scan-results.yml")
            manifest = MODULE.build_manifest(root, "failure")
            self.assertEqual("BLOCKED", manifest["status"])
            self.assertEqual(1, manifest["cache_scan_result_file_count"])


if __name__ == "__main__":
    unittest.main()
