# WP-202 evidence boundary

Repository `cbattlegear/MeshCoreOne-Android`; owner
`data-persistence-engineer`; branch `cbattlegear-potential-spork`.
App session `e83ac80e-ce2c-45c1-9574-3e04f1de87a6`, project
`663db92c-ed50-4a77-aded-bda85a7c503a`, native project alias
`8a3ef5a7-3f45-4c93-8ecb-e252fa000482`.

Bound initial HEAD/base: `dc15f1ba445acf3230383ea68d4827c592f3fafa`.
Coordinator-verified first integration base:
`fbb7eb6f88f1b3a74eaddb68cac911ff52650d02`, after the actual PR-20
evergreen WP-201/Windows newline repair. Later verified bases were
`3da3a73b8481c49d035486924a813b331bf184d0` after WP-107 and
`e303e8795ab4e7cc45e02d791e6a2829085c2288` after WP-205.
Current authorized merged base:
`2cf00464950e1fb9aae0dd913402eb3e12dc0044`, after genuine WP-204 merge.
Only owned attributed commits were rebased; their code remained unchanged. The initial
receipt and original reviewed checkpoint decisions remain historical
bindings, not an implicit change to source/schema/dependency pins.
Reference: `db14559b39d32322b06477c6ae676112f583db50`,
tree `8918fdc604341e6996a68c88f6bb1c02b9c2f87e`.
Manifest `78a22920beaa5899f9618806b5cd2b27d50399a9b29b4d8dbd79f755717ec746`;
policy `56bdc53548bc86d631245795dfa38b4fc86048e0e7cbe1c7d5695879b035b42a`.
Receipt `autonomous-WP-202-dc15f1ba` is the canonical package/evidence lease,
not blanket permission to edit shared build/lock/DAO/schema/contract inputs.
The coordinator separately reviewed checkpoint
`db02af0b50c4221d95d71616b0ce1c9f491a97c0` and admitted exactly the
four paths/changes in `amendment-request.json`. The module-local lock option
is selected; shared root locks/catalog/verification XML remain unowned.

All 26 primary production, 13 original test and 3 support inputs were
read, including the full 4,200-line store test and 2,362-line support mock.
The frozen catalog contains 208 single declaration families. Current
authored native annotations bind 190 uniquely; the eighteen historical
Apple migration declarations have individually coordinator-reviewed
native-equivalent/Apple-historical-only-exclusion decisions, not invented
Android upgrades. Both prior verified hosted heads executed all 154 native
methods and all 208 exact declaration dispositions; that execution does not
turn an Apple-only historical sweep or flag into an Android upgrade.

[`migration-dispositions.json`](migration-dispositions.json) enumerates every
one of those eighteen frozen IDs/blobs, the original effect, a concrete
authored native assertion, its limits and the retained backup/preference/
sync consumers. The stored decision is **coordinator-reviewed**, and its
named native assertions passed in the historical both-host runs below.
`InitialSchemaMigrationBoundaryTest` exercises actual v1 defaults, all
fourteen radio-owned tables, explicit/nullable keys, badge filters, exact
negative/fractional dates, same-v1 file reopen, and missing/malformed stored
date failures with actual Room. It does not
relabel any of those as Apple migration or compatible backup execution.
The coordinator-required parameter retention includes exact Hello/World
arrivals1704067200/1704070800, Buried backlog/send-time1700000000, a
distinct raw repeater7 and later1, and warm-up/file-reopen preservation of
explicit Swift `Date.distantPast`. Its independently pinned Foundation
value-type definition, epoch arithmetic and the hex-letter UUID/UTF-8 key
vector are recorded in
[`independent-vector-provenance.json`](independent-vector-provenance.json).
Those published source facts are not a fabricated macOS execution; the
different `NSDate` class constant was explicitly not substituted.

## Actual attempts

