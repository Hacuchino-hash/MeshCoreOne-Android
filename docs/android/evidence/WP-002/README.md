# WP-002 supervised dependent draft evidence

**Prepared for review, not accepted WP-002.** This records actual local scaffold
assertions/artifacts, not a controller bundle, completion/readiness receipt,
merged-prerequisite proof, license approval or fleet activation.

## Bound identity

| Field | Value |
| --- | --- |
| Repository / WP / charter | `cbattlegear/MeshCoreOne-Android` / WP-002 / unchanged `android-build-engineer` |
| Initial/parent SHA | `1214b5cf009705232907790d49d966823e7f410a` |
| Managed branch / dependent base | `cbattlegear-stunning-spork` / `cbattlegear-android-architecture-contracts` |
| Source commit / tree | `db14559b39d32322b06477c6ae676112f583db50` / `8918fdc604341e6996a68c88f6bb1c02b9c2f87e` |
| Canonical manifest binding | `f3fd3a0a51841a8fb43d3f2c4b3953e6ef564d4e96e74f4e3035200face5c90e` |
| Policy semantic revision | `1b8db2a4fc8c3049584d8c0bedf03880c6625fc7729b3f84ef26c80eb40ef910` |
| Coordinator draft lease | `draft-WP-002-af6f5c6e-1214b5cf`; prepare-only, exact canonical WP-002 paths |
| Session / native alias | `af6f5c6e-3ed1-4fe4-97bf-ab74756dcd9c` / `5c82ca61-0926-40a4-99aa-e9b8cb0d239b` |
| Candidate head | Commit containing this record; exact final SHA is in the dependent draft PR/handoff, avoiding a self-referential hash |

The active coordinator receipt was verified before source writes. Friendly
renaming was expressly waived because native rename/messaging tools were absent.
The original worktree and branch were retained; no private/live ledger or
controller claim was created. Parent #1 was OPEN/unmerged at
`9cd4fbd4f697c285b9729f016d552e693b21eac1`; parent #2 was OPEN/DRAFT/unmerged at
the exact parent SHA when checked. Their review/gates were not changed.

## Actual Windows results

Temurin **21.0.12.1+1**, Python **3.12.4**, installed SDK **37.2/rev1** and
build-tools **37.0.0** were used. Commands below are at the repository root with
the explicit private cache/JDK/SDK environment in the [build guide](../../build/README.md).
The launcher strips to the shared allowlist and runs the independent preflight.
No machine/pagefile setting or other-user process was modified.

| Executed command/task family | Observed result |
| --- | --- |
| `invoke-gradle.ps1 -ConstrainedMemory -BuildHeap 768m -GradleArguments @(':app:assembleDebug','--dependency-verification','strict','--quiet')` | Exit0, real debug APK; no lock/checksum rewrite |
| `invoke-gradle.ps1 -ConstrainedMemory -BuildHeap 640m -GradleArguments @('verifyScaffoldTests','verifyRoomSchema','validateModuleGraph','runtimeDependencyInventory','resolveScaffoldDependencies','--dependency-verification','strict','--quiet')` | Exit0, real graph/schema/component resolution and all mandatory assertions |
| `invoke-gradle.ps1 -ConstrainedMemory -BuildHeap 768m -GradleArguments @('lintScaffold','--dependency-verification','strict','--quiet')` | Exit0, all 24 Android lint targets; app 0 errors / 7 warnings |
| `:app:dependencyInsight` and `:core:contracts:dependencyInsight --dependency kotlin-compiler-embeddable --configuration kotlinCompilerClasspath` | Actual Android/JVM compiler **2.3.20**, lock-enforced |
| Build-logic `:convention:dependencyInsight --dependency kotlin-gradle-plugin --configuration runtimeClasspath` | Actual KGP **2.3.20**; AGP9.4.1 baseline **2.2.10 -> 2.3.20** |
| `javap -v` on built `FeatureRoute.class` | Class major version **61**, Java17 bytecode |
| `python .\android\scaffold\inspect_apk.py` | Exact APK shape/CRC/package/min/target/launcher/permission/notices and fixture absence checked |
| `python .\android\scaffold\sync_notices.py` | Verbatim pinned GPL/MIT/incumbent PNG inputs and Apache text checksum match |
| `python -m unittest discover -s .\android\scaffold -p test_environment.py -v` | 4 discovered/run/passed, 0 failed/errors/skipped |
| `python .\tools\android-port\controller\test_runner.py --quiet` | 111 discovered/run/passed, 0 failed/errors/skipped; controller fixtures only |
| `python .\tools\android-port\controller\validate.py` | Valid unchanged 1,866 pinned inputs, 65 WPs, 185 edges, eight human gates |
| `python .\tools\android-port\bootstrap.py` | CHECK MODE (`dry_run: true`), generator outputs unchanged; no write/refresh |
| `python .\tools\android-port\portmap.py` | 32 valid Android-only Kotlin production/test provenance entries, not feature coverage |

