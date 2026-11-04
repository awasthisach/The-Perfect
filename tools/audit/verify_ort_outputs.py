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



CURATION_BLOCK_RE = re.compile(
    r'(?ms)^\\s{4}-\\s+id:\\s*"(?P<package_id>[^"]+)"\\s*\\n'
    r"(?P<body>.*?)(?=^\\s{4}-\\s+id:|\\Z)"
)
SOURCE_PIN_RE = re.compile(
    r"source_artifact_url=(?P<url>\\S+)\\s+"
    r"source_artifact_sha1=(?P<sha1>[0-9a-fA-F]{40})"
)


def validate_source_artifact_pins(
    analyzer_data: dict, curations_file: Path
) -> list[str]:
    """Verify each ORT source-origin curation against the analyzer-resolved URL and SHA-1."""
    problems: list[str] = []
    try:
        curations_text = curations_file.read_text(encoding="utf-8")
    except (OSError, UnicodeError) as exc:
        return [f"curations file is unreadable: {exc}"]

    analyzer = analyzer_data.get("analyzer")
    result = analyzer.get("result") if isinstance(analyzer, dict) else None
    packages = result.get("packages") if isinstance(result, dict) else None
    if not isinstance(packages, list):
        return ["analyzer-result.json: packages list is unavailable for source-artifact pin validation"]
    packages_by_id = {
        item.get("id"): item
        for item in packages
        if isinstance(item, dict) and isinstance(item.get("id"), str)
    }

    for match in CURATION_BLOCK_RE.finditer(curations_text):
        package_id = match.group("package_id")
        body = match.group("body")
        comment_match = re.search(r'(?m)^\\s{8}comment:\\s*"([^"]*)"\\s*$', body)
        if not comment_match:
            problems.append(f"{package_id}: curation comment is missing")
            continue
        pin_match = SOURCE_PIN_RE.search(comment_match.group(1))
        if not pin_match:
            problems.append(f"{package_id}: source artifact URL/SHA-1 pin is missing")
            continue

        package = packages_by_id.get(package_id)
        if package is None:
            problems.append(f"{package_id}: package is missing from ORT analyzer output")
            continue
        source = package.get("source_artifact")
        if not isinstance(source, dict):
            problems.append(f"{package_id}: ORT analyzer did not resolve a source artifact")
            continue
        actual_url = source.get("url")
        actual_hash = source.get("hash")
        expected_url = pin_match.group("url")
        expected_sha1 = pin_match.group("sha1").lower()
        if actual_url != expected_url:
            problems.append(
                f"{package_id}: source artifact URL {actual_url!r} does not match pinned {expected_url!r}"
            )
        if not isinstance(actual_hash, dict) or actual_hash.get("algorithm") != "SHA-1":
            problems.append(f"{package_id}: ORT source artifact SHA-1 is missing")
        elif str(actual_hash.get("value", "")).lower() != expected_sha1:
            problems.append(
                f"{package_id}: source artifact SHA-1 {actual_hash.get('value')!r} does not match pinned {expected_sha1!r}"
            )
    return problems

def validate_outputs(\n    results_dir: Path, revision: str | None = None, curations_file: Path | None = None\n) -> list[str]:
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
                elif curations_file is not None:
                    problems.extend(validate_source_artifact_pins(data, curations_file))
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
    parser.add_argument("--curations-file", type=Path, default=Path(".ort.yml"))
    args = parser.parse_args()

    problems = validate_outputs(args.results_dir, args.revision, args.curations_file)
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
