# WP-302 execution admission

**Product implementation, exact Nav-only dependency support and closed
traceability admission integrated; real App compilation/assembly passed,
native acceptance incomplete. Not WP-302 acceptance or feature parity.**

## Current native milestone

Atf586c116, official verification actually discovered/executed150 App nodes:
148 passed, two font200 failures, zero skipped. All100 original SDK31/37
nodes passed; all140 mandatory WP-302 execution bindings are present.
Actual SDK31/37 rendering produced104 of108 required states (52 per SDK).
The remaining compact font200 labels measured67/68px wide with real horizontal
overflow; the assertion was not removed or weakened. Native labels now request
centered paragraph wrapping/hyphenation while preserving their incumbent
Material typography, and the assertion additionally checks actual fontScale2.
The new wrapping still requires execution.

Actual46df6f22 verification still measured horizontal overflow on Chats at
both SDKs (148/150 passed,104 renders, zero skips). Paragraph wrapping alone
was insufficient; the labels now occupy the full width their native item
allocates, rather than a rounded intrinsic-width box. Full-density/overflow
assertions remain unchanged and now report paragraph width/constraints.
`native-46df6f22/` and its visible log retain the failed attempt; raw run is
`/home/cbattagler/meshcoreone-work/local-checks/run-44ZqaWr2`.
The allocated-width correction still requires execution.

At6a22134b, all five font200 tab labels passed their full-density/overflow
checks on both SDKs. The next assertion exposed the same intrinsic-width
problem on the RTL heading: native measured width90px but paragraph width296px
(the actual allocated max width), so it was genuinely not contained. The
heading now fills its allocated native app-bar width too; no size, text or
overflow assertion is reduced. Complete148/150 failed suite/input bindings
are retained in `native-6a22134b/` and its visible log, raw run
`/home/cbattagler/meshcoreone-work/local-checks/run-ntSb12E1`.

Complete XML/input bindings remain in `native-f586c116/`, visible output
`native-f586c116.log`, raw run
`/home/cbattagler/meshcoreone-work/local-checks/run-ygp4xkfP`.
This is partial passing evidence, not140/108 acceptance or a complete cycle.

Coordinator-prioritized WP-218 missing-POM support is separately frozen in
`d8ccf1d8a2fb3fc5ca40adf1d511cb6b0425c381`: only three XML lines admitting
the already-selected annotation-experimental1.5.1 POM. Independently verified
publisher/cache-equal2257 bytes and full SHA256 are retained in
`annotation-pom-admission.json` and the raw POM. All1158 previous tuples remain,
with exactly one addition. The exact delta was delivered through PR25 for
serialized carry, never a blind copy of this receiver's full Nav metadata.

## First real native results and corrections

The actual subsequent75193694 verification stopped on the old strict3.5
lock, exactly as required (`native-75193694.log`), not on a relaxed resolver.
The receiver regenerated the existing declared
`:app:resolveWp302NavigationDependencies --write-locks --write-verification-metadata sha256 --dependency-verification strict`
command in its official isolated751 snapshot under the shared native lane and
same credential-stripped pinned toolchain. All3909 literal inputs matched their
committed blobs before generation. Exactly16 nonempty graphs resolved; the
only changed tracked files were the owned App lock and checksum XML.
All1153 inherited checksum tuples remain, with5 new artifacts independently
downloaded/rehashed from Google Maven/Maven Central. Outputs were byte-verified
before carrying back. Receipt, exact command, all graph hashes/TSVs and executed
recipe are retained in `espresso-graph-generation/`; complete visible output
is `espresso-graph-generation.log`. Raw publications/before/outputs remain at
`/home/cbattagler/meshcoreone-work/local-checks/wp302-espresso-1c_4lzg8`.
This changes only App test support, not incumbent pins or native acceptance.

Official `--stages verify` at39e7a763 really compiled and discovered150 App
nodes (140 WP-302 plus10 incumbent scaffold):134 passed,16 failed, zero
skipped. All100 original SDK31/37 nodes ran,94 passed. Raw XML contains133
bound WP-302 executions and49 actual SDK31 native renders, zero SDK37 renders.
The seven SDK37 Compose cases failed in Espresso initialization before their
WP-302 binding hook. Complete raw XML/input binding is retained in
`native-39e7a763/`; visible output is `native-39e7a763.log`, raw run
`/home/cbattagler/meshcoreone-work/local-checks/run-0Nh8oXqk`.
Neither partial screenshots nor discovered failures satisfy140/108 acceptance.

