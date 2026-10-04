# WP-201 implementation evidence

Repository `cbattlegear/MeshCoreOne-Android`; owner `data-persistence-engineer`.
Session `f7b1af4d-9429-49e8-bcb0-6e3cc11cd5de`; owning branch
`cbattlegear-potential-engine`. Clean initial base:
`47822de6ff8641a6d085baf0d4993aa3c792db8c`.
Coordinator-authorized final integration/publication base:
`0ae606992bf58c8d3f2bf08b7a26406c97f12f84`.
Read-only reference: `db14559b39d32322b06477c6ae676112f583db50`.
Canonical manifest:
`78a22920beaa5899f9618806b5cd2b27d50399a9b29b4d8dbd79f755717ec746`.
Semantic policy:
`56bdc53548bc86d631245795dfa38b4fc86048e0e7cbe1c7d5695879b035b42a`.

The kickoff is the exact all-write-path lease for model, contracts/domain,
database and these per-WP evidence/deviation paths. No live ledger, formal
review or protected approval is fabricated. Source/manifest/policy pins,
65 IDs/185 edges/eight human gates are unchanged. Implementation head and
normal hosted results bind through the actual PR rather than a self-referential
committed SHA.

## Implemented bounded scope

All **54 primary production + 14 test + 12 support inputs** are mapped in
[`local-evidence.json`](local-evidence.json), with immutable source blobs/hashes,
many-to-many native files, every original declaration/parameter family,
concrete passed testcase identity and exact JUnit file hashes. Header mapping
alone is not acceptance.

The pure JVM model contains full non-rendering DTOs/value utilities, original
defaults/raw values/units/options, source identity/hash algorithms, defensive
collections/binary values, source computed behavior and typed wire-field
policies. Neutral contracts include all thirteen persistence roles plus
current store extension methods, typed failures/tuples, process/radio/screen
seams and deliberate generic session-service factories. There is no fake
repository/ServiceContainer implementation or production testing dependency.

The actual initial Room database has **17 tables**, native version **1**,
identity `847554c8d7ca15f1f881ba6bccce4121`, every persistent source field, radio
keys/indexes, explicit nullable parent links, two real cascades, exact
seconds/nanoseconds, source global-cache/history exceptions and actual DAOs.
Its KSP export is
[`MeshCoreDatabase/1.json`](../../../../android/core/database/schemas/com.meshcoreone.android.core.database.MeshCoreDatabase/1.json).
Full repository/retention/adoption/delete/backup algorithms remain WP-202/203,
not unimplemented calls hidden behind a pretend store.

| Acceptance | Concrete evidence |
| --- | --- |
| WP-201-behavior | Immutable full-field DTO/Room round-trips, source computed cases, typed contract/sync/error metadata and real abstract repository boundaries |
| WP-201-boundaries | Radio isolation/upsert/Flow, foreign-key and primary-key failures/rollback/cancellation, shared-history deletion guard, original byte/UUID/width/date/legacy policies |
| WP-201-source-test-parity | Complete 114-declaration map: 113 source-behavior/native equivalents execute; one real WP-103 resolver consumer is retained explicitly |

Six Channel Codable cases are *field-policy equivalents*, not whole-envelope
compatibility. Twenty-three original persistence/ConnectionMethod cases run
actual native DAO/column equivalents, not WP-202 repositories. Seven
createDevice cases exercise the extracted actual pure source factory, not
a mock connection ceremony. Those distinctions are attached to each case.
The one deferred matchRegions integration is never ignored or synthesized.

## Actual commands and discovery

All Gradle invocations use `android\scaffold\invoke-gradle.ps1` with the existing
portable JDK21.0.12.1/SDK37.2 inputs, private per-session user/project/Android
caches, unchanged credential allowlist, one worker/in-process Kotlin,
640–768MiB build heap and 256MiB test heap/metaspace. No global host setup or
unpinned SDK CLI/package bootstrap occurred.

