#!/usr/bin/env python3
"""Fail closed when ORT does not produce valid result reports and SBOMs."""
from __future__ import annotations

import argparse
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path

RESULT_JSON_FILES = (
    "analyzer-result.json",
    "scan-result.json",
    "evaluation-result.json",
    "advisor-result.json",
)
REQUIRED_OUTPUTS = RESULT_JSON_FILES + (
    "bom.cyclonedx.json",
    "bom.cyclonedx.xml",
    "bom.spdx.json",
    "bom.spdx.yml",
)


def validate_assurance_sboms(cyclonedx_path: Path, spdx_path: Path) -> list[str]:
    """Validate the JSON SBOMs retained in the assurance ledger."""
    problems: list[str] = []
    for path in (cyclonedx_path, spdx_path):
        name = path.name
        if not path.is_file():
            problems.append(f"{name}: missing")
            continue
        if path.stat().st_size == 0:
            problems.append(f"{name}: empty")
            continue
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, UnicodeError, json.JSONDecodeError) as exc:
            problems.append(f"{name}: invalid JSON ({exc})")
            continue
        if not isinstance(data, dict) or not data:
            problems.append(f"{name}: expected a non-empty JSON object")
            continue
        if name == "ort-bom.cyclonedx.json":
            if data.get("bomFormat") != "CycloneDX" or not isinstance(data.get("specVersion"), str) or not data["specVersion"].strip() or not isinstance(data.get("version"), int) or data["version"] < 1:
                problems.append(f"{name}: invalid CycloneDX BOM metadata")
        else:
            required = ("spdxVersion", "dataLicense", "SPDXID", "name", "documentNamespace")
            if any(not isinstance(data.get(field), str) or not data[field].strip() for field in required) or data.get("SPDXID") != "SPDXRef-DOCUMENT" or not isinstance(data.get("packages"), list):
                problems.append(f"{name}: invalid SPDX document metadata")
    return problems


