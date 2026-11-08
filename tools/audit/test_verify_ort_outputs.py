#!/usr/bin/env python3
"""Tests for pinned ORT source-artifact provenance validation."""
from __future__ import annotations

import importlib.util
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).with_name("verify_ort_outputs.py")
SPEC = importlib.util.spec_from_file_location("verify_ort_outputs", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)

PACKAGE_ID = "Maven:example:library:1.2.3"
SOURCE_URL = "https://repo.maven.apache.org/maven2/example/library/1.2.3/library-1.2.3-sources.jar"
SOURCE_SHA1 = "0123456789abcdef0123456789abcdef01234567"


class SourceArtifactPinTests(unittest.TestCase):
    def validate(self, comment: str, url: str = SOURCE_URL, sha1: str = SOURCE_SHA1) -> list[str]:
        analyzer_data = {
            "analyzer": {
                "result": {
                    "packages": [
                        {
                            "id": PACKAGE_ID,
                            "source_artifact": {
                                "url": url,
                                "hash": {"algorithm": "SHA-1", "value": sha1},
                            },
                        }
                    ]
                }
            }
        }
        curations = (
            "curations:\n"
            "  packages:\n"
            f'    - id: "{PACKAGE_ID}"\n'
            "      curations:\n"
            f'        comment: "{comment}"\n'
            "        source_code_origins: [ARTIFACT]\n"
        )
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / ".ort.yml"
            path.write_text(curations, encoding="utf-8")
            return MODULE.validate_source_artifact_pins(analyzer_data, path)

    def test_matching_source_url_and_sha1_pass(self) -> None:
        comment = f"source_artifact_url={SOURCE_URL} source_artifact_sha1={SOURCE_SHA1}"
        self.assertEqual([], self.validate(comment))

    def test_source_url_drift_blocks(self) -> None:
        comment = f"source_artifact_url={SOURCE_URL} source_artifact_sha1={SOURCE_SHA1}"
        problems = self.validate(comment, url="https://example.invalid/source.jar")
        self.assertTrue(any("source artifact URL" in item for item in problems))

    def test_source_sha1_drift_blocks(self) -> None:
        comment = f"source_artifact_url={SOURCE_URL} source_artifact_sha1={SOURCE_SHA1}"
        problems = self.validate(comment, sha1="f" * 40)
        self.assertTrue(any("source artifact SHA-1" in item for item in problems))

    def test_missing_pin_blocks(self) -> None:
        self.assertTrue(any("pin is missing" in item for item in self.validate("no provenance pin")))


if __name__ == "__main__":
    unittest.main()
