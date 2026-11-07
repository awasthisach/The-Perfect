#!/usr/bin/env python3
"""Fail closed on absent deployment review protections before remote mutations."""
from __future__ import annotations
import argparse
import json
import sys
import tomllib
from pathlib import Path


def verify_environment(metadata: object, expected_name: str) -> None:
    if not isinstance(metadata, dict) or metadata.get("name") != expected_name:
        raise ValueError("deployment environment is absent or does not match")
    rules = metadata.get("protection_rules", [])
    reviewers = [rule for rule in rules if isinstance(rule, dict) and rule.get("type") == "required_reviewers"]
    if not any(rule.get("reviewers") and rule.get("prevent_self_review") is True for rule in reviewers):
        raise ValueError("required reviewers with self-review prevention must be configured")
    # Some API versions omit this setting; do not invent a proof for missing metadata.
    # Console verification of administrator bypass remains an owner release blocker.
    if metadata.get("can_admins_bypass") is True:
        raise ValueError("environment administrator bypass is explicitly enabled")
    policy = metadata.get("deployment_branch_policy")
    if not isinstance(policy, dict) or not (policy.get("protected_branches") is True or policy.get("custom_branch_policies") is True):
        raise ValueError("deployment branches or tags must be restricted")


def verify_worker_config(config: object) -> None:
    if not isinstance(config, dict) or not isinstance(config.get("vars"), dict):
        raise ValueError("Worker vars configuration is missing")
    values = config["vars"]
    if values.get("GEMINI_PRODUCTION_APPROVED") != "true":
        raise ValueError("Gemini billing and provider data-handling approval is not enabled")
    if (values.get("EMBEDDING_MODEL"), str(values.get("EMBEDDING_VERSION")), str(values.get("EMBEDDING_DIMENSION"))) != ("gemini-embedding-2", "4", "768"):
        raise ValueError("Worker configuration does not match the reviewed v4 model contract")
    if not values.get("FIREBASE_PROJECT_ID") or not values.get("FIREBASE_PROJECT_NUMBER"):
        raise ValueError("Firebase project configuration is incomplete")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--environment", required=True)
    parser.add_argument("--environment-file", type=Path, required=True)
    parser.add_argument("--worker-config", type=Path)
    args = parser.parse_args()
    try:
        verify_environment(json.loads(args.environment_file.read_text()), args.environment)
        if args.worker_config:
            with args.worker_config.open("rb") as stream:
                verify_worker_config(tomllib.load(stream))
    except (OSError, ValueError, TypeError) as error:
        print(f"Deployment preflight blocked: {error}", file=sys.stderr)
        return 1
    print("Deployment preflight metadata checks passed; platform reviewer approval remains required.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
