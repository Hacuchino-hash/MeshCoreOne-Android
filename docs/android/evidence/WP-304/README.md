# WP-304 candidate evidence

> Historical implementation record only. Normal candidate verification runs
> the UI and dependent JVM test tasks directly. Raw-JUnit retention and
> checked-in-style executor/manifest reader validation were retired; generated
> UI artifacts and source inventory remain actual test inputs/outputs.

**Status: final Companion/Sync amendment is issued and implemented locally.
All130 original families/158 scenarios now declare actual producer bindings;
static completeness is not executed parity. The final committed-head full
local cycle remains required before publication to the existing PR33.**

The final phase adds only two neutral families, getter-only projections on the
actual Companion/Sync errors and paired producer assertions. Constructors,
diagnostics, reasons, causes, cancellation, callers and business behavior stay
unchanged. The connectivity module is a test-only UI dependency. Every source
and native Companion case and all three Sync cases execute through the neutral
UI mapper. The four original consumer receipts no longer use copy fixtures.
Six independently declared producer suites must join their actual source
methods, raw JUnit and current immutable input hashes. No zero, failed, skipped,
missing, stale or declaration-only suite qualifies.

The previous155c258c measurement remains historical:202 UI cases, ten PNGs,
74 producer assertions,126/130 families and154/158 scenarios passed; four
producer families blocked the full cycle. It is not final-phase proof.

The active receipt and exact initial/reconciled identities are in
[`authorization.json`](authorization.json). Original clean HEAD824 and its
parents are retained; the explicitly authorized own-branch clean fast-forward
base was `d8f9b842581496baa382be1fe54dc866354b8150`. The dedicated original
managed branch is preserved. The coordinator has transferred the existing lease
to native CLI `b4682f12-ba0c-4fa2-a9f1-a21ccac74b01` / app
`cc2c1346-fc58-49b2-aae0-fc06fd03f540` on the app-materialized
`pr-33-cbattlegear-refactored-spoon` worktree and managed local branch
`pr/33/cbattlegear-refactored-spoon`. Only the existing remote PR33 head
`cbattlegear-refactored-spoon` may be published. The old native writer's permission
is revoked; its worktree has not been read or changed by this recovery.

The coherent authored batch is
`769b38f1008dd2aa493bee56ad9e432a97e83a0d` (parent d8f), with the required
Copilot App co-author trailer. Parent explicitly authorized reconciliation with
the actual CI-only PR29 merge `3c06d97e11cea96827c3349e78d1a5db7a8d5ad0`.
The clean owning branch integration commit
`1e0ce7549174e3da181b1d304ae74890a79e534c` has parents769b and3c06; no peer or
main checkout, source pin, policy, schema, catalog or ROOT UI lock changed.
These are provenance receipts, not native execution evidence.

Pinned source `db14559b39d32322b06477c6ae676112f583db50`, tree
`8918fdc604341e6996a68c88f6bb1c02b9c2f87e`; semantic manifest
`78a22920beaa5899f9618806b5cd2b27d50399a9b29b4d8dbd79f755717ec746`,
policy `56bdc53548bc86d631245795dfa38b4fc86048e0e7cbe1c7d5695879b035b42a`.
All88 primary blobs (75 production/13 tests) were independently matched against
the frozen source, HEAD and checkout. Source inventory remains65 WPs/185 edges/
eight gates/planned pending. Actual301/005/204 and203 merges/ancestors were
verified, not inferred from issue closure or session state.

## Actual commands and results

