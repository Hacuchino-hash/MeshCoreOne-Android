# WP-109 protocol sweep and executable TCP meshcli

**Candidate implementation; protocol passes, CLI proof remains incomplete.** The actual
898892e6 protocol run37232304276/attempt1 failed on BOTH Windows/Linux before
test discovery: the new ownership clock's inferred continuation type was
`CancellableContinuation<*>`, incompatible with its typed sleeper table.
Both real compiler logs agree on lines39/43. The scoped repair explicitly
specializes `suspendCancellableCoroutine<Unit>` in both new injected clocks;
no expectation, identity floor, timeout or source producer changes. Fresh
compiled/executed Kotlin/CLI and software acceptance still require proof.
The actual fbef8c7f protocol37238479360/attempt1 subsequently reached all
4,711 protocol cases successfully and then executed the72 real meshcli cases
on both hosts, with2 actual CLI failures. All four deployed-main process cases
were part of that real suite. The initial shared workflow retains protocol raw
XML only and strips CI output variables before Gradle, so failed CLI XML is not
yet retained in those bundles; this is the already requested concrete serialized
forwarding/artifact seam, not permission to invent case outcomes. The owned
test listener now emits actual failed fixture testcase/exception details at
error level even under the real quiet runner, enabling same-PR diagnosis while
the full raw forwarding amendment remains separately gated.
Actual a37d1d5a protocol37240261699/attempt1 exposes both identical CLI failures
on Windows/Linux: the UTF8 fixture incorrectly expected a trailing U+202E after
the unchanged pinned DeviceInfo parser trims terminal controls, and the contact
query's valid public ID `a5` repeated32 times contains the old `5a` repeated16
secret sentinel. The fixtures now put U+202E inside retained model text and use
the distinct exact16-byte `private-channel!` secret with independent literal
hex/plaintext absence assertions. Raw ESC/bidi output is still forbidden. No
production, source, golden, case identity or expected exit is changed; fresh
execution of the same72 CLI cases is required before reporting success.
The actual repaired5bd14bbd normal protocol37234505781/attempt1 then discovered
all4,711 cases/58 suites on both hosts: all4,708 old identities and the owning-job
TCP-close case passed, while two new cause assertions failed because coroutine
stack recovery adds a ConnectionLost copy before the exact canonical typed
correlation cause. Full actual58-suite bundles/three-case XML and both ZIP
digests are retained privately. The assertions now search the bounded real
cause chain for the **same exact type and wildcard metadata**, rather than
incorrectly assuming a direct cause. This is not a lowered error expectation,
ignored test, producer repair or passing CLI claim.
Normal79fafb7e run37236598495/attempt1 then executed all4,711 cases with
only the retained-link test's remaining state/type assumption failing on both
hosts. Inspection of the actual merged ownership code confirms two deliberately
different states: active foreign owner means ConcurrentTransportOwner; an ended
but physically retained link means RetainedTransport. The same real-TCP test
now explicitly exercises both phases in that order and keeps exact cause types,
zero unwanted connects/closes and awaited old-owner cleanup. All4,708 unchanged
baseline cases passed; no producer/golden/threshold was modified.
The local JVM lane initially remains held by WP-202 and the
explicit shared CI forwarding seam needs a separate serialized amendment.

Repository `cbattlegear/MeshCoreOne-Android`; owner `test-parity-engineer`.
One native session `275e27fd-1c49-41c5-9336-10210596a8c1`, app alias
`8d2d2cdb-cbba-4190-9f36-17b2cd01a728`, managed branch
`cbattlegear-protocol-parity-and-cli`. The coordinator's ACTIVE
`autonomous-WP-109-3da3a73b` receipt arrived before the first repository edit;
see [authorization](authorization.json). No extra agent/session was launched.

| Immutable binding | Value |
| --- | --- |
|Initial clean owning/main HEAD|`3da3a73b8481c49d035486924a813b331bf184d0`|
|Pinned source|`db14559b39d32322b06477c6ae676112f583db50`|
|Pinned source tree|`8918fdc604341e6996a68c88f6bb1c02b9c2f87e`|
|Semantic manifest|`78a22920beaa5899f9618806b5cd2b27d50399a9b29b4d8dbd79f755717ec746`|
|Semantic policy|`56bdc53548bc86d631245795dfa38b4fc86048e0e7cbe1c7d5695879b035b42a`|

Actual merged prerequisites were read directly: WP-105 PR10/merge
`4331a4dddd13126ab05f4b4d74e654f313c5d414`, and WP-107 PR19/merge3da at
reviewed head `2fe60386dc6cfe678e26fc24974d48027080926c`. No protocol input byte
differs between that reviewed head and initial3da.

## Concrete implementation

The owned application plugin declares a real deployed `MeshCli.main`, installed
launcher and module-only locks. The runner validates explicit numeric endpoint/
port/deadline arguments before constructing any socket, uses the real merged
WiFiTransport/MeshCoreSession, performs only local companion reads, reports
typed nonzero errors/partial streams, sanitizes UTF8 output, and awaits physical
cleanup/critical-filter drainage before publishing a complete result.

Owned loopback and production-only-classpath process tests target the real
entrypoint/parser/session/socket, not a surrogate mock CLI. Clock/barrier tests
cover deadlines/cancellation/teardown; source-independent frames cover real
split/coalesced reads, ACK/push filtering, channel windows/indexes/errors, contact
completeness and rejected secret/mutating operations. A separate protocol parity
suite targets intentional wildcard quarantine and retained physical ownership
over actual TCP. These are code declarations until actual execution is retained.

