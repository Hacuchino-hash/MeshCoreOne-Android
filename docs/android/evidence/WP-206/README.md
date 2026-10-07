# WP-206 evidence

External contribution, no lease. These are local macOS runs only, not CI or acceptance
receipts. Frozen source: `db14559b39d32322b06477c6ae676112f583db50`. Implementation
commit: `52c2c53b` (base `7237727f`, origin/main).

## Commands

Environment: JDK 21 (`/opt/homebrew/opt/openjdk@21`), the Android command-line SDK and
private Gradle/Android user homes. `--dependency-verification lenient` is used because
the macOS aapt2 artifacts are not in the verification metadata. Dependency locking
stayed STRICT.

```
./gradlew :core:connectivity:clean :core:connectivity:testDebugUnitTest   # x3
./gradlew :core:connectivity:clean :core:connectivity:testDebugUnitTest \
  :core:connectivity:lintDebug :core:runtime:test validateModuleGraph --rerun-tasks
python3.13 tools/android-port/portmap.py
python3.13 tools/android-port/controller/validate.py
```

## Observed results

- `:core:connectivity:testDebugUnitTest`: 14 suites, 159 tests, 0 failures, 0 errors,
  0 skipped. Three clean runs and one final `--rerun-tasks` run, all identical.
- Owned source cases: 111 in `docs/android/test-cases.json`. 110 are bound with
  `@OriginalCase` and passed in the JUnit XML: 80 source-behavior, 24 native-equivalent,
  6 platform-adaptation. The one unbound case (`clearing the surfaced-auth latch lets the
  same device re-alert()`) is blocked by missing runtime seam C-02. Per-case
  class/method/disposition/result is in [`source-cases.json`](source-cases.json).
- The other 49 tests are native WP-206 cases: CDM session, bonding/PIN, permissions,
  FGS hosting, presence, LAN binding, classification and manifest.
- `:core:runtime:test`: 226 tests, 0 failures (unchanged module).
- `validateModuleGraph`: 30 modules, no forbidden production edges.
- `:core:connectivity:lintDebug`: 0 errors, 0 warnings. An earlier run found
  `MissingPermission` and `NewApi` findings; both were fixed before the commit.
- `portmap.py` passed; `validate.py` returned `"result": "valid"`.

## Mutation checks

Each mutation was applied to one production file, run from a clean module build, then
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
