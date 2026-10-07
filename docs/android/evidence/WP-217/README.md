# WP-217 evidence

**External contribution:** this was prepared outside the dispatch controller, with
no lease or receipt. This document lists the commands that actually ran and their
observed results.

- **Reference:** `db14559b39d32322b06477c6ae676112f583db50`
- **Base:** `main` @ `7237727f`
- **Host:** macOS 26.5.1, JDK 21.0.12.1, Gradle 9.8.0 via the wrapper, Swift 6.3.2 (Command Line Tools)

| Command | Observed |
| --- | --- |
| `./gradlew :core:services:test --rerun validateModuleGraph --rerun`, three runs | `BUILD SUCCESSFUL` each time, and the test task ran (not `UP-TO-DATE`) each time. JUnit XML: **50 tests, 0 failures, 0 errors, 0 skipped**, all in `simulator/`. |
| Compile `:core:services` main with `-Xjdk-release=17` (`jdk17-release.init.gradle`, `--rerun-tasks`) | succeeds |
| `python tools/android-port/portmap.py` | exit 0 |
| `python tools/android-port/controller/validate.py` | exit 0 |

The prescribed form, `:core:services:test validateModuleGraph --rerun`, applies
`--rerun` only to `validateModuleGraph`. With it, the test task stayed
`UP-TO-DATE`, so the counts above come from the form that forces both tasks.

## Case coverage

All **18** source case ids that `docs/android/test-cases.json` assigns to WP-217
run under their exact ids, and none are missing: `SimulatorSeedTests` 11 and
`DemoModeManagerTests` 7. There are also 32 native `WP-217::` cases:

- the Swift oracle;
- determinism and clock injection;
- write order;
- reaction and repeat linkage;
- the Mesh HQ page boundary;
- the RX-log and snapshot skips;
- snapshot id stability;
- store-error and cancellation propagation;
- seed serialization;
- the transport and connection mode;
- demo-mode state;
- image seeding;
- a source scan for I/O imports.

## Swift oracle

`oracle/run-oracle.sh <scratch-dir>` copies the frozen
`Simulator/MockDataProvider*.swift` and `MockMessageFactory.swift`, rewriting
`Date()` to a pinned clock. It compiles them with `swiftc` against the stubs in
`oracle/OracleStubs.swift.txt`, which copy the Swift initializer defaults and raw
values, and runs `oracle/main.swift.txt`.

The output has 172 rows: the device, contacts, channels, every message, link
previews, reactions and summaries, repeats, RX log, snapshots and the demo image
digest. The Kotlin dump (`SimulatorSeedDump`) matches it row for row at
2026-01-01T00:00:00Z. Its SHA-256 matches at the fractional instant
1767225600.75 s too. Rerunning the script reproduced the committed vectors.
Snapshot ids are not compared, because Swift's are random (deviation A-02).

## Mutation checks

`wp217_mutations.py` lives in the session scratchpad. Each run applies one
break, runs the owning test classes, expects a failure, then restores the file.
All **14** breaks were caught, and the restored suite passed:

- **Seed determinism:** the seed reads the wall clock.
- **Demo-mode persistence:** `isUnlocked` no longer writes through.
- **Transport replies:** sends are echoed back.
- **Transport replies:** sends are accepted while disconnected.
- **Reaction linkage:** a reaction points at the wrong DM.
- **Reaction linkage:** a summary count is off.
- **Repeat linkage:** `heardRepeats` is off.
- **Avatar:** it is no longer preserved.
- **RX log:** fixtures are re-inserted.
- **Snapshots:** they are restacked.
- **Seed passes:** they are no longer serialized.
- **Connect:** it swallows cancellation.
- **Link previews:** they are never applied.
- **Oracle:** one message text drifts.

A second run reverted each review fix and confirmed that its regression test
fails, catching all **3**.

## Review

An independent read-only review compared the Kotlin with the Swift line by line.
It found no critical or high issues.

**Fixed, with regression tests:**

- **Snapshot ids (medium):** index-derived ids could collide with `core:data`'s
  abort-on-conflict insert during a later backup restore. Ids now derive from each
  row's store key.
- **`saveMessage` semantics (low):** the fake and the port's KDoc misdescribed
  them. Swift keeps the preview URL, title and summary.
- **`unlock()` (low):** it published an unlocked-but-disabled midpoint.
- **Visibility (low):** `nodeStatusSnapshots` was less visible than in Swift.
- **Shared defaults (low):** the shared-defaults test now restores its global
  flag in `finally`.

**Kept as instructed:** the build file's `WP-212` header line is the shared
WP-214 verify-stage hook, which must stay byte-identical.
