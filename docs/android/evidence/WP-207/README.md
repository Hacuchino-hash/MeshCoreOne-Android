# WP-207 connection runtime evidence

This is implementation/evidence in progress, not a merge/acceptance receipt.
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
the serialized WP-203 producer/coordinator, not this worker.

Local JVM execution uses the coordinator's private exclusive build gate and
the existing credential-stripped scaffold runner with private user/project/
Android caches, 512 MiB heap/metaspace, one worker, in-process Kotlin, SerialGC,
two active CPUs and 256 MiB test heap. There is no global toolchain/cache change.
The installed toolchain tuple is unchanged; Linux/Windows official CI remains
the required current-head whole-root proof.

## Declared commands and observed results

| Command | Current observed result |
| --- | --- |
| `python -B tools\android-port\controller\validate.py` | Passed; exact 65/185/eight gates/source/semantic pins |
| `python -B -m unittest discover -s android\core\runtime\verification -p test_dependency_proposal.py -v` | 8 discovered/passed, zero failures/skips |
| Updated proposal reader after actual hosted default-namespace failure | 10 discovered/passed, zero failures/skips |
| `python -B -m unittest discover -s docs\android\evidence\WP-207 -p test_collect_evidence.py -v` | 8 discovered/passed, zero failures/skips |
| Gated `:core:runtime:resolveRuntimeDependencies :core:runtime:compileTestKotlin --write-locks --dependency-verification strict` | Initial compile found a wrong `FrequencyRange` import; corrected |
| Gated `:core:runtime:test` selecting values/policy/utilities/coordinator | 105 discovered, 103 passed, two native original-error-identity failures; implementation corrected, assertions retained |
| Gated corrected source-core run | Found a misplaced deadline-racer return; corrected |
| Gated full `:core:runtime:resolveRuntimeDependencies :core:runtime:test --write-locks` | BLOCKED before JVM execution by capacity guard; free virtual 663,672 KiB, below required 2,097,152 KiB |
| Hosted auxiliary proposal, run `37327134397` attempt 1 at `b5761fa37e9eda8b420b3ad597e7c996adada788` | Failed closed on a default-namespace metadata-reader bug; fixed without changing shared XML or versions |
| Hosted Linux root proof, run `37327134377` attempt 1 at that head | Failed closed on absent runtime module lock; official artifact `11353320137` SHA-256 verified before reading its log |

The last two fixes and complete manager/native suites have **not yet passed**.
Zero/missing/failed/skipped test evidence is not acceptance. The module-local
runtime lock was not persisted by failed Gradle runs; dependency generation must
produce and validate the actual lock before strict full verification.

The owned `verifyConnectionRuntimeTests` task depends on `:core:runtime:test`
and `resolveRuntimeDependencies`, then runs `collect_evidence.py`. It is wired
into the existing root `verifyScaffoldTests` without editing root/build logic.
The generic CI module collector retains runtime's complete current input blobs
and raw JUnit. No invented Gradle task or source credit from prerequisite
protocol/data/store/BLE suites is used.

`verifyRuntimeNativeIntegrationTests` awaits the actual full data/store unit tasks
and checks all nine new runtime consumers plus the unchanged prior module floors.
`verifyRuntimeEvidenceReaders` runs positive/malformed/zero/skip/identity adversaries.
These new tasks are declared in the owned module before invocation. Both join
existing root verification; they do not manufacture Android/Room evidence.

## Assertion and adaptation boundaries

All 154 original declaration/parameter families are explicitly represented by
source-qualified dynamic tests. The retry family executes all three source rows.
The runtime uses a real merged `MeshCoreSession` over independently constructed
frozen Swift packet layouts; only future service/platform adapters are test
doubles. Native Room tests execute the real repository save/warm-up/public-key/
ghost/reset algorithms and verify a complete contact/message/pending-send
triple. DataStore tests use real process storage, reopen it, and check canonical
keys, raw ordered lists, choice absence and unrelated preference preservation.

See [`../../deviations/WP-207.md`](../../deviations/WP-207.md) for retained-link
renewal, stable identity, native cancellation and future graph boundaries.
No iOS/macOS runtime, actual radio/OEM/force-stop/FGS, signing, release or
feature-complete graph is certified by these JVM/native-host assertions.
