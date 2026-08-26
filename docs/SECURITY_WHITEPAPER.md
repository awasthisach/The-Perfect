# VVF Smart Manager — Security & Privacy Whitepaper

## 1. Zero-Knowledge Architecture & Privacy Guarantees
VVF Smart Manager is designed with an **Offline-First & Zero-Knowledge** security posture:
- **No Telemetry / No Tracking**: No user files, filenames, search queries, or biometric credentials are ever sent to remote tracking servers.
- **On-Device AI Inference**: All OCR text recognition and TensorFlow Lite semantic vector embeddings execute 100% locally on the user's device without cloud dependencies.
- **Standalone Integrity**: The application remains fully functional even in total airplane/offline mode.

---

## 2. Cryptographic Specifications

### A. Secure Vault (`:core:security` & `:feature:vault`)
- **Cipher Algorithm**: `AES/GCM/NoPadding` (256-bit encryption key).
- **Key Derivation**: 100,000 rounds of `PBKDF2WithHmacSHA256` with a secure random 32-byte salt (`SecureRandom`).
- **Initialization Vector (IV)**: Fresh 12-byte cryptographically secure random nonce generated per encrypted file item.
- **Storage Isolation**: Encrypted files are stored inside the protected `context.filesDir` sandbox with randomized `.vvfvault` extensions, preventing any third-party app access.
- **Hardware-Backed Protection**: Android Keystore integration protects master keys using hardware security modules (TEE / StrongBox) when available.

### B. Database Encryption (SQLCipher)
- SQLite database tables storing file metadata, vault items, and tags are encrypted using SQLCipher 4.5+ AES-256-CBC.

### C. Biometric Authentication
- Biometric authentication leverages AndroidX `BiometricPrompt` with `BIOMETRIC_STRONG` crypto objects to protect against PIN brute-force attempts and enforce rate-limiting.
