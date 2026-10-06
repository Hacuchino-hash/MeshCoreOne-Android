# WP-208 messaging evidence

**Authored candidate, not verified parity or a completion receipt.**
The exact manual authorization and immutable initial identity are in
[`receipt.json`](receipt.json). Branch rename is `NoTool`; the app-managed
branch remains `cbattlegear-supreme-guacamole`.

Source is `db14559b39d32322b06477c6ae676112f583db50`, tree
`8918fdc604341e6996a68c88f6bb1c02b9c2f87e`. All 34 primary inputs were read and
verified before the first edit. The frozen catalog contains 128 original
declarations/parameter families, expanding to 131 actual case identities:
127 single cases plus the four original dedup argument rows. Both disabled
source queue scenarios are authored as deterministic, enabled tests, not exclusions.
`collect_evidence.py --source-only` reconstructs this accounting from the frozen
catalog and explicit Kotlin test declarations. Headers/names alone are not
execution or behavior evidence.

The candidate uses actual merged `MeshCoreSession`, register-before-send
correlation, immutable model values and persistence contracts. Test firmware
provides deterministic raw bytes through the existing `MockTransport`; it does
not replace the session or cryptography. ACK expected bytes include the independent
source fixture `5e399e8a` for timestamp `0x6624AABB`, attempt2, `hello`, and the
32-byte `AA` key, calculated from the original formula with Python `hashlib`.
This is not an altered project golden or an iOS compatibility oracle.

## Actual commands and current result boundary

From the owning Windows worktree:

```powershell
python -B .\docs\android\evidence\WP-208\collect_evidence.py --source-only
python -B -m unittest discover -s .\docs\android\evidence\WP-208 -p test_collect_evidence.py -v
python -B .\tools\android-port\controller\verification_config.py --check
python -B .\tools\android-port\controller\validate.py
python -B .\tools\android-port\portmap.py
```

These commands inspect source/provenance and the reader. They do not execute Kotlin.
The first reader run discovered/passed21 tests, then the raw-retention,
duplicate-JSON and path-traversal additions discovered/passed24 tests, each
with zero failures/errors/skips.
The completed immutable-head independent review then added exact-base and
captured-run/attempt guards:26 Python reader tests discovered/passed with zero
failures/errors/skips. The same-WP coherent repair candidate retains128/131,
adds ten real-session interleaving regressions (46 authored native cases total)
and five actual Room interleavings (13 authored consumers total). None is
claimed Kotlin-executed before the serialized producer hook/lock handoff.
Source accounting reported128 families/131 expanded cases, 32 authored native
regressions and eight authored Room consumers, followed by three additional
channel-format/V3/datagram assertions and an immutable ACK-set/blocked-contact
radio-identity assertion (36 authored native regressions total).
Frozen overlay/schema/ownership
validators passed. Initial portmap validation rejected mixed `PortedFrom` and
`AndroidOnly` dispositions; the headers were corrected and the unchanged
validator passed. These results are not Kotlin execution evidence.
The real JVM task is `:core:services:test`; native consumers use the existing
`:core:data:testDebugUnitTest`. **Neither task has been executed by this worker.**
No Windows JVM, SDK/JDK/WSL provisioning, global cache or dependency installation
is authorized. The documented preflight returned exit2 because `ANDROID_CI_STATE`
is missing; it gates local execution, not the admitted owned authorship.

The services build file and root services lock remain exclusively WP-218's;
the data build file/module lock remain exclusively WP-211's. The proposed
`verifyMessagingTests` hook is **not yet an actual task and must not be invoked**.
The serialized producer request is [`build-hook-request.json`](build-hook-request.json).
No manifest/catalog/schema/policy/golden floor is changed to bypass missing wiring.

## Raw-first Linux evidence contract

The existing trusted executor supplies `meshCliInvocationFile` pointing to its
actual `wp109-invocation.json`: stage, host, exact repository/base/head/source/policy,
workflow run and attempt. The approved owner hook must pass that input unchanged,
not invent a worker-run binding or relabel historical proof.

`collect_evidence.py --capture-only --output <absolute-private-output>
--invocation <actual-executor-invocation>` copies complete services/data XML
verbatim and retains run identity and current/committed input blobs **before**
success validation. It is a task finalizer as well as the first step of the
success reader, so dependency-task failures retain their raw evidence.

The success command is the same reader without `--capture-only`. It requires
the Linux verify stage, a real run/attempt, exact base/head/source/manifest/policy,
all 131 expanded original identities, every declared native messaging regression,
all declared actual Room consumers (original8 floor retained) and the full
data suite's existing369 floor.
Zero/missing/malformed/skipped/failed/duplicate/stale/changed evidence fails.
Other services owners' cases are retained and must pass, but are not WP-208 credit.

The first hosted root run `37397264183`, attempt1 at authored head
`8bbad096d903b8789b47a4bdbe5ed563cfb83783`, failed actual production compilation:
`MessageService.kt:178:24` supplied `SnapshotSet<Any>` where `SnapshotSet<Bytes>`
was required. `Bytes` is itself iterable, so `set + bytes` selected the iterable
overload rather than adding one immutable byte value. The repair adds an explicit
`listOf(ackCode)` element collection without casts, empty fallbacks or loss of
prior codes; the whole-byte/blocked-contact/radio assertion guards its consumers.

Official failed artifact `11384056484`, `scaffold-37397264183-1`, is 16,028 bytes,
SHA-256 `3a982051a2e754f6d035c5ef7b61a4f6c73379538d540d839c7d0399de13bf6e`.
All14 bounded ZIP members passed path/type/size/CRC validation before extraction
as data to the owning session's artifact folder. Complete `gradle-verify.log`,
actual `wp109-invocation.json`, runtime inputs and preflight/Python logs are retained.
No messaging/Room assertion XML exists from this production compile failure;
zero Kotlin test or acceptance credit is claimed. This first run is historical,
not proof for subsequent repairs or producer changes.

Room assertions use real native in-memory/file-backed SQLite on simulated SDK31.
They cover fresh/recovered counters, cold store reopen, forgotten radios,
legacy-null purge versus current zero, FIFO/radio isolation, process-store
survival, actual closed-store failure, delivered manual-retry suppression and
conditional incoming nil-key backup backfill without changing valid sort dates.
These are not physical API31/37, OEM/background/TEE/radio/HIL evidence.

Independent review repair mapping and its unchanged-source rationale are in
`docs/android/deviations/WP-208.md`. `ReviewInterleavingTest.kt` uses actual
MeshCoreSession operations and deterministic raw transport barriers, not a
replacement session. Room fault/barrier roles delegate to the real store;
the final-read-only fault occurs after a real ACK and real count transaction.
No original assertion family, source parameter row, mandatory floor or
source-disabled equivalent is dropped to repair interleavings.

`WP-208-behavior`, `WP-208-boundaries`, and `WP-208-source-test-parity` remain
**BLOCKED pending exact-candidate Linux execution and independent review**.
Full graph/app readiness belongs to WP-303 and later prerequisites. Formal
source/license, hardware, signing and release gates remain protected.
