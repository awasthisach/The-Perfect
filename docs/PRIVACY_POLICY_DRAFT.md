# Privacy Policy — Draft for Owner Review

> **NOT READY FOR PUBLICATION.** This is an engineering draft, not legal advice. Before publishing, the app owner must confirm the operating legal entity, contact details, retention periods, Cloudflare configuration, Gemini API billing tier, enabled SDKs, and Google Play Data safety answers. Replace every bracketed placeholder and obtain appropriate review.

**App:** Drive Semantic Search  
**Operator:** [Confirm legal entity / publisher name]  
**Effective date:** [Insert publication date]  
**Privacy contact:** [Insert monitored privacy/support email]  
**Public policy URL:** [Publish this policy at a stable HTTPS URL]

## 1. What the app does

Drive Semantic Search lets you connect your Google account, index information about files in your Google Drive, and search those files using keywords and, if you opt in, semantic similarity. Indexing and search are designed to run on your Android device. Some features require Google's services and, when you enable neural semantic search, an external embedding service.

## 2. Information processed

Depending on the features you use, the app may process:

- **Google Drive file metadata:** file IDs, names, MIME types, sizes, modification times, parent IDs, links, and integrity hashes. The app requests `drive.readonly` to read the user's corpus and `drive.file` for app-created backup files; it does not request full Drive-management access. `drive.readonly` is a restricted scope and requires the applicable Google OAuth verification and security-assessment review before public release.
- **Extracted text:** text extracted from supported Drive files for local indexing and search. Full-content extraction for supported document formats requires the applicable in-app consent.
- **Search queries:** the text you enter in search.
- **Semantic vectors and hashes:** numerical embeddings and content hashes generated to support semantic ranking.
- **Authentication and integrity tokens:** Google Drive OAuth credentials, Firebase Authentication ID tokens, and Firebase App Check tokens. These serve different purposes and must not be shared with one another.
- **User-directed backups and exports:** if you initiate a backup or export, the selected archive or JSON index may include file metadata and, where full-content indexing is enabled, extracted text.

The app's encrypted local database stores the index and semantic vectors. The app is configured to disable Android's automatic application backup for its private data.

## 3. Optional neural semantic search and external processing

Neural semantic search is **off until you explicitly enable it**. When enabled, the app may transmit your search query and bounded batches of extracted document text to:

1. the operator's Cloudflare Worker, which authenticates requests and validates/rate-limits them; and
2. Google's Gemini API, which generates embeddings used for semantic ranking.

The Worker receives a Firebase Authentication ID token and a Firebase App Check token for authentication and app-integrity checks. The Google Drive access token is not the Worker authentication token.

Before publication, the operator must confirm that the Gemini API project uses an appropriate paid service with active Cloud Billing, review Google's applicable data-handling and abuse-monitoring terms, and accurately describe any retention by Google or Cloudflare. **Do not publish this policy or enable production neural indexing until those facts are verified.**

If you do not enable neural semantic search, the app can continue to provide local keyword/full-text search without sending query or document text to the embedding service.

## 4. Where information is stored

- **On your device:** Drive metadata, local search index, and (if enabled) semantic vectors are stored in the app's SQLCipher-protected database. Supported offline copies are stored in app-private storage.
- **In your Google Drive:** a backup archive is created only when you initiate a cloud backup. The app does not automatically delete or trash your Drive files. A cloud backup remains in your Drive until you delete it.
- **At service providers:** neural embedding requests are processed by the configured Cloudflare Worker and Google Gemini API. The operator must verify provider-side logging, abuse-monitoring, and retention settings before release.

## 5. How you control your information

You can:

- decline neural semantic search and continue using local keyword search;
- turn off neural consent, which cancels active embedding requests and attempts to remove persisted semantic vectors;
- sign out or revoke Google access through the available account controls;
- remove local indexed data or clear the app's local data;
- delete cloud backup archives from Google Drive yourself; and
- revoke the app's Google account access through your Google Account security settings.

Consent revocation prevents future embedding use by the app. It cannot undo processing that occurred before revocation, nor can it delete a backup that you previously uploaded to your Drive.

## 6. Sharing

The app uses Google services to access Drive and authenticate your account. If neural semantic search is enabled, the query and document text described above are shared with the configured Cloudflare Worker and Gemini API solely to provide embedding-based ranking. The operator must review the actual release dependency/SDK inventory and list any additional recipients before publishing this policy.

The operator must not log bearer tokens or document text. Provider operational logs and retention must be verified rather than assumed to be zero.

## 7. Security

The app uses encrypted local database storage, HTTPS for service requests, separate credentials for Google Drive and the embedding Worker, and checksum verification during backup restore. No security measure can guarantee absolute security; keep your device and Google account protected.

## 8. Retention and deletion

Local indexed data remains on your device until you clear it, uninstall the app, or use an in-app deletion control. Semantic vectors are intended to be cleared when neural consent is revoked; this behavior must be validated on a physical release build. Cloud backup archives remain in your Google Drive until you delete them. Before publication, the operator must state verified retention periods for Cloudflare and Gemini processing/logs and for any additional enabled SDKs.

## 9. Children's privacy

[Owner must confirm the app's intended age group and applicable child-privacy obligations. Do not publish this section until reviewed.]

## 10. Changes to this policy

The operator may update this policy when processing changes. The published version will show its effective date and will be linked from the app's store listing and in-app privacy information.

## 11. Contact

For privacy questions or requests, contact: **[Insert monitored privacy/support email and legal entity name]**.

---

## Owner publication checklist

- [ ] Replace all placeholders; confirm legal entity, privacy contact and effective date.
- [ ] Confirm actual Cloudflare and Gemini settings, paid-service status, abuse-monitoring retention and logs.
- [ ] Inventory all release SDKs and update the recipients/sharing section.
- [ ] Confirm Google OAuth restricted-scope verification and any required security assessment.
- [ ] Reconcile this policy with the Play Data safety form, in-app consent screens and release privacy/data disclosures.
- [ ] Publish at a stable HTTPS URL and add that URL to Google Play Console.
- [ ] Obtain owner/legal review and test account deletion, consent revocation and local data clearing on a physical device.
