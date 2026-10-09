# WP-401 implementation evidence

- Repository: `cbattlegear/MeshCoreOne-Android`
- Work package: `WP-401`
- Owner: `notifications-widgets-engineer`
- Integrated base: `2fc6cb6f59f49be94f033fdad32100c3fc5db055`
- Frozen Swift source: `db14559b39d32322b06477c6ae676112f583db50`
- Issue: `#127`

## Implemented acceptance

- Native message/activity/device-alert channels with audible and silent variants.
- `MessagingStyle`, stable conversation shortcuts, private lock-screen redaction and badge propagation.
- Explicit direct-reply, mark-read and open actions with typed content-free payload extras.
- Bounded cold-start retry, current-ready-session routing and stale-radio preservation.
- Existing API 33+ permission flow reused; API 31-32 remains authorized without a runtime permission.

## Verification

Pinned session-local JDK 21.0.12.1 and Android SDK 37.2/build-tools 37.0.0 were used.

1. `.\gradlew.bat :platform:notifications:testDebugUnitTest --max-workers=1 --quiet`
   - Result: passed.
   - Discovered: 6; passed: 6; failed: 0; errors: 0; skipped: 0.
2. `.\gradlew.bat :platform:notifications:lintDebug :platform:notifications:assembleDebug :platform:notifications:assembleDebugAndroidTest --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1536m -Dfile.encoding=UTF-8' --quiet`
   - Result: passed.
   - Produced `notifications-debug.aar` and `notifications-debug-androidTest.apk`.
3. `.\gradlew.bat :core:services:test --tests 'com.meshcoreone.android.core.services.notifications.*' --max-workers=1 --quiet`
   - Result: passed.
   - Discovered: 85; passed: 85; failed: 0; errors: 0; skipped: 0.
4. `.\gradlew.bat :app:assembleDebug --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx6g -XX:MaxMetaspaceSize=2g -Dfile.encoding=UTF-8' --quiet`
   - Result: passed; produced `app-debug.apk`.
5. `python -m unittest discover -s tools\android-port\tests -p 'test_*.py'`
   - Result: passed.
   - Discovered: 242; passed: 242; failed: 0; errors: 0; skipped: 0.
6. `.\gradlew.bat :resolveScaffoldDependencies :validateModuleGraph :platform:notifications:testDebugUnitTest --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1536m -Dfile.encoding=UTF-8' --quiet`
   - Result: passed after resolving every strict dependency-lock configuration.
   - Notification tests: 6 discovered/passed; 0 failed/errors/skipped.

No Android device/emulator, launcher badge, OEM channel UI, physical radio, signing or release
verification is claimed.
