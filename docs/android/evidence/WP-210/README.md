# WP-210 evidence

**External contribution:** this was prepared outside the dispatch controller, with
no lease or receipt. This document lists the commands that actually ran and their
observed results.

- **Reference:** `db14559b39d32322b06477c6ae676112f583db50`
- **Base:** `main` @ `7237727f`
- **Host:** macOS 26, JDK 21.0.12.1, Gradle 9.8.0 via the wrapper

| Command | Observed |
| --- | --- |
| `./gradlew :core:contracts:test --rerun :core:services:test --rerun validateModuleGraph --rerun` | `BUILD SUCCESSFUL`; `:core:services` **504 tests, 0 failures, 0 errors, 0 skipped** (31 XML files); `:core:contracts` **4 tests, 0 failures, 0 errors, 0 skipped** (1 XML file); module graph verified |
| `./gradlew -I jdk17-release.init.gradle :core:contracts:compileKotlin :core:services:compileKotlin --rerun-tasks` (adds `-Xjdk-release=17`) | `BUILD SUCCESSFUL` |
| `./gradlew :core:services:test validateModuleGraph --rerun` (before A-05) | `BUILD SUCCESSFUL`; 463 tests, 0 failures |
| `./gradlew :core:services:test --rerun` (twice more, before the regex change) | 462 / 462 each time |
| `python tools/android-port/portmap.py` | exit 0 |
| `python tools/android-port/controller/validate.py` | exit 0 |
| `sh docs/android/evidence/WP-210/foundation-oracle/run.sh` | output in `foundation-oracle/output-macos26.txt` |

All **292** source case ids that `docs/android/test-cases.json` assigns to
WP-210's test files run under their exact ids. None are missing. That includes the
two `ErrorLocalizationTests` cases for `RoomServerError` and `BinaryProtocolError`.

| Area | Source ids | Total tests |
| --- | ---: | ---: |
| RemoteNodeService, CLI/settings parsing, login timeouts, rewriter | 129 | 164 (incl. 35 native) |
| Node config import/export/planner, snapshots | 161 | 198 (incl. 37 native) |
| Repeater/room admin, RoomServerService, BinaryProtocolService | 2 | 101 (incl. 99 native) |
| Neutral fault projection (A-05) | 0 | 41 (all native) |

- **Foundation oracle:** `foundation-oracle/` compiles the Swift `MeshCoreNodeConfig` model with Foundation and prints the real encoder/decoder behavior. The Kotlin export fixture matches it byte for byte. Its decode edge cases (first duplicate key wins, trailing commas, lazy string/number validation) and the String-equality cases are pinned as Kotlin tests.
- **Mutation checks:** each was broken on purpose, the named test failed, and the code was restored.
  - Status broadcast before save; skipped path reset before retry (12 failures); a 21st path-discovery poll; room-connection recovery only for non-duplicates.
  - Unguarded audit logger (login aborted); posting without `ATOMIC` (pending row stranded); unguarded binary push handler (monitoring stopped); CLI slot handover on cancellation.
- **Independent reviews:** read-only reviews of the core, config and admin ports against the Swift. Every confirmed finding was fixed. Fixes a test can reproduce deterministically have regression cases. The CLI retry-id race and the monitor restart order don't, and are described in `deviations/WP-210.md`.

## Neutral fault declarations (A-05)

| File | Role | Git blob | SHA-256 |
| --- | --- | --- | --- |
| `android/core/contracts/src/main/kotlin/com/meshcoreone/android/core/contracts/domain/errors/ContactsChannelAdvertisementFaults.kt` | PR #49's declaration, carried byte-exact and not edited | `3f1f2077a0846242b1bbec669ce6e0ac913d1bae` | `1b7f21f5ff827e88462a2ac6c722a681c8c24dd9d496d130da9b3139d6e5a592` |
| `android/core/contracts/src/main/kotlin/com/meshcoreone/android/core/contracts/domain/errors/RemoteRoomNodeConfigFaults.kt` | This PR's declaration, frozen for PR #33 to carry byte-exact | `bc2d9b387d6183062ba90fa6f23be1197bacf1c1` | `356fe4521787b7a09aee8a372de266b17a271c5068d07fc7af7a4136fd852f13` |

`RemoteRoomNodeConfigFaultProjectionTests` (41 `WP-210::` cases) runs the projection for real:

- **Per case (34 tests):** constructs every producer case: Remote 14, Room 6, Binary 5, NodeConfig 9. The session cases use real `MeshCoreException`s: `ConnectionLost` with an inner cause, `DeviceError(3)` and `Timeout`. Each test projects through the `SourceServiceFaultCarrier` interface and checks four things:
  - the exact family and case;
  - equal payload values, with `assertSame` on the reason strings, the contact names and the session cause;
  - that the projection is stable;
  - that the producer's message, `errorDescription`, `toString`, raw fields, cause and Remote `isRetryable` are unchanged.

  The radio-settings case projects all 5 `RadioField`s, and the coordinate case all 4 `CoordinateField`s.
- **Exhaustiveness (5 tests):** `kotlin-reflect` isn't on the module's classpath, so these tests read the JVM `PermittedSubclasses` of each producer and payload type. They require:
  - 14, 6, 5 and 9 cases on both sides, every case exercised once, and a one-for-one mapping with matching case names;
  - Remote retryability equal on every case, and true for exactly `NotConnected`, `Timeout` and `FloodRouted`;
  - matching `RadioField`/`CoordinateField` mirrors;
  - each producer landing in its own family under `SourceServiceFault`.
- **Separation (1 test):** no `NodeConfigDecodingException` case implements the carrier.
- **Carry check (1 test):** reads the carried PR #49 file from the tree and requires its SHA-256 and git blob to match the values above.
- **Mutation check:** one projection per family was broken on purpose:
  - Remote `Timeout` mapped to `PermissionDenied`;
  - Room `SessionError` given a fresh `MeshCoreException`;
  - Binary `SendFailed` mapped to `Timeout`;
  - NodeConfig `RadioField.CODING_RATE` mapped to `SPREADING_FACTOR`.

  The run failed 8 of 41 tests, covering each family. The mutations were then reverted (`git diff HEAD` empty).
