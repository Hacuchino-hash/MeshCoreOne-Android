# WP-306 evidence

External contribution, no lease. Local macOS runs only, not CI or acceptance receipts. Frozen source:
`db14559b39d32322b06477c6ae676112f583db50`. Base: `c8a16529` (origin/main). Scope: chat list logic and Compose
UI in `android/feature/chats`; see [WP-306.md](../../deviations/WP-306.md) for ownership, seams and adaptations.

## Commands

Environment: JDK 21, Android command-line SDK, private Gradle/Android homes, `--dependency-verification lenient`
(`-x :core:ui:verifySharedUiInputs -x :core:services:prepareContentInvocation`).

```
./gradlew :feature:chats:testDebugUnitTest :feature:chats:lintDebug :app:assembleDebug validateModuleGraph
python3.13 tools/android-port/portmap.py
python3.13 tools/android-port/controller/validate.py
```

## Observed results

- Full build exit 0 (incl. `:app:assembleDebug`, `validateModuleGraph`). `:core:l10n:verifyL10nConversion` also ran and passed.
- JUnit XML (`:feature:chats:testDebugUnitTest`, clean rerun after the last source change): 9 suites, 106 tests,
  0 failures, 0 errors, 0 skipped.
- `lintDebug`: 0 errors; 3 `LocalContextResourcesRead` warnings (WP-306.md D-12).
- `portmap.py` and `controller/validate.py` exit 0.
- Mutation checks (applied, observed failing, restored; tree clean afterwards): unread filter without the muted
  exclusion -> `unread filter excludes muted` fails; ascending sort -> 3 sort tests fail; `toggleMute` using the stale
  copy -> `toggleMute with stale unmuted snapshot unmutes live row` fails.

## Source id coverage

77 owned ids in `docs/android/test-cases.json` (7 test files). Per-case class, method, disposition and JUnit result are
in [`source-cases.json`](source-cases.json).

| Status | Count | Notes |
| --- | --- | --- |
| Ported and passing | 76 | 75 source-behavior, 1 platform-adaptation (source scan of the Compose list files) |
| Deferred | 1 | `ConversationListScrollPerfHarnessTests::hosted list programmatic scroll stays under hitch budget()`: needs a device frame-timing run (WP-306.md D-1) |
| Missing | 0 | |

Native (not source-bound) tests: 30 (`ChatListActionsTest` 25, `ChatTextMatchingTest` 2, 1 search-ignores-filter,
2 hashtag-router behaviors). Includes one real-time 7 s test of the delete timeout.

## Oracle

`oracle/wp306_strings.swift.txt` is the swiftc program that produced `oracle/wp306_strings.output.txt`
(`localizedCaseInsensitiveCompare` and `localizedStandardContains` probes); the results are pinned in `ChatTextMatchingTest`.

## Not covered

Compose rendering, gestures and TalkBack are not exercised (no compose-ui-test/Robolectric on the locked classpath and
no device). See WP-306.md "Not verifiable without a device".
