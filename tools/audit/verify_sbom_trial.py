#!/usr/bin/env python3
"""Fail-closed structural and package-identity checks for the SBOM alternative trial."""
from __future__ import annotations

import argparse
import json
from pathlib import Path


def read_json(path: Path) -> dict:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        raise ValueError(f"{path}: invalid or unreadable JSON: {exc}") from exc
    if not isinstance(value, dict):
        raise ValueError(f"{path}: top-level JSON value must be an object")
    return value


def cdx_components(items: list[dict]):
    for item in items:
        if not isinstance(item, dict):
            continue
        yield item
        nested = item.get("components")
        if isinstance(nested, list):
            yield from cdx_components(nested)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--cyclonedx", type=Path, required=True)
    parser.add_argument("--spdx", type=Path, required=True)
    parser.add_argument("--revision", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    problems: list[str] = []
    cdx = read_json(args.cyclonedx)
    spdx = read_json(args.spdx)

    if cdx.get("bomFormat") != "CycloneDX":
        problems.append("CycloneDX bomFormat is missing or invalid")
    if cdx.get("specVersion") not in {"1.6", "1.7"}:
        problems.append(f"unsupported or missing CycloneDX specVersion: {cdx.get('specVersion')!r}")
    if not isinstance(cdx.get("version"), int) or cdx["version"] < 1:
        problems.append("CycloneDX BOM version is missing or invalid")
    if not isinstance(cdx.get("components"), list) or not cdx["components"]:
        problems.append("CycloneDX components are missing or empty")

    if not str(spdx.get("spdxVersion", "")).startswith("SPDX-2.3"):
        problems.append(f"SPDX output must be SPDX-2.3; found {spdx.get('spdxVersion')!r}")
    for field in ("dataLicense", "SPDXID", "name", "documentNamespace"):
        if not isinstance(spdx.get(field), str) or not spdx[field].strip():
            problems.append(f"SPDX required field is missing: {field}")
    if spdx.get("SPDXID") != "SPDXRef-DOCUMENT":
        problems.append("SPDX document SPDXID must be SPDXRef-DOCUMENT")
    if not isinstance(spdx.get("packages"), list) or not spdx["packages"]:
        problems.append("SPDX packages are missing or empty")

    cdx_items = list(cdx_components(cdx.get("components", [])))
    cdx_purls = {
        str(item["purl"]).strip()
        for item in cdx_items
        if isinstance(item.get("purl"), str) and item["purl"].strip()
    }
    spdx_purls: set[str] = set()
    for package in spdx.get("packages", []):
        if not isinstance(package, dict):
            continue
        for ref in package.get("externalRefs", []) or []:
            if isinstance(ref, dict) and str(ref.get("referenceType", "")).lower() == "purl":
                locator = ref.get("referenceLocator")
                if isinstance(locator, str) and locator.strip():
                    spdx_purls.add(locator.strip())

    if not cdx_purls:
        problems.append("CycloneDX SBOM contains no Package URLs")
    if cdx_purls != spdx_purls:
        missing_spdx = sorted(cdx_purls - spdx_purls)[:20]
        extra_spdx = sorted(spdx_purls - cdx_purls)[:20]
        problems.append(
            f"Package URL parity failed: missing in SPDX={missing_spdx}; extra in SPDX={extra_spdx}"
        )

    cdx_license_count = sum(1 for item in cdx_items if item.get("licenses"))
    spdx_license_count = sum(
        1 for package in spdx.get("packages", [])
        if isinstance(package, dict)
        and any(package.get(field) not in (None, "", "NOASSERTION") for field in ("licenseDeclared", "licenseConcluded"))
    )
    report = {
        "schema_version": 1,
        "candidate_sha": args.revision,
        "status": "SBOM_GENERATED" if not problems else "BLOCKED",
        "cyclonedx": {
            "spec_version": cdx.get("specVersion"),
            "component_count": len(cdx_items),
            "purl_count": len(cdx_purls),
            "components_with_license_metadata": cdx_license_count,
        },
        "spdx": {
            "spec_version": spdx.get("spdxVersion"),
            "package_count": len(spdx.get("packages", [])) if isinstance(spdx.get("packages"), list) else 0,
            "purl_count": len(spdx_purls),
            "packages_with_license_metadata": spdx_license_count,
        },
        "package_url_parity": cdx_purls == spdx_purls,
        "problems": problems,
        "note": "SBOM generation and format parity do not prove complete source-license detection or policy compliance.",
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))
    return 1 if problems else 0


if __name__ == "__main__":
    raise SystemExit(main())
