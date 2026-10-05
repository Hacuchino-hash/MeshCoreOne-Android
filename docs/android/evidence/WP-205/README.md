# WP-205 native BLE component evidence

Repository: `cbattlegear/MeshCoreOne-Android`. Owner: `connectivity-engineer`.
Owning branch: `cbattlegear-curly-guide`. Execution session:
`09d597dd-d530-44a6-bdfa-32ad1b81bfc4`; native project alias:
`0bdc2276-e010-4c61-ad20-e523bed48a7d`.

**Implemented native component with actual assertion evidence, not hardware or
full-product certification.** The complete BLE suite has **295
discovered/run/passed, 0 failed/errors/skipped**, including real
SDK31/32/33/37 framework sandbox bodies. Final BLE lint has zero warnings/errors.
The normal root graph/schema/runtime resolution and real debug APK pass.
Historical missing-lock, checksum-binding, test compilation, API37 bootstrap,
code-cache and review-repair failures remain distinct from these results,
including the reproduced pre-submission timeout defect at reviewed head92.

The coordinator subsequently verified merged main
`dc15f1ba445acf3230383ea68d4827c592f3fafa`. Only this owning branch was
rebased onto it, and the scoped timeout repair was restored unchanged.
The 295-case local proof above is preserved as **pre-dc15 integration proof**,
not a passing integrated run. The first normal integrated root attempt failed
in private-JDK readiness before Gradle started during shared-host paging/memory
exhaustion. Read-only replay also reproduced the merged WP-201 collector's
whole-repository ownership assertion rejecting legitimate WP-205 paths.
Neither its unleased script nor global tool/storage settings were changed;
final integrated root and exact-head Windows/Linux proof remain required.

| Binding | Actual value |
| --- | --- |
| Verified clean initial merged main | `0ae606992bf58c8d3f2bf08b7a26406c97f12f84` |
| Historical coordinator-verified reconciled main | `0394b83c9b47fa0d7198e2d631f7ddb313cd370d` |
| Latest coordinator-verified merged main | `dc15f1ba445acf3230383ea68d4827c592f3fafa` |
| Independently reviewed head before timeout follow-up | `92a90e74025e58e0046e71b6dae9e79d04232022` |
| Rebased byte-identical implementation | `fa0aa6f71d589236f28004cd42c5c92541fa8698` |
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
method names alone are not passing execution evidence.
[source-case-execution.json](source-case-execution.json) now binds each of the
86 original declarations to actual passing JUnit classname/method/artifact
identities, without turning a native adaptation or deferred consumer test into
claimed iOS/product parity. [verified-results.json](verified-results.json)
records all thirteen complete `junit/TEST-*.xml` reports, exact counters,
current production inputs, lint and original/stored byte hashes.

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
| Launcher `:core:ble:dependencies --write-locks --dependency-verification strict --no-build-cache --quiet` after the concrete receipt | **Passed**, only leased BLE lock content changes; exact six approved SDK JAR/POM verification entries, no other version/checksum/component added |
| First actual full native run `:core:ble:testDebugUnitTest --dependency-verification strict --no-build-cache --rerun-tasks --quiet` | **291 discovered/run,263 passed,28 failed,0 errors/skipped**: real API37 bodies initially blocked by JDK FileDescriptor access; raw evidence preserved, not skipped |
| API37 selector after BLE-test-only JPMS export | **28 discovered/run/passed,0 failed/errors/skipped** on the actual API37 runtime |
| Eager-clock reproduction `:core:ble:testDebugUnitTest --tests com.meshcoreone.android.core.ble.BleReviewRegressionTest --dependency-verification strict --no-build-cache --quiet` against reviewed production | **4 discovered/run,3 passed,1 failed,0 errors/skipped**; zero RSSI submissions followed by the healthy-link assertion failure |
| Targeted launcher `:core:ble:testDebugUnitTest --tests com.meshcoreone.android.core.ble.BleReviewRegressionTest --tests com.meshcoreone.android.core.ble.BleOperationFailureTest --tests com.meshcoreone.android.core.ble.BleLifecycleTest --dependency-verification strict --no-build-cache --quiet` | **66 discovered/run/passed,0 failed/errors/skipped**, all three raw suites retained |
| Final launcher `:core:ble:testDebugUnitTest --dependency-verification strict --no-build-cache --rerun-tasks --quiet` after the timeout follow-up | **295 discovered/run/passed,0 failed/errors/skipped**, complete thirteen raw suites |
| Final separate launcher `:core:ble:lintDebug --dependency-verification strict --no-build-cache --quiet` | **Passed**, zero warnings/Error/Fatal after explicit actual API guards and typed local operation overlap |
| Final launcher `verifyScaffoldTests verifyRoomSchema validateModuleGraph runtimeDependencyInventory resolveScaffoldDependencies :app:assembleDebug --dependency-verification strict --no-build-cache --quiet` | **Passed**, actual root hook executes BLE, normal graph/schema/strict artifacts and final debug APK |
| `git diff --check`, immutable source-root diff and complete leased write-path validator | **Passed** no source/manifest/policy/shared path edits |
| `python .\tools\android-port\controller\test_runner.py --quiet` after reconciliation | **187 discovered/run/passed, 0 failed/errors/skipped**, merged controller fixtures only, not BLE credit |
| `python .\tools\android-port\controller\verification_config.py --check` and `python .\tools\android-port\controller\workflows.py` after reconciliation | **Passed** unchanged canonical verification and workflow trust boundaries |