def validate_outputs(results_dir: Path, revision: str | None = None) -> list[str]:
    """Return missing, empty, malformed, or wrong-format ORT outputs."""
    problems: list[str] = []
    for name in REQUIRED_OUTPUTS:
        path = results_dir / name
        if not path.is_file():
            problems.append(f"{name}: missing")
        elif path.stat().st_size == 0:
            problems.append(f"{name}: empty")

    ort_sections = {
        "analyzer-result.json": ("analyzer", "result"),
        "scan-result.json": ("scanner", "scanResults"),
        "evaluation-result.json": ("evaluator", "violations"),
        "advisor-result.json": ("advisor", "results"),
    }

    for name in RESULT_JSON_FILES + ("bom.cyclonedx.json", "bom.spdx.json"):
        path = results_dir / name
        if not path.is_file() or path.stat().st_size == 0:
            continue
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, UnicodeError, json.JSONDecodeError) as exc:
            problems.append(f"{name}: invalid JSON ({exc})")
            continue
        if not isinstance(data, dict) or not data:
            problems.append(f"{name}: expected a non-empty JSON object")
            continue

        if name in ort_sections:
            # ORT serializes each stage as an OrtResult with a repository and the
            # stage-specific run. A generic JSON object such as {"result": true}
            # is not evidence that ORT actually ran.
            repository = data.get("repository")
            if not isinstance(repository, dict) or not isinstance(repository.get("vcs"), dict) or not isinstance(repository.get("config"), dict):
                problems.append(f"{name}: missing valid ORT repository metadata")
                continue

            vcs_revision = repository["vcs"].get("revision")
            if not isinstance(vcs_revision, str) or not vcs_revision.strip():
                problems.append(f"{name}: ORT repository VCS revision is missing")
                continue
            if revision is not None and vcs_revision != revision:
                problems.append(
                    f"{name}: ORT repository VCS revision {vcs_revision!r} does not match expected {revision!r}"
                )
                continue

            section_name, result_name = ort_sections[name]
            section = data.get(section_name)
            expected_type = dict if name in ("analyzer-result.json", "advisor-result.json") else list
            if not isinstance(section, dict) or not isinstance(section.get(result_name), expected_type):
                expected_label = "object" if expected_type is dict else "list"
                problems.append(
                    f"{name}: missing valid ORT {section_name}.{result_name} {expected_label}"
                )
                continue
            if name == "analyzer-result.json":
                analyzer_result = section[result_name]
                if not isinstance(analyzer_result.get("projects"), list) or not isinstance(analyzer_result.get("packages"), list):
                    problems.append(f"{name}: analyzer result must include projects and packages lists")
        elif name == "bom.cyclonedx.json":
            if data.get("bomFormat") != "CycloneDX":
                problems.append(f"{name}: bomFormat is not CycloneDX")
            if not isinstance(data.get("specVersion"), str) or not data["specVersion"].strip():
                problems.append(f"{name}: missing valid specVersion")
            if not isinstance(data.get("version"), int) or data["version"] < 1:
                problems.append(f"{name}: missing valid positive BOM version")
        elif name == "bom.spdx.json":
            required_spdx_strings = ("spdxVersion", "dataLicense", "SPDXID", "name", "documentNamespace")
            missing_fields = [
                field for field in required_spdx_strings
                if not isinstance(data.get(field), str) or not data[field].strip()
            ]
            if missing_fields:
                problems.append(f"{name}: missing required SPDX fields: {', '.join(missing_fields)}")
            if not str(data.get("spdxVersion", "")).startswith("SPDX-"):
                problems.append(f"{name}: missing valid spdxVersion")
            if data.get("SPDXID") != "SPDXRef-DOCUMENT":
                problems.append(f"{name}: SPDXID must be SPDXRef-DOCUMENT")
            if not isinstance(data.get("packages"), list):
                problems.append(f"{name}: packages must be a list")

    xml_path = results_dir / "bom.cyclonedx.xml"
    if xml_path.is_file() and xml_path.stat().st_size:
        try:
            root = ET.parse(xml_path).getroot()
            local_name = root.tag.rsplit("}", 1)[-1]
            if local_name != "bom" or "cyclonedx.org/schema" not in root.tag:
                problems.append("bom.cyclonedx.xml: root is not a CycloneDX bom")
        except (OSError, ET.ParseError) as exc:
            problems.append(f"bom.cyclonedx.xml: invalid XML ({exc})")

    yaml_path = results_dir / "bom.spdx.yml"
    if yaml_path.is_file() and yaml_path.stat().st_size:
        try:
            yaml_text = yaml_path.read_text(encoding="utf-8")
        except (OSError, UnicodeError) as exc:
            problems.append(f"bom.spdx.yml: unreadable ({exc})")
        else:
            required_yaml_patterns = {
                "spdxVersion": r"(?m)^spdxVersion:\s*['\"]?SPDX-\d+\.\d+['\"]?\s*$",
                "SPDXID": r"(?m)^SPDXID:\s*['\"]?SPDXRef-DOCUMENT['\"]?\s*$",
                "name": r"(?m)^name:\s*\S",
                "documentNamespace": r"(?m)^documentNamespace:\s*https?://\S+",
                "packages": r"(?m)^packages:\s*(?:\[\]|\s*)$",
            }
            for field, pattern in required_yaml_patterns.items():
                if not re.search(pattern, yaml_text):
                    problems.append(f"bom.spdx.yml: missing or invalid {field} field")

    return problems


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--results-dir", type=Path, required=True)
    parser.add_argument("--revision", required=True)
    args = parser.parse_args()

    problems = validate_outputs(args.results_dir, args.revision)
    report = {
        "gate_id": "GATE-LICENSE-001",
        "revision": args.revision,
        "results_dir": str(args.results_dir),
        "required_outputs": list(REQUIRED_OUTPUTS),
        "validation_problems": problems,
        "status": "PASS" if not problems else "BLOCKED",
    }
    print(json.dumps(report, indent=2))
    return 1 if problems else 0


if __name__ == "__main__":
    raise SystemExit(main())