| Command | Actual result |
| --- | --- |
| `python tools\android-port\controller\ci.py preflight` at startup | BLOCKED exit2: missing explicitly supplied state; no JVM started |
| `python tools\android-port\controller\verification_config.py --check` | Passed on original824 and reconciled d8f; frozen overlays unchanged |
| `python tools\android-port\controller\validate.py` | Passed on824 and d8f; complete1,866 inputs and immutable graph |
| `python tools\android-port\portmap.py` before authorship | Passed traceability only, not feature acceptance |
| `python docs\android\evidence\WP-304\collect_evidence.py --static --self-test` | Passed15 reader regressions and all130 declarations/158 scenarios;151 native methods at that checkpoint; **not executed native tests** |
| Same declared static/self-test on committed/reconciled candidate | Passed16 reader regressions,130/158 and165 declared native methods; **no JVM/native execution** |
| `python docs\android\evidence\WP-304\generate_source_map.py --write`, then check mode | Passed exact88-input/130-family/158-scenario owned map; no shared catalog changes |
| `python docs\android\evidence\WP-301\verify_consumer_locks.py --check` | Passed immutable10-consumer/80-configuration/16-addition historical delta |
| Independent complete changed-path lease inspection | All55 changed paths matched the original three subtrees plus exact shared three-file amendment; zero unleased paths |
| Ordinary Linux scaffold run37358730009 attempt1/head723e1 | Failed before provisioning/JVM: ten owned files mixed PortedFrom and AndroidOnly dispositions; complete official artifact/log ZIPs retained before repair validation |
| Actual repository `port_map(load_manifest(...))` after owned header repair | Passed450 current native paths/43 UI paths; original source headers retained, no validator/policy weakening |
| `git diff --check` | Passed at authored checkpoints |
| `python -B android\core\ui\verification\dependency_proposal.py --self-test --workflow-check` after exact context amendment | Passed41 regressions; closed26-pair admission, same-config incumbent/unknown-context/version/metadata/write guards and seven compile alignments retained |
| Same declared helper command after serialized external-lock restoration | Passed43 regressions, including the actual admitted38-configuration file and rejection of the observed12-byte empty-SDK drop despite identical external component sets |
| Same declared helper command after actual Linux line-ending failure repair | Passed45 regressions; only the two exact parent-CRLF/Git-LF length/hash representations are accepted, with mixed newline/extra-byte/pair mutation negatives |
| `python -B docs\android\evidence\WP-304\collect_evidence.py --static --self-test` after discovery and messaging repair | Passed23 reader regressions;197 direct class-level declarations,130 families/158 scenarios accounted,21 pending producer families; **not native execution** |
| `python -B docs\android\evidence\WP-304\generate_source_map.py` | Passed exact frozen88-input map in its actual default check mode |
| `python -B docs\android\evidence\WP-304\collect_evidence.py --static --self-test` after measured9f harness/output repair | Passed28 reader regressions, including exact forwarded invocation, raw PNG/XML export and failure-before-export cases;197 direct native declarations,130/158 accounting and21 pending producer families unchanged. **Not native execution.** |

The declaration count grows as native flow/API evidence is authored; it is not
an executed suite count. No iOS, physical radio, Android device/TalkBack,
license/signing, Linux Gradle or native PNG pass is claimed by these results.

The initial Linux failure is recorded in
[`hosted-initial-failure.json`](hosted-initial-failure.json). The official687-byte
artifact ZIP matches digest
`a044352e2583857bb06e974df6c9c0ac46d22ffd341dda05f2f3fa9ebf34c9af`;
the complete32,875-byte workflow-log ZIP has SHA256
`b52d30c220a52241eaaf0df203cd0618f37f85e549359b38030de7f737ab7fdd`.
All ZIP CRCs and exact official run/head/attempt metadata were checked.
The repair changes only native-adaptation comment prefixes in ten owned files.
No native suite existed at this failing stage; none was manufactured.

## Declared output contract

[`source_inventory.py`](source_inventory.py) checks every88 owned input/blob,
the frozen case/parameter catalog and immutable ROOT UI lock. The owned source
map is generated by [`generate_source_map.py`](generate_source_map.py); this is
provenance/accounting, never a substitute for assertions or consumer wiring.

[`collect_evidence.py`](collect_evidence.py) independently matches source
declarations, every executed native method, XML counters/outcomes, all130 family
receipts/158 parameter scenarios and complete meaningful native PNG bytes/CRCs/
dimensions/hashes. Required states are compact light, expanded dark/high-
contrast, resize,200% CJK/RTL, failure/retry, native dialog, persisted-tip display
and crop. The rendered components are real Material/Compose, not theme-picker
screenshots or manufactured device evidence.

`ProducerBindingPending` receipts are emitted separately from native and
native-adaptation receipts. The reader preserves the exact unresolved source
case/owner list in `producer_binding_blockers`; copy-policy assertion success
cannot silently become a concrete service-dispatch parity claim.

[`retain_raw.py`](retain_raw.py), finalized before success validation, preserves
verbatim produced XML, failures/stacks and current input hashes independently.
Complete raw bytes and bindings are also emitted into the existing stage log;
missing reports are a diagnosed blocked artifact, never a passing result.
Same-head Linux CI run/attempt/base/head and raw bundles must be retained before
acceptance inspection.

## Explicit local retention

The user requires a successful declared local cycle before new-development code
pushes. Existing green-PR merges/mechanical resolutions are not retrospectively
subject to that gate. The installed shared `meshcore-local/check.py` runs the
actual seven-stage cycle in a serialized pinned WSL Ubuntu22.04 snapshot, with
visible output. The old missing-local-toolchain blocker is obsolete.

The genuine initial default-all local run at efe528cc passed Python/preflight
and executed all197 UI methods with zero failures/errors/skips,18 XML reports,
ten actual PNGs and96 matching input hashes. Its verify stage then failed at
`retainSharedUiRaw`: the declared executor emits the explicit local
`identity: null`, while the owned retainer previously required a hosted run.
Standalone/assemble/lint/inspect did not run. The complete visible log and
32-member raw XML/PNG/readiness/invocation archive were retained in session
artifacts; this failure is not a successful local cycle.

