# Release Checklist

**वर्तमान निर्णय:** **NO-SHIP**
**तारीख:** 20 नवंबर 2026
**Branch / Phase 1 source SHA:** `audit/fixes-2026-11` / `fd6a35ca49622217c876bdb3758aeafab978adbf`

यह checklist source map मात्र से PASS नहीं देती। कोई build/test/runtime/release workflow इस phase में नहीं चला।

## P0 — Release रोकने वाले gates

- [ ] Room schema export source version 5 से मेल खाता है; fresh तथा legacy migrations बिना data loss pass — **सत्यापित नहीं** (checked-in export v2)।
- [ ] SQLCipher + Keystore passphrase creation, wrong-key refusal, key loss/recovery और real DB reopen validated — **सत्यापित नहीं**।
- [ ] Google OAuth/Firebase configuration, minimal `drive.file` scope, consent/revoke और offline/error behavior runtime tests से validated — **सत्यापित नहीं**।
- [ ] Full document/OCR/chunks/fingerprints/embeddings अपने-आप external AI server पर नहीं जाते; repository-wide network/log audit और tests — **सत्यापित नहीं**। Inspected remote embedding client मौजूद है, पर reviewed search flow में call site नहीं मिला।
- [ ] Logs/secrets/artifacts में private text या credentials नहीं — **सत्यापित नहीं** (सीमित source observations को पूरे app audit न मानें)।

## P1 — Product behavior

- [ ] Full OCR text local encrypted index में सुरक्षित, long-document/chunking policy और opt-out cancellation validated — **आंशिक source path; execution नहीं**।
- [ ] Local keyword search tests pass — **सत्यापित नहीं**।
- [ ] वास्तविक on-device semantic embeddings; current production path hash-based — **पूरा नहीं**।
- [ ] Drive sync pagination/retry/cancel/revoke behavior validated — **सत्यापित नहीं**।

## P2 — Engineering and supply chain

- [ ] Wrapper/JDK/SDK/dependency resolution, debug build, unit tests, lint — **Phase 2 में बाकी**।
- [ ] Hilt या manual DI की एक स्पष्ट, tested strategy — **Phase 4 में बाकी**।
- [ ] Dependency/code cleanup हर deletion के कारण और tests सहित — **Phase 3 में शुरू नहीं**।
- [ ] ORT/CPAS/SBOM exact-SHA results, CycloneDX/SPDX parity और coverage effects — **current execution सत्यापित नहीं**।

## P3 — Device/store

- [ ] वास्तविक Android phone test — बाकी।
- [ ] Signed AAB, Play Console/Privacy disclosures, production approval — बाकी।

जब तक सभी अनिवार्य P0 gates प्रमाण सहित PASS न हों और आगे के release gates पूरे न हों, **production-ready न कहें**।