The real failures exposed these bounded corrections:

- Frozen `ChatRoute.swift` compares conversation kind/UUID, not every DTO
  field. Native selections now preserve that equality/hash contract, retain
  entry IDs and refresh duplicate destination payloads. Original pending
  contact assertions now match the source's UUID checks, rather than adding
  an unrelated null-versus-zero Room timestamp assertion. Original fixture
  contact type/path values are also restored to1/0. Existing native assertions
  exercise changed payloads with stable route identity; counts are unchanged.
- The suite's legacy `NavigationBar` combines unweighted short items; its
  published source recommends `ShortNavigationBarCompact`. The actual native
  compact bar now uses that type. Back/setup icon layouts explicitly have48dp
  minimums. Hidden retained panes are asserted not displayed, not absent from
  composition, preserving saveable state and the same visible-only-detail
  requirement. Font200 diagnostics still fail on actual measured overflow.
- Transitive Espresso3.5.0 reflects the removed API37 `InputManager.getInstance`.
  First-party stable3.7.0 uses the actual context input service on API23+.
  App-only local/instrumentation test coordinates now explicitly request3.7.0;
  the actual16 graphs and strict lock/checksum outputs must be regenerated
  before another native run. Incumbent catalog pins are unchanged.

## Actual compiled-head verification iteration

At `a19f571f95916c244b22fda8fe52e1d382811e43`, the installed official
`--stages python,assemble` cycle passed: controller234/234, scaffold15/15,
installed-helper64/64, then real App Kotlin compilation and `:app:assembleDebug`
under strict dependency verification. Full visible output is
`assembly-a19f571f.log`; isolated raw directory is
`/home/cbattagler/meshcoreone-work/local-checks/run-BXmEWA9B`.
This removes the earlier dirty-input and traceability blockers. It is not the
required complete publication cycle.

The subsequent actual `--stages verify` command at the same head reached
`:app:compileDebugUnitTestKotlin` and failed: shared original-case/fixture
Kotlin sources were not in that compilation. The producer had attached them
to the Java source set. The real pinned AGP9.4.1 API exposes
`AndroidSourceSet.getKotlin()`; the receiver now uses the corresponding
`test.kotlin.srcDir` for the exact existing shared-case directory, preserving
one assertion body for local and instrumentation consumers. No duplicated
assertions, missing-test fallback or changed family count is introduced.
`native-a19f571f.log` retains full output and
`/home/cbattagler/meshcoreone-work/local-checks/run-4ufkSaqp` is the raw run.

The reaction-channel original uses source index1; its actual seeded Room
lookup is now explicitly given that same channel instead of default index0.
Expected source assertions are unchanged. Actual native execution/screens
remain zero until the corrected wiring really compiles and executes.

## Actual validator transfer and closed admission

The normal official status now records thirteen WP-302 paths: the previous
eleven plus `tools/android-port/portmap.py` and its existing `tests/test_cli.py`.
D0's receipt has thirty-four paths and no longer contains these two.
`reservation-after-validator-transfer.json` retains that actual readback.
No direct ledger edit, source/manifest/policy change or Content-specific
validator import is involved.

`navigation-scope-admission.json` binds exactly the approved native launcher
and unit-navigation prefix to WP-302, frozen source DB145, original manifest
78a229 and receiver base678. The validator requires the complete exact proof
and rejects missing, malformed, duplicate-key, tampered or broadened proof,
wrong ownership, sibling prefixes and changed source/manifest. This is
traceability admission only, never parity or merge acceptance.

Actual commands and outcomes before committing this admission:

```powershell
python -B tools\android-port\portmap.py
python -B tools\android-port\controller\test_runner.py
python -B -m unittest discover -s docs\android\evidence\WP-302 -p test_evidence_reader.py -v
python docs\android\evidence\WP-302\collect_evidence.py --inventory
```

Traceability passed. The controller runner discovered/executed234 tests:
232 passed, zero failed/skipped, two errors because its strict immutable-input
checks correctly reject the then-uncommitted `portmap.py` change. The new
positive/adversarial provenance assertions passed; the committed-head cycle
must rerun the full runner without this dirty-input mismatch. Reader19/19
passed. Static inventory still declares50 original families/140 native nodes,
not execution. Raw results are the `navigation-admission-*.log` files.