The owned repair distinguishes that exact local representation from hosted
evidence. A local invocation must have the existing bounded Linux/verify/schema
shape and an explicitly null identity, and execute on actual Linux x64 against
a clean committed snapshot. The retainer independently binds the actual head/
tree, original source/tree, manifest/policy and all owned source/input checks.
It exports only `wp304-local`, labels its raw record `execution_scope: local`
and emits `WP304_LOCAL_ONLY`; it never invents GitHub run/attempt IDs or writes
a hosted `wp304-native` bundle. Retained failures remain unvalidated raw bytes.

Hosted `pipeline_invocation` still rejects null identities and requires the same
positive run/attempt and exact repository/base/head/source/manifest/policy.
Malformed/stale hosted records cannot fall back to local. Unsafe paths,
overwrite, dirty snapshots and missing JUnit still fail while preserving
produced raw evidence. No hook, shared executor, environment allowlist, Gradle
dependency or behavior verifier is changed.

The declared static/self-test now passes34 reader regressions, including actual
local-binding/namespace and adversarial hosted/local separation fixtures.
All197 native declarations,130 original families/158 scenarios, seven parameter
families/35 rows and21 pending producer families remain unchanged. A fresh
default-all local run is required to verify the authored repair. The original
producer-binding gate remains mandatory even when local retention succeeds;
no complete cycle, push, source-parity or merge permission follows from these
Python results.

The user-approved installed reservation tool atomically reserves already
authorized paths rather than requiring another manual receipt. Recovery
reserved only this retainer, its regressions and the owned evidence/deviation
documents under its existing WP-304 assignment. No producer/shared support path
was added to that reservation.

## Actual Runtime/BLE producer phase

The coordinator subsequently granted concrete205/207 implementation and closed
the old runtime integration writer. Exactly nine support paths are enumerated
in authorization; the existing own reservation was reconciled atomically with
unchanged identity/revisions and every overlap guard. No manual producer hold,
competing source writer or blanket shared-file permission is inferred.

The external3f1f2077/bc2d9b38 declarations were carried at their exact approved
3047/4653 LF bytes, hashes and blobs, then frozen. They supply the existing
`SourceServiceFaultCarrier` and sealed root; no replacement root or UI-local
fault family was introduced. Runtime preserves its actual four source plus five
native connection cases and timeout name/Duration. BLE preserves all14 source
plus18 native cases and typed operation/status/domain/recovery/property/bond/
size/generation metadata. Existing constructors, diagnostics, raw fields and
causes remain; additions are exhaustive projection getters/pure enum mappings.

Actual producer assertions use the existing runtime `nativeCase` helper and
BLE JUnit pattern. The unchanged runtime reader verifies154 original families
and230 assertions. UI production consumes neutral payloads only; Runtime/BLE
are testImplementation edges. The UI reader now requires both genuine four-case
projection XML suites and exact current source/input joins; the raw retainer
copies them before identity or success validation.

[`runtime-ble-phase1.json`](runtime-ble-phase1.json) retains the genuine
working-tree iteration:202 UI methods passed, zero failures/errors/skips,
19 raw reports, ten verified PNGs and107 current input bindings. Only the
original timeout/connection/BLE dispatch families are newly bound. Accounting
stays130/158 with112/140 nonpending and18 still policy-only. The service-carrier
fixtures test all55 copy/payload policies but emit no original producer receipt.
The five new UI tests add coverage; none of the prior197 methods was removed.

The remaining overall failure is the unweakened18-family verifier, so this
iteration is not a complete local cycle or new-code push receipt. Runtime/BLE
producer XML reused their actual previous executions with identical inputs;
UI202 executed in this iteration. Historical plain-JUnit discovery and enum/
struct scanner failures are retained, not relabeled. Paired tests now follow
existing producer conventions without editing their validators.

Strict compile/runtime resolution succeeded with the incumbent UI lock. A
separate read-only graph probe failed before the resolver because the hook's
working-tree overlay does not satisfy that data reader's exact checkout-byte
contract. No new test context, generated lock or hash waiver was claimed.
Clean committed graph proof remains required; any real membership consequence
will be proposed from actual selected coordinates/checksums, not blocked on an
arbitrary historical file-size cap or silently copied.

The user now directs integration of actual main e04afa92/PR61 before publication.
Its connectivity and WP211 reader repairs are independently owned and must be
carried unchanged, not duplicated here. Companion/Sync neutral projections and
the unmerged209/210 actual producer consumers remain the next bounded phase.

## Linux resolution/execution proposal, not a command already run

### Landed service consumer continuation

