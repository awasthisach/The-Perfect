# Sprint-2 truth notes

## Applied (this change set)

- CPAS: active waiver `expires_at` enforced; instrumented evidence must be ≤14 days old
- CI: explicit `::warning` when emulator skip leaves SQLCipher instrumented unrun
- Weekly hard instrumented workflow (`weekly-instrumented.yml`) — no `continue-on-error`

## Not claimed done here

- Drive resumable upload (P1 product work — separate PR)
- Cross-device `PRAGMA rekey` (design + instrumented tests — separate PR)
- Release SBOM/SLSA — **already present** in `release.yml` (`anchore/sbom-action`, `actions/attest`)

## False-green policy

Primary CI may stay green when GH emulator flakes; compensation is weekly hard gate + verifier freshness, not silent rot.
