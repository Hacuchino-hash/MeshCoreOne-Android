# WP-316 evidence

**External contribution:** prepared outside the dispatch controller, with no lease or
receipt. This lists what actually ran. Logic layer only: CLI terminal, RX log and
noise-floor state holders, completion engine, parsers and chart math. No Compose
screens (they follow once the shared UI and shell are on main).

- **Reference:** `db14559b39d32322b06477c6ae676112f583db50`
- **Host:** macOS 26, JDK 21, Gradle via the wrapper (`--dependency-verification lenient` because macOS aapt2 isn't in the verification metadata; `:core:ui:verifySharedUiInputs` excluded because it rejects macOS temp paths)

| Command | Observed |
| --- | --- |
| `./gradlew :feature:tools:testDebugUnitTest :feature:tools:lintDebug validateModuleGraph` | exit 0; JUnit XML **307 tests, 0 failures, 0 errors, 0 skipped**; lint no issues; graph valid |
| `python tools/android-port/portmap.py` / `controller/validate.py` | both exit 0 |

All **242** source case ids that `docs/android/test-cases.json` assigns to WP-316's
test files are bound with `@OriginalCase` and run: none missing. The remaining 65
tests are native.

The Swift oracle sources used for formatting expectations are committed as `.swift.txt`
(`oracle.swift.txt`, `oracle2.swift.txt`) with their outputs, so the repository-wide
SwiftLint pass skips them. Adaptations are in `docs/android/deviations/WP-316.md`.
