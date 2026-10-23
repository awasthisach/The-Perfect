from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from verify_ort_outputs import REQUIRED_OUTPUTS, validate_outputs


class OrtOutputValidationTest(unittest.TestCase):
    def write_valid_reports(self, root: Path) -> None:
        repository = {
            "vcs": {
                "type": "Git",
                "url": "https://github.com/awasthisach/The-Perfect",
                "revision": "0123456789abcdef0123456789abcdef01234567",
            },
            "config": {},
        }
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
                "bomFormat": "CycloneDX",
                "specVersion": "1.6",
                "version": 1,
                "components": [],
            },
            "bom.spdx.json": {
                "spdxVersion": "SPDX-2.3",
                "dataLicense": "CC0-1.0",
                "SPDXID": "SPDXRef-DOCUMENT",
                "name": "The-Perfect test SBOM",
                "documentNamespace": "https://example.invalid/sbom/test",
                "packages": [],
            },
        }
        for name, report in reports.items():
            (root / name).write_text(json.dumps(report), encoding="utf-8")
        (root / "bom.cyclonedx.xml").write_text(
            '<bom xmlns="http://cyclonedx.org/schema/bom/1.6" version="1"><components /></bom>',
            encoding="utf-8",
        )
        (root / "bom.spdx.yml").write_text(
            "spdxVersion: SPDX-2.3\nSPDXID: SPDXRef-DOCUMENT\nname: Test SBOM\ndocumentNamespace: https://example.invalid/sbom/test\npackages: []\n",
            encoding="utf-8",
        )

    def test_missing_reports_fail_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            problems = validate_outputs(Path(directory))
            self.assertTrue(problems)
            self.assertTrue(any("analyzer-result.json: missing" in item for item in problems))
            self.assertTrue(any("bom.spdx.yml: missing" in item for item in problems))

    def test_valid_report_shapes_and_sboms_pass(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_valid_reports(root)
            self.assertEqual([], validate_outputs(root))

    def test_syntactically_valid_fake_ort_json_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_valid_reports(root)
            (root / "analyzer-result.json").write_text('{"result": true}', encoding="utf-8")
            problems = validate_outputs(root)
            self.assertTrue(
                any("analyzer-result.json: missing valid ORT repository metadata" in item
                    for item in problems)
            )

    def test_fake_sbom_reports_fail_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_valid_reports(root)
            (root / "bom.cyclonedx.json").write_text(
                '{"bomFormat":"CycloneDX"}', encoding="utf-8"
            )
            (root / "bom.spdx.json").write_text(
                '{"spdxVersion":"SPDX-2.3"}', encoding="utf-8"
            )
            (root / "bom.spdx.yml").write_text(
                "spdxVersion: SPDX-2.3\n", encoding="utf-8"
            )
            problems = validate_outputs(root)
            self.assertTrue(any("missing valid specVersion" in item for item in problems))
            self.assertTrue(any("missing required SPDX fields" in item for item in problems))
            self.assertTrue(any("missing or invalid documentNamespace field" in item for item in problems))

    def test_reports_must_match_exact_expected_revision(self):
        expected_revision = "abcdef0123456789abcdef0123456789abcdef01"
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_valid_reports(root)
            problems = validate_outputs(root, expected_revision)
            self.assertTrue(
                any("does not match expected" in item for item in problems),
                "A valid-shaped ORT report from a different revision must fail closed",
            )

    def test_reports_for_expected_revision_pass(self):
        expected_revision = "0123456789abcdef0123456789abcdef01234567"
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_valid_reports(root)
            self.assertEqual([], validate_outputs(root, expected_revision))

    def test_malformed_json_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.write_valid_reports(root)
            (root / "advisor-result.json").write_text("{broken", encoding="utf-8")
            problems = validate_outputs(root)
            self.assertTrue(any("advisor-result.json: invalid JSON" in item for item in problems))


if __name__ == "__main__":
    unittest.main()
