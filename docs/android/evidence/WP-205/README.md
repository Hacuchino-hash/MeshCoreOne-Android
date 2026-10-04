# WP-205 native BLE component evidence

Repository: `cbattlegear/MeshCoreOne-Android`. Owner: `connectivity-engineer`.
Owning branch: `cbattlegear-curly-guide`. Execution session:
`09d597dd-d530-44a6-bdfa-32ad1b81bfc4`; native project alias:
`0bdc2276-e010-4c61-ad20-e523bed48a7d`.

**Candidate implementation, not completion/parity certification.** The real
production module and debug APK build. BLE assertion execution is currently
blocked by the existing strict module lock and the narrowly requested additional
test SDK admission. No planned/native test is counted as passed.

| Binding | Actual value |
| --- | --- |
| Verified clean initial merged main | `0ae606992bf58c8d3f2bf08b7a26406c97f12f84` |
| Actual WP-108 prerequisite merge/ancestor | `eaf0fdb956afcb20e2de3d7d6550e0cbeeb50730` |
| Read-only source commit | `db14559b39d32322b06477c6ae676112f583db50` |
| Read-only source tree | `8918fdc604341e6996a68c88f6bb1c02b9c2f87e` |
| Semantic manifest | `78a22920beaa5899f9618806b5cd2b27d50399a9b29b4d8dbd79f755717ec746` |
| Semantic policy | `56bdc53548bc86d631245795dfa38b4fc86048e0e7cbe1c7d5695879b035b42a` |
| Coordinator prompt lease | `autonomous-WP-205-0ae60699` |

[Authorization](authorization.json) records the explicit coordinator message;
it is not a fabricated dispatcher/shared-gate receipt.

## Implemented scope and accounting

`BleTransport` implements the existing protocol `MeshTransport` and immutable
`Bytes`. `AndroidGattFacade`/`PlatformGattApi` call the real public Android GATT
API, with API31/32, API33+ and actual API37 settings branches. Connection and
full GATT/write operations use separate whole-operation locks; callbacks carry
the original key and match exact GATT/attribute identity. Timers and cleanup
are owned/awaited, with typed failures and cancellation preserved.

NUS/CCCD validation, actual MTU/frame guards, immutable single-ingestion
per-generation streams, whole writes, opt-in capabilities, bond/power/permission
recovery, explicit RSSI and refresh-only live-generation/verification-epoch
bookkeeping are implemented. Scanner/pairing UI, CDM, background service/runtime
and complete session/application assembly are not fabricated.

[source-cases.json](source-cases.json) catalogs all **27 owned inputs and 86
frozen declarations**, plus each actual native assertion method. It is generated
from the immutable case/inventory inputs, not candidate test output or altered
goldens. Original iOS retry/restoration semantics are proposed native adaptations,
and process-store bond race proof remains explicitly separate. Headers and
method names are not passing execution evidence.

The [Nordic spike](nordic-spike.md) binds actual source/POM/license hashes and
official API signatures. No Nordic dependency/binary or hidden refresh is linked.
[Adaptations](../../deviations/WP-205.md) and
[human scenarios](hardware-scenarios.md) preserve the unresolved product/platform
decisions without claiming hardware or independent approval.

## Actual local commands and results

