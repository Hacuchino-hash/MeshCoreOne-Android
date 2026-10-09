# WP-305 evidence

Local macOS run (JDK 21, Gradle, `--dependency-verification lenient`, `-x :core:ui:verifySharedUiInputs`):
`:feature:onboarding:testDebugUnitTest :feature:onboarding:lintDebug :app:assembleDebug validateModuleGraph`
succeeded. Results are in `android/feature/onboarding/build/test-results/testDebugUnitTest/*.xml` (not committed).

- Source ids: all 16 `OnboardingStateTests` ids are carried by `@OriginalCase` in
  `OnboardingStateTests.kt` (5 `ported`, 11 `adapted` dispositions); each test
  prints a `WP305_CASE|<base64 id>|<disposition>|class#method` line only after its assertions return.
- Additional behavior tests (pairing cancel/other-app/Bluetooth blockers, WiFi, region manual/no-network,
  preset retry/demo, full demo journey, resume path, animation model, frequency format) are
  `OnboardingBoundaryTests.kt` and `OnboardingUiLogicTest.kt`.
- Mutation checks (all failed as expected, then restored): resume guard `||`->`&&`, preset
  `MAX_RETRIES` 3->9, WiFi host normalization removed.
- Not covered: Compose UI tests (deps not locked) and anything listed under "Unverifiable without a
  device" in `docs/android/deviations/WP-305.md`.