The actual official assembly retry at committed9f0e5511 passed App configuration
and reached localization conversion; its74-test self-check had one error
because the old portmap rejected the approved launcher. Full output is
`assembly-9f0e5511.log`, raw directory
`/home/cbattagler/meshcoreone-work/local-checks/run-kWo91q7U`.
This admission addresses that observed blocker; compilation and native
execution still require the next actual committed-head iteration. Historical
ownership/pending wording below refers to earlier heads, not current holds.

## Actual serialized App support integration

User-directed approval is effective; no additional parent carry approval is
required. The official installed transfer now assigns exactly the three App
build/lock/checksum paths to this WP-302 receipt, extending eight paths to eleven
while preserving the original identity, base/source/manifest/policy and eight
paths. D0 no longer owns these three paths. The complete actual status is
`reservation-after-app-transfer.json`; the historical eight-path receipt remains
unchanged in `reservation-receipt.json`.

The exact receiver535 Nav-only output blobs from D0's frozen434c packet were
verified against their SHA256/size and applied, not the incompatible full EFD
Content build. `nav-only-producer-freeze.json` retains the producer's actual
16-graph/3828-input receipt references and three byte bindings.
The receiver's original three inputs matched base678 and its actual head535.
Read-only independent lock/XML review confirms stable Nav3.1.1.0,
NavigationEvent1.0.2, Compose1.10.6 and all1078 receiver checksum tuples retained
with75 additions. D0's earlier full Content graph had1097 inherited tuples;
these are distinct producer contexts, not contradictory receiver counts.

Actual App configuration now declares `verifyWp302NavigationTests`, shared
original-case sources, SDK31/37 support, mandatory pre-test binding and actual
native artifacts. It does not reference absent Content invocation/collector
consumers. Kotlin compilation, native140/108 evidence and a full passing final
cycle are still required. Minimal base-applicable validator admission remains
separate; the full D0 portmap cannot be copied without its absent Content imports.
No source pin, incumbent catalog pin, hardware/license/signing gate or normal
publication requirement is changed.

The first integrated committed-head official `preflight,assemble` iteration at
`9edad240` passed readiness and then failed during App Gradle configuration:
`tasks.named<Test>("testDebugUnitTest")` ran before AGP registered that variant
task. `assembly-9edad240.log` retains the full output and raw-run path.
The receiver-owned App wiring now uses the repository's existing
`tasks.withType<Test>().configureEach` pattern with the exact task-name guard;
mandatory prebinding/freshness/verifier dependencies are preserved. This
corrects actual task registration timing, not a missing-test fallback.

## Current product implementation and producer contract

The coordinator's three-path approval is effective, not pending. Official
installed `reserve.py reconcile` extended the original lease to eight paths:
the actual launcher thin subclass, additive version catalog and unit-test prefix,
plus the five primary paths. `reservation-before-reconcile.json` preserves the
full CAS input; `reservation-receipt.json` is the actual current receipt.
D0's App build/lock/checksum paths remain solely D0; none were edited here.

Implemented source includes immutable five-tab state, Navigation3 per-tab entries,
current-width Material bar/rail/list-detail, native Back target scenes, edge-to-edge
host, public/redacted process restoration, real notification-service callback
binding, process-store lookup and queued generation-safe cold-route delivery.
The existing feature entries still state **Not yet ported**. No process/radio
graph, service owner or future feature success is manufactured.

The fifty original bodies are in the exact shared `androidTest/.../navigation/cases`
directory. Local concrete tests use actual seeded Room and policy callbacks on
SDK31/37; the separately named instrumentation class executes the same bodies
only on a real admitted runner. Static inventory currently declares **140**
local native nodes and **108** SDK-bound rendered states. **Executed native
WP-302 nodes remain 0**. The map-consumer assertion uses an extracted real
forwarding body rather than the earlier function-reference proxy; that narrow
equivalent is explicitly pending parity review.

Actual reader command:

```powershell
python -B -m unittest discover -s docs\android\evidence\WP-302 -p test_evidence_reader.py -v
python docs\android\evidence\WP-302\collect_evidence.py --inventory
```

Current results: **19 reader regressions passed**, no skips; static inventory maps all
50 families and declares 140 native nodes. Synthetic reader XML is **not**
native execution. No native assertion, screenshot, instrumentation or complete
committed-head cycle is claimed from either command.

