# Abandoned branch cleanup

**Keep (do not delete):**

- `main` (protected)
- `rebuild/stabilize-v1` (PR #187 integration line)

**Delete (abandoned / superseded):** all other remote heads listed in the audit dated 2026-10-30, including stale `fix/*`, `feat/*`, `candidate/*`, `docs/*`, `chore/*`, `ops/*`, `integrate/*`, `audit-*`, `awasthisach-patch-*`, `main-1`, and parallel phase3 stacks.

Dependabot branches may be left if auto-recreated; otherwise delete and let Dependabot open fresh PRs after stabilize merges.

## One-shot cleanup (owner machine with `gh` auth)

```bash
gh api repos/awasthisach/The-Perfect/git/matching-refs/heads --paginate \
  --jq '.[].ref' | sed 's#refs/heads/##' | while read -r b; do
  case "$b" in
    main|rebuild/stabilize-v1) echo "KEEP $b" ;;
    *) echo "DELETE $b"; gh api -X DELETE "repos/awasthisach/The-Perfect/git/refs/heads/$b" ;;
  esac
done
```

Or GitHub UI: Repo → Branches → each stale branch → trash icon.
