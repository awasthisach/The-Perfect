# Performance budgets

**Status:** TARGET (release checklist on mid-range API 34+ device)

| Metric | Budget |
|--------|--------|
| Cold start to first frame | < 2.5 s |
| Tab switch | < 300 ms |
| Directory list (≤500 entries) | < 200 ms |
| SQLCipher open | < 150 ms |
| AES-GCM 1 MB throughput | > 10 MB/s |
| WorkManager sync | Idempotent; no unbounded loops |

`OptimizationBenchmarkTest` covers memory-trim hooks; expand device benches before public Play launch.
