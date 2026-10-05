# WP-211 device and radio settings

This is implementation evidence for one manually coordinated WP, not a fleet
activation, completed app graph, hardware result, license approval or merge gate.

## Immutable receipt and inputs

- Coordinator receipt: `autonomous-WP-211-d147865c`, active on 2026-10-05.
- Native CLI session: `18dd9693-255c-4cb4-8154-86ee8040a8dc`.
- App session: `37c512cc-f89c-440b-8c91-5ba08345d7b0`.
- Original managed branch: `cbattlegear-supreme-engine`, retained because this
  profile has no app-native rename tool. No raw Git rename is authorized.
- Initial HEAD: `19ebc49442d2efaeed9ba030937a266da953b4b7`.
- Clean, explicitly authorized fast-forward/receipt base:
  `7e2835bad2c03dfb5a088063655f9fc4dbafd00f`.
- Source: `db14559b39d32322b06477c6ae676112f583db50`, tree
  `8918fdc604341e6996a68c88f6bb1c02b9c2f87e`.
- Semantic manifest:
  `78a22920beaa5899f9618806b5cd2b27d50399a9b29b4d8dbd79f755717ec746`.
- Gate policy:
  `56bdc53548bc86d631245795dfa38b4fc86048e0e7cbe1c7d5695879b035b42a`.
- Sole WP prerequisite: WP-207, GitHub PR26 genuinely merged at
  `d147865c15d09b7e9f9956db3e8a0a10e54d5134` and ancestral to this base.

The 24 primary input blobs were compared with the manifest, frozen Git tree,
current HEAD and checkout. All match. All 11 production and 13 original test
inputs and their assertions were read before implementation. Frozen inventory
derivation is **163 declaration/parameter families and 220 expanded cases**.
The four OCV parameter axes contain 15, 15, 15 and 16 cases, not four executions.

## Write boundary

The four manifest paths are the `core/services` device production/test packages,
`docs/android/deviations/WP-211.md`, and this evidence directory.
The coordinator separately admitted exactly:

- `android/core/contracts/src/main/kotlin/com/meshcoreone/android/core/contracts/domain/errors/DeviceSettingsFaults.kt`:
  the two DeviceService and six SettingsService source faults and typed wrappers.
- `android/core/data/src/test/kotlin/com/meshcoreone/android/core/data/repository/DeviceSettingsRoomTest.kt`:
  the seven original KnownRegion cases and actual device/settings Room consumers.
- `android/core/data/build.gradle.kts`: only the services **test** dependency and
  bounded `verifyDeviceSettingsTests`/owned evidence-reader hook.
- `android/core/data/gradle.lockfile`: actual module test-configuration resolution
  if needed, with incumbent entries preserved; never manual lock text.

Services build script/root services lock remain exclusive to WP-218. No app,
DAO, schema, production store, preferences, container, shared workflow,
manifest, catalog, golden, policy or global tool installation is writable.

## Declared verification contract

The early producer's read-only static command is:

```powershell
python -B .\docs\android\evidence\WP-211\verify_producers.py
```

It checks frozen source table/declaration equality only; it does not execute
Kotlin, assert original-case parity, approve a license or complete this WP.

`verifyDeviceSettingsTests` is the bounded data-module consumer hook, to be
registered before execution. It depends on the actual `:core:services:test`
and `:core:data:testDebugUnitTest` tasks, then runs this WP's fail-closed reader.
It must be wired to the incumbent root `verifyScaffoldTests` and owning `check`,
without editing the unleased root or services build scripts.

The admitted hook is now registered as `:core:data:verifyDeviceSettingsTests`.
Its exact reader is `python -B docs/android/evidence/WP-211/collect_evidence.py`;
the root's already-declared `meshCliInvocationFile` carries actual Linux
base/head/source/policy/run/attempt/host without changing the CI executor.
`wp211EvidenceDirectory` can select a new owned/private evidence destination.
The source-only check, which does not execute Kotlin, is:

```powershell
python -B .\docs\android\evidence\WP-211\collect_evidence.py --check-source-map
```

The actual Linux module command, executable only after the coordinator's
services build/lock and pinned Linux readiness handoff, is:

```text
verifyDeviceSettingsTests --dependency-verification strict --no-build-cache --rerun-tasks
```

The executor and evidence-reader regression commands are declared before use:

```text
python -B docs/android/evidence/WP-211/run_linux_verification.py --state <actual-absolute-Linux-state> --output <new-absolute-private-output>
```

```powershell
python -B -m unittest discover -s .\docs\android\evidence\WP-211 -p test_*.py -q
```

The executor runs only the actual two module unit tasks with `--continue`,
strict verification, no build cache, forced execution, one worker and the
incumbent pinned/credential-stripped Linux preflight. Its `finally` retains
complete raw reports and immutable inputs before checking the Gradle exit or
parsing XML. The regression suite uses explicitly synthetic reader fixtures;
its counts are not device/settings assertion counts.

The owning data hook and `check`/`verifyScaffoldTests` are real Gradle
registrations, not hypothetical task names. A separate declared Linux executor
must retain raw files after failed Gradle execution as well: a dependent Exec
does not run when its prerequisite test task fails. No failed test is counted
as acceptance merely because a diagnostic bundle was retained.

The admitted data hook now routes raw reports/input maps into the actual
root invocation's sibling `wp211-native` directory, so the incumbent always-on
artifact uploader retains them without a workflow/root/services-build edit.
`wp211EvidenceDirectory` selects an explicit **root** for those outputs;
each Gradle invocation has separate new `room-completion-<attempt>` and
`full-<attempt>` snapshots rather than overwriting or validating stale reports.
The data runner's `retainDeviceSettingsRoomEvidence` finalizer runs with
`--retain-only` even when that actual test task fails, preserving unsuccessful
XML before any WP-211 verdict. It does not assert successful execution when
services reports are absent. The full verifier still requires both actual
runners and all301 device-JVM names plus all12 Room method identities.
The standalone Linux executor's `finally` remains the bounded mechanism for
retaining compilation/prerequisite failures as well; task finalizers do not
claim to run when their finalized runner never started.

The exact additional retention task, automatically finalized after the real
data runner, is `:core:data:retainDeviceSettingsRoomEvidence`. The actual hook
invokes this reader shape before validation:

```text
python -B docs/android/evidence/WP-211/collect_evidence.py --retain-only --output <new-root/room-completion-attempt> [--invocation-file <actual-root-invocation>]
python -B docs/android/evidence/WP-211/collect_evidence.py --output <new-root/full-attempt> [--invocation-file <actual-root-invocation>]
```

The Linux executor must first verify declared pinned execution state, then run
those actual module tasks with strict dependency verification, no build cache
and forced execution. Complete verbatim JUnit, current compiled inputs and
source blobs, exact base/head/source, lease and actual run/attempt/host must be
retained **before** evaluating the Gradle exit or evidence, including failures.
Missing/malformed/zero/duplicate/skipped/failed evidence fails closed.

The original seven KnownRegion assertions execute against the real in-memory
Room repository in the admitted data test file. The pure JVM services module
does not acquire a production Room/data/runtime/Android dependency. Existing
WP-201 OCV/RegionSelection implementations are reused and tested, not copied.

## Current execution state

After the complete authoring pass, the exact declared source-only commands
produced these outcomes:

| Command | Actual result |
| --- | --- |
| `python -B -m unittest discover -s .\docs\android\evidence\WP-211 -p test_*.py -q` | 23 reader/producer/freeze/native-hook regression tests passed; synthetic XML/static catalog/hook checks only, no native parity credit |
| `python -B .\docs\android\evidence\WP-211\collect_evidence.py --check-source-map` | All 163 frozen families / 220 expanded cases mapped; 88 native JVM declarations and 12 real Room declarations; `native_execution=false` |
| `python -B .\docs\android\evidence\WP-211\verify_producers.py` | Exact 51 US / 8 AU subdivision rows, 36 countries, ten county keys, all 28 regular/three repeat preset fields/order/availability/priorities/hash sizes, and 2+6 fault declarations; `native_tests_run=false` |
| `git --no-pager diff --check` | Passed for the authored changes |

Nineteen additional actual-session role assertions cover forwarding/range
clipping without optimistic success, invalid raw SF/CR firmware rejection,
PIN/clock/capability widths, DTO-default/compatibility other-parameter paths,
unverified preset semantics, complete stats units, custom variables, GPS
enabling, key export/import/disabled/length behavior, actual signing capacity/
chunks/signature and source reset-versus-reboot completion contracts.
All device-changing packets target only deterministic test transports.
The native reader floor is now88 declarations, not a lowered placeholder;
the exact raw names, all parameter rows and twelve Room method identities
are additionally checked against current committed source.