`-BuildMetaspace 512m -BuildCodeCache 96m -TestHeap 256m -TestMetaspace 256m`
were the final constrained defaults. Startup heap64m/build and32m/test, one
worker, two processors, SerialGC/TieredStopAtLevel1 and in-process Kotlin limited
the shared host. Assembly, assertions and lint used **separate JVM processes**.
Memory and code-cache failures during preparation were repaired, not hidden or
converted to disabled/skipped tests. Successful final commands used strict
metadata/locks without regeneration.

| Required suite / runner | Discovered | Passed | Failed/errors/skipped |
| --- | --- | --- | --- |
| Build-logic `:convention:test` / JUnit5 | 31 | 31 | 0 / 0 / 0 |
| `:core:contracts:test` / JUnit5 | 4 | 4 | 0 / 0 / 0 |
| `:app:testDebugUnitTest` / JUnit4, including five Robolectric launcher flows | 10 | 10 | 0 / 0 / 0 |
| `:scaffold:room-verification:testDebugUnitTest` / JUnit4, real Room/Robolectric | 2 | 2 | 0 / 0 / 0 |
| **Kotlin total** | **47** | **47** | **0 / 0 / 0** |

Launcher flows assert all five sections/seven registrations, explicit incomplete
semantics, disabled operations, auxiliary setup/remote navigation and native Back,
activity recreation, compact/840dp resize, 48dp targets and 200% font in dark
appearance. These are **simulated SDK31 scaffold flows**, not TalkBack/physical
API31/37 or feature parity.

Room tests assert equal-ID partition isolation, actual upsert/Flow rows and failed
transaction rollback preserving a prior committed row. KSP really generated the
fixture implementation and exported its schema. Neither fixture nor test helpers
are production project dependencies or present in APK dex. Android SDK31 testing
is offline from the separately locked/checksummed exact Robolectric artifact.

The actual graph task inspected all **30 leaf modules**, including inherited/KSP
production inputs, forbidden feature/platform/dev edges, cycles and JVM Android
leakage. Negative fixtures use actual Gradle configurations/production sources;
the lock resolver also tests secondary artifact variants and unresolved components.

Six remaining lint warnings advise newer dependency versions; pins were **not**
advanced against the provided candidate. One `MonochromeLauncherIcon` warning
points to the base API31 resource; a separate API33 resource includes the mask.
No lint checks were suppressed or baselined. All other Android modules report no issues.

## Persistent artifact/evidence outputs

The APK is local/ignored, **not published or installed**:
`android\app\build\outputs\apk\debug\app-debug.apk`, **28,529,112 bytes**,
SHA-256 `7632143250354176f30581c4aeaee93340191a38353d097a9eefde41e0fbc9d4`.
It has package `com.meshcoreone.android.debug`, min31/target37 and the actual
`com.meshcoreone.android.MainActivity` launcher. The only uses-permission is
AndroidX's generated application signature permission
`com.meshcoreone.android.debug.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`;
no radio/network/notification/location request is declared/performed.
Debug signing was AGP-local in the explicit private Android user directory;
no human release key, release signing or install-over-upgrade was used.

Pinned GPLv3/MIT/Apache text is present byte-for-byte in APK assets. The native
libraries are `libandroidx.graphics.path.so` for arm64-v8a, armeabi-v7a, x86 and
x86_64. Presence is recorded; **16KB compatibility is not verified**.

