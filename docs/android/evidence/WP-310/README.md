# WP-310 implementation evidence

- Repository: `cbattlegear/MeshCoreOne-Android`
- Work package: `WP-310`
- Owner: `chats-ui-engineer`
- Base: `ffb7ff6092fc07c70ea0f31020cf68907d6c4836`
- Frozen Swift source: `db14559b39d32322b06477c6ae676112f583db50`
- Issue: `#118`

## Implemented acceptance

- `WP-310-behavior`: room login/contact lookup, reconnect, message sync/read side effects, ordered live
  arrivals, post/retry and guest/member states; channel option/create/private/public/hashtag/link join,
  share URI/QR callback and region propagation.
- `WP-310-boundaries`: typed failures, exact 16-byte secrets, 31-byte UTF-8 names, slot-zero reservation,
  available-slot ordering, hashtag full-name passphrase, cancellation propagation, deterministic room
  ordering/grouping and Material 3 sheet/accessibility behavior.
- `WP-310-source-test-parity`: frozen room ordering and channel validation/confirmation families are bound
  to 26 Android assertions. Translation is separately owned by WP-406.

## Verification

Pinned session-local JDK 21.0.12.1 and Android SDK 37.2/build-tools 37.0.0 were used.

1. `.\android\gradlew.bat -p android :feature:chats:compileDebugKotlin --console=plain`
   - Result: passed.
2. `.\android\gradlew.bat -p android :feature:chats:testDebugUnitTest --tests 'com.meshcoreone.android.feature.chats.rooms.*' --tests 'com.meshcoreone.android.feature.chats.channels.*' --console=plain`
   - Result: passed.
   - Discovered: 26; passed: 26; failed: 0; errors: 0; skipped: 0.
3. `.\android\gradlew.bat -p android :feature:chats:testDebugUnitTest --console=plain`
   - Result: passed.
   - Discovered: 158; passed: 158; failed: 0; errors: 0; skipped: 0.

No Android device, camera, physical radio, hardware, signing or release verification is claimed.
