# Drive Semantic Search — current production-readiness audit

Scope: `awasthisach/The-Perfect`, candidate branch `fix/drive-semantic-search-product-alignment`. This document records source and CI evidence; it does not declare the app production-ready.

## Implemented in the candidate source

- Native Kotlin/Jetpack Compose application; no WebView wrapper.
- Google Drive OAuth integration, account matching with Firebase Auth, paginated Drive indexing and changes-feed handling.
- Room/SQLCipher-backed Drive index and 768-dimensional embedding-vector storage.
- Real `gemini-embedding-2` neural embedding adapter in `CloudflareEmbeddingClient.kt`; the client sends a Firebase Auth ID token and Firebase App Check token, not the Drive access token or Gemini key.
- Consent-gated embedding, bounded batches, vector caching/persistence, cancellation, and local FTS/metadata fallback.
- Cloudflare Worker source with Firebase JWT and App Check verification for native calls without `Origin`, per-UID Durable Object rate limiting, bounded request payloads, response contract validation and upstream retry handling.
- Bounded text extraction, resumable page checkpoints, offline pinning, and JSON index import/export that excludes tokens, API keys and file bytes.
- Restore checksum verification, cleanup/rollback protections and release-signing hardening are present on separate draft PR #134 and still need to be reconciled with this candidate before release.

## Current verified external/CI facts

- Live Worker health endpoint was fetched on 2026-10-14 and returned `{"ok":true,"model":"gemini-embedding-2","version":"3","dimension":768}`.
- This health response proves the public health contract only. It does **not** prove the updated native App Check path is deployed or that an authenticated Android `POST /embed` succeeds.
- CI run [#1081](https://github.com/awasthisach/The-Perfect/actions/runs/38548792319) on an earlier candidate passed unit tests, lint and debug APK assembly but failed Open Source License Compliance. FOSSA reported 192 issues and said the configured push-only API key cannot display issue details.
- Current candidate checks are linked from [Draft PR #138](https://github.com/awasthisach/The-Perfect/pull/138). Every code change creates a new candidate SHA; only the latest completed run for the current head can be counted as release evidence. Do not treat prior runs or external FOSSA status badges as proof that the current candidate passed.
- CPAS Production Gate passed for the newer candidate. That is one required gate, not overall release approval.
- The public repository's `main` advanced by one commit after PR #130's merge base. GitHub currently reports PR #130 as not mergeable; it must be reconciled with current `main` before merge.
- PR #134 is still Draft and separate. Its restore/signing changes are not yet proven integrated with PR #130.

## Remaining release blockers

1. **FOSSA findings:** provide a narrowly scoped key that can read issue details, review all reported findings, fix incompatible/unknown license or vulnerability blockers, document approved exceptions, and rerun the fail-closed gate. Track this in [issue #136](https://github.com/awasthisach/The-Perfect/issues/136). Do not bypass the gate.
2. **Worker deployment and Gemini data handling:** run the reviewed manual deployment workflow only after confirming Cloudflare and Firebase App Check secrets are correctly configured. Verify the deployed health contract and missing-credential rejection. Then test a real native request from a signed-in Android device with valid Firebase Auth and App Check. Confirm the Gemini API key belongs to a paid project with active Cloud Billing; the free tier may use prompts/documents to improve products. Review abuse-monitoring retention and privacy disclosures.
3. **OAuth and Drive access:** verify the production Firebase project, Android app ID/package, OAuth client, signing certificate fingerprints, account switching and token refresh. Confirm the granted Drive scope actually matches the promised corpus; do not claim full-Drive access without proof.
4. **Consent and privacy:** test fresh-install consent defaults to off, query/document embedding only after consent, immediate request cancellation on revocation, deletion of persisted vectors, and local-search continuity when the Worker is unavailable.
5. **Indexing and data integrity:** exercise full-list/change-feed pagination, cap handling, trashed/removed files, interrupted page commits, expired page tokens, process death, account isolation, schema migration against existing encrypted databases, malformed/oversized imports and atomic restore rollback.
6. **Restore/security integration:** reconcile PR #134's durable IDs, checksums, restore rollback and signing hardening with this branch without overwriting the newer Drive indexing/embedding implementation. Rerun tests on the integrated candidate.
7. **Product acceptance:** test search relevance against a labelled sample corpus, hybrid neural/FTS ranking, no-result/failure/retry behavior, offline mode, duplicate review, and all user-confirmed Drive actions on a physical device.
8. **Release:** pass current-head unit tests, lint, APK/AAB release build, hard SQLCipher instrumentation, CodeQL, CPAS and FOSSA. Verify the signed AAB/APK, signing fingerprint, SBOM and provenance. Confirm Play Console permission/policy eligibility, privacy/Data safety declarations, and staged rollout/monitoring.

## Release decision

**Not production-ready.** A green compile or CI badge is not sufficient. Keep PRs in Draft until each blocker above has reproducible evidence, all required checks pass on one integrated candidate SHA, and real-device/external-service acceptance is complete.