The owned collector fails on source drift, zero/skipped/malformed/mismatched
XML, absent per-test SDK/head/tree/nonce/input bindings, missing native screen
states and altered hashes. It checks literal compiled checkout bytes against
candidate Git blobs; no CRLF reader waiver. Full raw XML is retained before
validation. It never approves a hardware/license/signing/parity gate.

Native PNG validation now reuses the existing WP-304 strict decoder rather than
trusting only the signature/IHDR dimensions and a matching hash. CRCs, complete
chunks/ending, bounded decompression, pixel counts and row filters are checked.
The two read-only helper files are included in literal pre-test input binding;
neither helper is edited and no WP-304 source-family credit is imported.
`png-reader-regressions.log` retains the 19-case result, including exact malformed
image rejection before screen-inventory checks. Valid synthetic pixels still
cannot replace the mandatory 108 real rendered states or 140 current executions.
Earlier 15-case results below remain historical evidence for those commits.

The D0-only build producer must declare the five catalog aliases as actual App
dependencies, resolve the corrected matched Nav3 runtime/UI1.1.0 proposal with
its published lifecycle2.10/savedstate1.4/navigationevent1.0.2 requirements,
and preserve existing Content/HTTP hooks. Test dependencies need the existing
coroutine-test and Room-runtime pins, instrumentation Compose/core/ext-junit/
runner, and the exact shared `cases` source directory in the unit source set.
Use the existing SDK31/37 Robolectric Sync/argument-provider/native export prior
art, not a substituted SDK or new incumbent pin.

Before `testDebugUnitTest` executes, call this actual owned CLI in the isolated
snapshot and declare its resulting `.properties` file as the test JVM's
`navigationInputBinding`:

```text
python docs/android/evidence/WP-302/collect_evidence.py --bind-inputs
```

Declare `navigationArtifactDirectory` under App's private build reports.
After real unit execution, the owned CLI is:

```text
python docs/android/evidence/WP-302/collect_evidence.py --check --self-test
```

`verifyWp302NavigationTests` is a **proposed producer task, not yet declared or
executed**. It must depend on the actual App unit task and retain existing
`verifyScaffoldTests` dependencies. The producer must freeze a reviewed Nav-only
carry applicable to base678; full D0 blobs referencing absent Content collectors
must not be blindly copied here.

Real OS keyboard/inset/Back assertions are declared separately in
`NavigationKeyboardBackTest`; they are unexecuted. Local native Back-dispatch
assertions do not establish Android37 predictive gesture progress/cancellation,
physical keyboard/TalkBack order, OEM/radio behavior or license/signing acceptance.
Those remain explicit evidence gaps, not skipped successes.

## Actual committed-head assembly iteration

The official command on product head
`7a5b369bbea43f8bda04907a60873ac3948429dd` was:

```powershell
python C:\Users\camer\source\repos\MeshCoreOne-Android\.git\hooks\meshcore-local\check.py --distribution Ubuntu-22.04 --commit HEAD --stages preflight,assemble
```

**Preflight passed; assembly failed**, before App Kotlin compilation, in
`:core:l10n:verifyL10nConversion`. The traceability validator rejected mixed
file-level `PortedFrom` and `AndroidOnly` declarations in
`OriginalNavigationCases.kt`. Mixed declarations in source-derived WP-302
files are corrected to ordinary native-adaptation comments; their pinned
source provenance is preserved. Pure native files retain honest `AndroidOnly`
declarations.

The complete visible output is `assembly-7a5b369b.log`; the installed runner's
raw directory is
`/home/cbattagler/meshcoreone-work/local-checks/run-vgDWTHah`.
It materialized **3856 tracked inputs from immutable HEAD blobs**. This was an
iteration, not a complete local cycle or publication receipt. No Kotlin build
or WP-302 native execution is claimed.

After correcting the mixed declarations, the exact documented validator
`python -B tools\android-port\portmap.py` still **blocks** at
`android/app/src/main/kotlin/com/meshcoreone/android/MainActivity.kt`.
Its manifest-only Android-only ownership check has no consumer for the approved
canonical additive paths. The root launcher and pure native unit-test helpers
are genuinely Android-only; relabeling them as Swift ports or another WP would
be false provenance. Shared support is required in `tools/android-port/portmap.py` and its existing
`tools/android-port/tests/test_cli.py` regression surface to consume a trusted
bounded additive-scope admission, preserving rejection of unapproved paths.
The refreshed canonical D0 receipt already includes both paths in its existing
30-path cleanup scope: the earlier request for an additional amendment for
these two was unnecessary and has been corrected through the producer handoff.
They are **not leased here and were not edited**. The exact failed validator
output is `traceability-after-header-fix.log`. This remains distinct from the
outstanding D0 App graph/test-wiring handoff.