The copied [test discovery](test-discovery.tsv), [APK inspection](apk-inspection.json)
and [runtime inputs](runtime-dependencies.tsv) are sanitized reproducible output
shapes, not screenshots, independent gate verdicts or a release SBOM. Runtime
inputs include **113 resolved components** (including platform/variant metadata),
all with declared Apache2 license names/URLs and POM hashes, with human review
pending. Artifact checksums are in the tracked verification metadata.

| Output | SHA-256 |
| --- | --- |
| `android/gradle/verification-metadata.xml` | `fac805005dbc77f23f601cac520001da6b80441d5ae985a0a9f63ada0e3d2883` |
| `android/build-logic/gradle/verification-metadata.xml` | `0d0edc5230b9cef3a37c3db0e83dee3cfd21e5bf3851a17df18e3dab6a0de5b1` |
| Fixture Room exported `1.json` | `c4be5f9bff7a6e0ed8842363f8d026d05600304310c9882cf5914b7aea8e283f` |
| `test-discovery.tsv` | `41fef77457f63589cd064bec6b81ebc8d3fe0aecac540d0ecda682417bb76aff` |
| `runtime-dependencies.tsv` | `86776ba091b9c8733a28d124bb2042f1e271f8d45d86f884f7d506cc5196b07f` |

There are 62 root/module/buildscript lock outputs plus the included convention
build's settings/project locks. The publisher-pinned distribution/wrapper checksums
are in the build guide. Other artifact metadata was generated TOFU, then exercised
strictly; dependency signatures/legal approval were not verified.

## Fresh-cache review correction

Initial strict evidence at `d5ee909c` used the preparation cache, not a fresh-cache
or cross-build proof. Independent review found the controlling root metadata
missing the JUnit5.10.1 BOM POM despite a complete standalone included-build entry.
A new empty-cache run also reproduced a missing build-classpath coroutines BOM
POM. Five exact POM/module publication gaps were independently inspected and
added to root metadata; no resolved version, lock, policy or verification setting
changed. The included metadata already contained those exact pins and is unchanged.

[Fresh-cache verification](fresh-cache-verification.md) records both genuinely
empty-cache topologies: 47 required composite assertions and 31 standalone
convention assertions passed in strict mode with forced execution. Eight metadata
regressions plus four environment assertions passed; controller111 and frozen
generator/reference checks passed again. This supersedes any broader cache-portability
inference from the original run, not the unchanged APK/feature evidence.
The current root verification metadata SHA-256 is
`54748e59265fa76289ff3d1a8d4154ed1ecf65e9528b1206978b7bdb1ef68e0d`;
the earlier table records the initial reviewed candidate before this repair.

## Acceptance/source accountability and protected handoff

`WP-002-behavior`, `WP-002-boundaries`, `WP-002-source-test-parity` are **prepared,
not passed**. The primary original input is the unchanged `.gitignore` blob
`528a4fe68879bfdddcddeca6d31478910f07cd71`: Android-specific rules preserve real
source/resources/schemas while excluding builds/caches/local properties/keys.
The leased guide needs explicit staging because the inherited root `build/` ignore
also hides that documentation directory; root ignore policy was not changed.
No original Swift behavior/test family is marked ported by the scaffold.
Reused GPL/MIT/icon inputs are pinned generator inputs, not a file-parity claim.
Git's full staged whitespace check reports only line3 of the two verbatim MIT
notice copies; their source copyright spacing is preserved. All other staged
paths pass the unchanged whitespace rules and notice checks prove exact pinned text.

The canonical WP-002 verification entry remains **unconfigured**. These actual
commands/discovery/output contracts are a later protected WP-003 configuration
handoff, not an autonomous manifest/policy amendment or trusted CI publication.
All automation stays paused/off. Linux, CI/cloud/backend/publisher identities,
human review/legal/source/asset obligations, real API/device/accessibility/HIL,
full protocol/store/theme/navigation/translation/backup behavior, 16KB release,
signed upgrade and release publishing remain genuine pending gates. See
[deviations](../../deviations/WP-002.md). Stop here for review; do not start another WP.
