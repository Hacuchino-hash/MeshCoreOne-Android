# WP-106 executable event foundation

Repository `cbattlegear/MeshCoreOne-Android`; owner `protocol-porter` role,
implemented by coordinator session `bcb17a74-5fa6-47d0-a4be-b6b595e20559`.
Actual merged prerequisite/base:
`4331a4dddd13126ab05f4b4d74e654f313c5d414`.
Source `db14559b39d32322b06477c6ae676112f583db50`.
Reservation `autonomous-WP-106-4331a4dd`; only event production/test paths
and WP-106 evidence/deviation paths changed.
Manifest `78a22920beaa5899f9618806b5cd2b27d50399a9b29b4d8dbd79f755717ec746`.
Policy `56bdc53548bc86d631245795dfa38b4fc86048e0e7cbe1c7d5695879b035b42a`.
The normal PR/CI binds the committed head; this record avoids a self-referential
head/evidence-only commit cycle.

## Actual Windows commands and outputs

Using the existing private checksum-pinned JDK/SDK, credential-stripped launcher,
strict dependency verification, one worker and constrained local memory:

```powershell
& .\android\scaffold\invoke-gradle.ps1 -ConstrainedMemory -BuildHeap 640m `
  -GradleArguments @(':core:protocol:test', '--tests', `
    'com.meshcoreone.android.core.protocol.event.*', `
    '--dependency-verification', 'strict')

& .\android\scaffold\invoke-gradle.ps1 -ConstrainedMemory -BuildHeap 640m `
  -GradleArguments @(':core:protocol:test', ':core:testing:testDebugUnitTest', `
    'validateModuleGraph', '--dependency-verification', 'strict')

& .\android\scaffold\invoke-gradle.ps1 -ConstrainedMemory -BuildHeap 768m `
  -GradleArguments @(':app:assembleDebug', '--dependency-verification', `
    'strict', '--quiet')

python .\android\scaffold\inspect_apk.py
python .\tools\android-port\controller\validate.py
python .\tools\android-port\test_inventory.py --check
python .\tools\android-port\extract_vectors.py --check
git diff --check
```

All final commands passed. Complete actual XML nodes/counts/outcomes show
**577 protocol cases** (55 new event cases plus all existing 522) and
**35 real helper cases**, zero failures/errors/skips. The production graph
checks all 30 modules, including no forbidden edge or Android leakage into JVM.

| New suite | Actual cases | Required behavior |
| --- | ---: | --- |
| `EventDispatcherTest` | 12 | Eager multicast, exact newest100/drop observations, filter-before-buffering, termination/cancellation/early completion, independent buffers and 10,000-event concurrent order/conservation |
| `EventFilterTest` | 16 | Original ACK/factory/prefix/code behavior, all channel bytes, indicators and short-circuit combinators |
| `EventPayloadTest` | 17 | Six-code parameter family/all unknowns/null attributes, real LPP partial diagnostics, immutable snapshots, all 14 floating shapes, path validation, trace hashes, unsigned metadata and actual message-result variants |
| `NeighboursPaginationTest` | 7 | Ordered pagination, empty-page backstop, latest total/first identity, all512 cap, UInt16 saturation, unchanged failures and cancellation |
| `PinnedEventVocabularyTest` | 3 | All53 actual case payloads/order/names, every filter method/property and exact original route/payload wire table |

Source assertions read immutable Git objects, not mutable candidate expectations.
The original inventory still checks all 468 paths / 5,133 declarations /
70 parameter families / 382 declared rows, all pending feature dispositions.
All 49 independent protocol fixtures remain unchanged. These generators do not
declare source feature cases passed merely because their headers are mapped.

The first compile exposed a collection backing-field name colliding with
`AbstractMap.values`; the private snapshot name is fixed. An early immutability
test expected `UnsupportedOperationException` from a Kotlin read-only list,
while the stronger Kotlin immutable marker rejects the mutable cast with
`ClassCastException`; the final test asserts the actual nonmutability boundary.
Neither an original vector nor a golden result was changed. The final
dispatcher uses an atomic queue, not a retry/remove sequence that could drop
an extra event if a consumer freed space between operations.

Actual debug APK: **29,012,995 bytes**, SHA-256
`559964e0d5a2ce80a8ab648bba109c1b8f30fecffe6821e08fbae6a40ab9f955`.
Inspection verifies `.debug`, min31/target37, launcher, unchanged permission
surface, pinned GPL/MIT/Apache notices and absence of testing/Room fixture
classes. This ordinary inspector explicitly makes no physical device or
native-runtime claim.

## Acceptance and remaining boundaries

`WP-106-behavior` and `WP-106-boundaries` have real typed-value/dispatcher
evidence, not simulated connected sessions. All 13 owned source files,
the Rx-log value prerequisite and deferred message-result vocabulary have
source headers and the consequences documented in
[native adaptations](../../deviations/WP-106.md).

The 17 original event declarations/parameter family are accounted for.
The underlying behaviors of the two original filtered-session scenarios are
verified directly on the dispatcher; the original session-wrapper paths await
WP-107. Original packet error routing awaits WP-103. Full
`WP-106-source-test-parity` remains conditional on those three real integration
scenarios, not waived or counted as ignored success.
No original source/resource/license, manifest/catalog/lock, workflow, privileged
publisher/approval, signing configuration or global host setting changed.
The app remains an explicitly incomplete launcher until actual feature/runtime
wiring follows.
