# Drive Semantic Search — current production-readiness audit

Scope: `awasthisach/The-Perfect`, release candidate branch `candidate/drive-semantic-search-release`, PR #138, current audited head `855d788cafd695c3fa8410d10fedecdb66ce6e16`. This document records source and CI evidence; it does not declare the app production-ready.

## Implemented in the candidate source

- Native Kotlin/Jetpack Compose application; no WebView wrapper.
- Google Drive OAuth integration, account matching with Firebase Auth, paginated Drive indexing and changes-feed handling.
- Room/SQLCipher-backed Drive index and 768-dimensional embedding-vector storage.
- Real `gemini-embedding-2` neural embedding adapter in `CloudflareEmbeddingClient.kt`; the client sends a Firebase Auth ID token and Firebase App Check token, not the Drive access token or Gemini key.
- Consent-gated embedding, bounded batches, vector caching/persistence, cancellation, and local FTS/metadata fallback.
- Cloudflare Worker source with Firebase JWT and App Check verification for native calls without `Origin`, per-UID Durable Object rate limiting, bounded request payloads, response contract validation and upstream retry handling.
- Bounded text extraction, resumable page checkpoints, offline pinning, and JSON index import/export that excludes tokens, API keys and file bytes.
- The current candidate includes trusted local/Drive SHA-256 lookup, downloaded-byte rehashing, temporary artifact cleanup, rollback handling, durable remote IDs, fail-closed DAO persistence, and protected release signing. Real-provider restore, process-death and signed-artifact acceptance still need evidence.

## Current verified external/CI facts

- The protected deployment workflow [#38676382484](https://github.com/awasthisach/The-Perfect/actions/runs/38676382484) succeeded on the current candidate SHA. Its steps verified the Worker health contract (`gemini-embedding-2`, version `4`, dimension `768`) and that `/embed` rejects requests without Firebase credentials. This proves deployment and these server-side checks only; it does **not** prove a valid signed-in Android request with real Firebase Auth + App Check succeeds.
- On current candidate head `855d788cafd695c3fa8410d10fedecdb66ce6e16`, Android unit tests, lint and debug APK assembly succeeded in [CI run #38661489693](https://github.com/awasthisach/The-Perfect/actions/runs/38661489693), but the overall CI workflow failed at the FOSSA license gate. FOSSA reported 192 findings and explicitly said the configured push-only API key cannot display issue details.
- The dedicated SQLCipher instrumented hard gate passed on this head in [run #38661489617](https://github.com/awasthisach/The-Perfect/actions/runs/38661489617). This does not substitute for real-device Google OAuth/Drive/Worker acceptance.
- [CPAS Production Gate #38661489626](https://github.com/awasthisach/The-Perfect/actions/runs/38661489626) correctly fails closed: 13 of 14 checks passed, but `PROD-008`, `PROD-009`, and `PROD-010` remain blockers. The current gate result is **BLOCKED**, not PASS.
- The single release candidate is [Draft PR #138](https://github.com/awasthisach/The-Perfect/pull/138), with 90 changed files. Avoid merging older duplicate PRs; #138 must remain Draft until every required gate and external acceptance check is evidenced on one current SHA.

## Remaining release blockers

1. **FOSSA findings:** provide a narrowly scoped key that can read issue details, review all reported findings, fix incompatible/unknown license or vulnerability blockers, document approved exceptions, and rerun the fail-closed gate. Track this in [issue #136](https://github.com/awasthisach/The-Perfect/issues/136). Do not bypass the gate.
2. **Native Worker acceptance and Gemini data handling:** deployment is now verified by workflow #38676382484, including health version 4 and rejection of missing Firebase credentials. Still test invalid App Check rejection and a real native request from a signed-in Android device with valid Firebase Auth + App Check. Confirm the Gemini API key belongs to a paid project with active Cloud Billing; the free tier may use prompts/documents to improve products. Review abuse-monitoring retention and privacy disclosures.
3. **OAuth and Drive access:** the app requests `drive.readonly` for corpus reads and `drive.file` for app-created backups. `drive.readonly` is restricted. Verify the production Firebase project, Android app ID/package, OAuth client, signing fingerprints, account switching/token refresh, and applicable restricted-scope verification/security-assessment and User Data Policy requirements before public release. Do not claim full-Drive access without real-account evidence.
4. **Consent and privacy:** test fresh-install consent defaults to off, query/document embedding only after consent, immediate request cancellation on revocation, deletion of persisted vectors, and local-search continuity when the Worker is unavailable.
5. **Indexing and data integrity:** exercise full-list/change-feed pagination, cap handling, trashed/removed files, interrupted page commits, expired page tokens, process death, account isolation, schema migration against existing encrypted databases, malformed/oversized imports and atomic restore rollback.
6. **Restore/security integration:** the current PR #138 source includes durable IDs, checksums, restore rollback and protected signing hardening; verify these with real-provider, interrupted-restore and signed-artifact acceptance tests.
7. **Product acceptance:** test search relevance against a labelled sample corpus, hybrid neural/FTS ranking, no-result/failure/retry behavior, offline mode, duplicate review, and all user-confirmed Drive actions on a physical device.
8. **Release:** pass current-head unit tests, lint, APK/AAB release build, hard SQLCipher instrumentation, CodeQL, CPAS and FOSSA. Verify the signed AAB/APK, signing fingerprint, SBOM and provenance. Confirm Play Console permission/policy eligibility, privacy/Data safety declarations, and staged rollout/monitoring.

## Code-level findings that remain visible in source

- `SemanticSearchPluginImpl.suggestTags()` intentionally returns an empty list. Do not advertise AI tag suggestions as implemented until a real model contract and tests exist.
- `generateEmbedding()` maps remote failures to an empty vector, and near-duplicate detection maps failures to an empty list. Ensure UI/domain state distinguishes “no matches” from “service unavailable”; otherwise the UX can silently misrepresent a network/auth failure as a valid empty result.
- `CloudflareEmbeddingClient` correctly separates Firebase ID token from Drive OAuth access token and requires App Check. The live endpoint's health response cannot verify that these credentials work end-to-end on a physical device.

## Release decision

**Not production-ready.** The current Worker deployment is verified, but FOSSA and CPAS fail closed and external OAuth/privacy/signing/device acceptance remains open. A green compile or individual workflow is not overall release approval. Keep PR #138 Draft until each blocker has reproducible evidence, all required checks pass on one integrated candidate SHA, and real-device/external-service acceptance is complete.
