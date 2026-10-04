# WP-204 implementation and verification record

**Status: implementation written; native verification BLOCKED by unadmitted
shared dependency metadata. No native test has executed/passed yet.**

Repository `cbattlegear/MeshCoreOne-Android`; owner `data-persistence-engineer`.
ACTIVE coordinator receipt `autonomous-WP-204-dc15f1ba`; app session
`00cbcd6f-1b3b-4e40-8d44-2ce4216eb72e`; native project alias
`13956b48-f450-42b5-8c63-778fae11425d`; project
`663db92c-ed50-4a77-aded-bda85a7c503a`; branch
`cbattlegear-upgraded-carnival`. Initial verified clean HEAD and actual main:
`dc15f1ba445acf3230383ea68d4827c592f3fafa`.

Read-only source `db14559b39d32322b06477c6ae676112f583db50`, tree
`8918fdc604341e6996a68c88f6bb1c02b9c2f87e`; semantic manifest
`78a22920beaa5899f9618806b5cd2b27d50399a9b29b4d8dbd79f755717ec746`;
combined trusted policy revision
`56bdc53548bc86d631245795dfa38b4fc86048e0e7cbe1c7d5695879b035b42a`.
Actual merged003/102/201 ancestry and all8 source blobs were checked.
No reference, source case catalog, shared schema/contract, workflow, catalog,
app manifest or protected verification policy changed.

## Source/API/error map

| Owned source | Native surface | Original declaration evidence |
| --- | --- | --- |
| `MC1/Models/DevicePreferenceStore.swift` | DevicePreferenceStore, GPSSource, literal uppercase-UUID keys, immutable device snapshots; real shared DataStore reads/updates/observation/reset | DevicePreferenceSourceTest has all7 original cases; native boundary cases cover unknown/present GPS, reopen and reset separation |
| `MC1Services/.../Services/AppStorageKey.swift` | All48 AppStorageKey definitions, PreferenceValue/PreferenceSnapshot/PreferenceEditor, PreferenceStore, notification/appearance consumers and backup presence seam | Literal/raw/default/type/cold-start/reset/ordered-array/source-policy boundary assertions; no arbitrary replacement settings |
| `MC1Services/.../Services/SceneStorageKey.swift` | SceneStorageKey.MAP_CAMERA_REGION and ScenePreferenceStore, per-scene persisted default-empty string | Separate scenes, reopen, reset and exclusion from manual app-preference snapshot |
| `MC1Services/.../Services/KeyGenerationService.swift` | KeyGenerationService, SecureSeedSource, GeneratedIdentity, expanded import validation/public matching; four source failure families plus native InvalidPrefix guard | KeyGenerationSourceTest has all25 original declarations/32 parameter-expanded scenarios; independent RFC8032 seed/public and single SHA512 expansion assertion |
| `MC1Services/.../Services/KeychainService.swift` | SecretStore password store/retrieve/delete/has, source service/base64 account, stable radio partition, AndroidKeystoreCryptography and real FrameworkKeystoreKeyAccess | Actual DataStore/JCA boundary tests declare namespace, typed availability/loss/invalidation/authentication/version/IO, nonce/AAD, concurrent creation, rotation/deletion/cancellation and durability assertions |
| `MC1Services/Tests/.../PersistenceKeysThemeTests.swift` | PersistenceKeysSourceTest consumes the actually merged model constants and datastore appearance definitions | Both2 exact bare-name/no-prefix cases |
| `MC1Services/Tests/.../Services/KeyGenerationServiceTests.swift` | KeyGenerationSourceTest | All25 source identities, both three-row vanity families and four-row invalid-length family; none excluded |
| `MC1Tests/Models/DevicePreferenceStoreTests.swift` | DevicePreferenceSourceTest | All7 source identities, none excluded |

Every production/test Kotlin file has an owned pinned PortedFrom or AndroidOnly
disposition. Native tests currently declare the34 original cases and41 source
parameter scenarios; **declarations do not count as execution**.
[`collect_evidence.py`](collect_evidence.py) validates all8 inputs/blobs, the
trusted WP-004 literal family rows and complete actual native JUnit set.
Missing/malformed/zero/failed/skipped XML fails; it does not produce a
`local-evidence.json` pass while native execution is absent.

`StorageFailure` distinguishes first-unlock, current lock, inaccessible user
storage, permission, IO, preferences/state/envelope corruption, GCM
authentication, missing formerly-used key, permanently invalidated key, corrupt
native key type, provider failure, unsupported version, oversize, owner lifetime
and input errors. `StorageOperation` preserves read/write/delete/rotation/export
context and causes. `StoreState` exposes loading/ready/failure without durable
empty replacement. Only absent secret entries return null/false.

## Actual commands and results so far

