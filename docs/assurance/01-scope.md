# Product Scope

## In scope

- Offline-first Android file manager (browse, list, organize local storage)
- Encrypted vault (AES-GCM, Android Keystore key management)
- Privacy-focused productivity features (cleaner, search, plugins)
- Optional cloud backup/restore (Google Drive) with fail-closed restore
- Continuous Production Assurance (CPAS) evidence and gates

## Out of scope (current release train)

- Multi-user / enterprise MDM management
- Non-Android platforms
- Server-side vault hosting
- Guaranteeing third-party cloud SLA beyond documented contracts

## Supported platform bounds

See `04-technology-registry.yaml` (minSdk 24, target/compile SDK 36, JDK 17).
