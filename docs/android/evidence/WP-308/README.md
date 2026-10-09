# WP-308 evidence

External contribution, no lease. Local macOS runs only, not CI or acceptance receipts. Frozen source:
`db14559b39d32322b06477c6ae676112f583db50`. Base: `b8c15cc5` (origin/main). Logic, Compose UI and the native
adaptations are described in [WP-308.md](../../deviations/WP-308.md).

## Commands

Environment: JDK 21, Android command-line SDK, private Gradle/Android homes, `--dependency-verification lenient`.
The `-x :core:ui:verifySharedUiInputs -x :core:services:prepareContentInvocation` exclusions from earlier WPs are
omitted: those tasks do not exist on this base and Gradle rejects the exclusion.

```
./gradlew :feature:chats:testDebugUnitTest :feature:chats:lintDebug :app:assembleDebug validateModuleGraph
python3.13 tools/android-port/portmap.py
python3.13 tools/android-port/controller/validate.py
```

## Observed results

- Gradle exit 0 (`BUILD SUCCESSFUL`), including `:app:assembleDebug`, `validateModuleGraph` and
  `:core:l10n:verifyL10nConversion` (its self-test runs the traceability validator over the new headers).
- JUnit XML (`:feature:chats:testDebugUnitTest`): 20 suites, 304 tests, 0 failures, 0 errors, 0 skipped.
  WP-308 `composer` suites: 9 suites, 172 tests, 0 failures (`MentionUtilitiesTest` 53, `MessageTextTest` 37,
  `MessageLinkTokenizerTest` 23, `ComposerStateHolderTest` 17, `LinkLogicTest` 15, `ComposerUiSourceScanTest` 10,
  `ComposerShareTest` 8, `EmojiPickerTest` 5, `ComposerTextTest` 4). The other 132 are WP-306/WP-307.
- `lintDebug`: exit 0; no findings in `composer/`. The only warnings are the pre-existing WP-306 files
  (`ConversationListRow`, `NewChatSheet`, `ResourceChatListStrings`).
- `portmap.py` and `controller/validate.py` exit 0.
- Mutation checks (applied, observed failing, restored; tree clean afterwards): UTF-8 length replaced by UTF-16
  length -> 4 tests fail (`ComposerTextTest` oracle and lone-surrogate, both byte-limit tests in
  `ComposerStateHolderTest`); overlap resolution ordered by start offset instead of kind priority -> the two
  hashtag-inside-meshcore-link tests fail; send without the cooldown -> `send clears the field, sends the
  trimmed text once and re-enables after one second` fails.

## Source id coverage

121 owned ids in `docs/android/test-cases.json` (10 test files). Per-case class, method, disposition and JUnit
result are in [`source-cases.json`](source-cases.json).

| Source suite | Ids | Android test class |
| --- | --- | --- |
| `MentionUtilitiesTests` | 48 | `MentionUtilitiesTest` |
| `MentionInsertionTests` | 4 | `MentionUtilitiesTest` |
| `MessageTextTests` | 16 | `MessageTextTest` |
| `MessageTextContactShareTests` | 11 | `MessageTextTest` |
| `ChatCoordinateDetectorTests` | 9 | `MessageTextTest` |
| `MessageLinkTokenizerTests` | 14 | `MessageLinkTokenizerTest` |
| `MessageLinkAccessibilityTests` | 7 | `MessageLinkTokenizerTest` |
| `ChatShareMenuTests` | 6 | `ComposerShareTest` |
| `TiledViewInputBarChromeTests` | 4 | `ComposerUiSourceScanTest` (platform-adaptation scan) |
| `ChatInputBarThemedChromeTests` | 2 | `ComposerUiSourceScanTest` (platform-adaptation scan) |

| Status | Count | Notes |
| --- | --- | --- |
| Ported and passing | 115 | `source-behavior` |
| Platform-adaptation, passing | 6 | source scans of the Compose layer, not layout/rendering measurements (WP-308.md D-1) |
| Deferred | 0 | |
| Missing | 0 | |

Native (not source-bound) tests: 51 = `ComposerStateHolderTest` 17, `LinkLogicTest` 15, `EmojiPickerTest` 5,
`ComposerTextTest` 4, `ComposerUiSourceScanTest` 4, `ComposerShareTest` 2, `MessageLinkTokenizerTest` 2,
`MentionUtilitiesTest` 1, `MessageTextTest` 1.

## Oracle

`oracle/wp308_oracle.swift.txt` (swiftc 6.3.2) produced `oracle/wp308_oracle.output.txt`: `utf8.count`/`count`,
`trimmingCharacters(.whitespacesAndNewlines)`, `Character.isWhitespace`, `addingPercentEncoding` with
`urlPathAllowed` minus `/`, `removingPercentEncoding`, the coordinate regex, the hashtag regex, `NSDataDetector`
link matches, and the strippable-scalar properties. `oracle/wp308_oracle2.*` pins `Double("３７.５") == nil`.
The results are pinned in `ComposerTextTest`, `MessageTextTest`, `LinkLogicTest` and `MentionUtilitiesTest`.

## Not covered

Compose rendering, gestures, IME composition, hardware keyboard, TalkBack and insets are not exercised (no
compose-ui-test/Robolectric on the locked classpath and no device). No network preview fetch or image decode ran.
