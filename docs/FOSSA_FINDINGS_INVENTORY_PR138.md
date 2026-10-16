# Historical FOSSA license-policy inventory — PR #138

**Repository:** `awasthisach/The-Perfect`  
**Candidate revision scanned:** `59e712d045beffde424dae4c56febe6f0562e74b`  
**Evidence:** [FOSSA CI run #38979011280](https://github.com/awasthisach/The-Perfect/actions/runs/38979011280), [diagnostics artifact](https://github.com/awasthisach/The-Perfect/actions/runs/38979011280/artifacts/12080212594), [blocker issue #136](https://github.com/awasthisach/The-Perfect/issues/136).  
**Status:** ARCHIVED HISTORICAL EVIDENCE — the paid FOSSA gate was removed from CI and replaced by the open-source OSS Review Toolkit (ORT). This report documents 34 active license-policy findings on the older scanned revision; it is not a current scan and does not prove those findings are resolved. ORT must now be run and its current reports reviewed.

## Package-level inventory

| Maven coordinate | Graph depth | Declared license(s) in FOSSA export | Active issue IDs and detected licenses | Match-path evidence |
|---|---:|---|---|---|
| `com.google.firebase:firebase-auth:24.2.0` | 1 (direct) | `android-sdk` | 21614411 APSL-2.0 (conflict); 21614451 CPL-1.0; 21614463 GPL-2.0-only; 21614481 Apache-2.0 WITH llvm-exception; 21614546 android-sdk; 21614555 EPL-1.0 | The extra license signatures match `third_party_licenses.txt`; android-sdk is also a declared license. |
| `com.google.mlkit:common:18.11.0` | 2 (transitive) | `ml-kit-terms-of-service` | 21614402 APSL-2.0 (conflict); 21614468 oracle-openjdk-classpath-exception-2.0; 21614483 NPL-1.1; 21614538 LGPL-2.1-or-later; 21614545 CPL-1.0; 21614550 GPL-2.0-only; 21614551 LGPL-2.1-only | All reported issue signatures match `third_party_licenses.txt`. |
| `com.google.firebase:firebase-auth-interop:20.0.0` | 2 (transitive) | `android-sdk` | 21614455 android-sdk | The finding is a policy flag on the declared `android-sdk` license; not a bundled-notice-only mismatch. |
| `jakarta.inject:jakarta.inject-api:2.0.1` | 2 (transitive) | `Apache-2.0`, `GPL-2.0-with-classpath-exception` | 21614420 GPL-2.0-with-classpath-exception | The package itself declares both licenses; the policy finding is on the GPL-with-classpath-exception option. Apache-2.0 is also declared, but no FOSSA correction has been made. |
| `com.google.android.play:core-common:2.0.4` | 3 (transitive) | FOSSA reports multiple discovered license signatures plus `play-core-sdk-tos`; see issue IDs | 21614407 APSL-2.0 (conflict); 21614423 CPL-1.0; 21614428 oracle-openjdk-classpath-exception-2.0; 21614444 GPL-2.0-only; 21614469 LGPL-2.1-only; 21614477 NPL-1.1; 21614495 LGPL-2.1-or-later | The license matches include `third_party_licenses.txt`, `LICENSE`, and `DECLARED_LICENSE`; the exported metadata alone does not establish which licenses apply to shipped code. |
| `com.google.mlkit:vision-common:17.3.0` | 3 (transitive) | `ml-kit-terms-of-service` | 21614437 GPL-2.0-only; 21614476 LGPL-2.1-or-later; 21614536 NPL-1.1; 21614543 LGPL-2.1-only | All reported issue signatures match `third_party_licenses.txt`. |
| `com.google.mlkit:vision-interfaces:16.3.0` | 3 (transitive) | `ml-kit-terms-of-service` | 21614404 APSL-2.0 (conflict); 21614408 GPL-2.0-only; 21614418 LGPL-2.1-or-later; 21614434 CPL-1.0; 21614467 NPL-1.1; 21614482 GPL-2.0-or-later; 21614499 LGPL-2.1-only; 21614535 oracle-openjdk-classpath-exception-2.0 | All reported issue signatures match `third_party_licenses.txt`. |

## Evidence interpretation and required actions

1. The current scan includes all seven coordinates in the resolved release dependency graph. They must not be dismissed as test-only dependencies.
2. For ML Kit, the published artifact metadata identifies the ML Kit Terms of Service: [ML Kit Terms](https://developers.google.com/ml-kit/terms), and the package listing reports the same for [`common:18.11.0`](https://mvnrepository.com/artifact/com.google.mlkit/common/18.11.0) and [`vision-interfaces:16.3.0`](https://mvnrepository.com/artifact/com.google.mlkit/vision-interfaces/16.3.0).
3. Play Core's published terms are [Play Core SDK Terms of Service](https://developer.android.com/guide/playcore/license); the package listing reports that license for [`core-common:2.0.4`](https://mvnrepository.com/artifact/com.google.android.play/core-common/2.0.4).
4. The shared `third_party_licenses.txt` match path is evidence of bundled notice attribution, **not proof by itself** that a finding is a false positive. Inspect the exact artifact contents and upstream package/POM metadata before correcting license attribution.
5. The Jakarta Inject finding is different: its package metadata declares both Apache-2.0 and GPL-2.0-with-classpath-exception. Review the applicable dual-license choice in the package's upstream metadata. If detected license data is correct but one license should govern this project/revision, use a project/revision-scoped license conclusion; if the detected data itself is wrong, a correction is the appropriate tool but FOSSA documents corrections as organization-wide across projects and versions. See [FOSSA's conclusions-vs-corrections guidance](https://docs.fossa.com/docs/help/license-conclusions-vs-corrections). No correction or conclusion has been made.
6. The `android-sdk` finding on `firebase-auth-interop` is a policy flag on the declared license and needs an explicit policy decision; it is not solved by changing bundled-notice attribution.
7. Do not exclude `third_party_licenses.txt` globally, blanket-allow licenses, suppress findings, or close `PROD-008` to make CI green. No FOSSA package corrections or dispositions were applied. FOSSA dashboard workflow is no longer part of CI; use the ORT scan and independently verify package/source evidence.

## Historical closure criteria (retain for traceability; active gate is ORT)

Use the replacement ORT workflow for the current candidate and do not treat this archived report as a current scan.

## Closure gate

- [ ] Review the seven Maven artifacts and POM/license metadata for the exact scanned versions.
- [ ] Determine which detected notices correspond to code actually redistributed by each artifact versus aggregate notice text.
- [ ] Correct only evidenced attribution errors; otherwise replace/remove incompatible runtime components or document a narrow, approved exception with owner, rationale, and expiry.
- [ ] Verify required notices are included in the release artifact.
- [ ] Re-run `fossa analyze` and `fossa test` on the final candidate SHA; require a green license gate and durable disposition for every finding.
- [ ] Update `PROD-008` only after evidence-backed closure.
