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


class OrtRevisionValidationTests(unittest.TestCase):
    def make_valid_results(self, root: Path, revision: str) -> None:
        import json

        repository = {"vcs": {"revision": revision}, "config": {}}
        reports = {
            "analyzer-result.json": {
                "repository": repository,
                "analyzer": {"result": {"projects": [], "packages": []}},
            },
            "scan-result.json": {
                "repository": repository,
                "scanner": {"scanResults": []},
            },
            "evaluation-result.json": {
                "repository": repository,
                "evaluator": {"violations": []},
            },
            "advisor-result.json": {
                "repository": repository,
                "advisor": {"results": {}},
            },
            "bom.cyclonedx.json": {
                "bomFormat": "CycloneDX", "specVersion": "1.5", "version": 1,
            },
            "bom.spdx.json": {
                "spdxVersion": "SPDX-2.3",
                "dataLicense": "CC0-1.0",
                "SPDXID": "SPDXRef-DOCUMENT",
                "name": "The-Perfect",
                "documentNamespace": "https://example.invalid/spdx/test",
                "packages": [],
            },
        }
        for name, payload in reports.items():
            (root / name).write_text(json.dumps(payload), encoding="utf-8")
        (root / "bom.cyclonedx.xml").write_text(
            '<bom xmlns="http://cyclonedx.org/schema/bom/1.5"></bom>',
            encoding="utf-8",
        )
        (root / "bom.spdx.yml").write_text(
            "spdxVersion: SPDX-2.3\n"
            "SPDXID: SPDXRef-DOCUMENT\n"
            "name: The-Perfect\n"
            "documentNamespace: https://example.invalid/spdx/test\n"
            "packages: []\n",
            encoding="utf-8",
        )

    def test_all_ort_reports_with_exact_revision_pass(self) -> None:
        import tempfile

        revision = "a" * 40
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self.make_valid_results(root, revision)
            curations = root / ".ort.yml"
            curations.write_text("curations: {}\n", encoding="utf-8")
            self.assertEqual([], MODULE.validate_outputs(root, revision, curations))

    def test_report_from_wrong_revision_is_rejected(self) -> None:
        import tempfile

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self.make_valid_results(root, "b" * 40)
            curations = root / ".ort.yml"
            curations.write_text("curations: {}\n", encoding="utf-8")
            problems = MODULE.validate_outputs(root, "a" * 40, curations)
            self.assertTrue(any("does not match expected" in item for item in problems))

    def test_missing_ort_output_is_rejected(self) -> None:
        import tempfile

        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            problems = MODULE.validate_outputs(root, "a" * 40, root / ".ort.yml")
            self.assertTrue(any("analyzer-result.json: missing" in item for item in problems))
            self.assertTrue(any("bom.spdx.yml: missing" in item for item in problems))


if __name__ == "__main__":
    unittest.main()
