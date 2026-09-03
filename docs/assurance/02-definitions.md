# Definitions & Canonical ID Grammar

## Status semantics

| Status | Meaning |
|--------|---------|
| `TARGET` | Planned control; not implemented or not verified |
| `IMPLEMENTED` | Code exists; evidence missing or incomplete |
| `VERIFIED` | Code + passing test + ledger entry with CI linkage |
| `BLOCKED` | Critical risk open, missing mandatory evidence, or gate failed |
| `PASS` / `FAIL` | Gate/test outcome values only |

## Canonical ID grammar

Format: `{DOMAIN}-{TYPE}-{SEQ}`

- **DOMAIN:** `STORAGE`, `VAULT`, `CLOUD`, `AUTH`, `PRIVACY`, `BUILD`, `DB`, `RESTORE`, `WORK`, `PROD`
- **TYPE:** `REQ`, `INV`, `TEST`, `RISK`, `GATE` (gates may also use `GATE-{AREA}-{SEQ}`)
- **SEQ:** 3-digit zero-padded integer

Examples:

- `STORAGE-INV-001` — storage boundary invariant
- `RESTORE-TEST-001` — restore fail-closed unit test
- `PROD-002` — production risk finding (legacy RISK domain)
- `GATE-LICENSE-001` — FOSSA / license compliance gate

Risk register may keep `PROD-xxx` as the established finding ID space; new invariants and tests must use domain-prefixed grammar.
