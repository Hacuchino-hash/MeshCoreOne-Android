# WP-314 evidence

External contribution, no lease. Local macOS runs only, not CI or acceptance receipts.
Frozen source `db14559b39d32322b06477c6ae676112f583db50`; base `372fbc58` (origin/main).
Scope: logic layer only (see `docs/android/deviations/WP-314.md`).

## Commands

Environment: JDK 21, Android command-line SDK, private Gradle/Android homes, `--dependency-verification lenient`.

```
./gradlew :feature:tools:cleanTestDebugUnitTest :feature:tools:testDebugUnitTest :feature:tools:lintDebug validateModuleGraph   # x3
python3.13 tools/android-port/portmap.py
python3.13 tools/android-port/controller/validate.py
```

## Observed results

- `:feature:tools:testDebugUnitTest`: 13 suites, 195 tests, 0 failures, 0 errors, 0 skipped (three clean runs at 193 tests, then two tests were added by the mutation review; the final run is 195).
- `lintDebug`: no issues. `validateModuleGraph`: passed; feature:tools edges are core:contracts/ui/l10n only.
- 139 owned source cases (`docs/android/test-cases.json`): **139 bound with `@OriginalCase` and passed** in the JUnit XML, 0 unbound, 0 unknown bindings. Per-case class/method/disposition/result: [`source-cases.json`](source-cases.json). 56 further tests are native WP-314 tests (execution/timeouts, batch, cancellation, save, discovery, saved paths, map logic, navigation, oracle vectors).
- Ids by suite: TracePathViewModelTests families 104, RepeaterResolverTests 18 (the parameterized `key display byte count` family is one id covering its 5 argument rows), BatchTrace families 15, TracePathListenerTests 2.

## Deferred (no source test ids)

Every owned test id is logic, so none is deferred. Deferred are production-only UI/map items with no unit tests in the source: Compose views (`TracePathView`, `TracePathListView`, rows, sheets, `SavedPath*View`, `NodeDiscoveryView/RowView`, `Tools*View/Column`, `ToolDestinationView`, `MiniSparkline`, `TotalDistanceRow`, `DistanceInfoSheetView`) and map rendering (`TracePathMapView`, toolbar/floating/actions sections, map lines/badges/camera of `TracePathMapViewModel`). Needs core:ui, WP-302 shell and MapLibre admission.

## Oracles (`oracles/`, `.swift.txt` sources + recorded output; swiftc 6.3.2, macOS 26.5.1)

`distance` (CLLocation vectors), `text` (trimming, isHexDigit, Character count, uppercased, canonical equality, localized compares), `ws` (full `.whitespaces` scalar enumeration), `codes` (verbatim copies of the code parser and width inference run on edge inputs).

## Mutation checks

Each applied to main code from a clean test run, then restored (final tree re-tested green):

| Mutation | Failing tests |
| --- | --- |
| Response tag not checked | 3 |
| Device id not checked | 1 |
| No 500 ms inter-trace gap | 1 |
| `canSavePath` ignores path change | 1 (after adding a test; the source test was vacuous because `addNode` clears the result) |
| Match kind always exact | 1 (after adding a test) |
| Batch send-suspended guard removed | 1 |
| Scan duration x100 | 3 |

Survivor: dropping the odd-length guard in `autoReturnOutboundCount` is an equivalent mutant (an even-length list can never equal its mirror).