The first actual Linux scaffold attempt at authored head
`cf079fdcac68457417f72c5efd483bc7500554e3`, run **37382894958 / attempt1**,
stopped at the frozen traceability step before SDK/JVM execution. Its error
was mixed `PortedFrom`/`AndroidOnly` dispositions in six source-derived files.
Those files retain their actual frozen source mappings; the native-adaptation
comments no longer assert a contradictory Android-only disposition.
The real `portmap.py` check is now part of the exact local verification:

```powershell
python -B .\tools\android-port\portmap.py
```

The failed run's actual artifact is `scaffold-37382894958-1`, id11375822238,
size700 bytes, published digest
`sha256:0d40eb5f459e3cae1ed1252b1dd10193342ede8a3a0666d440d2eeb42e89190b`.
This is failed **pre-JVM** diagnostic evidence, not native parity.

The independent Linux consumer run **37382894934 / attempt1** reached actual
`:core:services:compileKotlin` through the real data producer tasks at that
same head. It failed at RegionDiscoveryService's missing import of the
incumbent `Bytes.uppercaseHexString` model extension. The service and native
discovery test now import that exact existing helper; no identity algorithm
or wire/persistence implementation was duplicated.
Its failed producer artifact is `wp203-producer-37382894934-1`, id11375173475,
size14656 bytes, published digest
`sha256:15872c9ebc62aa0b2a5b66cc2aa233d6310fabdb135f7e473a7794de7b7d693d`.
This is failed Kotlin compilation evidence, **zero successful service/Room
executions**, not WP-211 parity or WP-203 compatibility credit.