| Command / arguments | Actual result |
| --- | --- |
| `python -B tools\android-port\controller\verification_config.py --check` | Passed frozen manifest overlay validation; feature acceptance remains unconfigured |
| `python -B tools\android-port\controller\validate.py` | Passed pinned source, 65 WPs, 185 edges and ownership inventory |
| `python -B tools\android-port\controller\runtime_inputs.py` | Passed all 77 committed/checkout runtime inputs at the initial head |
| `python -B tools\android-port\controller\test_runner.py --quiet` at `e5b3b9568dd1549120186870b2303a604ed8d7f9` | Passed187 actual controller-fixture tests,0 failed/errors/skips; not native persistence assertions |
| `python -B tools\android-port\controller\runtime_inputs.py` at `e5b3b9568dd1549120186870b2303a604ed8d7f9` | Passed all77 exact committed/checkout runtime inputs |
| `python -B android\scaffold\sync_notices.py` | Passed pinned GPLv3/MIT/Apache notice and artwork drift check; not legal admission |
| `python -B -m unittest discover -s docs\android\evidence\WP-202 -p test_collect_evidence.py -v` | Passed12 actual collector-only tests,0 failures/errors/skips; synthetic XML is never native original-case proof |
| Same collector-only runner after encoded-declaration hardening | Passed15 actual collector tests,0 failures/errors/skips; UTF-16/32 declarations and oversized inputs are rejected before any XML parser call |
| Credential-stripped `python -B android\scaffold\check_environment.py` before provision | Failed: missing explicit JDK/SDK/private cache inputs; PATH exposed JDK11 |
| `python -B tools\android-port\controller\ci.py provision --root <new-own-session-toolchain> --accept-sdk-license` | Passed exact checksummed archive-only installation; no SDK CLI/helper bootstrap or global configuration |
| `python -B tools\android-port\controller\ci.py preflight --state <own-environment.json> --output <own-evidence> --local` | Passed actual isolated `check_environment.py`, exact JDK21.0.12.1/SDK37.2/build-tools37.0.0/private cache checks |
| `android\scaffold\invoke-gradle.ps1 -ConstrainedMemory -BuildHeap 512m -BuildMetaspace 512m -TestHeap 256m -TestMetaspace 256m -GradleArguments @(':core:data:compileDebugKotlin', '--project-cache-dir', <own-private-project-cache>, '--dependency-verification', 'strict', '--no-build-cache')` | Failed in 4m33s, 32 executed tasks: missing module compile classpath and two undeclared DAO operations |
| `:core:data:dependencies --write-locks --dependency-verification strict --no-build-cache` through the same512m private launcher | Passed actual admitted module-local lock generation |
| `:core:data:compileDebugKotlin --dependency-verification strict --no-build-cache` | First admitted attempt failed on observed late `androidApis` missing local state |
| `:core:data:compileDebugKotlin --write-locks --dependency-verification strict --no-build-cache` | Passed actual compilation and generated owned late empty `androidApis` state;38 byte-proven newline-only shared lock effects restored to configured Windows checkout form |
| `:core:data:testDebugUnitTest --tests com.meshcoreone.android.core.data.repository.PendingSendPersistenceTest --tests com.meshcoreone.android.core.data.repository.MessageWindowPersistenceTest --tests com.meshcoreone.android.core.data.repository.NativeRepositoryBoundaryTest --dependency-verification strict --no-build-cache` | First actual34-case run33passed/1failed due measured269-character native Windows sandbox file path; exact same repaired suite34passed/0fail/errors/skips with short sandbox filenames |
| `:core:data:verifyPersistenceRepositoryTests --dependency-verification strict --no-build-cache` | Actual full module153discovered/152passed/1failed/0errors/skips; sole NaN trace-encoder NPE recorded in the exact narrow converter amendment request, not waived |
| `validateModuleGraph runtimeDependencyInventory resolveScaffoldDependencies --dependency-verification strict --no-build-cache` | Passed actual production graph/runtime/component resolution |
| `:app:assembleDebug --dependency-verification strict --no-build-cache` | First512m attempt failed actual D8heapOOM; fresh512m JVM retry passed actual APK assembly, with74 actual l10n generator self-tests |
| `lintScaffold --dependency-verification strict --no-build-cache` | Passed all24 actual lint XML reports;0fatal/errors,495warnings reported without suppression or reclassification |
| `python -B android\scaffold\inspect_apk.py` with private pinned SDK inputs | Passed actual40,201,289-byte debug APK, SHA256`14d6592b16043b570ccf8a41137fddcc46611266204869646e7df837838ba268`, min31/target37/debug package/notices/fixture exclusion; not physical/native16KiB certification |
| Standalone `:convention:test --dependency-verification strict --no-build-cache` with initially private separate Gradle/project cache | Passed31 actual convention cases/0fail/errors/skips |
| `:core:database:verifyDomainRoomTests :core:protocol:test :core:testing:testDebugUnitTest --dependency-verification strict --no-build-cache` on first verified foundation | Passed separately scoped WP201215, protocol4445 and helper35 cases, not WP202source assertions |
| `:core:protocol:test :core:data:compileDebugKotlin --dependency-verification strict --no-build-cache` after actual WP107 integration | Passed actual compile and separately scoped merged protocol baseline; raw counters recorded independently |