Commands run from the owning repository worktree. Private absolute toolchain/
cache paths are intentionally not committed; the actual provisioner's own
environment.json and logs remain in this session's artifact directory.

| Actual command/task arguments | Result |
| --- | --- |
| `python tools\android-port\controller\ci.py preflight` before provisioning | Failed exit2: missing explicitly supplied state; no build started |
| `android\scaffold\invoke-gradle.ps1 -ConstrainedMemory -BuildHeap 512m -GradleArguments @(':core:datastore:testDebugUnitTest','--dependency-verification','strict')` before environment setup | Failed exit1: JAVA_HOME/SDK absent; no Gradle/test started |
| `python tools\android-port\controller\ci.py provision --root <own-private-session-directory> --accept-sdk-license` | Passed exit0: exact first-party pinned JDK/SDK archive checksums/sizes, unchanged pinned SDK terms and extraction-only installation |
| `python tools\android-port\controller\ci.py preflight --state <own-provisioner-environment.json> --output <own-private-evidence> --local` | Passed exit0: actual JDK21.0.12.1+1, platform37.2r1/build-tools37, private caches and unchanged credential allowlist |
| `:core:datastore:compileDebugKotlin --dependency-verification strict --no-build-cache --project-cache-dir <own-private-cache> --quiet`, bounded512MiB launcher | Failed exit1 after3m31s: missing owned module androidApis strict lock state |
| Same exact compile with `--write-locks`, still `--dependency-verification strict`, bounded512MiB launcher | Failed exit1 after1m19s:18 new DataStore1.2.1/Okio3.9.1 artifacts lack root checksums; Kotlin/tests did not execute |
| `python tools\android-port\controller\validate.py` | Passed exit0: frozen ownership/reference,65 WPs/185 edges/eight human gates |
| `python tools\android-port\controller\verification_config.py --check` | Passed exit0: unchanged foundation overlays; feature formal verification remains unconfigured |
| `python tools\android-port\controller\runtime_inputs.py` | Passed exit0: all77 required committed runtime inputs/checkout bytes matched initial base |
| `python tools\android-port\portmap.py` | Passed exit0: real provenance validator; headers are not feature acceptance |
| `python docs\android\evidence\WP-204\collect_evidence.py` | Correctly BLOCKED exit2: actual testDebugUnitTest XML absent; no synthetic passed artifact written |
| `git diff --check` | Passed; no shared path modifications observed |

Gradle uses its actual checksum-pinned9.8.0 wrapper and AGP9.4.1 built-in Android
Kotlin, existing2.3.20 compiler/coroutines1.10.2/BC1.86, one worker/in-process
Kotlin,512MiB build heap/512MiB metaspace and256MiB test heap/metaspace,
SerialGC/two CPUs. Provisioning used only the trusted pinned archives; no
unversioned SDK CLI/helper bootstrap, global installation/cache/credentials,
third-party source upload, checksum weakening or signing operation occurred.

## Concrete remaining admission and execution

The receipt leases only `android/core/datastore/`, this evidence subtree and
`docs/android/deviations/WP-204.md`. New DataStore is absent from the actual
merged catalog/verification metadata. The module uses an independently
inspected exact1.2.1 coordinate without editing the shared catalog.
[`dependency-admission.json`](dependency-admission.json) records actual strict
failure artifact identities and independently checked publisher SHA256 values.

Root `android/gradle/verification-metadata.xml` is exclusively leased to WP-205.
Its serialized coordinator-owned admission must cover actual DataStore/Okio
metadata/binaries, runtime proto/shaded notices and any actually resolved JSON
transitive/POM inputs. Normal integration also requires narrowly admitted
`android/gradle/dependency-locks/app.lockfile` and the actual core:data lock,
coordinated with WP-202. None was modified or bypassed. An owned module
`gradle.lockfile` must be generated by the real successful resolution, not
handwritten from these candidate artifact summaries.

The real `:core:datastore:testDebugUnitTest` is wired through
`:core:datastore:verifyPreferenceTests` into the existing `verifyScaffoldTests`
task, following the actual merged l10n/model/database pattern. Once admitted,
run the smallest actual module test/collector, then normal strict native
protocol/model/datastore/root graph/runtime resolution, actual APK/inspection
and lint; retain normal same-head Windows/Linux schema2 raw JUnit/input/run/
attempt/artifact replay, not legacy dummy counts. No completion, PR readiness,
hardware, real Keystore TEE, API37 device, full backup oracle, legal/signing or
release claim is made before those executions and coordinator exact-head review.

See [native deviations](../../deviations/WP-204.md). The unchanged app already
depends on datastore and disables automatic backup/extraction, but source
inspection does not substitute for the required final actual APK proof.
No next work package, factory, agent, fleet, merge or approval was started.