Both actual failed artifacts were retained under the native CLI session's
private `files/wp211-ci/run-<run>-attempt-1` directories. The verbatim trace log
SHA-256 is `83d43c523b6b454414343b1e7b3ec5fc104081bad72dd19b6d3220a355717dd5`;
the actual consumer compile log SHA-256 is
`18bed43e576771b31475ffde14e72d837c13faaef4b34041bf05328edc665e2d`.
The latter run's raw-retention record explicitly contains zero XML reports.
Its mechanically generated data lock has **the same Git-normalized text**
as the incumbent (LF SHA-256
`9e33b19e97d8b8f20f95f7b83dc744c5403082615bd434546dcad43751eb0c10`,
incumbent Git blob `af40912627e2ca3c03445d5d43164a53c676294c`).
No proposal bytes, manual entries or line-ending-only lock edit were adopted.
This observation does not authorize the producer's broader `dependencies
--write-locks` command as a new WP-211 command or replace exact test evidence.

The ordinary WP-211 PR was published from the actual complete authoring
checkpoint, as the coordinator explicitly requested. It is not a producer-only
PR or a completion/merge claim. Subsequent repairs stay in that same managed
branch/PR; current immutable head and native run outcomes are reported there.
The original early producer checkpoint remains
`83347ede94cb1af5854d8d306caced49695eb335`. RegionalAreas/fault declaration
behavior is unchanged by the traceability-comment repair.

At repaired head `06d63f1f3d61f5dd5e67130cefa75f6b68e602cd`, actual Linux
consumer run **37384873522 / attempt1** passed the real data producer tasks
`:core:data:verifyBackupTests :core:data:verifyPersistenceRepositoryTests
validateModuleGraph`. Its full381 data cases passed without failure/error/skip.
The raw **DeviceSettingsRoomTest** suite contains exactly **12/12 passed**,
including all seven KnownRegion originals and the five real device/settings
consumers. Its2452-byte XML SHA-256 is
`00cfe57dbbaf00c0fb7a750b65e0f5e4f1024084dbcdc5fa9328f99108d96f97`.
Artifact `wp203-producer-37384873522-1`, id11377966119, size88134 bytes,
published digest
`sha256:a955f9efb10877534bc69e08449cfce4bad656d980b8af063081dfd77dae83b7`
was retained with complete raw XML, actual source/input/run bindings and Linux
readiness before inspection. This is limited **Room consumer evidence**;
it is not the213 JVM-expanded original device cases,88 JVM regressions,
full WP-211 acceptance, or a new WP-203/Swift/device/license claim.

The matching scaffold run **37384873542 / attempt1** passed source preflight,
traceability and actual pinned Linux readiness, then failed
`:core:services:compileTestKotlin` on the still-unprovided
`kotlinx.coroutines.test` dependency. Its full compiler log also identified
two independent EnumEntries/SnapshotList assertion-inference errors; those
assertions now compare explicit lists. No services JVM tests were discovered
or claimed passed. The complete75403-byte failed artifact
`scaffold-37384873542-1`, id11377611522, published digest
`sha256:2ecc80c9386b451dfef08480dc606ffdaaf8ae0436fa9db959c7e4cacb7e2a45`
was retained, including294277-byte compiler log SHA-256
`24a0b97bdbac2ef3b012f80781e215c1b90c17bcd4d313d19e497234d9986ba2`.
The services build/strict-lock handoff remains exclusively WP-218; no
dependency bypass, borrowed unmerged producer or hand-written lock is used.

The later actual **reviewed98f64 head** consumer run **37386539848 / attempt1**
also passed all381 data cases including **12/12 DeviceSettingsRoomTest**
(seven original +five native, zero failures/errors/skips). The complete
artifact `wp203-producer-37386539848-1`, id11379941597, size88177 bytes,
published digest
`sha256:5cae1691a4f7fb94ffa46be108f993953eef2e78459bd338fd4b26840109e286`
was retained before reading its raw XML and exact HEAD/base/source/run binding.
The exact Room XML SHA-256 is
`4ea4289b6e13fdac240a4fe2c735e88d42bcf3b9ddb5287d2372ca0a65f9ff28`.
That head's failing services compiler artifact `scaffold-37386539918-1`,
id11378997057, size75289 bytes, published digest
`sha256:df75c4a27855357224032e26fdf9ede1eedc405c944df26769b0daa93f44f3dc`
was likewise retained; compiler log SHA-256
`d26a97b626e4786ee686bdc941807b6b9f0ecfc1dc88004c2443c254d09f5bb8`.
These results do not roll forward to later hook edits: they are exact
historical98f64 Room evidence and a still-failing services compile.

The native-retention hook head
`dbe77fe8aacc33b8a780074ff7b0262a5e747439` exposed a real Kotlin-DSL
configuration issue in Linux run **37389443110 / attempt1**: Gradle's generated
`java` accessor shadows the package qualifier in `java.io.File(...)`.
The admitted data hook now imports `java.io.File` and uses `File(...)`;
the regression guard checks that the shadowed invocation cannot return.
No frozen producer or unleased services build/lock path changed.
The complete failed artifact `wp203-producer-37389443110-1`, id11380641448,
size3044 bytes, published digest
`sha256:27d9c0b731c19d706dca6e22d0df48afe4af3b9de02bdbe202174ff4a8998a68`
was retained before inspection; its7582-byte exact script compiler log
SHA-256 is
`c79b743ab34e311a8210b74f8f345fe28510f37ca38c121fd457f13c86afb941`.
This is failed build configuration evidence; no native test execution is
claimed at that head.

The following head `8433950416648f687030a1cfa0c50e60d728cd26` fixed the
File namespace but exposed the actual AGP9 registration phase: early
`tasks.named("testDebugUnitTest")` lookup ran before AGP registered that real
variant test task. Linux scaffold run **37389858486 / attempt1** therefore
failed configuration, not a nonexistent runner or skipped accepted suite.
The data finalizer now attaches through `tasks.withType<Test>().configureEach`
filtered to the exact `testDebugUnitTest` name, following the incumbent lazy
test-configuration pattern. The actual verifier dependencies and raw-failure
finalizer remain; no task is renamed/invented, no suite is dropped and no
source/golden/native floor is lowered.
The complete15803-byte failed artifact `scaffold-37389858486-1`,
id11380642286, published digest
`sha256:db0530253541e145273730dd5f5a59dee357853028905b2e079e9e25004d0d16`
was retained before inspection. Its805-byte exact configuration-failure log
SHA-256 is
`49111739242d230c4976541e6146beee111a4b4b92d5e375039640aa331e9e13`.
No native case executed in that failing configuration.

Static declaration counts are not executed services-JVM counts; the limited
actual Room consumer result above is recorded separately. The services module needs the
incumbent pinned `libs.kotlinx.coroutines.test` test dependency in the
**WP-218-owned** services build producer and its real scoped lock graph.
Neither that file nor the root services lock has been edited here.
The admitted data test edge is present; any new data test-configuration lock
entries require actual sanctioned resolution, not hand-written lock text.

Initial declared readiness command:

```powershell
python -B .\tools\android-port\controller\ci.py preflight
```

Result: **BLOCKED, exit 2**, missing `ANDROID_CI_STATE`; no JVM invoked.
This is not native service acceptance. The separately permitted read-only
coordinator toolchain record describes a Windows/vendor-template probe, not a
Linux execution state. An installed WSL Ubuntu22.04 was observed, but no JVM,
SDK provisioning or configuration change has been performed there.
Required CI is the actual sole Linux host, not a native Windows Gradle run.

Implementation/native execution and immutable artifact identities will be
recorded here as they actually occur. No passing services-JVM cases or formal
gate approval are asserted by this record.

## Provenance and consumer handoffs

RegionalAreas and RadioPresets copy the complete frozen application catalogs,
their IDs, availability tiers, priorities, aliases, hash sizes and matchers.
Geographic names are not presumed copyright-free. Application/service/catalog
GPLv3 and protocol MIT notices remain unchanged. Chile's MeshChile citation and
the existing model OCV reference to Meshtastic firmware remain attributable to
their pinned original inputs. No new dataset or external provider is admitted.

The neutral fault file is WP-211's source-facing producer hosted in contracts;
that location does not confer WP-201 parity credit. WP-304 selects localized
whole-sentence GPS variants. WP-218 consumes the actual `RegionalAreas`
lookup; it does not substitute a resolver lambda or duplicate the catalog.
Neither handoff completes this WP or assembles the WP-303 service graph.

The coordinator's 2026-10-05T23:22:32Z message and explicit source-closure
follow-up freeze exactly the eight producer/test/helper blobs at reviewed head
`98f64d2582e16e2e49c8c4fe79d5b7a239b970dd`; see `producer-freeze.json`.
Source-map/native readers reject any drift from those exact eight blobs
without a same-task actual-bug repair receipt. The independent reviewer
reported no significant production issue on that head, **static only**.
No executed301-case JVM evidence, human gate or merge authority follows
from that report.

Production closure is **RegionalAreas.kt + RadioPresets.kt**: the actual
RadioRegion enum lives in RadioPresets, and recommendation calls back into
RegionalAreas. RadioPresets also uses the already merged protocol's
MeshCoreException and model snapshots/RegionSelection. No enum is duplicated.
**RadioOptions.kt** is added only to close the paired RadioPresetTest's
direct bandwidth/range dependencies. The paired RegionalAreas/RadioPreset/
fault tests use the exact **SourceCases.kt** JUnit identity helper; its async
definitions require the already pinned coroutines-test in the WP-218 build
producer. **DeviceSettingsFaults.kt** is independently carryable with its
already merged protocol exception dependency. The carry set contains no
DeviceService, SettingsService, context, discovery, factory, Room test edge,
full service graph, build/lock edit or ownership/catalog advancement.

The data hook already scheduled the actual services test in the original
PR. It cannot supply a missing services test dependency. The current-head
scaffold run **37386539918 / attempt1** actually fails at
`:core:services:compileTestKotlin` on `kotlinx.coroutines.test`/`TestScope`.
The exact read-only WP-218 build producer at
`0e36f61839d1cd7a99e2d7c1c800b912fd362532`, blob
`3a6ecda9eb8b3d3db315b91746276222692e1174`, declares
`testImplementation(libs.kotlinx.coroutines.test)`. It is **unmerged** and
has not been copied or injected into this candidate. An actual coordinator
frozen/serialized build-and-strict-lock handoff is necessary. This is not a
JSON dependency requirement for WP-211 and is not fixed by adding another
duplicate task hook or borrowing the data module's test classpath.
That exact producer's root services lock blob is
`6065703315850927e5836e9af4845b873735638a`; its declared testCompileClasspath/
testRuntimeClasspath contain coroutines-test and coroutines-test-jvm1.10.2.
This is a read-only identity observation, not authorization to carry either
shared file or evidence of a successful WP-211 resolver/test command.

The source `BatteryInfo+Display` voltage/linear/OCV-interpolation helpers and
their original percentage assertions are primary-owned by **WP-304**;
`BatteryMonitor` polling/threshold lifecycle is **WP-303**. This WP retains
actual battery millivolts/storage and OCV DTO/preset behavior without duplicating
those consumers or claiming that their UI/monitor/mathematical suites ran.
