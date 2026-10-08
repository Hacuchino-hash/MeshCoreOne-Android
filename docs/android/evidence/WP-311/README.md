# WP-311 evidence

External contribution, no lease. Local macOS runs only, not CI or acceptance receipts. Frozen
source: `db14559b39d32322b06477c6ae676112f583db50`. Base: `372fbc58` (origin/main at the time,
not rebased). Scope is the logic layer of `android/feature/nodes`; see
[WP-311.md](../../deviations/WP-311.md) for ownership, seams and adaptations.

## Commands

Environment: JDK 21, Android command-line SDK, private Gradle/Android homes,
`--dependency-verification lenient`.

```
./gradlew :feature:nodes:cleanTestDebugUnitTest :feature:nodes:testDebugUnitTest \
  :feature:nodes:lintDebug validateModuleGraph --dependency-verification lenient   # x3
python3.13 tools/android-port/portmap.py
python3.13 tools/android-port/controller/validate.py
```

## Observed results

- Three consecutive runs, all exit 0. JUnit XML each time: 15 suites, 173 tests, 0 failures,
  0 errors, 0 skipped. (Runs 2 and 3 reused up-to-date lint/graph results.)
- `lintDebug`: "No issues found." `validateModuleGraph`: 30 modules, no forbidden edges.
- 173 tests = 96 bound to source ids + 77 native tests (state-holder behavior, discovery timing,
  scan/add flows, string-semantics oracle pins).
- `portmap.py` passed (exit 0). `controller/validate.py` result is in the final report.

## Source id coverage

98 owned ids in `docs/android/test-cases.json` (8 test files). Per-case class, method,
disposition and JUnit result are in [`source-cases.json`](source-cases.json).

| Status | Count | Notes |
| --- | --- | --- |
| Ported and passing | 96 | 91 source-behavior, 3 native-equivalent, 2 platform-adaptation |
| Deferred | 2 | `ContactShareContentTests` "uri is meshcore contact/add and round-trips" and "uri preserves contact type": need WP-209's real URI encoder and WP-405's parser (WP-311.md B-1) |
| Missing | 0 | |

Native-equivalent: `loadContacts keeps an upserted contact...`, `deleteDiscoveredNode reapplies...`
(in-memory store double instead of SwiftData), `bounded command times out` (virtual clock).
Platform-adaptation: the two `ContactURIActivityItemTests` cases (text/plain share payload).

No owned test id covers layout, maps or QR camera, so none is listed as deferred. Those
surfaces (Compose screens, `ContactLocationSection`/`ContactFullMapView`, camera scanning, QR
bitmap rendering) are unbuilt and not claimed. They wait on core:ui/WP-302, MapLibre and
CameraX/ZXing admission (WP-311.md B-2 to B-4).

## Oracles

`oracle/*.swift.txt` are the `swiftc` programs (Apple Swift 6.3.2, macOS) used to pin Foundation
string behavior, with their outputs next to them. `SwiftTextTest` asserts those outputs.

## Mutation checks

Each mutation was applied to one main file, the module tests run, then the file restored (tree
verified clean). All nine were caught. The run was interrupted once; the first mutation's
result is from the original run and the other eight from the resumed run.

| Mutation | File | Failing tests |
| --- | --- | --- |
| Hop sort puts unknown (flood) hop counts first | `contacts/ContactsSorting.kt` | 3 |
| Confirmed delete no longer masks the row | `contacts/ContactsStateHolder.kt` | 1 |
| Delete ignores the pending guard | `contacts/ContactsStateHolder.kt` | 1 |
| Save no longer refuses an unresolvable narrow hop | `path/PathEditing.kt` | 3 |
| Late discovery response accepted after timeout/cancel | `path/PathDiscoveryRunner.kt` | 2 |
| Folded contains ignores grapheme boundaries | `text/SwiftText.kt` | 1 |
| Bulk codes are not de-duplicated | `path/HopCodeParser.kt` | 1 |
| Scan does not claim before the lookup | `add/ScanContactStateHolder.kt` | 1 |
| Ambiguous contacts fall through to discovered nodes | `path/PathManagementStateHolder.kt` | 1 |