The earlier receipt equality probe incorrectly compared the retained
`{ledger,result}` wrapper with the raw receipt. Its corrected comparison
against `reservation-receipt.json`'s `result` passed, confirming the exact
eight-path canonical receipt; the failed probe did not indicate a lease change.

## Subsequent bounded correctness review

Device cleanup now synchronizes the selected tool/settings fields with retained
offline/app-wide stack entries. Previously, dropping a radio-only top entry
could expose a retained Line of Sight or Language detail while leaving its
selection null (and therefore deriving incorrect sidebar collapse). The native
lifetime family now asserts both layered stacks and their surviving selections.
Successful duplicate routes and disconnected-room authentication requests also
clear a superseded navigation failure rather than displaying an old error after
the new transition. Both connected/disconnected callback branches assert this.

The existing native Back family now drives the published Activity1.13 dispatcher
start/progress/cancel/completion methods at both 360dp and 834dp, asserting no
premature stack commit and retaining draft/focus/selection on cancellation.
Scene equality and remembered transition metadata avoid incidental scene churn.
These are **unexecuted**
SDK31/37 local-host assertions, not actual OS predictive gesture evidence.
The node/render declarations remain 140/108; no acceptance count was increased.

The source notification failure routes are also preserved: new-contact lookup
failure still selects Nodes, and a reaction's failed contact lookup can recover
through its channel while preserving the message ID. Unlike Swift's `try?`, the
native recovery retains the typed repository failure in state and returns
`NavigationOutcome.Fallback`; it does not pretend an unconditional clean success.
If the channel is absent or not found, the original repository failure is surfaced.
Cancellation still propagates instead of starting fallback. Actual callback and
wrapped-cancellation assertions cover these branches, but remain unexecuted.

The initial published API review used then-proposed coordinates. Navigation-suite Android1.4.0
sources SHA256 is
`877bb57e1c5c96c716abeb574454fe8cefe336f8fba015ceebec1b4313380c82`;
Activity1.13.0 sources SHA256 is
`c7cf1b2e315e08867b10ef4505945d1993d90281445c65b929e81b9420216ce9`.
Nav3 Android1.2.0 runtime/UI sources are respectively
`34ee8af5cde26c77c902c69a556ddde67dc7d7afba7df4c35132ca41008a6ab1` and
`7426b905f04b46a7adfe573f844a08a96ab73dcf3dcdc7b3108227fd6fea1cde`;
the real UI scene decorator owns movable/shared entry content, not a shell
workaround for duplicate composition.
The suite's explicit `NavigationRail` still renders the 80dp Material rail;
its newer bar overload permits scalable labels. Reading licensed public source
APIs is not binary checksum admission, Kotlin compilation or native execution.

## Actual dependency incompatibility and bounded correction

D0's actual sixteen-graph generator rejected the original Nav3 runtime/UI1.2.0
proposal at producer head `31609ad77e8d6cecd1fc9d2e744fdb2e56f4bcb9`.
It selected Compose1.11.0-rc01, not incumbent1.10.6. No rejected generated
lock/checksum bytes were adopted.

Independent root/Android publication review identifies the concrete RC path:
Nav3 UI1.2.0 requires navigationevent-compose1.1.1, whose POM **and** Gradle
module require Compose1.11.0-rc01. Nav3 UI Android1.2.0 separately disagrees
between its POM runtime minimum1.11.2 and module minimum1.10.0. Reading just
that module's lower minimum cannot establish compatibility.

Only the owned additive `navigation3` version is corrected to **1.1.0**,
matching runtime/UI. Its root and Android UI POM/module agree on Compose1.10.0;
runtime requires1.9.5 and navigationevent-compose1.0.2 requires1.9.2.
These requirements fit below incumbent Compose1.10.6. All incumbent pins,
suite1.4.0, adaptive/layout1.3.0 and test coordinates remain unchanged.
The actual complete selected graph still requires D0's serialized generator;
there is no force/downgrade rule, RC admission or compatibility-pass claim.