The user-directed main738 batch and then main4033 frontier were carried by
non-destructive own-branch merges; every incoming staged blob matched the actual
approved main. Runtime/BLE projections and the two frozen API files were
preserved. Actual contacts/channels/advertisement and remote/room/binary/config
exceptions now execute through the default neutral UI mapper. All nine original
session wrappers are exercised with their original underlying cases and cause
identity; raw login/send reasons remain on the producer/payload, not visible
copy. The typed source-English accessor delegates real session payloads without
changing the producer's existing diagnostic constructor.

Actual committed validation at155c258c/tree
`d2036b23232b88e0d7d9d3de5a3ebc07a12db7ce`, integrated with main4033, retains202
passing UI assertions/19 XML reports/ten PNGs and the unchanged real Runtime4,
BLE4, Contacts25 and Remote41 projection cases. The owned reader requires all
four independent source-derived suites and current input hashes;39 regressions
pass. Accounting remains130/158 and35 parameter rows, with126/154 nonpending.
No producer, connectivity or WP211 implementation/validator was edited by this
consumer continuation. Concrete Services is testImplementation only.

The full installed local hook still exits1 at `verifySharedUiTests`/reader2,
not a failed native case or an old local-mode hold. Exactly these four IDs remain:
`ErrorUserFacingMessageTests::accessory setup kit error dispatches to concrete mapping()`,
`ErrorUserFacingMessageTests::sync coordinator error dispatches to concrete mapping()`,
`ErrorLocalizationTests::SyncCoordinatorError.alreadySyncing produces readable description()`,
and the original complete dispatch family. The real producers are already
landed; their remaining neutral Companion/Sync carrier projections need the
minimal assigned source paths. There is no reason to recreate those services or
remove the gate. Publication remains prohibited until the complete actual local
cycle succeeds.

The committed Runtime/BLE graph readback succeeded under strict/no-build-cache,
38 configurations/1472 external rows,47 actual seven-pin alignment edges and
zero new context memberships. All3445 tracked inputs and the incumbent owned
lock were unchanged. The earlier checkout-byte failures were caused by stale
CRLF materialization from a working-tree iteration: only the authorized phase
files in the managed snapshot were formatted to verified immutable Git bytes,
and its index/tree remained exact. No shared hook, hash predicate or source-owner
reader was changed.

The coordinator explicitly admitted the owned resolver and one bounded workflow
on2026-10-05T14:23:19.109-05:00. The exact
`android-shared-ui-dependency-generation.yml` candidate job uses one Ubuntu24.04
host/20minutes, pinned actions/Python, read-only token, false checkout credential
persistence, no cache/secrets/privileged duty and512MiB heap/metaspace/one worker.
[`dependency_proposal.py`](../../../../android/core/ui/verification/dependency_proposal.py)
checks identity/source/policy/command/caps/current committed inputs, retains raw
produced data before validation and compares exact owned configuration/version/
graph/lock state. Every tracked input and non-build lock is snapshotted; any
unowned/root/unknown write blocks without restoration. The16 adversarial
generator tests passed in Python only. There is no auto-persistence or gate.

The own clean branch was further reconciled by explicit authority at
`d11721d66128d800cea2be2c84b39a1ac035e1b3` onto verified main
`7e2835bad2c03dfb5a088063655f9fc4dbafd00f`. Original824/d8f/3c06 and failed94e
history remain intact. Runtime connection/timeout errors now exist in their
concrete runtime module; neutral UI projection remains a producer boundary,
not authorization for a UI-to-runtime edge.

The WP-211 neutral Device/Settings fault file was subsequently supplied by an
exact serialized carry-only receipt: reviewed producer
`98f64d2582e16e2e49c8c4fe79d5b7a239b970dd`, 3,183-byte one-file patch SHA256
`9a0b5e5e601406cf9455873562bc3e242b1f89d3d3b4f42d8a3f4c1ecdbf4bb7`.
The absent destination was populated byte-exactly and verified as Git blob
`b2a6b84a3846c016184e06772da4800700e3e8af`. It is frozen read-only here.
No producer implementation/tests or peer/uncommitted source was carried.
The actual2+6 fault cases now bind to the UI mapper and original tests, including
cause retention, central session delegation, expected/actual/GPS booleans and
source-only retry advice. Five original copy-only binding markers were removed;
the other producer blockers remain. A separate native boundary method increases
native declarations to166, not an execution count.

The actual declared module resolver is
`:core:ui:resolveSharedUiDependencies --write-locks --dependency-verification strict --no-build-cache`.
It resolves only owned UI configurations into the module-local lock, forces all
frozen incumbent versions and preserves complete selected/unresolved graphs
before failure. It runs only through the now-admitted exact scoped Linux proposal;
no hand-edited records/rootwide unlock or metadata weakening.
The following actual declared verifier is
`:core:ui:verifySharedUiTests --dependency-verification strict --no-build-cache`.
The resolver did execute on approved Linux attempt3, but its data proposal
failed the snapshot guard; the native verifier has not executed.

