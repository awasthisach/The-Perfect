# VVF Smart Manager — Release Notes

**Package:** `com.vvf.smartmanager`  
**Track:** production hardening on `main` (debug CI artifacts available; store GA conditional)

---

## 2026-09 — Cloud backup & CI hardening

### Fixed

- **Google Drive upload HTTP 400** — use `multipart/related` against `upload/drive/v3` (#115).
- **HTTP 404 `VVF_Backups`** — treat backup folder as a **name**, not a Drive `fileId` (#117).
- **`Snapshot failed for: database`** — resolve SQLCipher DB path variants; stage empty placeholder when missing; surface copy errors (#118).
- **Release workflow** — run unit tests + lint before `assembleRelease`; FOSSA fail-closed on release (#116).
- **OCR → search** — rebuild FTS after OCR text index (#116).
- **OAuth callback** — Activity-scoped Drive sign-in token application (#116).
- **CI** — Node 24 artifact actions; clearer soft emulator vs hard unit gates (#114).

### Device checklist

1. Uninstall previous debug build.
2. Install latest CI `vvf-smartmanager-debug-apk`.
3. Configure Google OAuth (Android SHA-1 + Web client ID).
4. Connect Drive → **Start cloud backup**.

---

## Product highlights (v1 line)

### File explorer & storage

- Storage overview, categories, breadcrumbs
- Batch copy / move / rename / delete / share
- Recycle bin with restore / purge

### Secure vault

- AES-GCM encrypted vault files
- Android Keystore–backed material
- PIN + biometric unlock

### Cleaner

- Duplicate detection (size / hash levels)
- Junk / cache oriented cleanup flows

### Search & on-device intelligence

- SQLite FTS4 indexing
- Tags
- ML Kit OCR plugin
- Semantic search plugin architecture (on-device)

### Cloud & plugins

- **Google Drive** core backup / restore
- Cloud-driver SPI (additional providers may be incomplete)
- WorkManager background indexing / maintenance

---

## Build / version notes

- Prefer CI debug APK from a **green** main or PR run for tester builds.
- Production/store builds require release signing secrets and `.github/workflows/release.yml` success.
- See `README.md` and `SECURITY_STATUS.md` for current policy.
