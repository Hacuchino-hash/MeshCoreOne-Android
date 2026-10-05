# WP-207 connection runtime evidence

This records the bounded runtime's provenance, verification commands and
immutable execution history, not a merge/acceptance receipt. The exact current
head's run, raw artifact digest and independent review are bound in normal
[PR #26](https://github.com/cbattlegear/MeshCoreOne-Android/pull/26); historical
snapshots below must not be relabelled as a later head's proof.
Owner: `services-porter`. Native session:
`0b884790-dba5-4f43-bef4-70d6f11a2e38`; app alias:
`509a284a-90c8-40c8-934f-a74d37cd21ef`. Sole app-managed branch:
`cbattlegear-connection-runtime-ownership`.

Initial clean HEAD/local main/origin main:
`e697823c8937eb5b12a40362ca2c5aae4c45f56a`, actual parents
`2cf00464950e1fb9aae0dd913402eb3e12dc0044` and
`7d76858c17e607af1a7f96ff6a42392de1b58f85`. Prerequisite implementation
PRs [#19](https://github.com/cbattlegear/MeshCoreOne-Android/pull/19),
[#23](https://github.com/cbattlegear/MeshCoreOne-Android/pull/23),
[#21](https://github.com/cbattlegear/MeshCoreOne-Android/pull/21) and
[#18](https://github.com/cbattlegear/MeshCoreOne-Android/pull/18) were actually
merged and their merge SHAs were verified ancestors of the initial HEAD.

Frozen source: `db14559b39d32322b06477c6ae676112f583db50`; tree:
`8918fdc604341e6996a68c88f6bb1c02b9c2f87e`. All 28 primary production/test/helper
blobs match. Semantic manifest:
`78a22920beaa5899f9618806b5cd2b27d50399a9b29b4d8dbd79f755717ec746`;
policy:
`56bdc53548bc86d631245795dfa38b4fc86048e0e7cbe1c7d5695879b035b42a`.
65 IDs, 185 edges, all planned pending states and eight original gates are unchanged.

## Write and execution receipts

The coordinator bound `autonomous-WP-207-e697823c` to this native/app identity,
worktree and initial head before the first edit. Canonical writes are the entire
`android/core/runtime/` module, this evidence directory and the WP-207 deviation.
The single app-native branch rename was explicitly approved.

Separate exact amendments authorize the two new native integration test files,
the DataStore **test-only** runtime edge/actual module lock generation, and the
bounded `android-runtime-dependency-generation.yml` candidate workflow. Data's
shared build/lock and evergreen repository source-reader changes remain with
the serialized WP-203 producer/coordinator, not this worker. The approved
historical three-file producer carry is recorded in `producer-carry.json`.
It was subsequently superseded by actual merged WP-203 production at base
`d8f9b842581496baa382be1fe54dc866354b8150`; the coordinator explicitly authorized
that exact current-main adoption through an attributed owning-branch merge.
The backup hooks and source floors are preserved, not reverted to the carry.
The later independently merged CI-consolidation PR #29 advanced actual main
to `3c06d97e11cea96827c3349e78d1a5db7a8d5ad0` (parents `d8f9b842` and
`4f3793dd`). It is adopted only as an actual merged base, not as an unmerged
external actor's checkout. Earlier run/base bindings remain historical.

Initial local JVM execution used the coordinator's private exclusive build gate and
the existing credential-stripped scaffold runner with private user/project/
Android caches, 512 MiB heap/metaspace, one worker, in-process Kotlin, SerialGC,
two active CPUs and 256 MiB test heap. Actual capacity guards blocked subsequent
execution before Java; no guard bypass or global toolchain/cache change occurred.
Maintainer-directed merged PR #27 made official verification Linux-only.
No Windows retry or global WSL installation is used for final evidence.

The admitted shared failure-observability amendment forwards
`-Pwp207EvidenceDirectory=<job-output>/wp207-native` only at the existing verify
stage. Owned test-task finalizers retain full runtime/data/store raw XML,
committed module input blobs and the actual executor binding even on failure.
Successful owned hooks additionally retain `runtime-assertions.json` and
`native-assertions.json`; neither changes a failed verdict into success.

A subsequent exact two-file DATA-reader amendment authorizes only canonical,
case-sensitive `path.name` sorting in `module_junit.safe_reports` and focused
regressions. Windows `Path` normcase sorting had reordered `SourceReconnect*`
and `SourceReconnection*`, making a genuine Linux artifact fail strict array
replay on the observing host. The original failing PureWindowsPath-enumeration
test was executed before the one-line repair. No set normalization, missing
report acceptance, schema/workflow change or Windows native build is introduced;
membership, raw bytes/hashes, ordering and all XML/failure guards stay strict.

## Declared commands and observed results

| Command | Observed result and scope |
| --- | --- |
| `python -B tools\android-port\controller\validate.py` | Passed; exact 65/185/eight gates/source/semantic pins |
| `python -B tools\android-port\portmap.py` | Passed; source provenance only, not behavior acceptance |
| `python -B -m unittest discover -s android\core\runtime\verification -p test_dependency_proposal.py -v` | 10 discovered/passed, zero failures/errors/skips, including Gradle XML default namespace |
| `python -B -m unittest discover -s docs\android\evidence\WP-207 -p test_collect_evidence.py -v` | 10 discovered/passed, zero failures/errors/skips |
| `python -B -m unittest discover -s docs\android\evidence\WP-202 -p test_collect_evidence.py -v` | 19 discovered/passed after exact approved producer carry; original repository source credit remains separate |
| `python -B -m unittest discover -s tools\android-port\tests -p test_ci_environment.py -v` | 18 discovered/passed for the admitted verify-only forwarding, exact argv and failure/credential boundaries |
| `python -B -m unittest discover -s tools\android-port\tests -p test_module_junit.py -v` with `PYTHONPATH=tools\android-port` | 22 discovered/passed, zero failures/errors/skips. The focused case-sensitive regression failed before the approved one-line fix; full strict historical `validate_result` subsequently passed unchanged rather than using set-normalized acceptance |
| Gated `:core:runtime:resolveRuntimeDependencies :core:runtime:compileTestKotlin --write-locks --dependency-verification strict` | Initial compile found a wrong `FrequencyRange` import; corrected |
| Gated `:core:runtime:test` selecting values/policy/utilities/coordinator | 105 discovered, 103 passed, two native original-error-identity failures; implementation corrected, assertions retained |
| Gated corrected source-core run | Found a misplaced deadline-racer return; corrected |
| Gated full `:core:runtime:resolveRuntimeDependencies :core:runtime:test --write-locks` | BLOCKED before JVM execution by capacity guard; free virtual 663,672 KiB, below required 2,097,152 KiB |
| Hosted auxiliary proposal, run `37327134397` attempt 1 at `b5761fa37e9eda8b420b3ad597e7c996adada788` | Failed closed on a default-namespace metadata-reader bug; fixed without changing shared XML or versions |
| Hosted Linux root proof, run `37327134377` attempt 1 at that head | Failed closed on absent runtime module lock; official artifact `11353320137` SHA-256 verified before reading its log |
| Hosted owned dependency DATA proposal, run `37329549317` attempt 1 | Exact module-local lock independently admitted; see `lock-admission.json`. This ran no tests |
| Hosted raw runtime runs before final source repairs | 218/213/5 failed, then 218/217/1 failed; zero errors/skips. All faulty assertions/production paths were repaired in this same PR, never disabled |
| Hosted full Linux scaffold, run `37349456919` attempt 1 at `10936dea54feb0d7797a111a05739555a106a80f`, base `d8f9b842581496baa382be1fe54dc866354b8150` | Composite verification, standalone assertions, APK assembly, lint, APK inspection and aggregate all passed. Raw runtime 224, data 369 and DataStore 137 all passed without failures/errors/skips |
| Hosted corrective Linux scaffold, run `37352588958` attempt 1 at `d09fc4c107d290047913171377e48bf67cec287e` | Failed before native/JVM test execution: Gradle's `java` extension shadowed the new fully-qualified `java.io.File` output path. Repaired with an explicit `File` import; no tests or acceptance claimed from this run |
| Hosted repaired-build Linux scaffold, run `37353308922` attempt 1 at `e24c5608a024b09b55e2ab2e952ca361e224846d` | Actual raw runtime 226 discovered/225 passed/one failed, zero errors/skips; data 369 and DataStore 137 all passed. The new authored handshake test incorrectly expected CONNECTING after physical connection. Frozen state semantics define CONNECTED before AppStart completes; the test now asserts that exact phase, no READY token/send drain, and the entire unchanged snapshot across observer cancellation |

The last failed run's official artifact `11364565110`, 1,722,774 bytes,
SHA-256 `7235c60e4b7cfcec759d81e290f418a25ae3f618b8e2e3fb6183cbb917408b0f`,
passed official digest and all 75 bounded member/CRC checks. Its exact executor
binding and complete failed runtime/native raw outcomes were retained before
the fail-closed result. It is not full-root, final-head or APK acceptance.

The successful `10936dea` snapshot is **historical**, not acceptance for later
source edits: independent review subsequently identified coupled shared-attempt
observer/reporting races, repaired under A-06 with two additional real-session
cases. Final current-head source/JVM/native/root proof must execute those cases
as well. Zero, missing, malformed, failed or skipped mandatory evidence blocks.

That official successful snapshot's artifact is `11362732210`,
`scaffold-37349456919-1`, 24,705,276 bytes, SHA-256
`a48a99d25806b17a70de057375d70f67da3c85a8884c562657e8e628e3bc8de4`.
All 1,548 bounded ZIP members passed safe-path/type/size/CRC checks; every
retained file matched `SHA256SUMS` before raw JSON/XML was read as data.
Its actual eight native root modules discovered/passed 1,353 cases:
BLE 295, data 369, database 45, DataStore 137, design system 92,
localization 25, model 166 and runtime 224. These other modules' cases are
prerequisite/current-root evidence, not WP-207 original-family credit.
The debug APK was `com.meshcoreone.android.debug`, minSdk31/target37,
44,334,796 bytes, SHA-256
`e461688b4051e7e17f203bc9faf023b38b3d34dc7398fea3cf9670b220b704c5`.
Its GPL/MIT notices, absence of verification fixtures and static 16KB alignment
were inspected; physical native-runtime compatibility was not.

The owned `verifyConnectionRuntimeTests` task depends on `:core:runtime:test`
and `resolveRuntimeDependencies`, then runs `collect_evidence.py`. It is wired
into the existing root `verifyScaffoldTests` without editing root/build logic.
The generic CI module collector retains runtime's complete current input blobs
and raw JUnit. The owned collector requires exact execution of every declared
case, including all source-family identities and all native regression names.
No invented Gradle task or source credit from prerequisite
protocol/data/store/BLE suites is used.

`verifyRuntimeNativeIntegrationTests` awaits the actual full data/store unit tasks
and checks all ten new runtime consumers plus the unchanged prior source floors.
At the actual merged backup base the full data floor is 369
(154 repository, 211 backup, four runtime consumers); DataStore is 137
(131 prior cases plus six runtime consumers).
`verifyRuntimeEvidenceReaders` runs positive/malformed/zero/skip/identity adversaries.
These new tasks are declared in the owned module before invocation. Both join
existing root verification; they do not manufacture Android/Room evidence.

## Assertion and adaptation boundaries

All 154 original declaration/parameter families are explicitly represented by
source-qualified dynamic tests. The retry family executes all three source rows.
The runtime uses a real merged `MeshCoreSession` over independently constructed
frozen Swift packet layouts; only injected platform/process/service roles are
test doubles, never production placeholder services. Native Room tests execute
the real repository save/warm-up/public-key/
ghost/reset algorithms and verify a complete contact/message/pending-send
triple, the current pending-send attempt sentinel and genuine legacy-null purge.
DataStore tests use real process storage, reopen it, and check canonical
keys, raw ordered lists, choice absence and unrelated preference preservation.
Process storage survives generation and controller teardown; startup resets
stale remote sessions globally, including orphan-radio rows.

Per-connection regressions inspect real receiver/monitor/factory child completion,
physical close counts, tokens, callback closure and typed causes. Coalesced caller
cancellation and late reporter failure are combined with actual suspended or
completed shared operations, not merely tested separately. Default-dispatcher
caller barriers are released before narrowly non-cancellable cancellation/join.

See [`../../deviations/WP-207.md`](../../deviations/WP-207.md) for retained-link
renewal, stable identity, native cancellation and future graph boundaries.
No iOS/macOS runtime, actual radio/OEM/force-stop/FGS, signing, release or
feature-complete graph is certified by these JVM/native-host assertions.