[`generation-attempt3-failure.json`](generation-attempt3-failure.json) preserves
the authentic failure and independent full artifact/log hashes. All3,306 prior
inputs were unchanged; the sole added snapshot key was the198-byte ignored
`android/settings-gradle.lockfile`. It was not an OWNER_LOCK or porcelain bug.
The coordinator admitted only this auxiliary unlinked bounded UTF8 bookkeeping
file, validated against the existing `ScaffoldSchema` rule: comments plus
`empty=incomingCatalogForLibs0`. The helper now retains raw bytes before
validation, rejects remote/unknown/duplicate entries and compares all other
inputs strictly. This file is never committed.

The same coherent helper repair shares one bounded configuration-name grammar
across lock/roster/graph readers, accepting actual `kotlin-extension` and
`unified-test-platform-gradle-work-action` names without omitting either
configuration or artifact.32 generator regressions and16 source-reader
regressions pass;130/158 original accounting is unchanged. Attempt3 remains
failed. A diagnostic readback reached a later strict metadata blocker for seven
actual unit-compile coordinates; no metadata/version exception or hand-edited
lock was introduced. The historical roster also lacked lazy `androidApis`;
the repaired reader explicitly rejects loss of this original seed configuration
rather than allowing a generated proposal that still cannot compile resources.
Fresh Linux proposal data and independent parent resolution/persistence review
are still required before native tests or PNG evidence can be claimed.

The actual fresh b667 run37391043845 attempt1 passed the narrow settings and
hyphen-name guards, retained every3,308 prior input unchanged plus validated
raw198-byte settings bookkeeping, and then correctly failed for missing original
`androidApis` state. Its full official artifact11381077275 is preserved at
1,266,128bytes/SHA256
`dd6ed5f65387b3c65c24c26f61b382f52e0810b9acad8516ffd5449b0fd7f837`;
complete18,093-byte logs SHA256
`965631aabd22cbf7c670dca87b86705627764038b5dd29d9e0c4b65a8fd70b52`.
See [`generation-b667-failure.json`](generation-b667-failure.json).
An intermediate fea repair realized the actual owned resource task; its result
is separate and does not establish final generated-state acceptance.

The coordinator then admitted the smaller explicit initial-state migration:
only if the owned lock is absent, copy the exact frozen47,512-byte ROOT UI
file into the owned target before actual Gradle resolution. This is genuine
already-generated source state, not a manufactured `empty=androidApis` record.
The raw ROOT and seeded-owned bytes/hashes are retained separately, with
`preexisting_prior=null` and `seeded_from_root=true`; an existing owned lock
is never replaced. Actual write-locks produces the final38-configuration
union when37 current configurations execute and one original empty SDK
configuration remains unexecuted. The reader verifies every unexecuted prior
configuration exactly and rejects omission/mutation. No private AGP resolvable
flag, extra resource-task dependency, ROOT write, metadata exception or
automatic persistence is used.32 generator tests cover initial exact copy,
existing-state preservation, corrupt/partial input, empty SDK retention and
strict prior-state boundaries. Further actual classpath admission issues may
still block the data proposal.

The ROOT lock stays at blob`566089f945f40442b8c0980aabee409c1de2c6da`,
canonical-LF47,512 bytes/SHA256
`95055e812451d9906683f36ee3e46373dc5fe5424fa833f02163bb13f78f1c96`.
No Activity1.8.2-to1.13.0 update, other lock, root catalog/XML, schema or new
dependency/provider admission is inferred.

## Explicit blockers and adaptations

The coordinator independently replayed actual b3run37400389283/artifact
11385132030 and admitted only26 literal new test-context memberships.
[`dependency-context-admission.json`](dependency-context-admission.json) binds
the full retained official ZIP and historical FAILED result. The owned reader
retains every same-configuration source/prior component and rejects production
or other-context/version use, even when a coordinate has an existing checksum.
An already-admitted identical owned membership remains idempotent; this never
permits replacing an incumbent or adding a second same-artifact version.

Parent then explicitly admitted byte persistence of the actual54,192-byte
generated local lock, SHA256
`d453654da31f2c5ebcb6866679918ef004d0035d68b89988fc03d51c1c8bda18`,
Git blob`4a53800cec13823aa260df1fe399ef446f804f96`. The absent destination was
populated mechanically from the shared generated file after unchanged build/
ROOT seed/catalog/metadata/convention guards.38 final configurations preserve
37 real resolved graphs plus original empty `androidApis`,1472 external rows
and40 actual seven-pin alignment edges. No unchanged Gradle rerun, manual lock
record or replacement hosted/native PASS was used for this admission.

