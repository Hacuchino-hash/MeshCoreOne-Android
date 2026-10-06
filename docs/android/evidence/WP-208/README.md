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
The subsequent direct narrow review identified unacknowledged lookup-retirement
success and polling-consumer self-close. Seven real-session and five Room
counterexamples extend the current authored counts to53 native and18 Room;
all128/131 original identities and both source-disabled equivalents remain.
Original send/resend family equivalents now use genuine transport ACKs instead
of seeded isDelivered state. The26 Python reader assertions still pass;
Kotlin execution remains separately unverified.
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

The coordinator's subsequent temporary four-path build-wiring grant is recorded
in [`services-bootstrap-carry.json`](services-bootstrap-carry.json). The exact
WP-218 dependency/resolver/failure-logging two-file patch is carried and frozen;
WP-218 may immediately resume live ownership of those paths. No content/device
implementation or whole donor branch was copied. The one-line data TEST services
edge preserves the existing published amendment and does not change production
dependencies. The local data lock remains the exact incumbent `af409126` blob.

The owning data script now declares actual `:core:data:verifyMessagingTests`,
depending on both `:core:services:test` and `:core:data:testDebugUnitTest`,
and `:core:data:verifyMessagingEvidenceReaders`, wired to root
`verifyScaffoldTests`/owning `check`. Existing repository/backup/runtime hooks
remain untouched. Separate lazy `Test.configureEach` finalizers retain
`raw/services-completion` and `raw/room-completion`; full validation is a
separate `validated` directory under the actual forwarded invocation's parent
`wp208-native` directory. The reader receives only its real `--output`,
`--invocation` and optional `--capture-only` flags, never a guessed task/input
or environment credential. These tasks are declared, **not yet executed proof**.
The historical producer request and actual status are in
[`build-hook-request.json`](build-hook-request.json).
No manifest/catalog/schema/policy/golden floor is changed to bypass missing wiring.

The actual Linux root executor command remains:

```text
python tools/android-port/controller/ci.py run --stage verify
```

It forwards the real invocation to the new owning hook through the existing
`meshCliInvocationFile` property. Four focused hook/carry regression assertions
extend the Python reader suite to30 cases; actual JVM/Room discovery and outcomes
still come only from complete raw XML. No Windows JVM/Gradle execution occurs.

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

Historical frozen-review-head run `37403210616`, attempt1 at `7e13dfbb`,
passed production compilation and failed `:core:data:compileDebugUnitTestKotlin`
on the absent serialized test-only services edge, with corresponding secondary
unresolved/inference errors. Official artifact11386322731 is17,171 bytes,
SHA-256 `51aea4e5392d5f74318cb7dd31f02082df8859855c0bc064e818e5a8681c37e5`.
All14 safe ZIP members/CRC, the complete compiler log and exact actual Linux
base/head/source/policy/run/attempt binding are retained before validation.
It contains zero messaging/Room XML and is not the two-residual repair's proof.

The first dependency-enabled run `37406373755`, attempt1 at `ba9de4e3`,
reached the composite command but timed out at the declared1,800-second executor
limit. Official artifact11388177397 is16,480 bytes, SHA-256
`3fab61d6fec0bdec22c607a7d2ec64e0808d73c31dfd2519716b60bf86d1de7a`;
all14 bounded member paths/types/sizes/CRC and actual Linux invocation were
verified and retained before analysis. Its real base is the authorized `7e2835ba`,
not a fabricated main723 substitution. The full2,460-byte Gradle log contains
only earlier helper output and no completed runner XML or messaging verdict.
This is **TIMEOUT / unverified**, never zero-failure success.

The owning data TEST hook now logs actual runner and per-case start/end/outcome
identities at the quiet-visible error log level, without touching the frozen
services build. This distinguishes a configuration/runner stall from an actual
assertion failure on the next run; progress lines cannot substitute for complete
JUnit. Original/native JVM wrappers now always close their explicitly owned
fixtures/queues and cancel the fixture supervisor even when assertions fail,
retaining the original exception and any suppressed cleanup causes instead of
leaking jobs until the runner deadline. No assertion, source floor, timeout
budget, mandatory suite or error outcome is weakened.
Two guard regressions extend the Python reader/hook suite to32 cases.

Diagnostic run `37409573578`, attempt1 at `f3056870`, retained actual data-runner
progress before again reaching the unchanged1,800-second limit. Artifact
11388804836 is23,922 bytes / SHA-256
`940edadef51d83fbdfd8164aff0edb5899aec23aeecb43ec4c0b2ed66034e45b`;
all14 members and full log were verified/retained before analysis. The last
started case was
`deliveredManualRetryRowDoesNotEmitAnotherPacketOrLoseItsPersistedStatus`.
Its authored harness left the actual connection at CONNECTED and then awaited
a READY-gated queue forever. The repair explicitly enters READY before draining;
the production readiness gate is not weakened.

