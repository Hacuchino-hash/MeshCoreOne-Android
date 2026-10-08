# WP-313 evidence (logic layer)

External contribution, no lease. Local macOS runs only, not CI or acceptance receipts.
Frozen source: `db14559b39d32322b06477c6ae676112f583db50`. Scope: the non-UI code of
`android/feature/remotenodes/`; deviations and type placement are in
[`../../deviations/WP-313.md`](../../deviations/WP-313.md).

## Commands

JDK 21 (`/opt/homebrew/opt/openjdk@21`), the Android command-line SDK, private Gradle/Android
user homes, `--dependency-verification lenient` (the macOS aapt2 artifacts are not in the
verification metadata). Run from `android/`, three times, with the feature's tests cleaned first:

```
./gradlew :feature:remotenodes:cleanTestDebugUnitTest :feature:remotenodes:testDebugUnitTest \
  :feature:remotenodes:lintDebug validateModuleGraph --dependency-verification lenient --console=plain
python3.13 tools/android-port/portmap.py
python3.13 tools/android-port/controller/validate.py
```

## Observed results

- `:feature:remotenodes:testDebugUnitTest`, three runs: 38 suites, 330 tests, 0 failures,
  0 errors, 0 skipped each time (no test task was up to date).
- `lintDebug`: "No issues found". `validateModuleGraph` passes (the module depends only on
  core:model, core:contracts, core:designsystem, core:ui, core:maps, core:l10n; the build adds
  no dependencies).
- `portmap.py` exits 0 and `validate.py` reports `"result": "valid"` on the committed tree.
- The module registers `verifyScaffoldTests` in its `build.gradle.kts` (same pattern as
  core:connectivity), so CI collects its JUnit reports.

## Source-case coverage

`docs/android/test-cases.json` lists 175 cases owned by WP-313 (15 Swift test files).
[`source-cases.json`](source-cases.json) binds each to its `@OriginalCase` method and the JUnit
result read from the XML.

| | Count |
| --- | --- |
| Owned source cases | 175 |
| Bound and passed in JUnit | 174 (153 source-behavior, 21 platform-adaptation) |
| Deferred | 1 |
| Missing | 0 |

The other 156 tests are native: Foundation reproductions, chart math, drawing-free map data,
login flow, retry runner, drift and route presentation, session matching, late-reply recovery.

Parameterized Swift families are one method each covering every argument (validation
boundaries, SNR buckets, key-width). `platform-adaptation` cases:

- the snapshot-store cases (RepeaterStatusViewModelTests, NodeLocationCaptureTests) run against a
  fake that reproduces `recordNodeStatusSnapshot`'s 15-minute enrich-or-insert window, because a
  feature cannot use the Room store or `core:services`;
- the two `RemoteNodeModelTests` DataStore round trips test only the DTO role/permission logic
  (core:data's `RoomSourceTest` covers the persisted role);
- three `NodeContactInfoSectionTests` cases check only the state-holder half (edits arrive as
  `setOwnerInfo`, Apply sends the edited value); the UITextView typing path is not exercised;
- locale-dependent assertions (`Locale.current.measurementSystem`) take an injected
  `MeasurementSystem` and test every system;
- `syncTime` uses the injected clock instead of a real before/after bracket.

### Deferred

- `NodeContactInfoSectionTests::contact info text color stays readable while the field is focused()`:
  a UIKit focus-color/hit-test assertion, to be redone when the Compose Contact Info field exists.

Not ported (no test id attached): all Compose screens and rows (repeater/room settings, status,
history, login sheet, add-region sheet, CLI tab, the tab picker and glass filter bar); chart
drawing, scrub gestures and haptics (math and models are ported); MapLibre rendering, camera
animation, pin sprites and attribution (pins, lines, regions and filters are ported); VoiceOver
announcement posting (the decision logic is ported); `RepeaterSettingsView`, `RoomSettingsView`,
`SharedNodeSettingsViews`, `NodeManagementTabPicker` are layout-only apart from the pieces named in
the code (clock-drift warning, load placeholder, owner-info counter). Room conversation files are
owned by another WP.

## Mutation checks

Each mutation was applied to a clean tree, the feature tests run, then the file restored
(`git checkout`). Results from the JUnit XML:

| Mutation | Failing tests |
| --- | --- |
| Name byte cap `>` to `>=` | `NodeSettingsValidationTest#name at byte cap is valid` |
| Late-reply duplicate guard removed | `NodeSettingsLateRecoveryTest#mesh duplicate of an answered reply is not recovered for another query` |
| Default-scope clear ignores the firmware gate | `RepeaterDefaultScopeTest#removeRegion of current default skips clear before repeater v1_15` |
| Region dump cap `>=` to `>` | `RepeaterRegionParseTest#parseRegionTree rejects a saturated newline-terminated dump` |
| SNR "good" threshold made non-strict | `MapPrimitivesTest#snrQualityBucketsUseStrictGreaterThresholds`, `NeighborSNRMapBuilderTest#snrBucketsMapToTheExpectedTraceLineStyle` |
| Empty prefix matches a session | Survived at first (no test); added `NodeStatusSessionMatchTest`, then `empty prefix never matches a session` fails |

## Swift oracles

`oracles/*.swift.txt` are the swiftc programs used to check Foundation-dependent output (rename to
`.swift` to run): number formatting and unit conversion (`numbers`, `numbers_default`),
`localizedStandardCompare` (`compare`), CLI argument text and device-time dates (`settings_text`),
region parser edge cases (`settings_regions`), abbreviated duration formatting (`drift`),
location report formatting and calendar arithmetic (`history_location`), road-usage distance text
(`map_road_usage`) and stable SHA-256 pin ids (`map_badge_ids`).

## Not claimed

Original-case counts are source-case parity for the logic layer only. They do not establish UI
parity, map rendering, real BLE/service integration (the ports are driven by fakes), or
CI/acceptance results.