Publication of own code28c3c47 was first rejected because external same-branch
commit1a153 added a different local lock. Its only difference was removal of
`androidApis,` from the empty record:54,180bytes/SHA25682e44d21,37 rather than38
configurations. The external commit states a root-wide resolver; no actual
scoped generation evidence was supplied for that variant. It was not produced
by this CLI session's migration/helper. Parent then explicitly required exact
whole-file restoration while preserving other changes. A normal merge keeps
both histories and resolves only the known add/add lock conflict by copying
the original admitted generated file. Two regressions bind the real38-state
file and ensure a components-only comparison cannot hide the missing SDK state.
No force/reset, manually written empty record or protected-main adoption occurs.

The two new relative-time methods and two recovery Compose methods were found
nested inside other functions. All four are now direct class-level JUnit
methods; the reader rejects nested/orphan annotations using bounded lexical
scope instead of counting text. The197 declaration count is still not a test
execution count. Source-generated L10nR and native UiR IDs are distinct so the
postcommit copy no longer references the wrong resource module.

One additional WP-208 frozen carry now supplies only
`MessagingFaults.kt`, exact Git blob
`d6f96917b135f12d3fa62c0e5c456aac709257bc`, canonical-LF1969bytes/SHA256
`5b0e6332ece1068240a39a9856d87dd809db326793f17197c1dd13c13897cb03`.
The original2436-byte reviewed patch was applied without type/body edits.
Windows checkout CRLF bytes are recorded separately; they are not claimed to
have the canonical file's raw hash. Seven Message/three Polling/two Queue cases
now dispatch through their actual neutral fault types, preserve payload/cause
identity and suppress source raw send reasons. Queue persistence recursively
wraps a real message/session/protocol or real storage failure. The original
English fallback is exposed through an explicitly documented typed native
accessor, separate from localized visible copy and the frozen exception's
diagnostic constructor. No message/ACK/Room/queue service implementation or
UI-to-services edge was carried; producer execution remains separate.

Twenty-one source families still have uncredited producer bindings. In
particular, the combined nine-wrapper original now exercises the three real
available Message/Polling/Settings wrappers, not nine direct protocol stand-ins;
its other six wrappers keep the whole family blocked. Protected main723/PR40
adoption is held by the coordinator; source/schema exceptions are not adopted
on the own source-pinned7e lineage.

Actual017865 ordinary Linux run37407036772 passed the restored SDK lock state
and compiled UI production Kotlin, then failed `compileDebugUnitTestKotlin`.
Two member assertions had incorrect package imports; four collection/value
comparisons failed Kotlin2.3 inference. The repair uses exact object identities
and explicit argument/nonnull-slot shapes, retaining every197 native method and
130/158 source scenario. No UI native method or PNG executed at this failure.
[`native-017865-failure.json`](native-017865-failure.json) binds the complete
77-member official artifact and23-member logs, independently retained first.
The actual data369/DataStore137 suites passed; the real14-test transaction suite
includes the passing committed-preference marker/Room case. This producer proof
does not substitute for the unexecuted UI consumer or iOS compatibility.

The separate017865 proposal job37407036788 failed before generation because
the Windows parent data receipt used3908-byte CRLF/SHA84a8153f, while the same
committed JSON checked out on Linux as3794-byte LF/SHA3f13f185. Exactly114 line
separators changed; none of the26 pairs did. The owned reader now admits only
these two exact known length/hash representations and parses those verified
bytes, not arbitrary newline/whitespace normalization.
[`generator-017865-eol-failure.json`](generator-017865-eol-failure.json) retains
the genuine43-test1-failure/6-error historical result and complete raw logs.
No proposal artifact or resolver ran in that failing job.

The subsequent actual d4b42d proposal37408412391 genuinely passed on Linux.
[`generation-d4b42d-success.json`](generation-d4b42d-success.json) binds its
complete official artifact/logs and independent readback:38 configurations,
1472 external rows,40 compile-alignment edges and zero new context admissions.
An existing owned lock is not replaced by ROOT state; actual resolution retained
the same54,192/d453 bytes, with all3334 prior inputs unchanged plus only the
validated empty settings bookkeeping file. This is data proof, not UI acceptance.

Actual d4b42d native run37408412414 discovered and executed all197 UI methods:
189 passed,8 failed, zero errors/skips. Complete18 raw JUnit reports and one
input binding are retained through the official log and were re-hashed after
decoding. All130/158 source receipts exist, but21 remain policy-only.
[`native-d4b42d-failure.json`](native-d4b42d-failure.json) records exact failures
and repairs; none is dropped or relabeled. The seven actual partial PNGs are
inside JUnit system-out receipts, not standalone artifact members. They do not
cover all ten required states. Visual inspection exposed a missing root dark
canvas and a nominal font200/RTL dialog using default Android-system values;
the next assertions verify painted roles and actual layout density/direction.
Candidate labels or a passing screenshot method alone are not sufficient proof.