| Exact command/task arguments | Result |
| --- | --- |
| `python android\scaffold\check_environment.py` in the stripped declared environment | Passed exact JDK/SDK/license-presence/private-cache checks |
| `:core:model:dependencies :core:database:dependencies --write-locks --dependency-verification strict` | Passed owned local-lock generation with already admitted versions |
| `:core:database:assembleDebug :core:contracts:compileKotlin --write-locks --dependency-verification strict` | Passed real Room/KSP generation and Kotlin compilation; observed late androidApis lock state generated |
| `:core:model:test :core:contracts:test :core:database:testDebugUnitTest validateModuleGraph runtimeDependencyInventory resolveScaffoldDependencies --dependency-verification strict --no-build-cache --rerun-tasks` | Passed on final integrated base: **165 + 4 + 42 = 211** actual cases; 0 failed/errors/skipped; graph/runtime/strict resolution passed |
| `:app:assembleDebug --dependency-verification strict --no-build-cache` | Passed actual production packaging, including 74 executed l10n self-tests and integrated locales/crypto |
| `:core:database:lintDebug --dependency-verification strict --no-build-cache` | Passed actual Room-module Android Lint |
| `python android\scaffold\inspect_apk.py` with declared SDK variables | Passed actual debug APK min31/target37/package/notices/fixture exclusion |
| `python tools\android-port\portmap.py` | Passed real trusted provenance-disposition validator after repairing mixed headers |
| `python tools\android-port\controller\validate.py` | Passed frozen source/ownership/DAG/65 IDs/185 edges/eight gates |
| `python tools\android-port\controller\verification_config.py --check` | Passed unchanged foundation overlays; feature formal verification still unconfigured |
| `python tools\android-port\controller\runtime_inputs.py` | Passed all76 exact committed/checkout runtime inputs at final integrated base |
| `python docs\android\evidence\WP-201\collect_evidence.py --write` | Passed nonzero/unskipped XML discovery, all80 primary input mappings, 114 case accounting and actual17-table/two-FK schema checks |

Actual debug APK: `android\app\build\outputs\apk\debug\app-debug.apk`,
39,677,001 bytes; SHA256
`fa2c1b0c03a91334e4b4c7e2897aca7765a9c0d467f5714456e5cbee603c67b8`.
Package `com.meshcoreone.android.debug`, min31/target37. GPL/MIT/Apache pinned
notices retained; no fixture or imaginary connected/send/restore UI packaged.
This is not physical-device, signed-release or native16KiB certification.
The local evidence collector records raw XML and schema hashes; canonical-LF
schema hash permits only Git text newline normalization, never byte-value or
golden changes.

## Real failures repaired, not waived

The first compile exposed Kotlin's unavailable predicate split and AbstractMap
values-name collision; proper types/regex/backing names fixed them. A reified
intersection-array diagnostic was fixed with explicit Any? element type.
Source contract reflection now distinguishes compiler-generated static
default-argument bridges from actual abstract instance methods.

Real Room failures exposed generator-omitted JSON defaults and Windows
file-backed WAL VFS behavior. The assertions consume the actual generator
shape, and the file proof explicitly uses test-only TRUNCATE/native SQLite.
An invalid Closeable assumption was replaced by explicit awaited lifecycle
and finally-close. Actual row conflict/cancellation tests prove preserved
committed rows and absence of each uncommitted row, not a proxy count.

The real localized APK build caught conflicting PortedFrom/AndroidOnly
dispositions in owned files. These were repaired without changing the trusted
validator or localization tests; the same actual assembly then passed.
No zero/ignored suite, missing metadata, fake formal review, catch-all default,
shared pin advance or lowered golden was used.

## Remaining external/consumer evidence

The coordinator-authorized module build-script hooks now attach the real model
and database suites plus `:core:database:verifyDomainRoomTests` to
`verifyScaffoldTests`, following the merged l10n pattern. The independent
collector fails on a missing, malformed, zero, skipped or unaccounted suite.
The coordinator owns the separately leased generic raw-module retention/reader
amendment; its integrated SHA and actual normal hosted artifact reparse remain
required before final publication/handoff. A green legacy-only build is not
treated as retained WP-201 original-case proof.

See [deviations](../../deviations/WP-201.md) for explicit native adaptations.
Robolectric simulates API31; production AUTOMATIC/WAL on actual devices,
supported future Android upgrades and full persistence consumers remain their
actual owners. Initial v1 does not manufacture historical migrations.

The owned compression utility consumes the real WP-004 Swift raw-DEFLATE
artifact, matching the independent JSON bytes, high bits and Unix fractions.
This does **not** establish candidate-to-Swift export or bidirectional restore,
atomic import/preference completion, radio remapping, key loss/security,
hardware, licensing/signing or a feature-complete release.

WP-202 receives real rows/DAOs/checked mappings and full neutral interfaces;
WP-203 receives exact legacy/wire policies and raw framing; WP-207 receives
generation/factory/lifetime seams. Coordinator review/merge, not this worker's
test summary, controls their dispatch. No next work package is started here.