The historical initial measured debug APK SHA-256 is
`10bc9863fbf5510e6648df1f406d5742e1e1033cffc464e5d94e675fd1d178fc`.
The historical reviewed-head92 local APK SHA-256 is
`0329037ea42775cded768fe162af3e8b0437265ab1750b623b1593891e5e716e`.
The final timeout-follow-up local APK SHA-256 is
`9c3899dd72c39ab6df19a667f54f4510dd5b4e8a5b7c0af7f489d644cb9ddadc`,
with actual debug/min31/target37/notices/native static alignment and test-only
SDK/helper/fixture exclusion in `final-apk-inspection.json` and
`final-apk-alignment.json`.
Raw build/failure logs are retained as deterministic `logs/*.log.gz`, whose
decompression reproduces the complete unchanged runner bytes including its
progress-line whitespace; original and stored hashes are both recorded.
Actual inspection/alignment
results are in `apk-inspection.json` and `apk-alignment.json`. Their current
source/byte hashes are bound separately; final exact-head hosted proof is still
required. The APK/tool caches themselves are not committed.

## Admission, independent repair and honest remaining gates

[dependency-request.json](dependency-request.json) preserves the actual
pre-discovery failure and narrow module-lock request; no strict verification
was disabled, no lock edited and no failure replaced with a success summary.
[sdk-test-admission-request.json](sdk-test-admission-request.json) requests
only the source-declared, published-checksummed SDK32/33/37 test artifacts.
The explicit coordinator receipt on the same session/lease now admits exactly
the BLE lock and those six root verification entries. The initial worker
SDK33/37 JAR/POM tuple association was wrong; the coordinator independently
checked all archive/POM bytes, versions, license and absence of dependencies.
Corrected values are used, while the rejected old mapping remains recorded.
All four actual framework JARs are now independently hash/size verified and
executed offline. No new production dependency or APK notice obligation is
invented by these unpackaged test inputs.

Within the leased module build file, root `verifyScaffoldTests` now depends on
the actual `:core:ble:testDebugUnitTest` task. The coordinator separately owns
generic raw XML copying/counter replay/source bindings in trusted CI. That
follow-up actually merged at the coordinator-verified main
`0394b83c9b47fa0d7198e2d631f7ddb313cd370d`, and only this owning branch was
rebased onto it. BLE production was byte-identical at that reconciliation;
subsequent bounded independent-review repairs are recorded separately.

[Reconciliation](reconciliation.json) preserves the actual old-head failure:
Android scaffold CI run37184367506/attempt1 at
`7f07d3b47ed341aca2c88fd4483e552014637dbb` failed on both Windows and Linux
at the same strict BLE-lock check before discovery. Exact jobs/artifact
IDs/archive hashes are recorded; that run is not claimed green. The normal
implementation-candidate PR is
[the existing review](https://github.com/cbattlegear/MeshCoreOne-Android/pull/18).
The merged helper alone did not admit the dependencies; the subsequent explicit
serialized receipt is recorded in `authorization.json`. The Gradle lock writer's
LF-only working-tree side effects on33 other locks were restored to their exact
pre-command SHA only after proving no content changed; all63 unleased lock
bytes remain unchanged. No unrelated lock is authored or staged.

Independent read-only review of frozen7f identified four real bugs. All are
fixed with actual regressions: RSSI budget begins after queue admission;
disconnect wakes old pacing before a fresh connect; connection-state/HCI
status8 remains timeout regardless of pending operation while genuine ATT8
remains authorization failure; mixed-case addresses reach the actual adapter
as uppercase. Source expectations come from the exact Nordic/API status-domain
definitions, not candidate output. The old synthetic ATT “connect/RSSI”
parameter rows were replaced by correct procedure-domain rows and actual
connection-state regressions, not ignored or weakened.

[unsubmitted-timeout-repair.json](unsubmitted-timeout-repair.json) records the
later same-review follow-up, its genuine failed reproduction, all66 targeted
and295 complete passing cases, actual production hashes and current APK/lint.
A timer may complete synchronously before facade invocation. The skipped
request still throws its typed timeout, removes its pending waiter and joins
the timer, but does not close a healthy READY link. The RSSI regression proves
zero submissions, connected/close0/pending0/timer completion and a subsequent
actual submitted callback completing on the same generation. The write
equivalent and all four unsubmitted startup phases are also exercised.
Entering the facade remains conservatively ambiguous even if it throws;
submitted timeout/cancellation, bond loss and incomplete setup still close.
Complete failed and targeted XML are retained in their separate historical
directories, not merged into the current full-suite counters.

| Final suite | Actual cases passed |
| --- | ---: |
| API31 framework | 30 |
| API32 framework | 30 |
| API33 framework | 30 |
| API37 framework/settings | 31 |
| Bond refresh | 15 |
| Discovery | 15 |
| ATT status families | 28 |
| Lifecycle | 18 |
| Operation adversarial families | 42 |
| Independent review regressions | 6 |
| Notification/receiver generations | 12 |
| Values/source defaults | 21 |
| Whole-write/capability/pacing | 17 |
| **Total** | **295** |

Local raw module collector/replay and final same-head hosted Windows/Linux
schema2 proof are recorded separately after their exact candidate/run bindings,
not inferred from the local totals. Three source process-store consumer proofs,
proposed platform adaptations and physical API31/37/OEM/bond/MTU scenarios
remain explicitly separate. No hardware, iOS BLE, full-license/protected human
gate, privileged reviewer/publisher or release/signing result is claimed.

Canonical WP-205 manifest verification remains historically unconfigured.
This candidate does not rewrite the plan/policy/reference or invent a completion
receipt to conceal that fact.