Actual10b591 native37411758494 progressed to193/197 passing methods with four
remaining failures, zero errors/skips. Eight actual PNG state receipts now
include large-font CJK/RTL body and real failure/retry; full acceptance remains
blocked. [`native-10b591-failure.json`](native-10b591-failure.json) retains the
complete official artifact/log hashes and exact failures. Native dialog font2
was measured but its host still lacked RTL capability/direction. Focus-window
admission and an extra reactive pill-retention composition remained real timing
issues, while actual DataStore cleanup removed the blocked temporary obstacle.
The next bounded repair retains every assertion and the first-frame/focus/
typed filesystem failure requirements; it does not extend the idle timeout.

Actualc754a2 native37417500006 then ran197 methods with195 passes and two
remaining failures: native dialog lifecycle/layout and the test's animation
startup-frame model. Real font200/RTL dialog/Back and actual filesystem IO/
reopen assertions passed. [`native-c754a2-failure.json`](native-c754a2-failure.json)
binds the complete official artifact/logs. Its9f follow-up genuinely executed
197 methods, with191 passes and six harness failures. Five dialog tests failed
the attachment guard before behavior; the pill's composed state was still
Hidden after one Compose clock tick. The environment-dependent PNG export
also did not occur: the trusted executor strips `ANDROID_CI_OUTPUT` before
Gradle. [`native-9f8560-failure.json`](native-9f8560-failure.json) retains the
complete77-member official artifact,23-member logs,18 raw XML reports and
zero standalone `wp304-native` artifact members. This is not UI acceptance.

The bounded authored harness repair explicitly sends public Compose snapshot
apply notifications, drains due native callbacks, advances one Compose frame
and pumps16ms of the actual Android main looper. Dialog focus uses the pinned
Robolectric `ShadowViewRootImpl` adapter used by `ActivityController`; test-only
root extraction is not a shipping hidden-API dependency. Attachment, native
window/field focus, Back, current incoming state and hidden-action assertions
remain. The first-state check measures48ms/three controlled Compose frames,
not300ms or the end of the fade. Two fixed post-focus frames apply and lay out
focus before idle; the60s timeout is unchanged.

The owned Gradle retainer now forwards the already trusted
`meshCliInvocationFile` property. The reader validates its exact bounded,
unlinked Linux verify/WP003 repository/base/head/source/manifest/policy and
positive run/attempt identity, then derives the external evidence root.
Produced PNG/XML/input bytes are retained and console-emitted before export
or a success verdict, including invalid forwarding failures. Only the
`wp304-native` subtree may be copied; repository roots, linked paths and
overwrite are rejected. This repair is authored and Python-tested, not yet
proven by a new native run or a complete ten-state artifact. No global
environment allowlist, shared executor or workflow changed.

The actual615e Linux proposal37514521387 passed and retained the identical
54,192/d453 owned state:38 configurations,37 executions,1472 external rows,
40 alignment edges and zero new admissions. All3339 original input hashes
were unchanged; only validated ignored empty catalog bookkeeping was added.
[`generation-615e126-success.json`](generation-615e126-success.json) binds
the complete12-member official ZIP and logs. It is dependency data proof only.

Actual615e native37514521564 then executed197 UI methods,196 passed and one
failed, with zero errors/skips. The real artifact now includes18 standalone
XML reports,92 exact current input bindings, the actual forwarded invocation
and nine hash/CRC/dimension-verified native PNGs. Compact/expanded dark,
large-font CJK/RTL and storage-recovery images were visually inspected;
they are real shared UI, not a label or theme picker. The dialog state is
missing because the focused region fixture timed out at `waitForIdle`.
[`native-615e126-failure.json`](native-615e126-failure.json) retains the
complete105-member official artifact and23-member logs without relabeling it.

Reading the exact admitted Compose1.10.6 idling implementation confirmed that
its recomposition/frame pump is gated by `mainClock.autoAdvance`. The focused
input fixture had no timing assertion but unnecessarily froze that clock.
The bounded follow-up restores/asserts its normal automatic clock, preserving
actual attachment/focus, invalid input, zero add calls, Back and required PNG.
The timed pill test remains manually clocked and measured. No production UI,
60s timeout, source requirement, golden or dependency changes. Fresh actual
native proof and the tenth PNG remain required.