`coordinate-correction.json` records this exact producer request and source API
review. `coordinate-review/` retains eighteen first-party root/Android raw POMs
and modules, including both event versions and the rejected UI Android1.2.0
publications. Corrected Nav3 Android1.1.0 runtime/UI source SHA256s are
`2959e7f504c93451888c94b9607f19d95bdd0ece352a7d6cc33c3a3211e65c70` and
`ad364e0b64307169bcdf40a27004d75e5d0799e6c59ee57f4d4e63452aaa8e0b`.
Typed entries/decorators, scene-strategy lists/scope, scenes and predictive
transition metadata used by the shell exist in these actual sources; no shell
API rewrite was necessary. This is publication/API review, not Kotlin
compilation, binary checksum admission, native execution or license approval.
`coordinate-review-confirmation.log` retains all eighteen raw-byte SHA256s
and asserted POM/module requirements; `coordinate-api-review.log` retains
the matched source-jar hashes and exact used declarations. Earlier inspection
probes are retained separately and are not passing evidence.

## Full official cycle on the corrected dependency head

After the coordinate correction, the complete unchanged official cycle was
actually invoked again against committed `5507d4041ea7535b7b758d419894eb15eede92c9`:

```powershell
python C:\Users\camer\source\repos\MeshCoreOne-Android\.git\hooks\meshcore-local\check.py --distribution Ubuntu-22.04 --commit 5507d4041ea7535b7b758d419894eb15eede92c9
```

**Failed, exit 1.** All **3885** tracked inputs were materialized from immutable
candidate Git blobs. Manifest validation passed, but the actual Python-stage
traceability CLI again exited 2 at the approved Android-only preserved launcher.
The later preflight/verify/standalone/assemble/lint/inspect stages did not run.
Correcting Nav3 cannot repair this independently owned validator admission gap.
No corrected graph generation, Kotlin compilation or native execution occurred.

`full-cycle-5507d404.log` retains the complete visible output. Raw installed
reports are `/home/cbattagler/meshcoreone-work/local-checks/run-e26bnifB`, including
`traceability.log`. D0's subsequently inspected frozen `0f8ff964` commit changes
its Content TLS test, not the outstanding Nav producer support. Its retained Nav
packet still reports the rejected 1.2 graph; no support carry was inferred from
that branch advancing. No new development push or WP-302 PR was attempted.

Further bounded source review makes host restoration one atomic StateFlow
compare-and-set, matching the coordinator's other transitions. The former
check-then-assignment could overwrite a navigation update between those two
operations. The existing restoration assertion now checks fresh-host acceptance,
the exact restored state and rejection without changing a live host. This
preserves the host-only initialization contract and does not add a process/radio
owner. These Kotlin assertions remain unexecuted; declarations remain140/108.
`restoration-reader-regressions.log` retains the actual nineteen passing reader
checks after this correction; the inventory still reports fifty original
families and140 declared nodes, not native execution.
D0 subsequently recorded receipt of the exact5507d404 coordinate correction
in its frozen `2d275d06` producer packet and updated its owned pending proposal.
That packet explicitly still requires actual corrected graph generation;
recognizing the proposal is not a completed support carry.

## Frozen stable producer graph review and exact source scenarios

D0 subsequently committed the three producer files at `efd67917`. Read-only
Git lock review finds matching Nav3.1.1.0, NavigationEvent1.0.2 and incumbent
Compose1.10.6/Material3.1.4.0. XML artifact/SHA256 tuple comparison preserves
all1097 inherited checksum entries, with75 additions and no removals.
`producer-review-efd67917.json` binds the exact three blobs and limits.

The full App build is **not yet a base678-applicable Nav-only carry**: it
unconditionally depends on `:core:services:prepareContentInvocation` and
finalizes through the WP-218 collector, both absent on this branch. They exist
in D0's producer tree. The concrete missing-consumer review was delivered to
D0; no producer file was copied, edited or silently repaired here.
An actual corrected generator receipt and serialized reviewed carry remain
required, independently of the existing validator admission blocker.

The original-family audit corrects exact source scenarios rather than just
their names: the reaction-channel case now uses source index1, automatic-add
uses a real connected device with `manualAddContacts=false` instead of nil,
and the narrow sidebar family checks retained navigation and collapsing shapes.
The existing native Compose collapse family exercises both744 and834dp,
including noncollapsing CLI and both collapsing tools. Existing screenshot IDs
and declaration counts140/108 remain unchanged; reader19/19 still passes.
These corrected Kotlin bodies have not been compiled or executed.

