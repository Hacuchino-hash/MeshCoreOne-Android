# WP-318 evidence

External contribution, no lease. Local macOS runs only, not CI or acceptance receipts, and no device verification is claimed.
Frozen source: `db14559b39d32322b06477c6ae676112f583db50`. Base: `ffb7ff60` (origin/main). Scope: appearance, backup/restore,
log export, about/licenses, What's New and the IAP-free support screen in `android/feature/settings`; see
[WP-318.md](../../deviations/WP-318.md) for ownership, seams, adaptations and deferred items.

## Commands

Environment: JDK 21, Android command-line SDK, private Gradle/Android homes, `--dependency-verification lenient`.

```
android/gradlew -p android :feature:settings:testDebugUnitTest
android/gradlew -p android :feature:settings:lintDebug validateModuleGraph :app:assembleDebug
python tools/android-port/portmap.py
python tools/android-port/controller/validate.py
```

## Observed results

- `:feature:settings:testDebugUnitTest`: 9 suites, 84 tests, 0 failures, 0 errors, 0 skipped.
- `:feature:settings:lintDebug`, `validateModuleGraph`, `:app:assembleDebug`: build successful.
  `lintDebug` reports only `LocalContextResourcesRead` warnings (WP-318.md D-9).
- `portmap.py` and `controller/validate.py` exit 0.

## Source id coverage

47 owned ids in `docs/android/test-cases.json` (7 test files). Per-case class, method, disposition and JUnit result are in
[`source-cases.json`](source-cases.json).

| Status | Count | Notes |
| --- | --- | --- |
| Ported and passing | 37 | 30 source-behavior, 7 platform-adaptation (StoreKit entitlement cases, `UIImage(systemName:)`, security-scoped URL, services-store wiring) |
| Deferred | 10 | `NodeConfigImportViewModelTests` (radio `NodeConfigService`; WP-318.md D-8) |
| Missing | 0 | |

Native (not source-bound) tests cover: SAF/low-space/revoked-URI/oversize/superseded-parse/cancel/effects flows, the import-result
presenter, backup error mapping, log export formatting and flow, appearance selection and failure, About/Support/Licenses content,
and the WhatsNew catalog.

## Not covered

Compose rendering, gestures, TalkBack and the system pickers are not exercised (no compose-ui-test/Robolectric on the locked
classpath and no device). See WP-318.md "Not verifiable without a device".
