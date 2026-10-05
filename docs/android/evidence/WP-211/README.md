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
| `python -B -m unittest discover -s .\docs\android\evidence\WP-211 -p test_*.py -q` | 14 reader regression tests passed; synthetic XML only, no native parity credit |
| `python -B .\docs\android\evidence\WP-211\collect_evidence.py --check-source-map` | All 163 frozen families / 220 expanded cases mapped; 69 native JVM declarations and 12 real Room declarations; `native_execution=false` |
| `python -B .\docs\android\evidence\WP-211\verify_producers.py` | Exact 51 US / 8 AU subdivision rows, 36 countries, 10 county keys and 2+6 fault declarations; `native_tests_run=false` |
| `git --no-pager diff --check` | Passed for the authored changes |

These are not executed Kotlin/JUnit counts. The services module needs the
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
recorded here as they actually occur. No passed counts or gate approval are
asserted by this initial record.

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