## Complete official cycle attempt after the correctness fixes

The full, unchanged official cycle was actually invoked, not a selected-stage
or fixture-only substitute:

```powershell
python C:\Users\camer\source\repos\MeshCoreOne-Android\.git\hooks\meshcore-local\check.py --distribution Ubuntu-22.04 --commit bd59980e73c206c6d69397bda88e5607aff80165
```

**Failed, exit 1.** It materialized all **3859 tracked inputs from immutable
candidate Git blobs**, passed manifest validation, then failed the Python
stage's actual traceability CLI with exit 2 at the approved preserved launcher.
The later preflight/verify/standalone/assemble/lint/inspect stages did **not**
execute in this attempt. No Kotlin compilation, native discovery/execution or
successful complete cycle is inferred.

Full visible output is `full-cycle-bd59980e.log`; installed raw reports are
`/home/cbattagler/meshcoreone-work/local-checks/run-KZcizvbE`, including
`traceability.log`. D0 already holds the exclusive canonical validator and App
support paths. The current blocker is delivery of that authorized producer
correction/carry, not additional worker/scope authorization. No development
push or WP-302 PR was attempted. Reader regressions and static inventory output
for the corrective implementation are retained in
`correctness-reader-regressions.log`: 15/15 passed, 50 original families and
140 declared local nodes, not native acceptance.

## Historical admission at d6826d81

The remaining sections preserve the initial blocker/preflight facts. Their
proposal/unported/five-path wording describes that admission, not current scope.

The user authorized WP-302 alone. The canonical shared reservation is recorded
for app session `53768196-935e-4bee-8973-927411658acf`, managed branch
`cbattlegear-studious-train`, in its dedicated worktree. The CLI session is
`9c6930bf-1dd1-47ad-80b1-e96507c99e58`. No additional worker, WP, schedule,
repository setting, dependency installation, push or PR was started.

## Binding and prerequisite

| Input | Verified value |
| --- | --- |
| Implementation base / inspected product head | `67857474ef3d3012ab73c19a4943ec1334c20676` |
| Base tree | `784ac936ef8a2d6a669167ea4e1fd89ff9c61ceb` |
| Frozen Swift reference | `db14559b39d32322b06477c6ae676112f583db50` |
| WP-301 merged prerequisite | `824df06bfa91d2d7d39237a894b10ab06b8c039d`, ancestor of inspected head |
| Manifest semantic digest from canonical lease | `78a22920beaa5899f9618806b5cd2b27d50399a9b29b4d8dbd79f755717ec746` |
| Policy revision from canonical lease | `56bdc53548bc86d631245795dfa38b4fc86048e0e7cbe1c7d5695879b035b42a` |
| Test catalog checkout-byte SHA-256 | `742a3f04fa786f8dd98d800c56a3df5a37e2fa93ffd37f5895af15a1b34ac537` |

The ten primary WP-302 inventory entries all have identical blob IDs in the
manifest, frozen source commit and inspected head. There is no WP-302 source
drift. Repository-wide Swift test drift is explicitly recorded in
`docs/android/reference-amendments.json` (correlation and advertisement tests);
neither exception advances the pin or changes a WP-302 input.

The actual app is `com.meshcoreone.android`, debug suffix `.debug`. Its manifest
launches `com.meshcoreone.android.MainActivity`, not the authorized new
`com.meshcoreone.android.app.MainActivity` path. The existing Activity renders
`ScaffoldApp`; the existing feature registry exposes seven stable root entries,
five of them tabs. They remain explicitly incomplete feature shells.

## Official lease

The installed Git-common-dir `meshcore-reservations\reserve.py` returned a
`supervised-write-reservation` for WP-302/design-system-engineer with the binding
above. A subsequent `status` read confirmed exactly these five paths:

```text
android/app/src/main/kotlin/com/meshcoreone/android/app/navigation/
android/app/src/main/kotlin/com/meshcoreone/android/app/MainActivity.kt
android/app/src/androidTest/kotlin/com/meshcoreone/android/app/navigation/
docs/android/deviations/WP-302.md
docs/android/evidence/WP-302/
```

D0/WP-218's separate reservation remains intact. In particular, App build,
App dependency lock and root dependency checksums are assigned to D0, not to
this session. No release, transfer or additional-path claim was attempted.

