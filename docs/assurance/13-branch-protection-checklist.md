# Branch protection checklist (manual — repo Settings)

**Why:** Direct-to-main and unverified bot merges caused ~35% CI failure noise. Code quality is not the primary cause.

## Settings → Branches → Rule for `main`

- [ ] Require a pull request before merging
- [ ] Require approvals: 1 (or your team size)
- [ ] Require status checks to pass:
  - `Build, Test, Lint & Quality` (VVF Smart Manager CI)
  - `CPAS Verify (fail-closed)`
  - CodeQL (if enabled)
- [ ] Require branches to be up to date before merging
- [ ] Do not allow bypassing the above settings (restrict admins if possible)
- [ ] Block force pushes
- [ ] Block deletions

## Integrations

- [ ] **Disable fossabot** (Settings → Integrations / Installed GitHub Apps) — replaced by Dependabot
- [ ] Dependabot security/version updates: leave **enabled** (config: `.github/dependabot.yml`)

## After enable

- Dependency bumps only via Dependabot PRs
- Main runs are **not** cancelled mid-flight (`cancel-in-progress` is PR-only)
