# WP-307 acceptance evidence

- Repository: `cbattlegear/MeshCoreOne-Android`
- Work package / owner: `WP-307` / `chats-ui-engineer`
- Base: `f038047788eeccb78910a0e130e9e6609140aff2`
- Frozen Swift source: `db14559b39d32322b06477c6ae676112f583db50`
- Manifest source tree: `8918fdc604341e6996a68c88f6bb1c02b9c2f87e`
- Acceptance: `WP-307-behavior`, `WP-307-boundaries`, `WP-307-source-test-parity`

## Verified behavior

The feature owns chronological immutable timeline state, stable radio/message row keys, reverse-layout
projection, unread/day grouping, bounded initial and older paging, deduplication across page/event races,
one-shot initial anchors, persisted scroll/draft state, jump-to-latest badges, generation/event guards,
in-place status/round-trip updates, single-flight retry, delivered-ACK protection, reconnect window refresh,
and explicit loading/empty/passive/load/send failures. The route uses lifecycle-aware collection and does
not own the radio session.

## Commands and discovery

All commands used the repository's checksum-pinned JDK 21.0.12.1 and Android 37.2 / build-tools 37.0.0
provisioner in session-local storage. The first direct Gradle attempt failed closed because the host had no
Android SDK; no result was claimed from it.

```text
python tools\android-port\controller\ci.py provision --root <session-toolchain> --accept-sdk-license
PASS: pinned archives verified and extracted; this is tooling readiness only.

cd android
.\gradlew.bat :feature:chats:testDebugUnitTest --tests "com.meshcoreone.android.feature.chats.timeline.*" --console=plain
PASS: 26 discovered, 26 passed, 0 failed, 0 errors, 0 skipped.

.\gradlew.bat :feature:chats:lintDebug :feature:chats:testDebugUnitTest --console=plain
PASS: 132 discovered, 132 passed, 0 failed, 0 errors, 0 skipped.
PASS: Android lint 0 errors, 3 pre-existing WP-306 LocalContextResourcesRead warnings.

python tools\android-port\controller\validate.py
PASS: valid; 1,866 tracked reference files, 1,812 owned files, 65 work packages,
185 dependency edges; manifest SHA-256 ceb84b5e26fcc9ece5c0b3fb6c68b4d2965f9f24114fa81b7434ff73d1ed7904.
```

Test discovery comes from Gradle's JUnit XML (`TEST-*.xml`), not source-file counts. Reproducible hosted
results remain CI-authoritative; this directory intentionally contains no copied logs or CI-result bundle.

## Limits

No physical device, hardware radio, screenshot, TalkBack, IME, signing, iOS execution, or protected-gate
verification occurred. See `docs/android/deviations/WP-307.md` for native boundaries.
