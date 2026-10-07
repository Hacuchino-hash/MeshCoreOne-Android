# WP-315 evidence (line-of-sight logic layer)

This is an external contribution with no lease. All results come from local
macOS runs; none are CI or acceptance receipts. The frozen source is
`db14559b39d32322b06477c6ae676112f583db50` and the base is `origin/main`
`372fbc58`. The change covers the logic layer only; [WP-315.md](../../deviations/WP-315.md)
lists what is deferred.

## Commands

The runs used JDK 21 (`/opt/homebrew/opt/openjdk@21`), the Android command-line
SDK, and private Gradle/Android user homes. `--dependency-verification lenient`
is needed because the macOS aapt2 artifacts are not in the verification
metadata. No dependencies or locks were changed.

```
./gradlew :feature:tools:testDebugUnitTest --rerun :feature:tools:lintDebug validateModuleGraph \
  --dependency-verification lenient --console=plain        # x3
python3.13 tools/android-port/portmap.py
python3.13 tools/android-port/controller/validate.py
swiftc -O wp315_oracle.swift -o wp315_oracle && ./wp315_oracle   # Apple Swift 6.3.2, CLT only
```

## Observed results

- `:feature:tools:testDebugUnitTest` ran three times. Every run had 8 suites,
  137 tests, 0 failures, 0 errors and 0 skipped. The suite counts are
  ChartCoordinateSpace 7, FresnelZoneRenderer 10, LineOfSightAnalysis 31,
  LineOfSightSelection 36, LineOfSightValues 22, LineOfSightWorkflow 15,
  LosFormatting 6 and TerrainProfileChart 10.
- Owned source cases: 103 in `docs/android/test-cases.json`, made up of
  `LineOfSightViewModelTests` 89, `ChartCoordinateSpaceTests` 6 and
  `FresnelZoneRendererTests` 8. All 103 are bound with `@OriginalCase` and
  passed in the JUnit XML: 101 are source-behavior and 2 are
  platform-adaptation (an explicit `Locale` for `xLabel`, and the "Dropped
  pin" text resolved from the WP-005 resource). Unbound: 0. Deferred: 0.
  [`source-cases.json`](source-cases.json) has the per-case
  class/method/disposition/result.
- The other 34 tests are Android-only. They pin the terrain-chart geometry,
  the hill-profile Fresnel samples, the mirrored RF math, the
  ICU/printf formatting, the frequency grammar and the operator-workflow
  routing to the oracle or to the source view code.
- In the view-model cases, the elevation and RF ports are test doubles. The
  source mock covers elevation. The RF double records slices, heights and RF
  inputs but does not reproduce WP-212 clearance numerics, which WP-212's own
  tests cover. The source `Preselected contact sets point A()` used the live
  `ElevationService`; here it uses the fake.
- Async waits run on a single-thread runner with a virtual clock. There is no
  coroutines-test, Robolectric or wall-clock sleeping.
- `:feature:tools:lintDebug` found no issues. `validateModuleGraph` verified
  30 modules with no forbidden production edges, and feature:tools gained no
  new dependency.

## Numeric oracle

[`wp315_oracle.swift.txt`](wp315_oracle.swift.txt) holds verbatim copies of
the frozen Swift math. SwiftUI `EdgeInsets` is replaced with a same-shaped
struct. The file was compiled with `swiftc` from the Command Line Tools, and
its output is [`wp315_oracle.out.txt`](wp315_oracle.out.txt) (sha256
`f9d9b2cd…a6951b0d`; source sha256 `f46d74a1…6a4449b`). The tests assert the
printed bit patterns or strings. The hill-profile input elevations come from
the oracle bits, so JVM `sin` differences cannot leak in.

## Mutation checks

Each mutation was applied to one main file, the full suite was run, and the
file was restored. The main-file sha256 values matched the pre-mutation
snapshot afterwards. All 137 tests compiled every time, and every mutation
was caught:

| Mutation | Failing tests |
| --- | --- |
| `clearRepeater` stops cancelling the off-path elevation fetch | 1 (`clearRepeater cancels the in-flight off-path elevation fetch`) |
| Fresnel math uses global distances instead of segment-relative ones | 1 (`buildProfileSamples handles segment slice correctly (R→B case)`) |
| On-path A→R slice drops the shared junction sample | 2 (relay result, drag) |
| RF re-analysis no longer clears `isAnalyzing` | 3 (both task-hygiene re-analysis cases, frequency commit) |
| Formatting rounds the exact binary value instead of the shortest decimal | 3 (xLabel, loss and k-factor formatting) |
| Point B accepts point A's location | 1 (`Cannot set B to same location as A`) |