The existing [Project4](https://github.com/users/cbattlegear/projects/4) item
`PVTI_lAHOAA37Cs4BmG8Azg_OEcs` was refreshed before persistent evidence edits.
It matched WP-302, Worker `cbattlegear`, Agent `Copilot` and this managed
branch/CLI session. After the coordinator's user-directed instruction to keep
this card updated, its existing Blocker field was set to the concrete six-path
proposal and Status to **Blocked**. Readback retained the same owner, agent and
branch. No duplicate card was created. The project is coordination, not the
canonical atomic write reservation.

## Executed readiness

From this Windows worktree, the installed official checker was invoked:

```powershell
python C:\Users\camer\source\repos\MeshCoreOne-Android\.git\hooks\meshcore-local\check.py --commit 67857474ef3d3012ab73c19a4943ec1334c20676 --stages preflight
```

**Passed, exit 0.** It imported a Git bundle into its own managed WSL/ext4
snapshot and executed the scaffold's `android/scaffold/check_environment.py`.
Linux Git was not run against the Windows worktree pointer.

Raw reports remain at
`/home/cbattagler/meshcoreone-work/local-checks/run-Hn3j2KUJ`.
The visible checker output is retained in `preflight.log`.
The result binds the base commit/tree above, `stages: ["preflight"]`,
`elapsed_seconds: 0.56`, and explicitly states local-only authority.

This was an environment check, **not the complete official local committed-head
cycle** and not a test execution. Native WP-302 tests discovered/executed:
**0**. No acceptance ID is satisfied. Strict dependency preparation,
verification, standalone convention tests, assembly, lint, APK inspection,
screenshots, API37 instrumentation, IME/predictive-Back interaction and hardware
validation have not been run for WP-302. No new development push is permitted
on the basis of this preflight.

## Concrete support-path amendment

The primary write paths alone cannot change the actual launcher or add the
required libraries/test wiring. `support-path-amendment.json` identifies six
literal additional paths and the three active D0 overlaps. It is a proposal,
**not approval or a lease**.

The smallest launcher integration preserves the existing externally visible
Activity identity using a thin delegate/subclass in the existing launcher file.
This avoids changing AndroidManifest or loosening the APK inspector's exact
launcher assertion. The approved new app Activity and shell then own behavior.

The scaffold version catalog currently has no Navigation3 or Material adaptive
coordinates. Required additions include Navigation3 runtime/UI, Material
navigation suite and adaptive list-detail/window APIs, plus a real Android test
runner/Compose instrumentation setup. Exact mutually compatible versions must
be admitted with strict dependency locks/checksums, not guessed here.

The official `verifyScaffoldTests` path runs App Robolectric unit tests; it does
not execute `src/androidTest`. A separate leased App unit-test prefix is needed
for original-family and deterministic Compose evidence in that real local
cycle. Instrumentation must remain separately declared and actually executed;
unit assertions do not establish system predictive Back or IME behavior.

## Original acceptance inventory

`source-inventory.json` binds all ten original primary inputs and all fifty
declared families (22 coordinator, 21 state, 7 sidebar layout). Every family is
currently **unported**, not passed, excluded or credited to WP-304.

Additional production scenarios also require assertions: room notification
connected/authentication branches, missing repository entities and typed
failures, cancellation/stale-generation route delivery, selected-detail privacy,
manual disconnect versus radio replacement, cold start, tab-stack restoration,
width threshold crossings, tool-specific sidebar collapse, tool teardown on
tab exit, focus/keyboard preservation, single-applied insets, unread badge
semantics/overflow, 200% text, RTL/CJK and reduced motion.

`selectedContact` explicitly must not be persisted as an identity-bearing DTO.
Saved tab/detail state must not silently serialize its public key or radio ID.
Swift's 764-point sidebar tiling threshold and 744/834-point test scenarios
require an explicit Material/current-window adaptation, not a device check or
claim that 600dp alone preserves the three-column source layout.

The navigation shell may consume existing feature entries without manufacturing
future feature implementations or claiming their availability. Process/radio
graph assembly and connection ownership remain WP-303/WP-207 responsibilities.

## Resume condition

Obtain the bounded support-path amendment and a coordinated canonical lease for
the overlaps; do not take D0's lease. Then implement this same WP/worktree,
declare and run real nonzero native acceptance, retain source-family/parameter
mapping and current input hashes, and complete the unchanged official visible
committed-head local cycle before any new development push.

No human, license, OEM, physical radio, signing or release gate is claimed.
