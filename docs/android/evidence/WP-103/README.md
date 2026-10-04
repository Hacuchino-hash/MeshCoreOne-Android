# WP-103 complete native parser evidence

Repository `cbattlegear/MeshCoreOne-Android`; owner `protocol-porter`.
Dedicated branch `cbattlegear-expert-fishstick`, project session
`1e39cd9d-2615-405f-b34c-273d75bfb314`. The user-directed kickoff supplied the
shared all-write-path lease; [authorization](authorization.json) records its
actual identity/scope without manufacturing a receipt.

The initial clean HEAD was `47822de6ff8641a6d085baf0d4993aa3c792db8c`.
The coordinator subsequently identified actual merged WP-108 main
`eaf0fdb956afcb20e2de3d7d6550e0cbeeb50730`. This worktree fetched origin/main,
compared it to that exact SHA and fast-forwarded before final integrated proof.
The first normal PR head passed all local and Linux/Windows protocol checks
(4,082 cases per host). The coordinator then reported the actually merged WP-102
base `79bd7790edb174ed702010b98ee09b2674b70a3e`. An explicit remote lease on
`f2375f9e30ab6afeaa67e1448bfaf931a90ab97a` preceded this branch's rebase and reuse
of the real public wire HMAC helper. Final verification includes the363 actual
merged crypto cases. The coordinator subsequently required verified locale/
workflow-compatible main `19e1ae608a204a532eb500284ab8cad1c0b55b87`; the same remote
lease and a scoped autostash preserved the five owned changes through that rebase.
Final publication follows the coordinator's explicit current-main instruction at
`0ae606992bf58c8d3f2bf08b7a26406c97f12f84`; that additional reviewed upstream-tooling
merge leaves source pins and the1,012-case protocol baseline unchanged. The narrow
shared-HMAC repair uses `WireCrypto.hmacSha256` directly and retains all82 independent
HMAC expectations without a docs-only run-ID commit.
No other checkout or unverified branch interface was used.

Source `db14559b39d32322b06477c6ae676112f583db50`; semantic manifest
`78a22920beaa5899f9618806b5cd2b27d50399a9b29b4d8dbd79f755717ec746`;
semantic policy revision
`56bdc53548bc86d631245795dfa38b4fc86048e0e7cbe1c7d5695879b035b42a`.
Raw policy/file hashes are not these semantic revisions. Trusted validation
confirms the pinned reference tree, ownership and these revisions are unchanged.
Exact-head normal PR checks bind the committed head; local Kotlin fingerprints
avoid a self-referential head/evidence-only commit cycle.

## Implemented scope

Eight production files implement all46 response/push routes and all owned
device/contact/message/channel/status/stats/auth/binary/RF/region parsers.
Specialized ACL/MMA/neighbour parsing preserves original prefixes and exposes
typed diagnostics/raw unconsumed suffixes. Request-context layout/key/tag/prefix
length remain explicit. The actual existing53 event cases, immutable byte values,
checked reader/writer, LPP and canonical Rx-log/auto-add values are reused.

[source-map.json](source-map.json) accounts for all19 owned production files and
their original named declarations/bindings. It explicitly records the already
merged `event/RxLogTypes.kt` reuse rather than introducing duplicate types.
[source-cases.json](source-cases.json) binds original IDs/families to actual JUnit
names, not source-header coverage.

## Actual local verification

Windows; Python **3.12.4**; checksum-pinned Temurin **21.0.12.1+1**, Gradle **9.8.0**,
AGP **9.4.1**, Kotlin **2.3.20**, bytecode17, SDK37.2/build-tools37.0.0,
min31/target37. The provided read-only toolchain record supplied installations.
Only this session's explicit Gradle/Android-user caches were written. The
launcher stripped non-allowlisted credentials/environment before Gradle; no
global settings, dependency pins or verification switches changed.

The following real commands passed, using `JAVA_HOME`, `ANDROID_HOME`/
`ANDROID_SDK_ROOT`, private `GRADLE_USER_HOME` and private `ANDROID_USER_HOME`
declared afresh in each process:

```powershell
& .\android\scaffold\invoke-gradle.ps1 -ConstrainedMemory -BuildHeap 640m `
  -GradleArguments @(':core:protocol:test', 'validateModuleGraph', `
    '--dependency-verification', 'strict', '--quiet')

& .\android\scaffold\invoke-gradle.ps1 -ConstrainedMemory -BuildHeap 640m `
  -GradleArguments @(':core:protocol:test', '--tests', `
    'com.meshcoreone.android.core.protocol.parser.*', `
    '--dependency-verification', 'strict', '--quiet')

& .\android\scaffold\invoke-gradle.ps1 -ConstrainedMemory -BuildHeap 640m `
  -GradleArguments @(':core:protocol:test', 'validateModuleGraph', `
    'runtimeDependencyInventory', 'resolveScaffoldDependencies', `
    '--dependency-verification', 'strict', '--quiet')

& .\android\scaffold\invoke-gradle.ps1 -ConstrainedMemory -BuildHeap 640m `
  -GradleArguments @(':app:assembleDebug', '--dependency-verification', `
    'strict', '--quiet')

python .\android\scaffold\inspect_apk.py
python .\tools\android-port\controller\validate.py
python .\tools\android-port\test_inventory.py --check
python .\tools\android-port\extract_vectors.py --check
python .\tools\android-port\portmap.py
python .\docs\android\evidence\WP-103\python_reference_vectors.py --check
python .\docs\android\evidence\WP-103\verify_evidence.py `
  --base-sha 0ae606992bf58c8d3f2bf08b7a26406c97f12f84 --check
git diff --check
```

Separate invocations, one worker, in-process compiler and constrained640m build
heap preserved shared-host headroom. The original clean577-case baseline passed
before implementation. The final actual XML contains **4,445 protocol cases**:
**1,012 unchanged merged cases + 3,433 new parser cases**, in **43 nonzero suites**,
with **0 failures, 0 errors, 0 skips**. [local-results.json](local-results.json)
records every suite, report checksum, Kotlin input fingerprint and actual APK hash.
Its verifier checks XML testcase/outcome nodes against declared counters and
rejects missing/malformed/zero/skipped evidence.

| New suite | Discovered/passed | Failed/errors/skipped |
| --- | ---: | ---: |
| `OriginalResponseCasesTest` | 74 | 0/0/0 |
| `OriginalRxLogCasesTest` | 41 | 0/0/0 |
| `OriginalRegionsCasesTest` | 21 | 0/0/0 |
| `OriginalPythonReferenceCasesTest` | 28 | 0/0/0 |
| `OriginalBugFixCasesTest` | 34 | 0/0/0 |
| `CrossComponentParserCasesTest` | 50 | 0/0/0 |
| `ParserBoundaryTest` | 1,704 | 0/0/0 |
| `PythonReferenceVectorsTest` | 1,479 | 0/0/0 |
| `TransportParserIntegrationTest` | 2 | 0/0/0 |

Graph verification produced **470 actual production edges**, including no
forbidden Android/testing dependency in `core:protocol`. Runtime inventory
contains **114 resolved artifact/POM-license rows**; resolution ran in strict
checksum/lock mode. This is dependency evidence, not human legal admission.
The real debug APK and standard inspector preserve `.debug`, min31/target37,
GPL/MIT notices, unchanged permission surface and absence of test-helper classes.
The launcher remains an explicitly incomplete app, not a working radio UI.

The first isolated APK-inspector call correctly failed closed because that fresh
shell lacked SDK environment variables. It was retried with the declared SDK
and allowlisted environment; no inspector, expectation or policy was weakened.
No Kotlin compile/test failures were bypassed. A staged whitespace check exposed
unused trailing TSV columns; their padding is now explicit `-`, with every vector
ID, input byte and meaningful expected field independently compared unchanged
before rerunning the complete suite. No golden expectations or policy changed.
Traceability headers use the
trusted exclusive PortedFrom/AndroidOnly dispositions; native adaptation notes
do not pretend to be second ownership declarations.

## Independent bytes and original acceptance

The complete unchanged49-vector WP-004 catalog is checked. All31 builder byte
vectors (in28 original declarations), its two parser negatives and eight
applicable LPP/byte-input scenarios execute against real native code; crypto/TCP
fixtures remain their actual owning-WP evidence, not falsely relabelled parser
successes. Production does not depend on the Android `core:testing` module.

[python_reference_vectors.py](python_reference_vectors.py) uses only independent
Python `struct`, integer arithmetic, UTF-8 replacement, `hashlib` and `hmac`.
It pins original Git blobs and never imports or runs candidate Kotlin.
Its [1,479 vectors](python-reference-vectors.tsv) cover256 signed RF byte pairs,
768 route/type/version/path combinations, all27 MMA sensors,46 optional/push/
binary room/repeater status frames, six explicit neighbour prefix widths,
283 UTF-8 scenarios,11 scope names and82 HMAC cases including actual reserved
MAC values and empty/long keys. [metadata](python-reference-vectors.json) records
the generator, source hashes and canonical-LF TSV SHA-256:
`76e6d74b1aadf7c3a8cf2f8bedf81a0d42e1aeaf9c76c324309a9d2d78118923`.
These are supplementary proof, not a modified golden policy or a new Swift oracle.

`WP-103-behavior` and `WP-103-boundaries` have executable evidence.
For `WP-103-source-test-parity`, **198 of205 owned original cases** execute and
all seven genuine Session-dependent IDs are retained as unexecuted WP-107
integration, not exclusions/ignored tests. The additional **50** original seams
close WP-104's18 parser and31 non-builder round-trip/LPP scenarios plus WP-106's
real packet error-route scenario. Two ContactManager scenarios remain real
WP-107/109 integration; two `Session.events(filter:)` wrappers remain WP-107.
The global frozen catalog is deliberately not edited to claim completion.

See [native deviations](../../deviations/WP-103.md) for the source-specific short
helper behavior, typed trap adaptation and presentation collation. No iOS/macOS,
physical API31/API37, radio, backup interoperability, legal approval or protected
release signing verification occurred. Privileged publisher/activation state is
unchanged; this record is not a formal parity verdict, gate receipt or merge authority.