The same raw log identifies the exact typed expected storage failure in
`acceptedInsertStorageFailureIsIncludedInShutdownInsteadOfBeingLostAfterItReturns`.
Authoritative accepted writes now retain a typed `Result<Unit>` completion and
throw its original queue/persistence cause only to observers through getOrThrow,
with shutdown separately collecting that failure. The test observes that exact
failure as data rather than allowing an expected failing child to escape its
test scope. Source/native and Room fixture boundaries now explicitly close every
poller, queue, radio generation and session on both success/failure, attempting
all cleanup and retaining original/suppressed failures.
The log's individual start/end lines are diagnostic observations, not full
JUnit discovery or whole-WP PASS; that timed-out run has no completed native XML.

At repaired-stall head `ef9650de`, run `37412427683`/attempt1 completed the
**actual full data suite387/387 and all18/18 messaging Room consumers** with
zero failures/errors/skips, including the original mandatory8 methods. The
owned raw Room finalizer retained complete XML plus282 actual compiled input
blobs, all matching that exact head and the real authorized base/source/policy/
Linux run binding. Official artifact11390001223, 146,708 bytes, SHA-256
`be825b5e6e50bc67b3f9b47c9572d7b46360fe0256f9acd7febd4450c741e059`,
passed all85 safe-member/CRC checks before independent XML/counter replay.
The structured historical record is [`native-run-37412427683.json`](native-run-37412427683.json).

That run failed `:core:services:compileTestKotlin` before JVM assertions:
recursive snapshot property/function inference, a nullable cross-module
channelIndex smart cast, and generic live-context expected-list inference.
The owned test repair uses explicit/guarded proper types and unchanged expected
values, not unchecked casts or family removal. There is zero services JUnit
from this compile failure. The successful Room result is historical to its
exact head, **not final new-head or whole-WP proof**.

First owning JVM execution `37413477570`/attempt1 at `2d212bfc` retained
actual XML for96 cases:94 passed, one failed, one worker-crash skipped; the
remaining mandatory cases did not execute and the full verdict correctly fails.
Artifact11390047740, 217,090 bytes / SHA-256
`4d1ee7b6f04447ca8f982f6f3bff147928c216e1b3015426c5c345d9ef72b546`,
passed126 bounded member/CRC checks before reading. Complete partial services
XML, data XML, raw-finalizer inputs and failure stack are retained.

The typed stale-poll NotConnected observer failure is now captured and asserted
at that observer, not leaked as an expected failing background child. The last
source channel-deletion case crashed its worker with OOM after its fake returned
generic ERROR to the merged protocol's channel-specific response matcher.
The corrected actual-session harness replies with the real empty-name/zero-key
CHANNEL_INFO frame; its injected source-backed configuration rule recognizes
that unconfigured slot. The native equivalence is bounded and retains all original
failed-status/pending-row/no-extra-send assertions; no protocol matcher, heap,
timeout budget, mandatory skipped policy or original source is changed.

Run `37414658172`/attempt1 at `120a108d` executed all184 owning JVM identities:
183 passed, one failed, zero errors/skips. The full XML and real failure
`expected attemptCount3, actual1` identify the per-envelope channel counter
fixture: its helper persisted the requested attemptCount2 but returned the
pre-copy DTO with default0, which the next explicit sequence upsert wrote back.
The helper now returns the exact persisted immutable DTO; the original expected
counter/assertion is unchanged. Official artifact11390613449 is221,280 bytes,
SHA-256 `0ba6d39ba9cad6a5107e56de0d0972cd617bdfd14bc79282444a1d937cec69d1`,
all129 safe members/CRC verified before reading. This is a genuine complete
failed JVM run, not183-of184 partial acceptance; final repaired-head JVM and
Room/root proof remain required.

The coordinator's one-file frozen fault producer for UI-304 is exactly
`MessagingFaults.kt`, Git blob `d6f96917b135f12d3fa62c0e5c456aac709257bc`,
1,969 LF bytes / SHA-256
`5b0e6332ece1068240a39a9856d87dd809db326793f17197c1dd13c13897cb03`,
with message7/poll3/queue2 source shapes and unchanged real payload/cause types.
The worker's verified one-file receipt is retained in its session artifacts as
`wp208-messaging-faults-producer-freeze.json`. Its live writer grant is removed;
the file stays byte-frozen. This is not ACK/Room/whole-WP acceptance, a full
candidate carry, source-reference exception or a new shared build/data hook grant.

Actual default main has advanced to `7237727fe498e87261cc9b321051567a86279fef`.
This worker has **not adopted its source-reference exceptions or source tree**.
The evidence reader still requires the originally authorized exact base
`7e2835bad2c03dfb5a088063655f9fc4dbafd00f`; if a subsequent real PR execution
reports a different base, final acceptance fails until the coordinator supplies
an exact trusted base/ancestry amendment. Native assertions may be executed and
raw evidence retained without silently relabeling that binding as the old base.

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
