# WP-210 evidence

**External contribution:** this was prepared outside the dispatch controller, with
no lease or receipt. This document lists the commands that actually ran and their
observed results.

- **Reference:** `db14559b39d32322b06477c6ae676112f583db50`
- **Base:** `main` @ `7237727f`
- **Host:** macOS 26, JDK 21.0.12.1, Gradle 9.8.0 via the wrapper

| Command | Observed |
| --- | --- |
| `./gradlew :core:services:test validateModuleGraph --rerun` | `BUILD SUCCESSFUL`; `:core:services` **462 tests, 0 failures, 0 errors, 0 skipped** |
| `./gradlew :core:services:test --rerun` (twice more) | 462 / 462 each time |
| `python tools/android-port/portmap.py` | exit 0 |
| `python tools/android-port/controller/validate.py` | exit 0 |
| `sh docs/android/evidence/WP-210/foundation-oracle/run.sh` | output in `foundation-oracle/output-macos26.txt` |

All **292** source case ids that `docs/android/test-cases.json` assigns to
WP-210's test files run under their exact ids. None are missing. That includes the
two `ErrorLocalizationTests` cases for `RoomServerError` and `BinaryProtocolError`.

| Area | Source ids | Total tests |
| --- | ---: | ---: |
| RemoteNodeService, CLI/settings parsing, login timeouts, rewriter | 129 | 163 (incl. 34 native) |
| Node config import/export/planner, snapshots | 161 | 198 (incl. 37 native) |
| Repeater/room admin, RoomServerService, BinaryProtocolService | 2 | 101 (incl. 99 native) |

- **Foundation oracle:** `foundation-oracle/` compiles the Swift `MeshCoreNodeConfig` model with Foundation and prints the real encoder/decoder behavior. The Kotlin export fixture matches it byte for byte. Its decode edge cases (first duplicate key wins, trailing commas, lazy string/number validation) and the String-equality cases are pinned as Kotlin tests.
- **Mutation checks:** each was broken on purpose, the named test failed, and the code was restored.
  - Status broadcast before save; skipped path reset before retry (12 failures); a 21st path-discovery poll; room-connection recovery only for non-duplicates.
  - Unguarded audit logger (login aborted); posting without `ATOMIC` (pending row stranded); unguarded binary push handler (monitoring stopped); CLI slot handover on cancellation.
- **Independent reviews:** read-only reviews of the core, config and admin ports against the Swift. Every confirmed finding was fixed with a regression case. The plausible ones that remain are documented in `deviations/WP-210.md`.
