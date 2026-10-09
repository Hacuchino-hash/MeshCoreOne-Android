# WP-317 evidence

External contribution, no lease. Local macOS runs only, not CI or acceptance receipts.
Frozen source `db14559b39d32322b06477c6ae676112f583db50`; base `ffb7ff60` (origin/main, includes WP-312).
Scope: settings logic (state holders, ports, policies) plus Compose screens. Adaptations: `docs/android/deviations/WP-317.md`.

## Commands

Environment: JDK 21, Android command-line SDK, private Gradle/Android homes, `--dependency-verification lenient`.

```
./gradlew :feature:settings:testDebugUnitTest :feature:settings:lintDebug :app:assembleDebug validateModuleGraph
python3.13 tools/android-port/portmap.py
python3.13 tools/android-port/controller/validate.py
```

## Observed results

- `:feature:settings:testDebugUnitTest`: **193 tests, 0 failures, 0 errors, 0 skipped** (JUnit XML in
  `android/feature/settings/build/test-results/testDebugUnitTest`).
- `lintDebug`: 0 issues (`lint-results-debug.xml`). `:app:assembleDebug` and `validateModuleGraph`: passed; feature:settings edges are
  `core:contracts`/`core:ui`/`core:l10n` only.
- 29 owned source case ids (`docs/android/test-cases.json`: `DeviceSelectionFilterTests` 12, `PresetLocationPolicyTests` 16,
  `DangerZoneViewModelForgetTests` 1): **29 bound with `@OriginalCase` and passed**, 0 unbound, 0 unknown bindings. Per-case
  class/method/disposition/result: [`source-cases.json`](source-cases.json). Dispositions: 22 `source-behavior`, 6 `platform-adaptation`
  (4 registry-less device-selection cases, `useMyLocationAction`, `actionAfterAuthorizationWait`), 1 `adapted-service-fake`.
  164 further tests are native WP-317 tests (every section holder, parsing oracles, navigation).
- No Compose UI test ran (not on the locked classpath); the screens compile and lint clean only.

## Deferred (no source test ids)

All owned test ids are logic, so none is deferred. Deferred production items are listed in the deviations doc: blocked-sender and
trusted-contact lists, language, link/map-preview toggles, Wi-Fi section and edit sheet, the iOS-only pairing setup sheet, and the
location picker map.

## Oracles (`oracle*.swift.txt` sources + `oracle*-output.txt`; swiftc 6.3.2, macOS 26)

`oracle`: `TextField` number parsing (`FloatingPointFormatStyle` POSIX, `IntegerFormatStyle` en_US), frequency display and rounding,
`Int8`/`UInt8`/`UInt32` exact conversions, BLE PIN `UInt32(String)`, `NumberFormatter` bandwidth fallback, `nearestBandwidth`, hex
sanitising and reserved prefixes, `localizedStandardCompare` ordering, custom OCV parsing. `oracle2`: grouping and exponent edge cases.
`oracle3`: `CharacterSet.whitespaces`/`.whitespacesAndNewlines` membership and `Int(String)` after trimming.

## Mutation checks

Each applied to main code, the settings JUnit suite run, then restored (git checkout; final tree re-tested green):

| Mutation | Failing tests |
| --- | --- |
| Manual radio: TX power upper bound (`maxTxPower`) check removed | 2 |
| Preset location: appear-time commit replaces a manual choice | 1 (ported `appear commit keeps manual Portugal over California GPS`) |
| Factory reset skips the local device cleanup | 2 |