The owned `verifyProtocolParity` hook connects to normal root
`verifyScaffoldTests` and the real protocol task finalizer. It requires the full
existing4,708 protocol identities and new actual CLI suites, runs the owned
collector regressions, preserves every raw JUnit testcase/log node and required
immutable input blob, and reparses the full output. The generic root module
collector still intentionally excludes `tools`; separate CLI replay is required.

## Original assertions and actual prerequisite replay

[source-cases.json](source-cases.json) records all486 original MeshCore
declarations with exact source path/blob/ID/parameter family, actual assertion
sites/helper provenance, complete declared inputs, source-body digest and real
existing JUnit bindings. The direct audit reconciles455 exact names and31
native aliases into485 unique native cases (two nonerror accessor families share
one fully asserted native case). The three parameter declarations retain all12
declared rows; embedded source loop families remain in their actual assertions.
No filename/header-only acceptance or new original CLI labels are claimed.

All51 later WP-104 seams reconcile exactly:20 V112 nonbuilder cases
(18 parser+2 real ContactManager) and31 nonbuilder RoundTrip cases. The
legacy asynchronous flags scenario is separate from the former encoder-only
test. The seven WP-103 Session and two WP-106 filtered-wrapper seams execute
in the real existing session suites. No original source owner is reassigned.
The15 GPL application formatted-display adapters remain test-only/later
production consumers, not new protocol/application production acceptance.

The complete actual reviewed2fe normal protocol run
**37217793524 / attempt1** was independently downloaded and reparsed on this
worker: each host has4,708 actual cases/57 nonzero suites/34 reviewed regressions,
identical complete identities,0 failures/errors/skips. This is prerequisite
proof, not execution of the new CLI.

| Actual artifact | ID | Independently matched ZIP SHA256 |
| --- | ---: | --- |
|protocol-windows-37217793524-1|11309211075|`9acbac058d3d5744233e02a3dfd339c26c77ec9a1530e50a2713019183bada22`|
|protocol-linux-37217793524-1|11308899131|`438a7f46975082d1d1e20324f8369b850eef4d4e2054152b417dbbd5d4892f75`|
|wp004-helpers-37217793523-1|11309117966|`c34fa18fbae0bcfe8a7f04dea5fba084f4fa969a487a05a44e0f6aa8a04c9f21`|

The actual helper artifact has35 cases/6 suites including the original
three cancellation-clock families and native extensions. Its duplicate source
copies are not relabeled new CLI cases. [baseline-native.json](baseline-native.json)
is generated from all57 complete actual Linux XML files and independently pinned
at SHA256`b090ca122bbd28fd72a6e9654bd8590e4b5db3ef497b9530135e728d8bc7bcd4`.
Every baseline identity is mandatory in later candidate replay.

## Executed lightweight commands so far

| Exact command | Actual result |
| --- | --- |
|`python .\tools\android-port\controller\verification_config.py --check`|PASS; unchanged supervised overlay/semantic manifest|
|`python .\tools\android-port\controller\validate.py`|PASS;1,866 inputs/65 WPs/185 edges/eight gates unchanged|
|`python .\tools\android-port\test_inventory.py --check`|PASS;468 paths/5,133 original declarations unchanged|
|`python .\tools\android-port\extract_vectors.py --check`|PASS;49 immutable independent vectors unchanged|
|`python .\tools\android-port\portmap.py`|PASS; new Android-only/native/source headers accounted|
|`python .\android\tools\meshcli\verification\stage_locks.py`|PASS; unchanged generated initial module locks staged in owned directory|
|`python .\android\tools\meshcli\verification\collect_evidence.py baseline --baseline <own-private-reviewed2fe-junit>`|PASS; exact4,708 identities/57 reports and pinned baseline bytes|
|`python .\android\tools\meshcli\verification\collect_evidence.py self-test`|PASS;12 actual discovered/run/passed,0 failures/errors/skips|
|`git --no-pager diff --check`|PASS|
|Declared `ci.py preflight --state <own-private-environment> --output <own-private-output> --local`, before private state creation|BLOCKED: missing explicit own JSON state; failed before any Java/output creation|
|Declared `ci.py local-inputs --root <own-private-root> --jdk <approved-readonly-JDK> --sdk <approved-readonly-SDK>`|PASS; own private caches declared, no Java/installer/global configuration invoked|

The read-only ZIP replay and mapping probes initially selected the wrong uploaded
namespace (`junit/protocol` is beneath `_temp/.../evidence`), yielding zero matched
reports. This failed closed; the corrected exact namespace reparse reached all57
complete reports and486 original bindings. No counter/golden was reduced.

Actual Kotlin/module graph/runtime/APK commands and exact-head both-host
run/attempt evidence remain pending; none is invented in this candidate record.
No local Java is started before the coordinator grants the held lane.

## Boundaries

`WP-109-behavior`, `WP-109-boundaries` and `WP-109-source-test-parity` require
new candidate execution and coordinator independent review, not this prepared
implementation or historical prerequisite replay. See
[native adaptations](../../deviations/WP-109.md) and the
[operator contract](../../../../android/tools/meshcli/README.md).

**Physical-radio status: BLOCKED.** No device/firmware/endpoint/operator
authorization was supplied or exercised. No BLE/Android API/device, iOS/macOS,
backup interoperability, legal/signing/release/source/protected-publisher
acceptance, activation or downstream-WP claim is made.
