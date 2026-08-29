# VVF Smart Manager — Security Whitepaper (summary)

## Threat model (high level)

- Device loss / untrusted local access → vault and DB encryption
- Network observers → TLS only (cleartext disabled)
- Cloud provider → app uses least privilege Drive scope `drive.file` when OAuth wired
- Supply chain → dependency scanning (Dependabot / FOSSA when configured)

## Controls

1. **At-rest:** SQLCipher database; vault files encrypted (AES-GCM path in security module); Android Keystore-backed material where implemented.
2. **In-transit:** HTTPS only via network security config.
3. **AuthN/Z:** Biometric for vault unlock; Google OAuth for Drive (operator-configured).
4. **Backup:** `allowBackup=false`; extraction rules restrict sensitive data.
5. **Logging:** OkHttp body logging disabled by default to avoid token leakage.

## Reporting

See `SECURITY.md` — private GitHub Security Advisories preferred.

## Limitations

- Full Hilt-based DI not yet enabled (AGP 9 compatibility).
- OAuth client configuration is operator-owned; without tokens, Drive APIs correctly fail closed.
