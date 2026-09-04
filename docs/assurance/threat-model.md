# Threat model (compact)

**Status:** CURRENT

## Trust boundaries

1. App-private storage + Keystore  
2. Shared external storage (user files)  
3. Google Drive (after OAuth)  
4. In-process plugins (SPI)

## Priority abuse cases

| ID | Abuse | Mitigation |
|----|-------|------------|
| T-01 | Path traversal outside approved roots | `StoragePathPolicy` fail-closed |
| T-02 | Backup exfiltration of vault | `allowBackup=false`; encrypted DB |
| T-03 | Blank/forged cloud remote ID | `DurableUploadContract` |
| T-04 | Partial restore corruption | `FailClosedRestorePipeline` + rollback |
| T-05 | Permission denied silent empty UI | Gate → `needsStoragePermission` |
| T-06 | Token leakage in logs | No tokens in log messages |

Residual: live Drive E2E under `WAIVER-021-CLOUD-E2E`.
