# WP-213 evidence

**External contribution:** this was prepared outside the dispatch controller, with
no lease or receipt. This document lists the commands that actually ran and their
observed results.

- **Reference:** `db14559b39d32322b06477c6ae676112f583db50`
- **Base:** `main` @ `7237727f`
- **Host:** macOS 26.5.1, JDK 21.0.12.1, Gradle 9.8.0 via the wrapper, Swift 6.3.2 (Command Line Tools)

| Command | Observed |
| --- | --- |
| `./gradlew :core:services:test validateModuleGraph --rerun`, three runs | `BUILD SUCCESSFUL` each time; **299 tests, 0 failures, 0 errors, 0 skipped** |
| `python tools/android-port/portmap.py` | exit 0 |
| `python tools/android-port/controller/validate.py` | exit 0 |
| `sh docs/android/evidence/WP-213/foundation-casefold/generate.sh` | reproduces the 1552-entry table in `FoundationCaseFold.kt` |

All **126** source case ids that `docs/android/test-cases.json` assigns to
WP-213's test files run under their exact ids. None are missing. The other **173**
cases are native (`WP-213::`).

- **Swift-derived expectations:** reaction hashes, both reaction parsers, `isEmoji`, grapheme counts, whitespace sets, every `MentionUtilities` function, `lowercased()`, locale language codes and `caseInsensitiveCompare` results were printed by the frozen Swift compiled locally.
- **Mutation checks:** eight behaviors were broken on purpose and each was caught.
  - The failed-to-pending status guard, the stale-rebuild check and writer staleness.
  - LRU eviction order and the malware-before-preview gate, which only a native case catches.
  - Reaction-hash endianness, draft emptiness and registry LRU promotion.
- **Review fixes:** an independent read-only review against the Swift found four divergences, all fixed.
  - The throwing-rebuilder and case-folding fixes have regression cases that fail with their fix reverted.
  - The writer-atomicity and superseded-build fixes need a second thread to interleave and have no deterministic test.
