# CPAS Remediation Policy

## Priority

Remediation priority is ordered as follows: security, authorization, confidentiality, integrity, and data loss; build and CI blockers; correctness; reliability and recovery; coverage and invariant gaps; performance; accessibility, localization, and documentation.

## Finding lifecycle

A finding moves through `discovered`, `triaged`, `reproduced`, `root_cause_verified`, `repair_implemented`, `tested`, `ci_verified`, and `closed`. Closure requires the relevant lifecycle evidence; a commit message or a green unrelated build is not closure evidence.

## Definition of done

A critical or high finding is complete only when its root cause is documented, the minimal repair is reviewed, a regression test exists, relevant local validation passes, the change is present in the evaluated commit, the relevant CI gate passes, evidence is recorded, and the computed CPAS result reflects any remaining higher-level blocker.

## Waivers

A waiver must have an ID, affected control/finding, rationale, compensating control, residual risk, approver, creation date, expiry date, and affected release range. Waivers cannot silently suppress critical security, wrong-key rejection, unauthorized restore, release signing, or data-loss controls. Expired or malformed waivers are blocking.

## Change discipline

Prefer the smallest root-cause change. Refactoring is justified only when required for security boundaries, testability, correctness, or maintainability. Every change must include blast-radius review and targeted regression coverage. Security and destructive-data changes require explicit reviewer ownership.