The private provisioner is actually
`tools\android-port\controller\ci.py`; the kickoff's
`tools\android-port\scaffold\` directory does not exist at this base.
Every Gradle shell redeclares private pinned JDK/SDK/user caches; the existing
launcher strips credentials and uses one worker/in-process Kotlin, build640m,
metaspace512m, test256m/metaspace256m, SerialGC and two processors.

| Actual command | Actual result |
| --- | --- |
| Stripped launcher `validateModuleGraph` before provisioning | **Blocked**, JDK/SDK variables unset; Gradle was not started |
| `python .\tools\android-port\controller\ci.py provision --root <new-private-directory> --accept-sdk-license` | **Passed**, checksum-pinned first-party direct archives only; no Android CLI/bootstrap/global configuration |
| `python .\tools\android-port\controller\ci.py preflight --state <private-environment.json> --output <private-evidence> --local` | **Passed**, exact Python3.12.4/JDK21.0.12.1+1/SDK37.2r1/build-tools37.0.0 |
| Launcher `:core:ble:testDebugUnitTest --tests com.meshcoreone.android.core.ble.BleValuesTest --dependency-verification strict --no-build-cache --quiet` | **Failed before discovery** at `checkDebugUnitTestAarMetadata`: the declared existing test-only Robolectric/helper graph is absent from the module lock. **0 BLE tests executed**, not a passing zero-test run |
| Launcher `:core:ble:compileDebugKotlin --dependency-verification strict --no-build-cache --quiet` | **Passed** actual native production compilation against API37.2 |
| Launcher `:core:ble:compileDebugKotlin :app:assembleDebug --dependency-verification strict --no-build-cache --quiet` | **Passed** latest real BLE/RSSI/bond/teardown code and actual debug APK |
| Incidental existing locale pre-build source verifier during assembly | **74 existing locale Python assertions passed**; these are not BLE assertions |
| `python .\tools\android-port\controller\validate.py` | **Passed** complete 1,866-file source/ownership/manifest pin |
| `python .\tools\android-port\portmap.py` | **Passed** current source provenance after repairing an initial conflicting mixed header |
| `python .\docs\android\evidence\WP-205\source-map.py --check` | **Passed** exact 27 inputs/86 declarations and present native assertion symbols; not execution or parity |
| `python .\android\scaffold\inspect_apk.py` | **Passed** actual debug identity/min31/target37/launcher/notices/fixture exclusion |
| Actual ZIP/DEX scan | **Passed**, all real BLE/GATT/NUS/bond classes packaged and Robolectric/fake/test fixtures absent |
| Existing `controller.apk_alignment.inspect_alignment` with the actual APK/private SDK | **Passed**, zipalign `-P 16` and all four ELF PT_LOAD sets; no physical/native-runtime proof |
| `git diff --check`, immutable source-root diff and complete leased write-path validator | **Passed** no source/manifest/policy/shared path edits |

The measured debug APK SHA-256 is
`10bc9863fbf5510e6648df1f406d5742e1e1033cffc464e5d94e675fd1d178fc`.
Raw build/failure logs are retained as deterministic `logs/*.log.gz`, whose
decompression reproduces the complete unchanged runner bytes including its
progress-line whitespace; original and stored hashes are both recorded.
Actual inspection/alignment
results are in `apk-inspection.json` and `apk-alignment.json`. Their current
source/byte hashes are bound separately; final exact-head hosted proof is still
required. The APK/tool caches themselves are not committed.

## Actionable admission/hosted blockers

[dependency-request.json](dependency-request.json) preserves the actual
pre-discovery failure and narrow module-lock request; no strict verification
was disabled, no lock edited and no failure replaced with a success summary.
[sdk-test-admission-request.json](sdk-test-admission-request.json) requests
only the source-declared, published-checksummed SDK32/33/37 test artifacts so
their native API branches run on their actual framework, not a fake API number
on SDK31. None has been downloaded/executed or admitted yet.

Within the leased module build file, root `verifyScaffoldTests` now depends on
the actual `:core:ble:testDebugUnitTest` task. The coordinator separately owns
generic raw XML copying/counter replay/source bindings in trusted CI; its local
commit is **not** substituted for a merged-main prerequisite. Final publication
must reconcile only this branch with the actual merged helper and record
same-head Windows/Linux run/attempt/artifact proof. There is no hosted BLE,
independent review, hardware, iOS, protected-gate or release/signing result yet.

Canonical WP-205 manifest verification remains historically unconfigured.
This candidate does not rewrite the plan/policy/reference or invent a completion
receipt to conceal that fact.