The complete initial compile log is retained in the session evidence,
SHA256 `db54f8f544aeee558fe76d429c2b41a2c8312dc11d9445b3a06b7978ce604d7b`.
The initial local full-suite attempt failed on the recorded NaN converter
defect; it remains retained as a failed attempt. The source/negative
expectation was not lowered. The approved finite-value guard subsequently
passed in both complete historical hosted runs below. Local compilation,
graph/APK/lint and baseline results do not claim protected WP acceptance,
signing, device, radio or backup oracle results.
Additional authored native regressions now cover exact one-second partial
flush scheduling, trace rows/runs/malformed conversion, global log/cache
retention, sparse node-history baselines and first-wins enrichment, two-store
transaction races, the reaction100/message50 defaults, failed-send keys,
high-bit/null/empty fields, and cancellation on the Room commit-return edge.
Those methods executed in the initial 153-case suite; its failure is
recorded in
[`converter-amendment-request.json`](converter-amendment-request.json).
The coordinator subsequently admitted that exact one-path fix. Only the
pre-JSONArray finite-value guard is added; every other converter byte/
method/signature is unchanged. NaN and both infinities retain the original
typed-failure/rollback expectations. Additional real-DAO assertions cover
empty, negative/fractional/integral and signed-zero arrays against independently
pinned API31 JSON encoding facts, and confirm invalid appends do not delete
committed finite runs. The repaired complete 154-case suite passed on both
hosts at the historical heads below. The original failed 153-case artifact
is not rewritten or discarded.

The coordinator explicitly released WP204's slot and granted WP202 one
exclusive bounded Java lane. Commands use one worker, in-process Kotlin,
512m heap/metaspace and256m test heap/metaspace, SerialGC/twoCPU, private
caches and one command at a time. The earlier held phase launched no Java;
its sole earlier daemonPID52716 had already exited. Lightweight collector inventory verifies
the exact190 source bindings/18 individual reviewed decisions; mandatory
collection failed closed on the actual failed native XML. Each of those
eighteen can resolve only to its real executed testcase identity and
retains its reviewed split/consumer limits.
The collector additionally requires exact raw identities for every current
declared native test, including Android-only boundary assertions without
original-case annotations. Filtered/omitted or stale method suites cannot
satisfy it. Report size is checked before opening, reads are capped at
8 MiB plus one overflow byte, and DOCTYPE/ENTITY declarations are rejected
before parsing, including UTF-16/32 zero-byte-interleaved forms. These
collector-only negatives do not become native persistence assertions.
Normal nondraft PR Windows/Linux CI is allowed to execute the
actual module while a local Java slot is held. Historical normal CI
outcomes and complete same-run schema2 bindings were independently checked.

## Verified historical hosted proof

The following are exact prior-head proofs, not claims for the new WP-204
integration:

| Actual run | Bound head / base | Verified result |
| --- | --- | --- |
| [`37238922849/1`](https://github.com/cbattlegear/MeshCoreOne-Android/actions/runs/37238922849) | `ba8383664b02d43c24d22b85fc9ce98d42ae73d4` / `3da3a73b8481c49d035486924a813b331bf184d0` | Both hosts 154 real Room methods passed, 0 failed/errors/skips; all 208 exact dispositions; complete schema2 root/graph/runtime/APK/lint replay |
| [`37255526711/1`](https://github.com/cbattlegear/MeshCoreOne-Android/actions/runs/37255526711) | `3bd677e964b68e281a1d6b2ffd85387bd3d7b172` / `e303e8795ab4e7cc45e02d791e6a2829085c2288` | Both hosts 154 Room methods and all 208 dispositions passed; all 685 active-module methods and separate root/protocol baselines retained |
| [`37255526635/1`](https://github.com/cbattlegear/MeshCoreOne-Android/actions/runs/37255526635) | `3bd677e964b68e281a1d6b2ffd85387bd3d7b172` / `e303e8795ab4e7cc45e02d791e6a2829085c2288` | 4708 separately scoped real protocol methods per host passed; 57 original/mirror reports byte-verified and counted once |

Complete archives/raw XML and independent trusted-base-reader replay records
are retained in the owning app session. The PR body records provider
artifact IDs, exact archive hashes, inspected APK hashes and replay hashes.
No evidence-only Git head was created to relabel those prior runs.

## Merged WP-204 integration

The coordinator reported a complete independent static source review of
the reviewed WP-202 repository and original-test inputs with no significant
high-confidence correctness finding. This is not a formal WP/gate approval.
Only owning commits were rebased onto actual merged
`2cf00464950e1fb9aae0dd913402eb3e12dc0044`; the reviewed production, tests,
approved DAO/converter changes and schema are unchanged.

That base supplies real DataStore 1.2.1, Okio 3.9.1 and serialization 1.7.3,
with vetted committed verification metadata and generated shared consumer
locks. WP-202 still uses its separately admitted **module-local**
`android\core\data\gradle.lockfile`. It must be generated by actual strict
Gradle resolution for the merged graph, not copied from a shared lock or
constructed from the old shell.

The unchanged old local lock was genuinely rejected by both hosts in
[`37272272880/1`](https://github.com/cbattlegear/MeshCoreOne-Android/actions/runs/37272272880),
head `a0036559a6df4a7ed4d27d5a6d72e5906f4311ae`, base `2cf00464...`.
Each failed at `:core:data:checkDebugUnitTestAarMetadata` resolving
`debugUnitTestRuntimeClasspath`, with the same 19 admitted transitive
components missing from local lock state. Setup also failed stale state,
and the aggregator correctly failed closed. Complete failed bundles/logs
are retained; this was not a failed native assertion or a passing suite.

The owning build script now declares `resolvePersistenceDependencies`,
following the actually merged DataStore resolver pattern but restricted
to `:core:data` only. It resolves all owned resolvable configurations,
throws with the actual `UnresolvedDependencyResult` cause, and writes the
complete component TSV only after every owned graph succeeds. It does not
visit or rewrite app, feature, peer or shared consumer configurations.
The real repository verification task depends on this owned graph check
and actual native test execution.

The admitted next command is
`:core:data:resolvePersistenceDependencies :core:data:compileDebugKotlin --write-locks --dependency-verification strict --no-build-cache`
through the existing constrained private launcher, one command at a time.
The actual bounded generation/compile command now **passed**. It added
exactly the 19 admitted coordinates across eight runtime/lint configurations
to the module-local lock, canonical-LF SHA256
`21ce3d21f16b1b5e67e7102e58a782e17d07907136b96586ac603561552fd8cc`.
All incumbent records and 71 shared lock/pin/schema contents were preserved;
38 byte-proven generator EOL side effects were restored using their Git
checkout filters. Actual owner-only resolution covered 36 configurations
and 750 component rows, not a handwritten or copied graph.

The smallest diagnostic suite then passed 12 real cases. The full
`:core:data:verifyPersistenceRepositoryTests validateModuleGraph runtimeDependencyInventory resolveScaffoldDependencies --dependency-verification strict --no-build-cache`
command passed all **154 real Room methods and 208 exact original
dispositions**, zero failures/errors/skips. All 17 raw reports and the complete
source/identity record are retained verbatim. Exact commands, log/report
hashes, generated-state comparison and limits are in
[`wp204-integration-results.json`](wp204-integration-results.json).

The exclusive local slot was explicitly released after checking the exact
three recent own daemon PIDs had exited. No unrelated process was touched
and no local 512m D8/APK retry was performed. Final committed-head normal
both-host root/protocol proof still follows publication, including the
inherited 131 DataStore and 295 BLE cases without relabeling them WP-202
assertions. Earlier failing artifacts and old-base passing results remain
historical, not current acceptance or a self-issued review.

The exact shared amendment request is
[`amendment-request.json`](amendment-request.json).
It is now **ACTIVE** and reuses admitted pins. The build script applies the
existing Robolectric convention, adds direct Room/coroutines and coroutines-test,
selects the local lock, and hooks the actual native task/collector into
`verifyScaffoldTests`. Only global session reset and single selected
blocked-sender deletion are added to the actual DAO interfaces.
The local lock is actual strictly generated Gradle state, never hand-authored.
No root lock/XML, version/catalog, schema/model/contract, source/golden,
policy/acceptance or activation changes are authorized.
See [native adaptations](../../deviations/WP-202.md).
