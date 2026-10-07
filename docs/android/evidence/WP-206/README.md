# WP-206 evidence

External contribution, no lease. These are local macOS runs only, not CI or acceptance
receipts. Frozen source: `db14559b39d32322b06477c6ae676112f583db50`. Implementation
commit: `52c2c53b`, with review follow-up `a0f760a1` (base `7237727f`, origin/main).
The results below are for `a0f760a1` unless a line says otherwise.

## Commands

Environment: JDK 21 (`/opt/homebrew/opt/openjdk@21`), the Android command-line SDK and
private Gradle/Android user homes. `--dependency-verification lenient` is used because
the macOS aapt2 artifacts are not in the verification metadata. Dependency locking
stayed STRICT.

```
./gradlew :core:connectivity:clean :core:connectivity:testDebugUnitTest   # x3
./gradlew :core:connectivity:lintDebug :core:runtime:test validateModuleGraph :app:assembleDebug \
  --rerun-tasks "-Dorg.gradle.jvmargs=-Xmx3g -XX:MaxMetaspaceSize=1g"   # default heap thrashed on :app
ANDROID_HOME=... python3.13 android/scaffold/inspect_apk.py
python3.13 tools/android-port/portmap.py
python3.13 tools/android-port/controller/validate.py
python3.13 -m unittest discover -s tests            # in tools/android-port
```

## Observed results

- `:core:connectivity:testDebugUnitTest`: 15 suites, 174 tests, 0 failures, 0 errors,
  0 skipped, in three clean runs. The 15 added tests are review-follow-up regressions:
  hosting start/stop order, bond timeout re-read, stale-bond routing, chooser
  correlation/late host/abandonment, CDM reason mapping, name recording, removal
  across process death, and `AssociationsChanged`. At `52c2c53b` the count was 159.
- Owned source cases: 111 in `docs/android/test-cases.json`. 110 are bound with
  `@OriginalCase` and passed in the JUnit XML: 80 source-behavior, 24 native-equivalent,
  6 platform-adaptation. The one unbound case (`clearing the surfaced-auth latch lets the
  same device re-alert()`) is blocked by missing runtime seam C-02. Per-case
  class/method/disposition/result is in [`source-cases.json`](source-cases.json).
- The other 64 tests are native WP-206 cases: CDM session, bonding/PIN, permissions,
  FGS hosting, presence, LAN binding, classification, manifest and stale-bond recovery.
- `:core:runtime:test`: 226 tests, 0 failures (unchanged module).
- `validateModuleGraph`: 30 modules, no forbidden production edges.
- `:core:connectivity:lintDebug`: 0 errors, 0 warnings. An earlier run found
  `MissingPermission` and `NewApi` findings; both were fixed before the commit.
- `portmap.py` passed; `validate.py` returned `"result": "valid"`.
- `:app:assembleDebug` succeeded and `inspect_apk.py` passed. The merged permissions are
  the 10 connectivity permissions plus the scaffold receiver permission (see WP-206.md
  C-09); APK sha256 `b9b5d340bd0b048b061dd156c7d7e16741acabd807d813349a8ff118dc0a6e78`.
- `tools/android-port` unit tests: `test_ci_evidence.py` passes (13 tests). The full
  discovery runs 231 tests with 53 errors, all environmental on this macOS host: 43 are
  `Only isolated Windows x64 and Linux x64 build hosts are supported` and 10 are a
  missing PyYAML module. None come from the changed files.

## Mutation checks

Review follow-up mutations, on `a0f760a1`, from clean builds with the tree-guarded
`wp206_review_mutate.py`. All were caught:

| Mutation | Failing tests |
| --- | --- |
| Release stops a service that has not yet started | 2 |
| Bond timeout ignores the current bond state | 1 |
| Abandoned chooser request is still launched | 1 |
| Stale-bond routing ignores `removeBond` support | 1 |
| `user_rejected` treated as a failure | 1 |
| Removal made during process death is not reported | 1 |

The original seven mutations were rerun on `a0f760a1` and all were caught again. "Auth
failure classified as generic" now fails 9 tests, including the new recovery tests. The
background-start mutation targets the new `appInForeground` check.

Original checks (on `52c2c53b`): each mutation was applied to one production file, run from a clean module build, then
restored. A git-clean check ran before and after every mutation. All seven were caught:

| Mutation | Failing tests |
| --- | --- |
| Pairing no longer stops BLE scanning | 1 |
| Cancelled pairing keeps the association | 2 |
| Auth failure classified as generic | 7 |
| Any scan-stream termination stops scanning | 1 |
| Association treated as connectable for any id | 9 |
| Association treated as a background-start exemption | 1 |
| Link state ignores Bluetooth power | 1 |

A shared scratchpad mutation script was briefly overwritten by another job. The
checks above were rerun with a uniquely named, tree-guarded script; the tree was clean
afterwards and the final forced run above was taken on that clean tree.

## Not established here

Physical-device, OEM, Robolectric-sandbox and hardware-radio behavior is not
established; see "Device-only verification" in
[`../../deviations/WP-206.md`](../../deviations/WP-206.md).
