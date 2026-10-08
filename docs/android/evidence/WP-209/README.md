# WP-209 evidence

**External contribution:** this was prepared outside the dispatch controller, with
no lease or receipt. This document lists the commands that actually ran and their
observed results.

- **Reference:** `db14559b39d32322b06477c6ae676112f583db50`
- **Base:** `main` @ `7237727f`
- **Host:** macOS 26, JDK 21.0.12.1, Gradle 9.8.0 via the wrapper

| Command | Observed |
| --- | --- |
| `./gradlew :core:contracts:test --rerun :core:services:test --rerun validateModuleGraph --rerun` | `BUILD SUCCESSFUL`; `:core:services` **278 tests, 0 failures, 0 errors, 0 skipped** (16 XML files); `:core:contracts` **4 tests, 0 failures, 0 errors, 0 skipped**; module graph verified (30 modules) |
| `./gradlew -I jdk17-release.init.gradle :core:contracts:compileKotlin :core:services:compileKotlin --rerun-tasks` (adds `-Xjdk-release=17`) | `BUILD SUCCESSFUL` |
| `python tools/android-port/portmap.py` | exit 0 |
| `python tools/android-port/controller/validate.py` | exit 0 |

All **176** source case ids that `docs/android/test-cases.json` assigns to
WP-209's test files run under their exact ids. None are missing.

| Area | Source ids | Total tests |
| --- | ---: | ---: |
| ContactService, sync, share utilities | 48 | 83 (incl. 26 native + 3 real-session) |
| ChannelService, pipeline, flood scope, hashtags | 53 | 84 (incl. 22 native) |
| AdvertisementService + delta sync | 75 | 86 (incl. 11 native) |
| Neutral fault projection (A-05) | 0 | 25 (all native) |

- **Repeat runs:** the advertisement suite passed 7 consecutive times, including forced reruns. The channel suite passed 3 consecutive forced reruns.
- **Mutation checks:**
  - **Caught:** path-update escalation, stop not waiting for a delete flush, nickname trimming, and clearing a cancellation-safe claim.
  - **Not caught:** recording the busy-round sync end time. The source suite doesn't pin it either.

## Neutral fault declaration (A-05)

The frozen declaration that PR #50 and PR #33 carry byte-exact:

- **Path:** `android/core/contracts/src/main/kotlin/com/meshcoreone/android/core/contracts/domain/errors/ContactsChannelAdvertisementFaults.kt`
- **SHA-256:** `1b7f21f5ff827e88462a2ac6c722a681c8c24dd9d496d130da9b3139d6e5a592`
- **Git blob:** `3f1f2077a0846242b1bbec669ce6e0ac913d1bae`

`SourceServiceFaultProjectionTests` (25 `WP-209::` cases) runs the projection for real:

- **Per case (21 tests):** constructs every producer case: Contact 8, Channel 9, Advertisement 4. The session cases use real `MeshCoreException`s (`DeviceError(3)`, `Timeout`, and `ConnectionLost` with an inner cause). Each test projects through the `SourceServiceFaultCarrier` interface and checks four things:
  - the exact family and case;
  - equal payload values, with `assertSame` on the reason strings and on the session cause;
  - that the projection is stable;
  - that the producer's message, `errorDescription`, `toString`, raw fields and cause are unchanged.
- **Exhaustiveness (4 tests):** `kotlin-reflect` isn't on the module's classpath, so these tests read the JVM `PermittedSubclasses` of each producer and payload type. They require 8, 9 and 4 cases on both sides, every case exercised once, a one-for-one mapping with matching case names, and each producer landing in its own family under `SourceServiceFault`.
- **Mutation check:** one projection per family was broken on purpose:
  - Contact `SendFailed` mapped to `InvalidResponse`;
  - Channel `CircuitBreakerOpen` given `consecutiveFailures + 1`;
  - Advertisement `SessionError` given a fresh `MeshCoreException`.

  Each was caught by a failing test, then reverted (`git diff` empty).