Actual ad391 run37518004306/attempt1 supplies that proof:197 discovered/run/passed,
zero failures/errors/skips,18 standalone raw XMLs,94 exact committed input
bindings and all ten required standalone PNGs, including the real focused
dialog. [`native-ad391637-blocked.json`](native-ad391637-blocked.json) binds the
official3,026,372-byte artifact11439285197/SHA256
`dab620638d0747cb4247e988087bb89f86441581f7d38afc5a6cb4053823acd0` and106
verified ZIP CRC members. Each PNG's bytes match both raw-retention and actual
XML receipts; chunk CRCs and dimensions were checked independently. The complete
current Actions API log archive contains both jobs' consolidated logs/system
logs,20,347 bytes/four members/SHA256
`5cd81bc6e0c24122718a3907635a1f23c6d99f4a0ba14178a0db40e5b3c479e2`.
It is distinct from the older47,603-byte/23-member discussion receipt, not a
replacement for that historical archive.

The actual failed task is `:core:ui:verifySharedUiTests`, whose reader exits2 on
the21 policy-only original producer families. There are no failed native case
names at this head. All130 families/158 scenarios are accounted for, including
seven parameter families/35 rows;109/137 are nonpending and21/21 are still
uncredited. The passing focus, Back, first-visible-frame and real PNG assertions
must not be altered to address this separate producer seam.

Recovery initially read all eight discussion comments and confirmed zero reviews,
inline comments or review threads, then read the new coordinator amendment on
PR33 and the exact PR49/50 approval comments before further work. The discussion
requests a neutral
typed carrier with projections in the real producers, not UI-local duplicate
exceptions or a production UI-to-services/runtime/BLE dependency. Published
WP206/PR58 and WP214/PR54 now contain actual companion/sync fault declarations,
in addition to WP209/PR49 and WP210/PR50. Their presence does not authorize a
carry or complete those WPs. The exact heads/blobs, minimal extraction/projection
paths, producer tests and four test-only consumer edges are recorded in
[`producer-seam-proposal.json`](producer-seam-proposal.json). Shared writes await
the coordinator's atomic assignment for the remaining205/206/207/214 surfaces.
PR49 and PR50 are now the sole approved writers of their respective neutral
declarations and existing producer projections. Recovery must wait for actual
published/tested/frozen blobs before carrying them, not an obsolete UI-only
scope hold. Concurrent runtime integration stays with the coordinator; both
proposed runtime type blobs were independently re-read at its38a0 candidate and
are unchanged.

The coordinator has confirmed human approval of the two existing main test-only
Swift amendments and the e3369a97 JDK-release-17 compiler successor. Recovery
non-destructively reconciled that exact eleven-path successor on the managed
local branch, with no conflicts or runtime-path changes. The two adopted test
blobs match the approved registry exactly; no additional Swift edit was made.
The original behavior/oracle pin,88 primary blobs, semantic manifest/policy,
frozen B2/D6 faults and canonical ROOT/local UI locks remain unchanged.
The ad391 proof above binds its actual7e base, not a fresh integrated-head
execution. Exact current-head Linux evidence is still required.

The declared reader was also replayed against the retained official XML/images,
not generated expected files:28 Python regressions passed, then the reader
produced native-evidence with197/197,18 reports,ten PNGs and130/158 accounting
before returning the exact expected exit2 for21 policy-only families. This
replay's recovery-checkout input metadata does not replace the original94 Linux
input bindings. No Windows JVM or native execution occurred.

The independent full41e source review requested six concrete fixes. The coherent
authored response is in [`source-review-repair.json`](source-review-repair.json):
real native storage distinctions/adapters, exact committed-preference marker/
receipt and Room consumer assertions, actual-painted-surface contrast, integral
calendar/zone relative components, emitted status colors/native pixels and real
DataStore write/cancel/close tests. All native proof is still pending.31 truthful
native error/recovery resource keys cover all twelve source locales without
editing source/global/generated localization.

The reader now binds receipts to actual executed current test methods and emits
`WP304_POLICY_CASE` for unavailable producer chains rather than crediting them
as `WP304_CASE` equivalents. It retains native JUnit/PNG evidence then fails
original parity if pending bindings remain. The130/158 floor is complete
accounting, not a claim that the remaining21 pending families were ported or
executed. Earlier26-family snapshots remain historical, not rewritten.

The admitted seven new unit-test compile memberships use only existing exact
unit-runtime source Core1.16.0/Lifecycle2.9.4 coordinates. Their actual
requested/selected resolution edges are retained in
`unit-compile-alignment.tsv`; missing/ambiguous source or selected-version
inconsistency blocks. No Core1.9/Lifecycle2.8 unvetted artifact, ROOT/catalog/
metadata edit or Activity1.8.2 update is introduced.

See [`WP-304 deviations`](../../deviations/WP-304.md). Pending producer-binding
annotations are copy-policy evidence only. They must become actual typed
consumer/dispatcher evidence or receive independently reviewed native
equivalents; they are not removed from the130/158 floor. Missing producer
contracts, compiled/runtime failures, absent native evidence or a stale head
block macro completion. App graph303, navigation302, optional system status402,
whole-app completion, independent review/merge and later WPs are not performed.
